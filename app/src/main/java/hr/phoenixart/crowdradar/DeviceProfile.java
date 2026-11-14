package hr.phoenixart.crowdradar;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.os.Build;
import hr.phoenixart.crowdradar.core.Capabilities;
import org.json.JSONObject;
import java.util.UUID;

final class DeviceProfile {
    final String id,name,model; final int caps;
    DeviceProfile(String id,String name,String model,int caps){this.id=id;this.name=name;this.model=model;this.caps=caps;}
    static DeviceProfile local(Context c) {
        android.content.SharedPreferences prefs=c.getSharedPreferences("identity",0);
        String id=prefs.getString("id",null);
        if(id==null){id=UUID.randomUUID().toString();prefs.edit().putString("id",id).apply();}
        PackageManager pm=c.getPackageManager();int bits=0;
        if(pm.hasSystemFeature("android.hardware.bluetooth_le"))bits|=Capabilities.BLE;
        if(pm.hasSystemFeature("android.hardware.uwb"))bits|=Capabilities.UWB;
        if(pm.hasSystemFeature("android.hardware.wifi.aware"))bits|=Capabilities.AWARE;
        if(pm.hasSystemFeature("android.hardware.wifi.rtt"))bits|=Capabilities.RTT;
        if(pm.hasSystemFeature("android.hardware.location.gps"))bits|=Capabilities.GPS;
        SensorManager s=c.getSystemService(SensorManager.class);
        if(s!=null && s.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)!=null)bits|=Capabilities.COMPASS;
        return new DeviceProfile(id,prefs.getString("name","Korisnik"),Build.MANUFACTURER+" "+Build.MODEL,bits);
    }
    JSONObject json() throws Exception{return new JSONObject().put("id",id).put("name",name).put("model",model).put("caps",caps);}
    static DeviceProfile parse(JSONObject json)throws Exception{
        String id=json.getString("id"),name=json.getString("name"),model=json.getString("model");
        if(id.length()>64 || name.trim().isEmpty() || name.length()>40 || model.length()>100)throw new IllegalArgumentException("Invalid profile");
        UUID.fromString(id);return new DeviceProfile(id,name,model,json.getInt("caps")&63);
    }
}
