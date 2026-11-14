package hr.phoenixart.crowdradar;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.Context;
import android.os.*;
import hr.phoenixart.crowdradar.core.ProfileCrypto;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Encrypted group-only metadata; does not exchange coordinates. One client read at a time. */
@SuppressLint("MissingPermission")
final class ProfileLink {
    static final UUID SERVICE=UUID.fromString("abfa5630-8a65-4489-a390-b4ac6e79f821"),VALUE=UUID.fromString("abfa5631-8a65-4489-a390-b4ac6e79f821");
    interface Listener {void profile(int sessionId,DeviceProfile profile,String groupName,long revision);}
    final Context context;final Listener listener;final Handler main=new Handler(Looper.getMainLooper());
    final Map<String,byte[]> snapshots=new HashMap<>();final Map<String,Integer> mtus=new HashMap<>();
    final Map<Integer,Long> attempts=new HashMap<>();
    BluetoothGattServer server;BluetoothGatt client;byte[] secret;DeviceProfile own;int session;boolean active;int generation;
    ProfileLink(Context context,Listener listener){this.context=context;this.listener=listener;}
    void start(byte[] key,int id,DeviceProfile profile){stop();secret=key.clone();session=id;own=profile;active=true;
        BluetoothManager manager=context.getSystemService(BluetoothManager.class);
        try {
            server=manager.openGattServer(context,new BluetoothGattServerCallback(){
                @Override public void onMtuChanged(BluetoothDevice device,int mtu){synchronized(snapshots){mtus.put(device.getAddress(),mtu);}}
                @Override public void onConnectionStateChange(BluetoothDevice device,int status,int state){if(state==BluetoothProfile.STATE_DISCONNECTED)synchronized(snapshots){snapshots.remove(device.getAddress());mtus.remove(device.getAddress());}}
                @Override public void onCharacteristicReadRequest(BluetoothDevice device,int request,int offset,BluetoothGattCharacteristic characteristic){
                    BluetoothGattServer current=server;byte[] keyNow=secret;if(!active||current==null||keyNow==null)return;
                    try {if(!VALUE.equals(characteristic.getUuid())){current.sendResponse(device,request,BluetoothGatt.GATT_FAILURE,offset,null);return;}
                        synchronized(snapshots){String address=device.getAddress();byte[] frame=snapshots.get(address);
                            if(offset==0||frame==null){android.content.SharedPreferences prefs=context.getSharedPreferences("groupInfo",0);
                                JSONObject json=own.json().put("session",session).put("time",System.currentTimeMillis()).put("groupName",prefs.getString("name","Moja grupa")).put("revision",prefs.getLong("revision",0));
                                frame=ProfileCrypto.seal(keyNow,json.toString().getBytes(StandardCharsets.UTF_8));if(frame.length>512)throw new IllegalArgumentException();snapshots.put(address,frame);}
                            if(offset>frame.length){current.sendResponse(device,request,BluetoothGatt.GATT_INVALID_OFFSET,offset,null);return;}
                            int end=Math.min(frame.length,offset+Math.max(22,mtus.getOrDefault(address,23)-1));
                            current.sendResponse(device,request,BluetoothGatt.GATT_SUCCESS,offset,Arrays.copyOfRange(frame,offset,end));
                        }
                    }catch(Exception e){try{current.sendResponse(device,request,BluetoothGatt.GATT_FAILURE,offset,null);}catch(RuntimeException ignored){}}
                }
            });
            if(server==null)return;
            BluetoothGattService service=new BluetoothGattService(SERVICE,BluetoothGattService.SERVICE_TYPE_PRIMARY);
            service.addCharacteristic(new BluetoothGattCharacteristic(VALUE,BluetoothGattCharacteristic.PROPERTY_READ,BluetoothGattCharacteristic.PERMISSION_READ));server.addService(service);
        }catch(RuntimeException e){if(server!=null)server.close();server=null;}
    }
    void fetch(int peerSession,BluetoothDevice device){
        if(!active||client!=null||SystemClock.elapsedRealtime()-attempts.getOrDefault(peerSession,-60000L)<30000)return;
        attempts.put(peerSession,SystemClock.elapsedRealtime());int ticket=generation;byte[] keyNow=secret.clone();
        try {
            client=device.connectGatt(context,false,new BluetoothGattCallback(){
                boolean discovery;
                void discover(BluetoothGatt g){if(discovery)return;discovery=true;if(!g.discoverServices())finish(g);}
                @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){
                    if(status!=BluetoothGatt.GATT_SUCCESS||state==BluetoothProfile.STATE_DISCONNECTED){finish(g);return;}
                    if(state==BluetoothProfile.STATE_CONNECTED&&!g.requestMtu(512))discover(g);
                }
                @Override public void onMtuChanged(BluetoothGatt g,int mtu,int status){discover(g);}
                @Override public void onServicesDiscovered(BluetoothGatt g,int status){BluetoothGattService s=g.getService(SERVICE);BluetoothGattCharacteristic c=s==null?null:s.getCharacteristic(VALUE);
                    if(status!=BluetoothGatt.GATT_SUCCESS||c==null||!g.readCharacteristic(c))finish(g);}
                @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int status){read(g,value,status);}
                @SuppressWarnings("deprecation") @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status){if(Build.VERSION.SDK_INT<33)read(g,c.getValue(),status);}
                void read(BluetoothGatt g,byte[] value,int status){
                    if(status==BluetoothGatt.GATT_SUCCESS)try{
                        JSONObject json=new JSONObject(new String(ProfileCrypto.open(keyNow,value),StandardCharsets.UTF_8));
                        if(json.getInt("session")!=peerSession||Math.abs(System.currentTimeMillis()-json.getLong("time"))>15000)throw new IllegalArgumentException();
                        DeviceProfile p=DeviceProfile.parse(json);String name=json.getString("groupName");long revision=json.getLong("revision");
                        main.post(()->{if(active&&generation==ticket)listener.profile(peerSession,p,name,revision);});
                    }catch(Exception ignored){}finish(g);
                }
            },BluetoothDevice.TRANSPORT_LE);
            BluetoothGatt pending=client;main.postDelayed(()->{if(client==pending)finish(pending);},7000);
        }catch(RuntimeException e){client=null;}
    }
    void finish(BluetoothGatt g){main.post(()->{try{g.disconnect();g.close();}catch(RuntimeException ignored){}if(client==g)client=null;});}
    void stop(){active=false;generation++;if(client!=null){try{client.disconnect();client.close();}catch(RuntimeException ignored){}client=null;}
        if(server!=null){try{server.close();}catch(RuntimeException ignored){}server=null;}
        if(secret!=null)Arrays.fill(secret,(byte)0);secret=null;attempts.clear();synchronized(snapshots){snapshots.clear();mtus.clear();}}
}
