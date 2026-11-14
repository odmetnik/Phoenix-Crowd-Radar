package hr.phoenixart.crowdradar;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Base64;
import androidx.core.uwb.*;
import androidx.core.uwb.rxjava3.*;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.util.*;

/** One consenting peer; session addresses and random keys are exchanged only in encrypted GATT state. */
final class UwbLocator {
    final Context context; final Handler main=new Handler(Looper.getMainLooper());
    final CompositeDisposable work=new CompositeDisposable();
    volatile String published="{}";
    String status="UWB nije pokrenut",token,remoteToken;
    int generation,localSession,targetSession,config; boolean controller,ready,started;
    UwbManager manager;
    UwbClientSessionScope scope; UwbComplexChannel channel; byte[] key; String address;
    JSONObject offer; Double distance,azimuth; long measuredAt; boolean angleSupported;
    UwbLocator(Context context){this.context=context;}
    boolean permitted(){return context.checkSelfPermission(Manifest.permission.UWB_RANGING)==PackageManager.PERMISSION_GRANTED;}
    void start(int local,int target,DeviceProfile mine,DeviceProfile other){
        stop();localSession=local;targetSession=target;
        if(!context.getPackageManager().hasSystemFeature("android.hardware.uwb")){status="Ovaj telefon nema UWB; koristi GPS/BLE";return;}
        if(!permitted()){status="Odobri UWB dozvolu za precizno mjerenje";return;}
        controller=mine.id.compareTo(other.id)<0;int ticket=generation;token=UUID.randomUUID().toString();
        status="UWB priprema • na oba telefona odaberite jedno drugo";
        try{if(manager==null)manager=UwbManager.createInstance(context);
            Single<? extends UwbClientSessionScope> request=controller?UwbManagerRx.controllerSessionScopeSingle(manager):UwbManagerRx.controleeSessionScopeSingle(manager);
            work.add(request.subscribeOn(Schedulers.io()).subscribe(value->main.post(()->{
                if(ticket!=generation)return;scope=value;
                try{RangingCapabilities caps=scope.getRangingCapabilities();
                    if(!caps.isDistanceSupported())throw new IllegalArgumentException("No distance");
                    angleSupported=caps.isAzimuthalAngleSupported();address=Base64.encodeToString(scope.getLocalAddress().getAddress(),Base64.NO_WRAP);
                    int mask=(caps.getSupportedConfigIds().contains(1)?1:0)|(caps.getSupportedConfigIds().contains(4)?2:0);
                    if(mask==0)throw new IllegalArgumentException("No compatible config");
                    offer=new JSONObject().put("t",targetSession).put("r",controller?1:0).put("a",address).put("cs",mask).put("tok",token);
                    if(controller){channel=((UwbControllerSessionScope)scope).getUwbComplexChannel();key=new byte[16];new SecureRandom().nextBytes(key);
                        int id;do{id=new SecureRandom().nextInt();}while(id==0);
                        offer.put("ch",channel.getChannel()).put("pi",channel.getPreambleIndex()).put("sid",id).put("k",Base64.encodeToString(key,Base64.NO_WRAP));}
                    ready=true;publish();status="UWB spreman • čekam odabir na drugom telefonu";
                }catch(Exception e){fail("UWB parametri nisu dostupni; GPS/BLE ostaju dostupni");}
            }),error->main.post(()->{if(ticket==generation)fail("UWB nije dostupan: provjeri UWB postavku, dozvolu i Google Play usluge");})));
            main.postDelayed(()->{if(ticket==generation&&measuredAt==0)fail("UWB nema mjerenja. Provjeri odabir na oba telefona i pokušaj ponovno.");},25000);
        }catch(RuntimeException e){fail("UWB servis nije dostupan; koristi GPS/BLE");}
    }
    void publish(){published=offer==null?"{}":offer.toString();}
    JSONObject wire(){try{return new JSONObject(published);}catch(Exception e){return new JSONObject();}}
    void remote(int peer,JSONObject data){if(!ready||peer!=targetSession)return;
        try{if(data==null||data.optInt("t",localSession^1)!=localSession)return;
            if(data.getInt("r")== (controller?1:0))return;
            String theirToken=data.getString("tok");
            if(started){if(!theirToken.equals(remoteToken))fail("UWB sesija drugog telefona promijenjena. Pokreni ponovno.");return;}
            int common=offer.getInt("cs")&data.getInt("cs");int selected=(common&2)!=0?4:(common&1)!=0?1:0;
            if(selected==0){fail("Nema zajedničkog UWB načina; koristi GPS/BLE");return;}
            byte[] peerAddress=Base64.decode(data.getString("a"),Base64.NO_WRAP);if(peerAddress.length!=2&&peerAddress.length!=8)throw new IllegalArgumentException();
            int sessionId;byte[] sessionKey;
            if(controller){config=selected;offer.put("c",config);publish();sessionId=offer.getInt("sid");sessionKey=key;}
            else{if(!data.has("c"))return;config=data.getInt("c");if(config!=selected)throw new IllegalArgumentException();
                sessionId=data.getInt("sid");sessionKey=Base64.decode(data.getString("k"),Base64.NO_WRAP);if(sessionKey.length!=16)throw new IllegalArgumentException();
                int ch=data.getInt("ch"),pi=data.getInt("pi");if(!scope.getRangingCapabilities().getSupportedChannels().contains(ch))throw new IllegalArgumentException();
                channel=new UwbComplexChannel(ch,pi);}
            remoteToken=theirToken;started=true;int ticket=generation;
            RangingParameters parameters=new RangingParameters(config,sessionId,0,config==4?sessionKey:Arrays.copyOf(sessionKey,8),null,channel,
                Collections.singletonList(new UwbDevice(new UwbAddress(peerAddress))),RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC);
            status="UWB sesija pokrenuta • čekam stvarno mjerenje";
            work.add(UwbClientSessionScopeRx.rangingResultsObservable(scope,parameters).subscribeOn(Schedulers.io()).subscribe(result->main.post(()->{
                if(ticket!=generation)return;
                if(result instanceof RangingResult.RangingResultPosition){RangingPosition p=((RangingResult.RangingResultPosition)result).getPosition();
                    long stamp=p.getElapsedRealtimeNanos()/1000000,now=SystemClock.elapsedRealtime();
                    if(stamp<=0||stamp>now||now-stamp>3000)return;
                    RangingMeasurement d=p.getDistance();if(d==null||!Float.isFinite(d.getValue())||d.getValue()<0)return;
                    distance=(double)d.getValue();RangingMeasurement a=p.getAzimuth();
                    azimuth=angleSupported&&a!=null&&Float.isFinite(a.getValue())?(double)a.getValue():null;
                    measuredAt=stamp;status="UWB aktivan • "+(config==4?"provisioned STS":"static STS");
                }else if(result instanceof RangingResult.RangingResultPeerDisconnected){distance=null;azimuth=null;measuredAt=0;status="UWB mjerenje prekinuto; koristi GPS/BLE";}
            }),error->main.post(()->{if(ticket==generation)fail("UWB sesija nije uspjela. Pokušaj ponovno ili koristi GPS/BLE.");})));
        }catch(Exception e){fail("UWB dogovor nije valjan; koristi GPS/BLE");}
    }
    void fail(String reason){stop();status=reason;}
    void stop(){generation++;work.clear();ready=false;started=false;offer=null;published="{}";scope=null;distance=null;azimuth=null;measuredAt=0;remoteToken=null;
        if(key!=null)Arrays.fill(key,(byte)0);key=null;status="UWB nije pokrenut";}
}
