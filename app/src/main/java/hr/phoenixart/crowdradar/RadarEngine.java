package hr.phoenixart.crowdradar;

import android.Manifest;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.*;
import hr.phoenixart.crowdradar.core.*;
import org.json.*;
import java.util.*;

/** Application-owned session. No Activity, View, camera, or UI listener is retained here. */
final class RadarEngine {
    final Context context;final Handler handler=new Handler(Looper.getMainLooper());
    final Map<Integer,MainActivity.Peer> peers=new LinkedHashMap<>();
    final ProfileLink profiles;final BlePresence ble;final GpsLocator gps;final UwbLocator uwb;final NearbyLink nearby;final AwareLink aware;
    volatile RelayRouter relay=new RelayRouter(0);long lastAdvertise,lastQuery;int routeCursor;
    final ShortMessages messages=new ShortMessages();final LinkHealth health=new LinkHealth();
    byte[] groupKey;DeviceProfile own;String groupName,note="Kreiraj grupu ili se pridruži kolegama.",mailStatus="";long groupRevision;
    boolean searching,starting,nearWanted=true,uiVisible;Integer selectedSession;int unreadMessages,session,pingTarget;long pingId,pingAt,lastBleTry,lastNearTry,lastAwareTry,lastUwbTry;
    int uwbAttempts;RadarService service;
    RadarEngine(Context c){
        context=c.getApplicationContext();own=DeviceProfile.local(context);loadGroup();gps=new GpsLocator(context);uwb=new UwbLocator(context);
        profiles=new ProfileLink(context,(id,profile,name,revision)->{
            MainActivity.Peer peer=peers.get(id);if(peer==null)return;peer.profile=profile;peer.alias=profile.name;
            if(revision>groupRevision&&!name.trim().isEmpty()&&name.length()<=40){groupName=name;groupRevision=revision;context.getSharedPreferences("groupInfo",0).edit().putString("name",name).putLong("revision",revision).apply();}
        });
        profiles.stateSupplier=()->{JSONObject state=new JSONObject();try{JSONObject g=gps.wire();if(g!=null)state.put("g",g);JSONObject u=uwb.wire();if(u.length()>0)state.put("u",u);
            if(selectedSession!=null)state.put("f",selectedSession);
            if(pingId!=0&&SystemClock.elapsedRealtime()-pingAt<10000)state.put("p",new JSONObject().put("id",pingId).put("target",pingTarget));
        }catch(Exception ignored){}return state;};
        profiles.stateListener=(id,data,latency)->{
            MainActivity.Peer peer=peers.get(id);if(peer==null)return;peer.stateAt=SystemClock.elapsedRealtime();peer.hasGeo=false;peer.geoViaMesh=false;
            JSONObject g=data.optJSONObject("g");if(g!=null)try{double lat=g.getDouble("la"),lon=g.getDouble("lo"),error=g.getDouble("ac");long age=Navigation.receivedFixAge(g.getLong("age"),latency,0);
                if(Navigation.validFix(lat,lon,error,age)){peer.lat=lat;peer.lon=lon;peer.error=error;peer.fixAge=age;peer.geoAt=SystemClock.elapsedRealtime();peer.hasGeo=true;}}
            catch(Exception ignored){}
            uwb.remote(id,data.optJSONObject("u"));
            if(data.has("f")&&data.optInt("f")==session&&selectedSession==null)focus(id);
            JSONObject ping=data.optJSONObject("p");if(ping!=null&&ping.optInt("target")==session&&ping.optLong("id")!=0&&ping.optLong("id")!=peer.lastPing){peer.lastPing=ping.optLong("id");signal("PING od: "+peer.alias,true);}
        };
        profiles.meshSupport=(id,yes)->{MainActivity.Peer peer=peers.get(id);if(peer!=null)peer.canRelay=yes;};
        profiles.meshSupplier=this::relayWire;profiles.meshListener=(id,data,latency)->receiveRelay(id,data);
        profiles.mailSupport=(id,yes)->{MainActivity.Peer peer=peers.get(id);if(peer!=null)peer.canMessage=yes;};
        profiles.mailSupplier=this::messageWire;profiles.mailListener=(id,data,latency)->receiveMessages(id,data);
        NearbyLink.Listener receiver=new NearbyLink.Listener(){
            public JSONObject snapshot()throws Exception{return new JSONObject().put("profile",own.json()).put("group",groupName).put("revision",groupRevision).put("state",profiles.stateSupplier.get()).put("mail",messageWire()).put("x",relayWire());}
            public void received(int id,JSONObject frame,long latency){if(!searching)return;try{
                DeviceProfile profile=DeviceProfile.parse(frame.getJSONObject("profile"));MainActivity.Peer peer=peers.get(id);
                if(peer==null){if(peers.size()>=24)return;peer=new MainActivity.Peer(id,-127);peers.put(id,peer);}
                profiles.listener.profile(id,profile,frame.optString("group"),frame.optLong("revision"));profiles.stateListener.state(id,frame.getJSONObject("state"),latency);receiveMessages(id,frame.getJSONObject("mail"));if(frame.has("x")){peer.canRelay=true;receiveRelay(id,frame.optJSONObject("x"));}
            }catch(Exception ignored){}}
        };
        nearby=new NearbyLink(context,receiver);aware=new AwareLink(context,receiver);
        ble=new BlePresence(context,new BlePresence.Listener(){
            public void peer(int id,int rssi,BluetoothDevice device){if(!searching)return;MainActivity.Peer peer=peers.get(id);
                if(peer==null){if(peers.size()>=24)return;peer=new MainActivity.Peer(id,rssi);peers.put(id,peer);}
                peer.rssi=RadarMath.smooth(peer.rssi,rssi);peer.seen=SystemClock.elapsedRealtime();profiles.fetch(id,device);
            }
            public void status(String text){note=text;}
        });
    }
    void loadGroup(){try{groupKey=new GroupStore(context).load();}catch(Exception e){groupKey=null;}
        groupName=context.getSharedPreferences("groupInfo",0).getString("name","Moja grupa");groupRevision=context.getSharedPreferences("groupInfo",0).getLong("revision",0);}
    boolean wifiAllowed(){return Build.VERSION.SDK_INT<33||context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)==PackageManager.PERMISSION_GRANTED;}
    void start(){if(searching){starting=false;return;}starting=false;loadGroup();if(groupKey==null){note="Nema spremljene grupe.";return;}
        searching=true;peers.clear();selectedSession=null;uwbAttempts=0;session=new java.security.SecureRandom().nextInt();relay=new RelayRouter(session);lastAdvertise=lastQuery=0;
        profiles.start(groupKey,session,own);lastBleTry=lastNearTry=lastAwareTry=lastUwbTry=0;handler.post(tick);
        note="Automatsko povezivanje pokrenuto • BLE / Nearby / Wi-Fi Aware";
    }
    void focus(int id){if(!Objects.equals(selectedSession,id)){uwb.stop();uwbAttempts=0;lastUwbTry=0;health.reset();}selectedSession=id;radioFocus(id);}
    boolean directFresh(MainActivity.Peer peer){return searching&&(nearby.live(peer.id)||aware.live(peer.id)||(peer.canMessage&&Navigation.fresh(peer.stateAt,SystemClock.elapsedRealtime(),6000))||(peer.seen>0&&SystemClock.elapsedRealtime()-peer.seen<=12000));}
    boolean fresh(MainActivity.Peer peer){return directFresh(peer)||(searching&&Navigation.fresh(peer.relayAt,SystemClock.elapsedRealtime(),15000));}
    boolean relayed(MainActivity.Peer peer){return !directFresh(peer)&&peer.relayHops>1;}
    void radioFocus(int endpoint){MainActivity.Peer peer=peers.get(endpoint);RelayRouter.Route route=relay.route(endpoint,SystemClock.elapsedRealtime());int hop=peer!=null&&directFresh(peer)?endpoint:route==null?endpoint:route.via;profiles.focus=hop;nearby.focus(hop);aware.focus=hop;}
    String routeLabel(MainActivity.Peer peer){if(!relayed(peer))return "";RelayRouter.Route route=relay.route(peer.id,SystemClock.elapsedRealtime());if(route==null)return "Put više nije potvrđen";StringBuilder text=new StringBuilder("TI");for(int i=route.path.size()-1;i>=0;i--){MainActivity.Peer hop=peers.get(route.path.get(i));text.append(" → ").append(hop==null?String.format(Locale.ROOT,"%04X",route.path.get(i)&65535):hop.alias);}return text.toString();}
    boolean canMessage(MainActivity.Peer peer){return peer.canMessage||nearby.live(peer.id)||aware.live(peer.id)||(relayed(peer)&&relay.route(peer.id,SystemClock.elapsedRealtime())!=null);}
    String links(MainActivity.Peer peer){if(relayed(peer))return "PREKO ČLANOVA • "+peer.relayHops+" VEZE • "+routeLabel(peer);String s="";if(nearby.live(peer.id))s="Nearby BT/Wi-Fi";if(aware.live(peer.id))s+=(s.isEmpty()?"":" + ")+"Wi-Fi Aware";
        if(peer.canMessage)s+=(s.isEmpty()?"":" + ")+"BLE GATT";return s.isEmpty()?"Nema svježe podatkovne veze":s;}
    void startUwb(MainActivity.Peer peer){if(!directFresh(peer)||peer.profile==null||!uwb.permitted()||(own.caps&peer.profile.caps&Capabilities.UWB)==0)return;
        lastUwbTry=SystemClock.elapsedRealtime();uwbAttempts++;uwb.start(session,peer.id,own,peer.profile);}
    final Runnable tick=new Runnable(){public void run(){if(!searching)return;long now=SystemClock.elapsedRealtime();
        if(!ble.running()&&now-lastBleTry>15000){lastBleTry=now;ble.start(groupKey,session);}
        if(nearWanted&&wifiAllowed()&&now-lastNearTry>20000&&(!nearby.active||!nearby.discoveryHealthy())){lastNearTry=now;nearby.start(groupKey,session,selectedSession);}
        if(wifiAllowed()&&!aware.active&&now-lastAwareTry>20000){lastAwareTry=now;aware.start(groupKey,session);aware.focus=selectedSession;}
        relayTick(now);if(selectedSession!=null)radioFocus(selectedSession);
        MainActivity.Peer target=selectedSession==null?null:peers.get(selectedSession);
        if(target!=null){int change=health.sample(fresh(target),now);if(change<0)signal("Izgubljena veza: "+target.alias,false);else if(change>0)signal("Veza ponovno uspostavljena: "+target.alias,false);}
        if(target!=null&&directFresh(target)){
            if(!uwb.ready&&!uwb.started&&uwbAttempts<3&&now-lastUwbTry>30000)startUwb(target);
            if(target.profile!=null&&(target.profile.caps&own.caps&Capabilities.RTT)!=0)aware.range(target.id);
        }
        peers.values().removeIf(p->now-Math.max(p.relayAt,Math.max(p.seen,p.stateAt))>120000&&!fresh(p));
        messages.pending(now);if(service!=null)service.update();handler.postDelayed(this,1000);
    }};
    JSONObject messageWire(){JSONObject data=new JSONObject();try{JSONArray ack=new JSONArray();for(String id:messages.acknowledgements())ack.put(id);data.put("acks",ack);
        ShortMessages.Entry e=messages.pending(SystemClock.elapsedRealtime());if(e!=null)data.put("msg",new JSONObject().put("id",e.id).put("to",e.peer).put("text",e.text));}catch(Exception ignored){}return data;}
    void receiveMessages(int id,JSONObject data){MainActivity.Peer peer=peers.get(id);if(!searching||peer==null)return;long now=SystemClock.elapsedRealtime();
        JSONArray ack=data.optJSONArray("acks");if(ack!=null)for(int i=0;i<Math.min(6,ack.length());i++)if(messages.acknowledge(id,ack.optString(i),now))mailStatus="Potvrđen primitak od "+peer.alias+".";
        JSONObject incoming=data.optJSONObject("msg");if(incoming!=null){ShortMessages.Entry accepted=messages.receive(id,session,incoming.optInt("to",session^1),incoming.optString("id"),peer.alias,incoming.optString("text"),now);
            if(accepted!=null){unreadMessages++;mailStatus="Poruka od "+peer.alias;signal(mailStatus,false);}}
    }
    JSONObject relayWire(){try{return RelayWire.json(relay.outgoing(SystemClock.elapsedRealtime()));}catch(Exception ignored){return new JSONObject();}}
    MainActivity.Peer relayPeer(int id){MainActivity.Peer p=peers.get(id);if(p==null&&peers.size()<24){p=new MainActivity.Peer(id,-127);peers.put(id,p);}return p;}
    void receiveRelay(int from,JSONObject wire){if(!searching||wire==null||!wire.has("k"))return;long now=SystemClock.elapsedRealtime();
        try{RelayRouter.Delivery delivery=relay.receive(from,RelayWire.parse(wire),now);if(delivery==null)return;RelayRouter.Packet packet=delivery.packet;JSONObject body=new JSONObject(packet.body);
            MainActivity.Peer peer=relayPeer(packet.origin);if(peer==null)return;
            if(packet.kind=='a'){
                DeviceProfile profile=DeviceProfile.parse(new JSONObject().put("id",body.getString("u")).put("name",body.getString("n")).put("caps",body.getInt("c")).put("model","Model nije prenesen • preko članova"));
                if(peer.profile==null||!directFresh(peer)){peer.profile=profile;peer.alias=profile.name;}
                if(delivery.route.hops>1){peer.relayHops=delivery.route.hops;peer.relayVia=from;}
            }else if(packet.kind=='q'){
                if(body.has("i")&&body.has("s")){receiveMessages(packet.origin,new JSONObject().put("msg",new JSONObject().put("id",body.getString("i")).put("text",body.getString("s")).put("to",session)));}
                if(body.has("z")&&body.optLong("z")!=0&&body.optLong("z")!=peer.lastPing){peer.lastPing=body.getLong("z");signal("PING preko članova od: "+peer.alias,true);}
                JSONObject reply=new JSONObject();JSONObject g=gps.wire();if(g!=null)reply.put("g",g);
                if(body.has("i")&&messages.acknowledgements().contains(body.optString("i")))reply.put("a",body.getString("i"));
                relay.respond(packet,reply.toString(),now);
            }else if(packet.kind=='r'){
                peer.relayAt=now;peer.relayHops=delivery.route.hops;peer.relayVia=from;
                if(!directFresh(peer)){
                    peer.hasGeo=false;peer.geoViaMesh=true;JSONObject g=body.optJSONObject("g");
                    if(g!=null){long age=RelayRouter.fixAge(g.getLong("age"),delivery.roundTrip);double lat=g.getDouble("la"),lon=g.getDouble("lo"),error=g.getDouble("ac");
                        if(Navigation.validFix(lat,lon,error,age)){peer.lat=lat;peer.lon=lon;peer.error=error;peer.fixAge=age;peer.geoAt=now;peer.hasGeo=true;}}
                }
                if(body.has("a")&&messages.acknowledge(packet.origin,body.getString("a"),now))mailStatus="Potvrđen primitak preko članova od "+peer.alias+".";
            }
        }catch(Exception ignored){}
    }
    JSONObject relayBody(int endpoint,long now)throws Exception{JSONObject body=new JSONObject();ShortMessages.Entry message=messages.pending(now);if(message!=null&&message.peer==endpoint)body.put("i",message.id).put("s",message.text);if(pingId!=0&&pingTarget==endpoint&&now-pingAt<10000)body.put("z",pingId);return body;}
    void relayUrgent(int endpoint){MainActivity.Peer peer=peers.get(endpoint);if(peer==null||directFresh(peer)||relay.route(endpoint,SystemClock.elapsedRealtime())==null)return;try{relay.cancelQuery(endpoint);relay.query(endpoint,relayBody(endpoint,SystemClock.elapsedRealtime()).toString(),SystemClock.elapsedRealtime());radioFocus(endpoint);}catch(Exception ignored){}}
    void relayTick(long now){try{
        if(now-lastAdvertise>=6000){lastAdvertise=now;String name=own.name;JSONObject ad=new JSONObject().put("u",own.id).put("n",name).put("c",own.caps);while(ad.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>RelayRouter.MAX_BODY&&name.length()>1){name=name.substring(0,name.offsetByCodePoints(name.length(),-1));ad.put("n",name+"…");}relay.advertise(ad.toString(),now);}
        if(now-lastQuery<2000)return;lastQuery=now;List<RelayRouter.Route> routes=relay.routes(now),ordered=new ArrayList<>();
        for(RelayRouter.Route r:routes)if(Objects.equals(selectedSession,r.endpoint))ordered.add(r);
        for(int i=0;i<routes.size();i++){RelayRouter.Route r=routes.get(Math.floorMod(routeCursor+i,routes.size()));if(!Objects.equals(selectedSession,r.endpoint))ordered.add(r);}
        for(RelayRouter.Route route:ordered){MainActivity.Peer peer=peers.get(route.endpoint);if(peer==null||directFresh(peer)||route.hops<2)continue;
            if(relay.query(peer.id,relayBody(peer.id,now).toString(),now)){if(!Objects.equals(selectedSession,peer.id))routeCursor++;break;}
        }
    }catch(Exception ignored){}}
    void signal(String text,boolean tone){note=text;if(service!=null)service.signal(text);
        if(tone){android.media.ToneGenerator sound=new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,80);sound.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2,500);handler.postDelayed(sound::release,650);}}
    void stop(){starting=false;searching=false;handler.removeCallbacksAndMessages(null);ble.stop();profiles.stop();nearby.stop();aware.stop();gps.stop();uwb.stop();messages.stop();relay.clear();health.reset();pingId=0;selectedSession=null;note="Radar zaustavljen.";}
}
