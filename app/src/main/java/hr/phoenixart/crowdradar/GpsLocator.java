package hr.phoenixart.crowdradar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.GeomagneticField;
import android.location.*;
import android.os.*;
import org.json.JSONObject;

/** GNSS only; sharing is explicit and no coordinates are persisted. */
@SuppressLint("MissingPermission")
final class GpsLocator implements LocationListener {
    final LocationManager manager; volatile Location fix; volatile boolean sharing;
    String state="GPS dijeljenje isključeno";
    GpsLocator(Context context){manager=context.getSystemService(LocationManager.class);}
    void start(){stop();sharing=true;
        try{if(manager==null||!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){state="Uključi lokaciju/GPS u postavkama";return;}
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this,Looper.getMainLooper());state="GPS čeka svježu satelitsku lokaciju";
        }catch(RuntimeException e){state="GPS nije dostupan: provjeri preciznu lokaciju";}
    }
    void stop(){sharing=false;fix=null;if(manager!=null)try{manager.removeUpdates(this);}catch(RuntimeException ignored){}state="GPS dijeljenje isključeno";}
    @Override public void onLocationChanged(Location l){if(!sharing||!l.hasAccuracy()||!Float.isFinite(l.getAccuracy())||l.getAccuracy()<=0||l.isMock())return;
        if(!Double.isFinite(l.getLatitude())||!Double.isFinite(l.getLongitude()))return;fix=new Location(l);state="GPS aktivan";}
    @Override public void onProviderDisabled(String provider){fix=null;state="GPS isključen u sustavu";}
    @Override public void onProviderEnabled(String provider){state="GPS čeka svježu lokaciju";}
    long age(){Location f=fix;return f==null?Long.MAX_VALUE:Math.max(0,(SystemClock.elapsedRealtimeNanos()-f.getElapsedRealtimeNanos())/1000000);}
    JSONObject wire()throws Exception{Location f=fix;if(!sharing||f==null||age()>10000)return null;
        return new JSONObject().put("la",f.getLatitude()).put("lo",f.getLongitude()).put("ac",f.getAccuracy()).put("age",age());}
    float declination(){Location f=fix;if(f==null||age()>10000)return Float.NaN;
        return new GeomagneticField((float)f.getLatitude(),(float)f.getLongitude(),(float)f.getAltitude(),System.currentTimeMillis()).getDeclination();}
}
