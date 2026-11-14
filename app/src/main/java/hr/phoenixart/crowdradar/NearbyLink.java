package hr.phoenixart.crowdradar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.*;
import android.util.Base64;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;
import hr.phoenixart.crowdradar.core.NearCrypto;
import hr.phoenixart.crowdradar.core.ReplyWindow;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** One extra high-bandwidth pair; BLE continues independently for the whole group. No relay. */
@SuppressLint("MissingPermission")
final class NearbyLink {
    interface Listener {JSONObject snapshot()throws Exception;void received(int session,JSONObject frame,long latency);}
    final Context context;final Listener listener;final ConnectionsClient client;final Handler main=new Handler(Looper.getMainLooper());
    final Map<String,Integer> found=new LinkedHashMap<>();final Map<String,Long> attempts=new HashMap<>();
    byte[] key;int session,generation;Integer focus,remote;String endpoint,nonce;long requested,lastReceived,connecting;boolean active,connected,authenticated;
    String status="Nearby isključen";String service;
    NearbyLink(Context c,Listener l){context=c;listener=l;client=Nearby.getConnectionsClient(c);}
    boolean live(int id){return active&&authenticated&&remote!=null&&remote==id&&SystemClock.elapsedRealtime()-lastReceived<=6000;}
    void start(byte[] group,int ownSession,Integer selected){
        stop();WifiManager wifi=context.getApplicationContext().getSystemService(WifiManager.class);
        if(wifi==null||!wifi.isWifiEnabled()){status="Nearby: uključi Wi-Fi; BLE ostaje aktivan";return;}
        key=group.clone();session=ownSession;focus=selected;active=true;int ticket=generation;
        try{service=NearCrypto.service(key);}catch(Exception e){stop();status="Nearby ključ nije dostupan";return;}
        status="Nearby BT/Wi-Fi • tražim dodatnu vezu";
        ConnectionLifecycleCallback life=new ConnectionLifecycleCallback(){
            @Override public void onConnectionInitiated(String id,ConnectionInfo info){
                if(!valid(ticket)){return;}Integer peer=parseName(info.getEndpointName());
                if(peer==null||peer==session||(focus!=null&&!focus.equals(peer))||(endpoint!=null&&!endpoint.equals(id))){client.rejectConnection(id);return;}
                endpoint=id;remote=peer;connecting=SystemClock.elapsedRealtime();
                client.acceptConnection(id,payloads(ticket)).addOnFailureListener(e->{if(valid(ticket))drop("Nearby prihvat nije uspio");});
            }
            @Override public void onConnectionResult(String id,ConnectionResolution result){
                if(!valid(ticket)||!id.equals(endpoint))return;
                if(!result.getStatus().isSuccess()){drop("Nearby veza nije uspjela; pokušavam ponovno");return;}
                connected=true;status="Nearby • provjeravam grupni ključ";client.stopAdvertising();client.stopDiscovery();request();
            }
            @Override public void onDisconnected(String id){if(valid(ticket)&&id.equals(endpoint))drop("Nearby prekinut • BLE ostaje aktivan");}
        };
        lifecycle=life;advertise(ticket);discover(ticket);main.post(tick);
    }
    ConnectionLifecycleCallback lifecycle;
    boolean valid(int ticket){return active&&generation==ticket;}
    static Integer parseName(String name){try{if(name==null||!name.matches("PCR6-[0-9a-f]{8}"))return null;return (int)Long.parseLong(name.substring(5),16);}catch(RuntimeException e){return null;}}
    String ownName(){return String.format(Locale.ROOT,"PCR6-%08x",session);}
    void advertise(int ticket){if(!valid(ticket))return;
        client.startAdvertising(ownName(),service,lifecycle,new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
            .addOnFailureListener(e->{if(valid(ticket)){status="Nearby oglašavanje nedostupno • BLE ostaje aktivan";}});
    }
    void discover(int ticket){if(!valid(ticket))return;
        client.startDiscovery(service,new EndpointDiscoveryCallback(){
            @Override public void onEndpointFound(String id,DiscoveredEndpointInfo info){if(!valid(ticket))return;Integer peer=parseName(info.getEndpointName());
                if(peer!=null&&peer!=session&&(found.containsKey(id)||found.size()<24)){found.put(id,peer);connectNext();}}
            @Override public void onEndpointLost(String id){if(valid(ticket))found.remove(id);}
        },new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
            .addOnFailureListener(e->{if(valid(ticket))status="Nearby traženje nedostupno • provjeri dozvole / Google Play usluge";});
    }
    void focus(Integer id){if(Objects.equals(focus,id))return;focus=id;if(remote!=null&&!Objects.equals(remote,id))drop("Nearby • mijenjam odabrani par");else connectNext();}
    void connectNext(){if(!active||endpoint!=null)return;long now=SystemClock.elapsedRealtime();
        for(Map.Entry<String,Integer> item:found.entrySet()){
            int peer=item.getValue();if(focus!=null&&peer!=focus)continue;
            // Only the unsigned-lower session initiates. Both sides advertise/discover; no simultaneous request race.
            if(Integer.compareUnsigned(session,peer)>=0||now-attempts.getOrDefault(item.getKey(),-20000L)<15000)continue;
            String id=item.getKey();if(attempts.size()>64)attempts.clear();attempts.put(id,now);endpoint=id;remote=peer;connecting=now;int ticket=generation;
            client.requestConnection(ownName(),id,lifecycle).addOnFailureListener(e->{if(valid(ticket)&&id.equals(endpoint))drop("Nearby • ponovno povezivanje");});return;
        }
    }
    PayloadCallback payloads(int ticket){return new PayloadCallback(){
        @Override public void onPayloadReceived(String id,Payload payload){
            if(!valid(ticket)||!connected||!id.equals(endpoint)||payload.getType()!=Payload.Type.BYTES)return;
            try{byte[] bytes=payload.asBytes();JSONObject frame=new JSONObject(new String(NearCrypto.open(key,bytes),StandardCharsets.UTF_8));
                if(frame.getInt("from")!=remote||frame.getInt("to")!=session)return;
                String n=frame.getString("n");if(!n.matches("[A-Za-z0-9+/]{22}=="))return;
                if("q".equals(frame.getString("kind"))){
                    JSONObject reply=listener.snapshot().put("kind","r").put("from",session).put("to",remote).put("n",n);send(reply);
                }else if("r".equals(frame.getString("kind"))&&ReplyWindow.accepts(nonce,n,remote,frame.getInt("from"),session,frame.getInt("to"),requested,SystemClock.elapsedRealtime())){
                    long latency=SystemClock.elapsedRealtime()-requested;if(latency<0||latency>6000)return;nonce=null;
                    // Validate profile and all mandatory objects before declaring this an authenticated, usable peer.
                    DeviceProfile.parse(frame.getJSONObject("profile"));frame.getJSONObject("state");frame.getJSONObject("mail");
                    listener.received(remote,frame,latency);lastReceived=SystemClock.elapsedRealtime();authenticated=true;status="Nearby BT/Wi-Fi • dodatna veza aktivna (medij bira sustav)";
                }
            }catch(Exception ignored){/* Invalid or wrong-key frames are never passed to application state. */}
        }
        @Override public void onPayloadTransferUpdate(String id,PayloadTransferUpdate update){}
    };}
    void send(JSONObject frame)throws Exception{if(!active||!connected||endpoint==null)return;String id=endpoint;int ticket=generation;
        client.sendPayload(id,Payload.fromBytes(NearCrypto.seal(key,frame.toString().getBytes(StandardCharsets.UTF_8))))
            .addOnFailureListener(e->{if(valid(ticket)&&id.equals(endpoint))drop("Nearby slanje prekinuto • BLE ostaje aktivan");});
    }
    void request(){if(!active||!connected||nonce!=null)return;try{byte[] random=new byte[16];new SecureRandom().nextBytes(random);nonce=Base64.encodeToString(random,Base64.NO_WRAP);requested=SystemClock.elapsedRealtime();
        send(new JSONObject().put("kind","q").put("from",session).put("to",remote).put("n",nonce));
    }catch(Exception e){drop("Nearby paket nije pripremljen");}}
    final Runnable tick=new Runnable(){public void run(){if(!active)return;long now=SystemClock.elapsedRealtime();
        if(endpoint!=null&&((!authenticated&&now-connecting>10000)||(authenticated&&now-lastReceived>8000)))drop("Nearby veza nije svježa • BLE ostaje aktivan");
        if(nonce!=null&&now-requested>6000)nonce=null;
        if(connected)request();else connectNext();main.postDelayed(this,1000);
    }};
    void drop(String reason){if(!active)return;String old=endpoint;endpoint=null;remote=null;connected=false;authenticated=false;nonce=null;lastReceived=0;
        if(old!=null){attempts.put(old,SystemClock.elapsedRealtime());client.disconnectFromEndpoint(old);}status=reason;
        client.stopAdvertising();client.stopDiscovery();int ticket=generation;main.postDelayed(()->{if(valid(ticket)&&endpoint==null){advertise(ticket);discover(ticket);}},1500);
    }
    void stop(){active=false;generation++;main.removeCallbacksAndMessages(null);endpoint=null;remote=null;nonce=null;connected=false;authenticated=false;lastReceived=0;found.clear();attempts.clear();
        try{client.stopAdvertising();client.stopDiscovery();client.stopAllEndpoints();}catch(RuntimeException ignored){}
        if(key!=null)Arrays.fill(key,(byte)0);key=null;status="Nearby isključen";
    }
}
