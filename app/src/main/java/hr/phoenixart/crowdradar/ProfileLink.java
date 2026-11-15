package hr.phoenixart.crowdradar;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.Context;
import android.os.*;
import android.util.Base64;
import hr.phoenixart.crowdradar.core.ProfileCrypto;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Supplier;

/** Persistent, nonce-bound encrypted GATT reads. Radio transport version 3. */
@SuppressLint("MissingPermission")
final class ProfileLink {
    static final UUID SERVICE=UUID.fromString("abfa5630-8a65-4489-a390-b4ac6e79f821"),VALUE=UUID.fromString("abfa5631-8a65-4489-a390-b4ac6e79f821"),
        STATE=UUID.fromString("abfa5632-8a65-4489-a390-b4ac6e79f821"),CHALLENGE=UUID.fromString("abfa5633-8a65-4489-a390-b4ac6e79f821"),MAIL=UUID.fromString("abfa5634-8a65-4489-a390-b4ac6e79f821"),MESH=UUID.fromString("abfa5635-8a65-4489-a390-b4ac6e79f821");
    interface Listener{void profile(int session,DeviceProfile profile,String groupName,long revision);}
    interface StateListener{void state(int session,JSONObject data,long latency);}
    final Context context; final Listener listener; final Handler main=new Handler(Looper.getMainLooper());
    StateListener stateListener,mailListener,meshListener;Supplier<JSONObject> meshSupplier=JSONObject::new; Supplier<JSONObject> stateSupplier=JSONObject::new,mailSupplier=JSONObject::new;
    java.util.function.BiConsumer<Integer,Boolean> meshSupport=(id,supported)->{};
    java.util.function.BiConsumer<Integer,Boolean> mailSupport=(id,supported)->{};
    final Map<String,byte[]> nonces=new HashMap<>(),snapshots=new HashMap<>(); final Map<String,Integer> mtus=new HashMap<>();
    final Map<Integer,Client> clients=new LinkedHashMap<>(); final Map<Integer,Long> attempts=new HashMap<>();
    BluetoothGattServer server; volatile byte[] secret; volatile DeviceProfile own; volatile int session; volatile boolean active; int generation,focus;
    ProfileLink(Context c,Listener l){context=c;listener=l;}
    void clearFrames(String a){snapshots.remove(a+VALUE);snapshots.remove(a+STATE);snapshots.remove(a+MAIL);snapshots.remove(a+MESH);}
    void clearDevice(String a){nonces.remove(a);clearFrames(a);mtus.remove(a);}
    void start(byte[] key,int id,DeviceProfile profile){stop();secret=key.clone();session=id;own=profile;active=true;
        try{server=context.getSystemService(BluetoothManager.class).openGattServer(context,new BluetoothGattServerCallback(){
            @Override public void onMtuChanged(BluetoothDevice d,int mtu){synchronized(nonces){mtus.put(d.getAddress(),Math.min(517,mtu));}}
            @Override public void onConnectionStateChange(BluetoothDevice d,int status,int state){if(state==BluetoothProfile.STATE_DISCONNECTED)synchronized(nonces){clearDevice(d.getAddress());}}
            @Override public void onCharacteristicWriteRequest(BluetoothDevice d,int request,BluetoothGattCharacteristic c,boolean prepared,boolean response,int offset,byte[] value){
                BluetoothGattServer s=server;if(!active||s==null)return;int result=BluetoothGatt.GATT_FAILURE;
                synchronized(nonces){String a=d.getAddress();if(CHALLENGE.equals(c.getUuid())&&!prepared&&offset==0&&value!=null&&value.length==16&&(nonces.containsKey(a)||nonces.size()<8)){
                    clearFrames(a);nonces.put(a,value.clone());result=BluetoothGatt.GATT_SUCCESS;}}
                if(response)try{s.sendResponse(d,request,result,offset,null);}catch(RuntimeException ignored){}
            }
            @Override public void onCharacteristicReadRequest(BluetoothDevice d,int request,int offset,BluetoothGattCharacteristic c){
                BluetoothGattServer s=server;byte[] keyNow=secret;if(!active||s==null||keyNow==null)return;
                try{synchronized(nonces){String a=d.getAddress();byte[] nonce=nonces.get(a);
                    if(nonce==null||(!VALUE.equals(c.getUuid())&&!STATE.equals(c.getUuid())&&!MAIL.equals(c.getUuid())&&!MESH.equals(c.getUuid())))throw new IllegalArgumentException();
                    String cache=a+c.getUuid();byte[] frame=snapshots.get(cache);
                    if(offset==0||frame==null){JSONObject json;
                        if(VALUE.equals(c.getUuid())){android.content.SharedPreferences prefs=context.getSharedPreferences("groupInfo",0);
                            json=own.json().put("kind","p").put("groupName",prefs.getString("name","Moja grupa")).put("revision",prefs.getLong("revision",0));
                        }else if(MESH.equals(c.getUuid()))json=meshSupplier.get().put("kind","x");
                        else if(MAIL.equals(c.getUuid()))json=mailSupplier.get().put("kind","m");
                        else json=stateSupplier.get().put("kind","s");
                        json.put("session",session).put("n",Base64.encodeToString(nonce,Base64.NO_WRAP));
                        frame=ProfileCrypto.seal(keyNow,json.toString().getBytes(StandardCharsets.UTF_8));if(frame.length>512)throw new IllegalArgumentException();snapshots.put(cache,frame);
                    }
                    if(offset>frame.length){s.sendResponse(d,request,BluetoothGatt.GATT_INVALID_OFFSET,offset,null);return;}
                    int end=Math.min(frame.length,offset+Math.max(22,mtus.getOrDefault(a,23)-1));
                    s.sendResponse(d,request,BluetoothGatt.GATT_SUCCESS,offset,Arrays.copyOfRange(frame,offset,end));
                }}catch(Exception e){try{s.sendResponse(d,request,BluetoothGatt.GATT_FAILURE,offset,null);}catch(RuntimeException ignored){}}
            }
        });
        if(server==null)return;BluetoothGattService service=new BluetoothGattService(SERVICE,BluetoothGattService.SERVICE_TYPE_PRIMARY);
        for(UUID uuid:new UUID[]{VALUE,STATE,MAIL,MESH})service.addCharacteristic(new BluetoothGattCharacteristic(uuid,BluetoothGattCharacteristic.PROPERTY_READ,BluetoothGattCharacteristic.PERMISSION_READ));
        service.addCharacteristic(new BluetoothGattCharacteristic(CHALLENGE,BluetoothGattCharacteristic.PROPERTY_WRITE,BluetoothGattCharacteristic.PERMISSION_WRITE));server.addService(service);
        }catch(RuntimeException e){if(server!=null)server.close();server=null;}
    }
    void fetch(int peer,BluetoothDevice device){if(!active||clients.containsKey(peer)||clients.size()>=6||SystemClock.elapsedRealtime()-attempts.getOrDefault(peer,-20000L)<10000)return;
        attempts.put(peer,SystemClock.elapsedRealtime());Client c=new Client(peer,generation);clients.put(peer,c);
        try{c.gatt=device.connectGatt(context,false,c,BluetoothDevice.TRANSPORT_LE);c.timeout();}catch(RuntimeException e){c.close();}}
    final class Client extends BluetoothGattCallback{
        final int peer,ticket; BluetoothGatt gatt; byte[] nonce; long requested,lastProfile; boolean discovery,busy,closed;
        BluetoothGattCharacteristic profile,state,challenge,mail,mesh;
        Client(int p,int t){peer=p;ticket=t;}
        boolean valid(){return active&&!closed&&ticket==generation&&clients.get(peer)==this;}
        void timeout(){main.postDelayed(()->{if(valid()&&((busy&&SystemClock.elapsedRealtime()-requested>6000)||state==null))close();},8000);}
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int next){main.post(()->{if(!valid())return;
            if(status!=BluetoothGatt.GATT_SUCCESS||next==BluetoothProfile.STATE_DISCONNECTED){close();return;}
            if(next==BluetoothProfile.STATE_CONNECTED){gatt=g;if(!g.requestMtu(512))discover();}});}
        void discover(){if(!valid()||discovery)return;discovery=true;if(!gatt.discoverServices())close();}
        @Override public void onMtuChanged(BluetoothGatt g,int mtu,int status){main.post(this::discover);}
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){main.post(()->{if(!valid())return;BluetoothGattService s=g.getService(SERVICE);
            if(status!=BluetoothGatt.GATT_SUCCESS||s==null){close();return;}profile=s.getCharacteristic(VALUE);state=s.getCharacteristic(STATE);challenge=s.getCharacteristic(CHALLENGE);mail=s.getCharacteristic(MAIL);mesh=s.getCharacteristic(MESH);meshSupport.accept(peer,mesh!=null);mailSupport.accept(peer,mail!=null);
            if(profile==null||state==null||challenge==null){close();return;}cycle();});}
        @SuppressWarnings("deprecation") void cycle(){if(!valid()||busy)return;busy=true;requested=SystemClock.elapsedRealtime();nonce=new byte[16];new SecureRandom().nextBytes(nonce);
            try{boolean ok;if(Build.VERSION.SDK_INT>=33)ok=gatt.writeCharacteristic(challenge,nonce,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS;
                else{challenge.setValue(nonce);ok=gatt.writeCharacteristic(challenge);}if(!ok)close();else timeout();}catch(RuntimeException e){close();}}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){main.post(()->{if(!valid())return;
            if(status!=BluetoothGatt.GATT_SUCCESS||!g.readCharacteristic(SystemClock.elapsedRealtime()-lastProfile>15000?profile:state))close();});}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int status){main.post(()->read(c,value,status));}
        @SuppressWarnings("deprecation") @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status){if(Build.VERSION.SDK_INT<33){byte[] v=c.getValue();main.post(()->read(c,v,status));}}
        void read(BluetoothGattCharacteristic c,byte[] value,int status){if(!valid())return;
            try{if(status!=BluetoothGatt.GATT_SUCCESS)throw new IllegalArgumentException();JSONObject json=new JSONObject(new String(ProfileCrypto.open(secret,value),StandardCharsets.UTF_8));
                if(json.getInt("session")!=peer||!Base64.encodeToString(nonce,Base64.NO_WRAP).equals(json.getString("n")))throw new IllegalArgumentException();
                long latency=SystemClock.elapsedRealtime()-requested;if(latency>6000)throw new IllegalArgumentException();
                if(VALUE.equals(c.getUuid())){if(!json.getString("kind").equals("p"))throw new IllegalArgumentException();
                    listener.profile(peer,DeviceProfile.parse(json),json.getString("groupName"),json.getLong("revision"));lastProfile=SystemClock.elapsedRealtime();
                    if(!gatt.readCharacteristic(state))close();return;}
                if(STATE.equals(c.getUuid())){
                    if(!json.getString("kind").equals("s"))throw new IllegalArgumentException();
                    if(stateListener!=null)stateListener.state(peer,json,latency);
                    if(mail!=null){if(!gatt.readCharacteristic(mail))close();return;}
                    if(mesh!=null){if(!gatt.readCharacteristic(mesh))close();return;}
                }else if(MAIL.equals(c.getUuid())){
                    if(!json.getString("kind").equals("m"))throw new IllegalArgumentException();
                    if(mailListener!=null)mailListener.state(peer,json,latency);
                    if(mesh!=null){if(!gatt.readCharacteristic(mesh))close();return;}
                }else if(MESH.equals(c.getUuid())){if(!json.getString("kind").equals("x"))throw new IllegalArgumentException();if(meshListener!=null)meshListener.state(peer,json,latency);
                }else throw new IllegalArgumentException();
                busy=false;main.postDelayed(this::cycle,peer==focus?1000:2500);
            }catch(Exception e){close();}
        }
        void close(){if(closed)return;closed=true;meshSupport.accept(peer,false);mailSupport.accept(peer,false);if(clients.get(peer)==this)clients.remove(peer);if(gatt!=null)try{gatt.disconnect();gatt.close();}catch(RuntimeException ignored){}gatt=null;}
    }
    void stop(){active=false;generation++;main.removeCallbacksAndMessages(null);for(Client c:new ArrayList<>(clients.values()))c.close();clients.clear();
        if(server!=null){try{server.close();}catch(RuntimeException ignored){}server=null;}
        if(secret!=null)Arrays.fill(secret,(byte)0);secret=null;attempts.clear();synchronized(nonces){nonces.clear();snapshots.clear();mtus.clear();}}
}
