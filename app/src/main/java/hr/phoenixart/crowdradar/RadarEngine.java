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
            MainActivity.Peer peer=peers.get(id);if(peer==null)return;peer.stateAt=SystemClock.elapsedRealtime();peer.hasGeo=false;
            JSONObject g=data.optJSONObject("g");if(g!=null)try{double lat=g.getDouble("la"),lon=g.getDouble("lo"),error=g.getDouble("ac");long age=Navigation.receivedFixAge(g.getLong("age"),latency,0);
                if(Navigation.validFix(lat,lon,error,age)){peer.lat=lat;peer.lon=lon;peer.error=error;peer.fixAge=age;peer.geoAt=SystemClock.elapsedRealtime();peer.hasGeo=true;}}
            catch(Exception ignored){}
            uwb.remote(id,data.optJSONObject("u"));
            if(data.has("f")&&data.optInt("f")==session&&selectedSession==null)focus(id);
            JSONObject ping=data.optJSONObject("p");if(ping!=null&&ping.optInt("target")==session&&ping.optLong("id")!=0&&ping.optLong("id")!=peer.lastPing){peer.lastPing=ping.optLong("id");signal("PING od: "+peer.alias,true);}
        };
        profiles.mailSupport=(id,yes)->{MainActivity.Peer peer=peers.get(id);if(peer!=null)peer.canMessage=yes;};
        profiles.mailSupplier=this::messageWire;profiles.mailListener=(id,data,latency)->receiveMessages(id,data);
        NearbyLink.Listener receiver=new NearbyLink.Listener(){
            public JSONObject snapshot()throws Exception{return new JSONObject().put("profile",own.json()).put("group",groupName).put("revision",groupRevision).put("state",profiles.stateSupplier.get()).put("mail",messageWire());}
            public void received(int id,JSONObject frame,long latency){if(!searching)return;try{
                DeviceProfile profile=DeviceProfile.parse(frame.getJSONObject("profile"));MainActivity.Peer peer=peers.get(id);
                if(peer==null){if(peers.size()>=24)return;peer=new MainActivity.Peer(id,-127);peers.put(id,peer);}
                profiles.listener.profile(id,profile,frame.optString("group"),frame.optLong("revision"));profiles.stateListener.state(id,frame.getJSONObject("state"),latency);receiveMessages(id,frame.getJSONObject("mail"));
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
        searching=true;peers.clear();selectedSession=null;uwbAttempts=0;session=new java.security.SecureRandom().nextInt();
        profiles.start(groupKey,session,own);lastBleTry=lastNearTry=lastAwareTry=lastUwbTry=0;handler.post(tick);
        note="Automatsko povezivanje pokrenuto • BLE / Nearby / Wi-Fi Aware";
    }
    void focus(int id){if(!Objects.equals(selectedSession,id)){uwb.stop();uwbAttempts=0;lastUwbTry=0;health.reset();}selectedSession=id;profiles.focus=id;nearby.focus(id);aware.focus=id;}
    boolean fresh(MainActivity.Peer peer){return searching&&(nearby.live(peer.id)||aware.live(peer.id)||(peer.canMessage&&Navigation.fresh(peer.stateAt,SystemClock.elapsedRealtime(),6000))||(peer.seen>0&&SystemClock.elapsedRealtime()-peer.seen<=12000));}
    boolean canMessage(MainActivity.Peer peer){return peer.canMessage||nearby.live(peer.id)||aware.live(peer.id);}
    String links(MainActivity.Peer peer){String s="";if(nearby.live(peer.id))s="Nearby BT/Wi-Fi";if(aware.live(peer.id))s+=(s.isEmpty()?"":" + ")+"Wi-Fi Aware";
        if(peer.canMessage)s+=(s.isEmpty()?"":" + ")+"BLE GATT";return s.isEmpty()?"Nema svježe podatkovne veze":s;}
    void startUwb(MainActivity.Peer peer){if(peer.profile==null||!uwb.permitted()||(own.caps&peer.profile.caps&Capabilities.UWB)==0)return;
        lastUwbTry=SystemClock.elapsedRealtime();uwbAttempts++;uwb.start(session,peer.id,own,peer.profile);}
    final Runnable tick=new Runnable(){public void run(){if(!searching)return;long now=SystemClock.elapsedRealtime();
        if(!ble.running()&&now-lastBleTry>15000){lastBleTry=now;ble.start(groupKey,session);}
        if(nearWanted&&wifiAllowed()&&now-lastNearTry>20000&&(!nearby.active||!nearby.discoveryHealthy())){lastNearTry=now;nearby.start(groupKey,session,selectedSession);}
        if(wifiAllowed()&&!aware.active&&now-lastAwareTry>20000){lastAwareTry=now;aware.start(groupKey,session);aware.focus=selectedSession;}
        MainActivity.Peer target=selectedSession==null?null:peers.get(selectedSession);
        if(target!=null){int change=health.sample(fresh(target),now);if(change<0)signal("Izgubljena veza: "+target.alias,false);else if(change>0)signal("Veza ponovno uspostavljena: "+target.alias,false);}
        if(target!=null&&fresh(target)){
            if(!uwb.ready&&!uwb.started&&uwbAttempts<3&&now-lastUwbTry>30000)startUwb(target);
            if(target.profile!=null&&(target.profile.caps&own.caps&Capabilities.RTT)!=0)aware.range(target.id);
        }
        peers.values().removeIf(p->now-Math.max(p.seen,p.stateAt)>120000&&!fresh(p));
        messages.pending(now);if(service!=null)service.update();handler.postDelayed(this,1000);
    }};
    JSONObject messageWire(){JSONObject data=new JSONObject();try{JSONArray ack=new JSONArray();for(String id:messages.acknowledgements())ack.put(id);data.put("acks",ack);
        ShortMessages.Entry e=messages.pending(SystemClock.elapsedRealtime());if(e!=null)data.put("msg",new JSONObject().put("id",e.id).put("to",e.peer).put("text",e.text));}catch(Exception ignored){}return data;}
    void receiveMessages(int id,JSONObject data){MainActivity.Peer peer=peers.get(id);if(!searching||peer==null)return;long now=SystemClock.elapsedRealtime();
        JSONArray ack=data.optJSONArray("acks");if(ack!=null)for(int i=0;i<Math.min(6,ack.length());i++)if(messages.acknowledge(id,ack.optString(i),now))mailStatus="Potvrđen primitak od "+peer.alias+".";
        JSONObject incoming=data.optJSONObject("msg");if(incoming!=null){ShortMessages.Entry accepted=messages.receive(id,session,incoming.optInt("to",session^1),incoming.optString("id"),peer.alias,incoming.optString("text"),now);
            if(accepted!=null){unreadMessages++;mailStatus="Poruka od "+peer.alias;signal(mailStatus,false);}}
    }
    void signal(String text,boolean tone){note=text;if(service!=null)service.signal(text);
        if(tone){android.media.ToneGenerator sound=new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,80);sound.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2,500);handler.postDelayed(sound::release,650);}}
    void stop(){starting=false;searching=false;handler.removeCallbacksAndMessages(null);ble.stop();profiles.stop();nearby.stop();aware.stop();gps.stop();uwb.stop();messages.stop();health.reset();pingId=0;selectedSession=null;note="Radar zaustavljen.";}
}
