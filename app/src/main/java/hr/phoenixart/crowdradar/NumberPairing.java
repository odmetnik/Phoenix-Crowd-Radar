package hr.phoenixart.crowdradar;

import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Numeric verification uses Nearby's authenticated encrypted channel, not a PIN-derived group key. */
@SuppressLint("MissingPermission")
final class NumberPairing {
    interface Listener {String invitation(); boolean received(String invite); void status(String text);}
    static final String SERVICE="hr.phoenixart.crowdradar.pair.v1";
    final Activity activity; final Listener listener; final ConnectionsClient client;
    final Handler main=new Handler(Looper.getMainLooper());
    boolean active,host,installed;int generation,failures;String peer;AlertDialog dialog;long ackId=-1;
    final Set<String> authorized=new HashSet<>();final Map<String,String> found=new LinkedHashMap<>();
    ArrayAdapter<String> list;
    NumberPairing(Activity activity,Listener listener){this.activity=activity;this.listener=listener;client=Nearby.getConnectionsClient(activity);}
    void start(boolean isHost,String name){
        stop();BluetoothManager bm=activity.getSystemService(BluetoothManager.class);WifiManager wifi=activity.getApplicationContext().getSystemService(WifiManager.class);
        if(bm==null||bm.getAdapter()==null||!bm.getAdapter().isEnabled()||wifi==null||!wifi.isWifiEnabled()){
            listener.status("Za brojčano uparivanje uključi Bluetooth i Wi-Fi. Internet i spajanje na router nisu potrebni.");return;}
        active=true;host=isHost;installed=false;failures=0;ackId=-1;int ticket=generation;
        while(name.getBytes(StandardCharsets.UTF_8).length>100)name=name.substring(0,name.offsetByCodePoints(name.length(),-1));
        if(host){
            dialog=new AlertDialog.Builder(activity).setTitle("Dodaj člana brojem")
                .setMessage("Pokrećem povezivanje…\n\nKolega na svojem telefonu treba otvoriti GRUPA → Pridruži se brojem, zatim odabrati ovaj uređaj. Tada će se ovdje pojaviti kod koji treba upisati na drugom telefonu.")
                .setNegativeButton("Odustani",(d,w)->stop()).setOnCancelListener(d->stop()).show();
            client.startAdvertising("PCR • "+name,SERVICE,lifecycle,new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
                .addOnSuccessListener(v->{if(active&&generation==ticket){listener.status("Dodavanje člana otvoreno 90 s. Čekam kolegin telefon.");if(dialog!=null)dialog.setTitle("Čekam kolegin telefon • 90 s");}})
                .addOnFailureListener(e->{if(active&&generation==ticket){stop();listener.status("Brojčano uparivanje nije pokrenuto. Provjeri Google Play usluge i dozvole. "+failure(e));}});
        }else{
            list=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,new ArrayList<>());
            dialog=new AlertDialog.Builder(activity).setTitle("Odaberi kolegin uređaj")
                .setAdapter(list,(d,index)->{String id=new ArrayList<>(found.keySet()).get(index);peer=id;client.stopDiscovery();
                    client.requestConnection(DeviceProfile.local(activity).name,id,lifecycle).addOnFailureListener(e->{stop();listener.status("Zahtjev za povezivanje nije uspio.");});})
                .setNegativeButton("Odustani",(d,w)->stop()).setOnCancelListener(d->stop()).show();
            client.startDiscovery(SERVICE,new EndpointDiscoveryCallback(){
                @Override public void onEndpointFound(String id,DiscoveredEndpointInfo info){
                    if(!active||generation!=ticket||!info.getEndpointName().startsWith("PCR • "))return;
                    found.put(id,info.getEndpointName());refreshList();}
                @Override public void onEndpointLost(String id){if(active&&generation==ticket){found.remove(id);refreshList();}}
            },new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
                .addOnFailureListener(e->{if(active&&generation==ticket){stop();listener.status("Traženje za uparivanje nije uspjelo. Provjeri Google Play usluge i dozvole. "+failure(e));}});
        }
        main.postDelayed(()->{if(active&&generation==ticket){stop();listener.status("Vrijeme za dodavanje člana je isteklo.");}},90000);
    }
    String failure(Exception e){return e instanceof com.google.android.gms.common.api.ApiException?"Kod: "+((com.google.android.gms.common.api.ApiException)e).getStatusCode():e.getClass().getSimpleName();}
    void refreshList(){if(list!=null){list.clear();list.addAll(found.values());list.notifyDataSetChanged();}}
    final ConnectionLifecycleCallback lifecycle=new ConnectionLifecycleCallback(){
        @Override public void onConnectionInitiated(String id,ConnectionInfo info){
            if(!active||(peer!=null&&!peer.equals(id))){client.rejectConnection(id);return;}
            peer=id;String digits=info.getAuthenticationDigits();
            if(digits==null||!digits.matches("[0-9]{4,10}")){client.rejectConnection(id);stop();listener.status("Nema valjanog koda za potvrdu veze.");return;}
            if(dialog!=null)dialog.dismiss();
            if(host){
                client.stopAdvertising();
                dialog=new AlertDialog.Builder(activity).setTitle("Dodaj: "+info.getEndpointName())
                    .setMessage("Kolega neka na svojem telefonu upiše ovaj broj:\n\n"+digits+"\n\nOdobri samo ako je to osoba koju dodaješ.")
                    .setPositiveButton("Odobri",(d,w)->accept(id)).setNegativeButton("Odbij",(d,w)->{client.rejectConnection(id);stop();})
                    .setOnCancelListener(d->{client.rejectConnection(id);stop();}).create();
                dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }else{
                EditText input=new EditText(activity);input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setHint("Broj s kolegina telefona");
                input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
                dialog=new AlertDialog.Builder(activity).setTitle("Upiši broj za uparivanje").setView(input)
                    .setPositiveButton("Poveži",null).setNegativeButton("Odustani",(d,w)->{client.rejectConnection(id);stop();})
                    .setOnCancelListener(d->{client.rejectConnection(id);stop();}).create();
                AlertDialog current=dialog;
                current.setOnShowListener(d->current.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                    if(!digits.equals(input.getText().toString().trim())){
                        input.setError("Broj se ne podudara");if(++failures>=3){client.rejectConnection(id);stop();listener.status("Uparivanje odbijeno nakon tri pogrešna unosa.");}return;}
                    current.dismiss();accept(id);
                }));current.show();
            }
        }
        @Override public void onConnectionResult(String id,ConnectionResolution result){
            if(!active||!id.equals(peer))return;
            if(!result.getStatus().isSuccess()||!authorized.contains(id)){stop();listener.status("Uparivanje nije potvrđeno na oba telefona.");return;}
            if(host){String invite=listener.invitation();if(invite==null){stop();return;}
                client.sendPayload(id,Payload.fromBytes(invite.getBytes(StandardCharsets.UTF_8)))
                    .addOnFailureListener(e->{stop();listener.status("Pozivnica nije poslana.");});
            }
        }
        @Override public void onDisconnected(String id){if(active&&id.equals(peer)){boolean saved=installed;stop();listener.status(saved?"Grupa je spremljena. Pokreni radar.":"Veza za dodavanje člana je prekinuta.");}}
    };
    void accept(String id){if(!active||!id.equals(peer))return;authorized.add(id);
        client.acceptConnection(id,payloads).addOnFailureListener(e->{stop();listener.status("Veza nije prihvaćena.");});}
    final PayloadCallback payloads=new PayloadCallback(){
        @Override public void onPayloadReceived(String id,Payload payload){
            if(!active||!authorized.contains(id)||payload.getType()!=Payload.Type.BYTES)return;
            byte[] bytes=payload.asBytes();if(bytes==null||bytes.length>2048){stop();return;}
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(host){if(text.equals("PCR_ACK")){stop();listener.status("Član je potvrdio spremanje grupe.");}}
            else if(listener.received(text)){
                installed=true;
                Payload ack=Payload.fromBytes("PCR_ACK".getBytes(StandardCharsets.UTF_8));ackId=ack.getId();
                client.sendPayload(id,ack)
                    .addOnFailureListener(e->{stop();listener.status("Grupa je spremljena, ali potvrda nije dostavljena kolegi.");});
                listener.status("Grupa je spremljena. Povezivanje završava nakon dostave potvrde.");
            }else{stop();listener.status("Pozivnica nije spremljena.");}
        }
        @Override public void onPayloadTransferUpdate(String id,PayloadTransferUpdate update){
            if(!host&&active&&update.getPayloadId()==ackId&&update.getStatus()==PayloadTransferUpdate.Status.SUCCESS){
                // The receiver's only outgoing payload is the ACK. Allow delivery before shutting down.
                int ticket=generation;main.postDelayed(()->{if(active&&generation==ticket){stop();listener.status("Grupa dodana. Pokreni radar na oba telefona.");}},1200);
            }
        }
    };
    void stop(){boolean wasActive=active;active=false;generation++;authorized.clear();peer=null;found.clear();
        if(dialog!=null){dialog.dismiss();dialog=null;}
        if(wasActive)try{client.stopAdvertising();client.stopDiscovery();client.stopAllEndpoints();}catch(RuntimeException ignored){}
    }
}
