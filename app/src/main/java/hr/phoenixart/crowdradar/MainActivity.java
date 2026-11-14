package hr.phoenixart.crowdradar;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import hr.phoenixart.crowdradar.core.Capabilities;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.*;
import android.os.*;
import android.text.InputType;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import hr.phoenixart.crowdradar.core.RadarMath;
import java.security.SecureRandom;
import java.util.*;

public final class MainActivity extends ComponentActivity implements SensorEventListener {
    static final int BG = Color.rgb(5,11,19), PANEL = Color.rgb(10,24,37), CYAN = Color.rgb(48,228,244),
        GREEN = Color.rgb(118,245,172), AMBER = Color.rgb(255,192,99), MUTED = Color.rgb(130,159,179);
    final Handler handler = new Handler(Looper.getMainLooper());
    final Map<Integer, Peer> peers = new LinkedHashMap<>();
    byte[] groupKey;
    GroupStore groupStore;
    ProfileLink profiles;
    NumberPairing pairing;
    DeviceProfile own;
    String groupName;
    long groupRevision;
    int pendingPairAction;
    BlePresence ble;
    boolean demo, searching, visible;
    Float heading;
    SensorManager sensors;
    TextView status, groupLabel, memberLabel, hardware;
    LinearLayout memberList;
    Button searchButton, demoButton;
    Radar radar;
    String selected;
    static final String[] REQUIRED = { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION };
    final ActivityResultLauncher<ScanOptions> qrScanner=registerForActivityResult(new ScanContract(),result->{
        if(result.getContents()!=null&&!receiveInvite(result.getContents()))status.setText("Neispravna QR pozivnica.");
    });
    static class Peer {
        int id; double rssi, baseline; long seen; String alias; DeviceProfile profile;
        Peer(int id, int signal) { this.id=id; rssi=baseline=signal; alias=String.format(Locale.ROOT,"Član %04X", id & 65535); }
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        groupStore = new GroupStore(this);
        try { groupKey = groupStore.load(); } catch (Exception e) { groupKey = null; }
        own=DeviceProfile.local(this);
        groupName=getSharedPreferences("groupInfo",0).getString("name","Moja grupa");
        groupRevision=getSharedPreferences("groupInfo",0).getLong("revision",0);
        profiles=new ProfileLink(this,(id,profile,name,revision)->{
            Peer peer=peers.get(id);if(peer==null)return;peer.profile=profile;peer.alias=profile.name;
            if(revision>groupRevision&&!name.trim().isEmpty()&&name.length()<=40){groupName=name;groupRevision=revision;saveGroupInfo();updateGroup();}
        });
        pairing=new NumberPairing(this,new NumberPairing.Listener(){
            public String invitation(){return invitationToken();}
            public boolean received(String token){return receiveInvite(token);}
            public void status(String message){status.setText(message);}
        });
        sensors = getSystemService(SensorManager.class);
        ble = new BlePresence(this, new BlePresence.Listener() {
            public void peer(int id, int rssi, BluetoothDevice device) {
                if (demo || !searching) return;
                Peer peer = peers.get(id);
                if (peer == null) { if (peers.size() >= 24) return; peer = new Peer(id,rssi); peers.put(id,peer); }
                peer.rssi = RadarMath.smooth(peer.rssi, rssi); peer.seen = SystemClock.elapsedRealtime(); profiles.fetch(id,device);
            }
            public void status(String message) {
                status.setText(message);
                if (!message.startsWith("Tražim")) { searching=false; searchButton.setText("POKRENI RADAR"); }
            }
        });
        build();
    }
    int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    TextView label(String text, int size, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL)); v.setPadding(0,dp(4),0,dp(4)); return v;
    }
    Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setTextColor(CYAN); b.setTextSize(12);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
        b.setOnClickListener(v -> action.run()); b.setMinHeight(dp(52)); return b;
    }
    void build() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(20),dp(20),dp(24));
        scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        root.addView(label("PHOENIX / FIELD TOOLS",11,CYAN));
        root.addView(label("CROWD RADAR",28,Color.WHITE));
        groupLabel = label("",12,MUTED); root.addView(groupLabel); groupLabel.setOnClickListener(v->editGroupName()); updateGroup();
        status = label("Kreiraj grupu ili zalijepi pozivnicu.",13,AMBER); root.addView(status);
        radar = new Radar(); int diameter = Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(480));
        root.addView(radar,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,diameter));
        root.addView(label("SAMO BLIZINA / SMJER NIJE IZMJEREN",11,CYAN));
        memberLabel = label("ČLANOVI GRUPE • 0",14,Color.WHITE); root.addView(memberLabel);
        memberList = new LinearLayout(this); memberList.setOrientation(LinearLayout.VERTICAL); root.addView(memberList);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        searchButton = button("POKRENI RADAR",this::toggleSearch);
        actions.addView(searchButton,new LinearLayout.LayoutParams(0,dp(56),1));
        actions.addView(button("GRUPA",this::groupMenu),new LinearLayout.LayoutParams(0,dp(56),1)); root.addView(actions);
        demoButton = button("DEMO IZGLEDA",this::toggleDemo); root.addView(demoButton);
        root.addView(button("MOJ PROFIL I MOGUĆNOSTI",this::profileMenu));
        hardware = label("",11,MUTED); root.addView(hardware);
        refreshHardware();
        root.addView(label("PHOENIX ART  /  by Željko Kušter\nv0.2.0-dev • razvojna verzija • samo uz otvorenu aplikaciju",10,MUTED));
        refreshMembers();
    }
    void updateGroup() { groupLabel.setText(groupKey == null ? "NEMA GRUPE • OFFLINE" : groupName+"  ✎  •  "+own.name); }
    void saveGroupInfo(){getSharedPreferences("groupInfo",0).edit().putString("name",groupName).putLong("revision",groupRevision).apply();}
    void refreshHardware(){hardware.setText("TVOJ UREĐAJ: "+own.model+"\nPodržano: "+Capabilities.supported(own.caps)+"\nPreciznost sada: BLE signalni razred; smjer i metri nisu izmjereni.");}
    void profileMenu(){
        EditText input=new EditText(this);input.setText(own.name);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        new AlertDialog.Builder(this).setTitle("Moje ime / uređaj").setMessage(own.model+"\nPodržano: "+Capabilities.supported(own.caps)+"\n\nOvo je podrška hardvera, ne potvrda aktivnog preciznog mjerenja.")
            .setView(input).setPositiveButton("Spremi",(d,w)->{String name=input.getText().toString().trim();if(name.isEmpty())return;
                getSharedPreferences("identity",0).edit().putString("name",name).apply();own=DeviceProfile.local(this);profiles.own=own;updateGroup();refreshHardware();})
            .setNegativeButton("Odustani",null).show();
    }
    void editGroupName(){
        EditText input=new EditText(this);input.setText(groupName);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        new AlertDialog.Builder(this).setTitle("Naziv grupe / događaja").setView(input).setPositiveButton("Spremi",(d,w)->{
            String name=input.getText().toString().trim();if(name.isEmpty())return;groupName=name;groupRevision=Math.max(System.currentTimeMillis(),groupRevision+1);saveGroupInfo();updateGroup();
        }).setNegativeButton("Odustani",null).show();
    }
    void stop() { searching = false; ble.stop(); if(profiles!=null)profiles.stop(); if (searchButton != null) searchButton.setText("POKRENI RADAR"); }
    boolean allowed() {
        for (String p : REQUIRED) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }
    void toggleSearch() {
        if (searching) { stop(); status.setText("Radar zaustavljen."); return; }
        if (groupKey == null) { groupMenu(); return; }
        if (!allowed()) { requestPermissions(REQUIRED,101); return; }
        pairing.stop(); demo=false; demoButton.setText("DEMO IZGLEDA"); peers.clear(); searching=true; searchButton.setText("ZAUSTAVI"); ble.start(groupKey); if(searching)profiles.start(groupKey,ble.sessionId(),own);
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==102){if(pairPermissions())startPair(pendingPairAction==1);else status.setText("Dozvole za brojčano uparivanje nisu odobrene.");return;}
        if (requestCode == 101 && allowed()) toggleSearch();
        else status.setText("Za BLE radar odobri obližnje uređaje i preciznu lokaciju. GPS se u ovoj verziji ne dijeli.");
    }
    void toggleDemo() {
        stop(); demo=!demo; peers.clear(); selected=null;
        status.setText(demo?"DEMO • simulirane osobe i smjerovi; senzori ne lociraju te osobe.":"Demo isključen. Pokreni BLE radar.");
        demoButton.setText(demo?"ISKLJUČI DEMO":"DEMO IZGLEDA"); refreshMembers(); radar.invalidate();
    }
    void groupMenu() {
        new AlertDialog.Builder(this).setTitle("Grupa / dodaj člana")
            .setItems(new String[]{"Promijeni naziv grupe", "Kreiraj novu grupu", "Prikaži QR pozivnicu", "Skeniraj QR kod", "Dodaj člana brojem", "Pridruži se upisom broja", "Zalijepi tekst pozivnice", "Napusti grupu"},(d,which)->{
                if(which==0)editGroupName();
                else if(which==1)new AlertDialog.Builder(this).setMessage("Zamijeniti grupu na ovom telefonu?").setPositiveButton("Kreiraj",(x,y)->{byte[] key=new byte[32];new SecureRandom().nextBytes(key);installGroup(key,groupName,System.currentTimeMillis());}).setNegativeButton("Odustani",null).show();
                else if(which==2)invite();
                else if(which==3){stop();pairing.stop();qrScanner.launch(new ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Skeniraj Phoenix Crowd Radar pozivnicu").setBeepEnabled(false).setOrientationLocked(false));}
                else if(which==4)startPair(true);
                else if(which==5)startPair(false);
                else if(which==6)importGroup();
                else new AlertDialog.Builder(this).setMessage("Ukloniti grupni ključ i zaustaviti radar?").setPositiveButton("Napusti",(x,y)->{
                    stop();pairing.stop();demo=false;groupStore.clear();if(groupKey!=null)Arrays.fill(groupKey,(byte)0);groupKey=null;peers.clear();updateGroup();status.setText("Napustio si grupu.");refreshMembers();
                }).setNegativeButton("Odustani",null).show();
            }).setNegativeButton("Zatvori",null).show();
    }
    boolean pairPermissions(){if(!allowed())return false;return Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)==PackageManager.PERMISSION_GRANTED;}
    void startPair(boolean host){
        if(host&&groupKey==null){status.setText("Prvo kreiraj grupu.");return;}
        if(!pairPermissions()){
            pendingPairAction=host?1:2;ArrayList<String> permissions=new ArrayList<>(Arrays.asList(REQUIRED));
            if(Build.VERSION.SDK_INT>=33)permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);requestPermissions(permissions.toArray(new String[0]),102);return;
        }
        stop();demo=false;peers.clear();refreshMembers();pairing.start(host,groupName+" / "+own.name);
    }
    boolean installGroup(byte[] key,String name,long revision) {
        try {groupStore.save(key);stop();if(groupKey!=null)Arrays.fill(groupKey,(byte)0);groupKey=key;groupName=name;groupRevision=revision;saveGroupInfo();demo=false;peers.clear();updateGroup();status.setText("Grupa spremljena. Pokreni radar.");refreshMembers();return true;}
        catch(Exception e){status.setText("Ključ nije spremljen. Grupa nije promijenjena.");return false;}
    }
    String invitationToken(){
        if(groupKey==null)return null;
        try {JSONObject json=new JSONObject().put("v",2).put("key",Base64.encodeToString(groupKey,Base64.NO_WRAP)).put("name",groupName).put("revision",groupRevision);
            return "phoenix2:"+Base64.encodeToString(json.toString().getBytes(StandardCharsets.UTF_8),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
        }catch(Exception e){return null;}
    }
    boolean receiveInvite(String text){
        try {if(text==null||text.length()>2048)return false;text=text.trim();
            if(text.startsWith("phoenix1:")){byte[] key=Base64.decode(text.substring(9),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);if(key.length!=32)return false;return installGroup(key,"Moja grupa",System.currentTimeMillis());}
            if(!text.startsWith("phoenix2:"))return false;
            JSONObject json=new JSONObject(new String(Base64.decode(text.substring(9),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING),StandardCharsets.UTF_8));
            byte[] key=Base64.decode(json.getString("key"),Base64.NO_WRAP);String name=json.getString("name").trim();long revision=json.getLong("revision");
            if(json.getInt("v")!=2||key.length!=32||name.isEmpty()||name.length()>40||revision<0)return false;
            return installGroup(key,name,revision);
        }catch(Exception e){return false;}
    }
    void importGroup(){
        EditText input=new EditText(this);input.setHint("phoenix2:…");input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Zalijepi pozivnicu").setView(input).setPositiveButton("Pridruži se",null).setNegativeButton("Odustani",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(receiveInvite(input.getText().toString()))dialog.dismiss();else input.setError("Neispravna pozivnica");}));dialog.show();
    }
    void invite(){
        String token=invitationToken();if(token==null){status.setText("Prvo kreiraj grupu.");return;}
        try {int size=640;BitMatrix matrix=new MultiFormatWriter().encode(token,BarcodeFormat.QR_CODE,size,size);int[] pixels=new int[size*size];
            for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
            Bitmap bitmap=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);ImageView image=new ImageView(this);image.setImageBitmap(bitmap);
            LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(16),dp(16),dp(16));
            body.addView(label(groupName+"\nPozivnica daje pristup grupi. Pokaži je samo kolegama.",14,Color.WHITE));body.addView(image,new LinearLayout.LayoutParams(-1,dp(260)));
            TextView text=label(token,10,MUTED);text.setTextIsSelectable(true);body.addView(text);ScrollView scroll=new ScrollView(this);scroll.addView(body);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("QR pozivnica").setView(scroll).setPositiveButton("Zatvori",null).create();dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.show();
        }catch(Exception e){status.setText("QR kod nije izrađen.");}
    }
    void refreshMembers() {
        memberList.removeAllViews(); long now=SystemClock.elapsedRealtime();
        peers.values().removeIf(p->now-p.seen>120000);
        if(demo) {
            memberLabel.setText("DEMO GRUPA • 3 SIMULIRANE OSOBE");
            demoRow("01  Marko", "UWB • 12 m • simulirano",CYAN);
            demoRow("02  Ana", "GPS • 38 m • simulirano",GREEN);
            demoRow("03  Ivan", "BLE • signal jača • smjer nepoznat",AMBER);
        } else {
            memberLabel.setText("ČLANOVI GRUPE • "+peers.size());
            for(Peer peer:peers.values()) {
                long age=(now-peer.seen)/1000; TextView row=label(peer.alias+"   "+Math.round(peer.rssi)+" dBm\n"+
                    (age>12?"ZADNJE VIĐEN • prije "+age+" s":RadarMath.band(peer.rssi)+" • "+RadarMath.trend(peer.baseline,peer.rssi))+"\n"+(peer.profile==null?"Podaci uređaja: čekam razmjenu":peer.profile.model+"\nPodržano: "+Capabilities.supported(peer.profile.caps)+"\n"+Capabilities.pairSummary(own.caps,peer.profile.caps))+"\nTrenutačno: BLE blizina; preciznost u metrima nije izmjerena",13,age>12?MUTED:CYAN);
                row.setPadding(dp(12),dp(10),dp(12),dp(10)); row.setBackgroundColor(PANEL);
                row.setOnClickListener(v->{new AlertDialog.Builder(this).setTitle(peer.alias).setMessage(peer.profile==null?"Ime i mogućnosti još nisu razmijenjeni.":
                    peer.profile.model+"\nPodržano: "+Capabilities.supported(peer.profile.caps)+"\n"+Capabilities.pairSummary(own.caps,peer.profile.caps)+"\n\nAktivno: BLE signal. UWB/GPS/RTT preciznost još nije izmjerena.").setPositiveButton("Zatvori",null).show();});
                memberList.addView(row);
            }
            if(peers.isEmpty())memberList.addView(label("Nema detektiranih članova. Na oba telefona otvori istu grupu i pokreni radar.",13,MUTED));
        }
    }
    void demoRow(String name,String info,int color) {
        TextView v=label(name+"\n"+info,13,color);v.setPadding(dp(12),dp(8),dp(12),dp(8));v.setBackgroundColor(PANEL);
        v.setOnClickListener(x->{selected=name;status.setText("DEMO odabir: "+name+" • podaci su simulirani");radar.invalidate();});memberList.addView(v);
    }
    final Runnable tick=new Runnable(){public void run(){if(!visible)return;refreshMembers();radar.invalidate();handler.postDelayed(this,1000);}};
    @Override protected void onResume(){super.onResume();visible=true;
        Sensor sensor=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if(sensor!=null)sensors.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI);handler.post(tick);}
    @Override protected void onPause(){visible=false;handler.removeCallbacks(tick);stop();pairing.stop();if(sensors!=null)sensors.unregisterListener(this);super.onPause();}
    @Override protected void onDestroy(){stop();pairing.stop();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){if(accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE)heading=null;}
    @Override public void onSensorChanged(SensorEvent event){
        if(event.accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE){heading=null;return;}
        float[] rotation=new float[9],remapped=new float[9],orientation=new float[3];SensorManager.getRotationMatrixFromVector(rotation,event.values);
        int display=getDisplay()==null?Surface.ROTATION_0:getDisplay().getRotation();
        int x=SensorManager.AXIS_X,y=SensorManager.AXIS_Y;
        if(display==Surface.ROTATION_90){x=SensorManager.AXIS_Y;y=SensorManager.AXIS_MINUS_X;}
        else if(display==Surface.ROTATION_180){x=SensorManager.AXIS_MINUS_X;y=SensorManager.AXIS_MINUS_Y;}
        else if(display==Surface.ROTATION_270){x=SensorManager.AXIS_MINUS_Y;y=SensorManager.AXIS_X;}
        SensorManager.remapCoordinateSystem(rotation,x,y,remapped);SensorManager.getOrientation(remapped,orientation);
        heading=(float)RadarMath.normalize(Math.toDegrees(orientation[0]));radar.invalidate();
    }
    final class Radar extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Radar(){super(MainActivity.this);setContentDescription("Radar blizine. Smjer članova u BLE načinu nije poznat. Imena i signali su dostupni na popisu ispod radara.");}
        void line(Canvas c,float x,float y,float x2,float y2,int color,float width){p.setColor(color);p.setStrokeWidth(width);c.drawLine(x,y,x2,y2,p);}
        void text(Canvas c,String t,float x,float y,float size,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTypeface(Typeface.MONOSPACE);p.setTextSize(dp(size));p.setTextAlign(Paint.Align.CENTER);c.drawText(t,x,y,p);}
        @Override protected void onDraw(Canvas c){
            float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)-dp(30);if(r<=0)return;
            p.setStyle(Paint.Style.FILL);p.setColor(PANEL);c.drawCircle(x,y,r,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));
            for(int i=1;i<=4;i++){p.setColor(i==4?Color.rgb(35,92,115):Color.rgb(22,53,69));c.drawCircle(x,y,r*i/4,p);}
            line(c,x-r,y,x+r,y,Color.rgb(22,53,69),dp(1));line(c,x,y-r,x,y+r,Color.rgb(22,53,69),dp(1));
            for(int i=0;i<72;i++){double a=Math.toRadians(i*5);float a1=r+(i%6==0?dp(5):dp(2));line(c,x+(float)Math.sin(a)*r,y-(float)Math.cos(a)*r,x+(float)Math.sin(a)*a1,y-(float)Math.cos(a)*a1,Color.rgb(42,90,108),dp(1));}
            if(demo){dot(c,x,y,r*.3f,40,"01",CYAN);dot(c,x,y,r*.78f,220,"02",GREEN);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(4));p.setColor(AMBER);c.drawCircle(x,y,r*.6f,p);
                text(c,"03 • BEZ SMJERA",x,y+r*.64f,9,AMBER);
                text(c,"DEMO",x,y-r-dp(15),12,AMBER);
            }else if(searching||!peers.isEmpty()){
                for(Peer peer:peers.values()){
                    if(SystemClock.elapsedRealtime()-peer.seen>12000)continue;
                    float band=peer.rssi>=-60?.25f:peer.rssi>=-75?.5f:.78f;
                    p.setStyle(Paint.Style.STROKE);p.setColor(CYAN);p.setAlpha(85);p.setStrokeWidth(dp(5));c.drawCircle(x,y,r*band,p);p.setAlpha(255);
                }
                text(c,"SMJER NEPOZNAT",x,y-r*.62f,10,MUTED);
            }else text(c,"RADAR ZAUSTAVLJEN",x,y-r*.55f,10,MUTED);
            p.setStyle(Paint.Style.FILL);p.setColor(CYAN);c.drawCircle(x,y,dp(7),p);
            text(c,"TI",x,y+dp(25),10,CYAN);
            text(c,heading==null?"KOMPAS —":String.format(Locale.ROOT,"KOMPAS %03.0f° MAG",heading),x,y+r+dp(24),10,MUTED);
        }
        void dot(Canvas c,float x,float y,float radius,float angle,String name,int color){double a=Math.toRadians(angle);float px=x+(float)Math.sin(a)*radius,py=y-(float)Math.cos(a)*radius;
            p.setStyle(Paint.Style.FILL);p.setColor(color);p.setAlpha(35);c.drawCircle(px,py,dp(16),p);p.setAlpha(255);c.drawCircle(px,py,dp(6),p);text(c,name,px,py-dp(13),10,color);}
    }
}
