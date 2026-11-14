package hr.phoenixart.crowdradar;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

/** User-started foreground session; no boot receiver, sticky restart or background camera. */
public final class RadarService extends Service {
    static final int ID=710;static final String CHANNEL="radar-session",ALERTS="radar-alerts";
    RadarEngine engine;PowerManager.WakeLock wake;long lastUpdate;final Handler handler=new Handler(Looper.getMainLooper());
    @Override public void onCreate(){super.onCreate();engine=((RadarApp)getApplication()).engine;
        NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Aktivni radar",NotificationManager.IMPORTANCE_LOW));manager.createNotificationChannel(new NotificationChannel(ALERTS,"Poruke i upozorenja radara",NotificationManager.IMPORTANCE_DEFAULT));}
    PendingIntent open(){return PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    Notification notification(){PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RadarService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Phoenix radar aktivan • "+engine.groupName)
            .setContentText(engine.peers.size()+" članova • poruka: "+engine.unreadMessages+" • dodirni za radar").setContentIntent(open()).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(new Notification.Action.Builder(null,"Zaustavi",stop).build()).build();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null&&"STOP".equals(intent.getAction())){engine.stop();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
        try{startForeground(ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE|ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            engine.service=this;engine.start();if(!engine.searching){stopSelf();return START_NOT_STICKY;}
            if(wake==null){PowerManager power=getSystemService(PowerManager.class);wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Phoenix:RadarSession");wake.acquire(6*60*60*1000L);handler.postDelayed(()->{engine.note="Sesija završena nakon 6 sati.";stopSelf();},6*60*60*1000L);}
        }catch(RuntimeException e){engine.stop();engine.note="Android nije dopustio rad servisa. Ponovno pokreni radar iz otvorene aplikacije.";stopSelf();}
        return START_NOT_STICKY;
    }
    void update(){long now=SystemClock.elapsedRealtime();if(now-lastUpdate<10000)return;lastUpdate=now;getSystemService(NotificationManager.class).notify(ID,notification());}
    void signal(String title){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        getSystemService(NotificationManager.class).notify(711,new Notification.Builder(this,ALERTS).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle(title).setContentText("Otvori Phoenix Crowd Radar").setContentIntent(open()).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(wake!=null&&wake.isHeld())wake.release();wake=null;engine.service=null;engine.stop();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
