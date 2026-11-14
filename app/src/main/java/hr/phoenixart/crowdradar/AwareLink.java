package hr.phoenixart.crowdradar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.aware.*;
import android.net.wifi.rtt.*;
import android.os.*;
import android.util.Base64;
import hr.phoenixart.crowdradar.core.*;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** Native Wi-Fi Aware discovery + encrypted short data, independently of Nearby / GMS. */
@SuppressLint("MissingPermission")
final class AwareLink {
    final Context context;final NearbyLink.Listener listener;final Handler main=new Handler(Looper.getMainLooper());
    final WifiAwareManager manager;final WifiRttManager rtt;WifiAwareSession attached;PublishDiscoverySession pub;SubscribeDiscoverySession sub;
    byte[] key;int session,generation,messageId,limit=180;Integer focus;boolean active,attaching,sending;String status="Wi-Fi Aware nije pokrenut";
    final Map<Integer,Member> members=new LinkedHashMap<>();final Map<PeerHandle,Fragments.Assembly> pubFrames=new HashMap<>(),subFrames=new HashMap<>();
    final ArrayDeque<Send> queue=new ArrayDeque<>();long lastRange,lastAdvertised;int rangingFor;Double distance,spread;long measuredAt;int measuredPeer;
    static final class Member{PeerHandle handle;String nonce;long requested,seen,received;Member(PeerHandle h){handle=h;}}
    static final class Send{DiscoverySession route;PeerHandle peer;byte[] bytes;int id;Send(DiscoverySession r,PeerHandle p,byte[] b,int i){route=r;peer=p;bytes=b;id=i;}}
    AwareLink(Context c,NearbyLink.Listener l){context=c;listener=l;manager=c.getSystemService(WifiAwareManager.class);rtt=c.getSystemService(WifiRttManager.class);}
    boolean live(int id){Member m=members.get(id);return active&&m!=null&&Navigation.fresh(m.received,SystemClock.elapsedRealtime(),6000);}
    void start(byte[] secret,int own){stop();if(!context.getPackageManager().hasSystemFeature("android.hardware.wifi.aware")||manager==null){status="Wi-Fi Aware: nema hardverske podrške";return;}
        if(!manager.isAvailable()){status="Wi-Fi Aware trenutačno nedostupan (radio / drugi način Wi-Fi-ja)";return;}
        key=secret.clone();session=own;active=true;attaching=true;int ticket=generation;status="Wi-Fi Aware • otvaram izravno otkrivanje";
        try{android.net.wifi.aware.Characteristics chars=manager.getCharacteristics();if(chars!=null)limit=Math.min(180,chars.getMaxServiceSpecificInfoLength());if(limit<80){stop();status="Wi-Fi Aware: premali podatkovni okvir";return;}
            manager.attach(new AttachCallback(){
                @Override public void onAttached(WifiAwareSession value){if(!valid(ticket)){value.close();return;}attached=value;attaching=false;try{
                    String name=NearCrypto.service(key).replace("hr.phoenixart.crowdradar.live.","pcr7-");
                    PublishConfig.Builder config=new PublishConfig.Builder().setServiceName(name).setServiceSpecificInfo(Presence.packet(key,session,System.currentTimeMillis()));
                    if(rtt!=null&&context.getPackageManager().hasSystemFeature("android.hardware.wifi.rtt"))config.setRangingEnabled(true);
                    value.publish(config.build(),callback(true,ticket),main);value.subscribe(new SubscribeConfig.Builder().setServiceName(name).build(),callback(false,ticket),main);
                    main.post(tick);
                }catch(Exception e){stop();status="Wi-Fi Aware sesija nije pokrenuta";}}
                @Override public void onAttachFailed(){if(valid(ticket)){stop();status="Wi-Fi Aware: sustav nije odobrio sesiju";}}
            },main);
            main.postDelayed(()->{if(valid(ticket)&&attaching){stop();status="Wi-Fi Aware: istek otvaranja";}},10000);
        }catch(RuntimeException e){stop();status="Wi-Fi Aware: provjeri dozvole i dostupnost Wi-Fi-ja";}
    }
    boolean valid(int ticket){return active&&generation==ticket;}
    DiscoverySessionCallback callback(boolean publisher,int ticket){return new DiscoverySessionCallback(){
        @Override public void onPublishStarted(PublishDiscoverySession value){if(!valid(ticket)){value.close();return;}pub=value;status="Wi-Fi Aware • otkrivanje aktivno";}
        @Override public void onSubscribeStarted(SubscribeDiscoverySession value){if(!valid(ticket)){value.close();return;}sub=value;status="Wi-Fi Aware • otkrivanje aktivno";}
        @Override public void onSessionConfigFailed(){if(valid(ticket)){stop();status="Wi-Fi Aware konfiguracija nije prihvaćena";}}
        @Override public void onSessionTerminated(){if(valid(ticket)){stop();status="Wi-Fi Aware sesija prekinuta • pokušat ću ponovno";}}
        @Override public void onServiceDiscovered(PeerHandle peer,byte[] info,List<byte[]> filters){found(peer,info);}
        @Override public void onServiceDiscoveredWithinRange(PeerHandle peer,byte[] info,List<byte[]> filters,int mm){found(peer,info);}
        void found(PeerHandle peer,byte[] info){if(!valid(ticket)||publisher)return;Integer id=Presence.verify(key,info,System.currentTimeMillis());if(id==null||id==session)return;
            Member m=members.get(id);if(m==null){if(members.size()>=24)return;m=new Member(peer);members.put(id,m);}m.handle=peer;m.seen=SystemClock.elapsedRealtime();}
        @Override public void onMessageReceived(PeerHandle peer,byte[] data){if(!valid(ticket))return;Map<PeerHandle,Fragments.Assembly> frames=publisher?pubFrames:subFrames;
            frames.values().removeIf(a->a.expired(SystemClock.elapsedRealtime()));
            Fragments.Assembly assembly=frames.get(peer);if(assembly==null){if(frames.size()>=24)return;assembly=new Fragments.Assembly();frames.put(peer,assembly);}
            byte[] joined=assembly.accept(data,SystemClock.elapsedRealtime());if(joined!=null){frames.remove(peer);receive(publisher?pub:sub,peer,joined,publisher);}
        }
        @Override public void onMessageSendSucceeded(int id){sent(id);}
        @Override public void onMessageSendFailed(int id){sent(id);}
    };}
    void sent(int id){if(queue.isEmpty()||queue.peek().id!=id)return;queue.remove();sending=false;pump();}
    void send(DiscoverySession route,PeerHandle peer,JSONObject data)throws Exception{if(route==null||!active)return;byte[] bytes=NearCrypto.seal(key,data.toString().getBytes(StandardCharsets.UTF_8));List<byte[]> parts=Fragments.split(bytes,new SecureRandom().nextInt(),limit);
        if(queue.size()+parts.size()>96)return;for(byte[] part:parts)queue.add(new Send(route,peer,part,++messageId));pump();}
    void pump(){if(!active||sending||queue.isEmpty())return;Send next=queue.peek();sending=true;int ticket=generation;try{next.route.sendMessage(next.peer,next.id,next.bytes);
        main.postDelayed(()->{if(valid(ticket)&&!queue.isEmpty()&&queue.peek().id==next.id){queue.remove();sending=false;pump();}},1500);
    }catch(RuntimeException e){queue.remove();sending=false;main.post(this::pump);}}
    void receive(DiscoverySession route,PeerHandle peer,byte[] bytes,boolean publisher){try{
        JSONObject frame=new JSONObject(new String(NearCrypto.open(key,bytes),StandardCharsets.UTF_8));int from=frame.getInt("from");if(frame.getInt("to")!=session||from==session)return;
        String nonce=frame.getString("n");if(!nonce.matches("[A-Za-z0-9+/]{22}=="))return;
        if("q".equals(frame.getString("kind"))&&publisher){send(route,peer,listener.snapshot().put("kind","r").put("from",session).put("to",from).put("n",nonce));}
        else if("r".equals(frame.getString("kind"))&&!publisher){Member m=members.get(from);long now=SystemClock.elapsedRealtime();if(m==null||!m.handle.equals(peer)||!ReplyWindow.accepts(m.nonce,nonce,from,from,session,session,m.requested,now))return;
            DeviceProfile.parse(frame.getJSONObject("profile"));frame.getJSONObject("state");frame.getJSONObject("mail");m.nonce=null;listener.received(from,frame,now-m.requested);m.received=now;status="Wi-Fi Aware • šifrirana izravna veza aktivna";}
    }catch(Exception ignored){}}
    final Runnable tick=new Runnable(){public void run(){if(!active)return;if(manager==null||!manager.isAvailable()){stop();status="Wi-Fi Aware nedostupan • ostale veze ostaju";return;}
        long now=SystemClock.elapsedRealtime();members.entrySet().removeIf(e->now-Math.max(e.getValue().received,e.getValue().seen)>60000);
        List<Map.Entry<Integer,Member>> list=new ArrayList<>(members.entrySet());list.sort(Comparator.comparingInt(e->Objects.equals(e.getKey(),focus)?0:1));
        int count=0;for(var entry:list){if(++count>6)break;Member m=entry.getValue();if(m.nonce!=null&&now-m.requested<=6000)continue;if(now-m.requested<3000)continue;
            try{byte[] random=new byte[16];new SecureRandom().nextBytes(random);m.nonce=Base64.encodeToString(random,Base64.NO_WRAP);m.requested=now;send(sub,m.handle,new JSONObject().put("kind","q").put("from",session).put("to",entry.getKey()).put("n",m.nonce));}catch(Exception ignored){}
        }
        if(pub!=null&&now-lastAdvertised>20000)try{lastAdvertised=now;pub.updatePublish(new PublishConfig.Builder().setServiceName(NearCrypto.service(key).replace("hr.phoenixart.crowdradar.live.","pcr7-")).setServiceSpecificInfo(Presence.packet(key,session,System.currentTimeMillis())).setRangingEnabled(rtt!=null&&context.getPackageManager().hasSystemFeature("android.hardware.wifi.rtt")).build());}catch(Exception ignored){}
        main.postDelayed(this,1000);
    }};
    void range(int id){long now=SystemClock.elapsedRealtime();Member peer=members.get(id);if(!active||rtt==null||peer==null||!live(id)||now-lastRange<4000)return;lastRange=now;int ticket=generation;rangingFor=id;
        try{if(!rtt.isAvailable())return;rtt.startRanging(new RangingRequest.Builder().addWifiAwarePeer(peer.handle).build(),context.getMainExecutor(),new RangingResultCallback(){
            @Override public void onRangingFailure(int code){}
            @Override public void onRangingResults(List<RangingResult> results){if(!valid(ticket)||!Objects.equals(focus,id))return;for(RangingResult result:results)if(result.getStatus()==RangingResult.STATUS_SUCCESS&&result.getNumSuccessfulMeasurements()>0&&result.getDistanceMm()>=0){distance=result.getDistanceMm()/1000.;spread=Math.max(0,result.getDistanceStdDevMm())/1000.;measuredAt=SystemClock.elapsedRealtime();measuredPeer=id;}}
        });}catch(RuntimeException ignored){}
    }
    void stop(){active=false;attaching=false;generation++;main.removeCallbacksAndMessages(null);queue.clear();sending=false;members.clear();pubFrames.clear();subFrames.clear();
        if(pub!=null)pub.close();if(sub!=null)sub.close();if(attached!=null)attached.close();pub=null;sub=null;attached=null;
        if(key!=null)Arrays.fill(key,(byte)0);key=null;distance=spread=null;measuredAt=0;status="Wi-Fi Aware isključen";}
}
