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
import hr.phoenixart.crowdradar.core.Navigation;
import android.location.Location;
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
    GpsLocator gps;
    UwbLocator uwb;
    Integer selectedSession;
    long headingAt,postureAt;
    double backHorizontal;
    int displayRotation;
    Button gpsButton;
    TextView rangeMode;
    boolean hadCompassAccuracy;
    String wireProblem="";
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
    Dashboard dashboard;
    long pingId,pingAt; int pingTarget;
    String selected;
    static final String[] REQUIRED = { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION };
    final ActivityResultLauncher<ScanOptions> qrScanner=registerForActivityResult(new ScanContract(),result->{
        if(result.getContents()!=null){if(receiveInvite(result.getContents()))toggleSearch();else status.setText("Neispravna QR pozivnica.");}renderDashboard();
    });
    static class Peer {
        int id; double rssi, baseline; long seen; String alias; DeviceProfile profile;
        boolean hasGeo; double lat,lon,error; long fixAge,geoAt,stateAt,lastPing;
        Peer(int id, int signal) { this.id=id; rssi=baseline=signal; alias=String.format(Locale.ROOT,"Član %04X", id & 65535); }
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        groupStore = new GroupStore(this);
        try { groupKey = groupStore.load(); } catch (Exception e) { groupKey = null; }
        gps=new GpsLocator(this);uwb=new UwbLocator(this);
        own=DeviceProfile.local(this);
        groupName=getSharedPreferences("groupInfo",0).getString("name","Moja grupa");
        groupRevision=getSharedPreferences("groupInfo",0).getLong("revision",0);
        profiles=new ProfileLink(this,(id,profile,name,revision)->{
            Peer peer=peers.get(id);if(peer==null)return;peer.profile=profile;peer.alias=profile.name;
            if(revision>groupRevision&&!name.trim().isEmpty()&&name.length()<=40){groupName=name;groupRevision=revision;saveGroupInfo();updateGroup();}
        });
        profiles.stateSupplier=()->{JSONObject state=new JSONObject();try{JSONObject g=gps.wire();if(g!=null)state.put("g",g);JSONObject u=uwb.wire();if(u.length()>0)state.put("u",u);if(pingId!=0&&SystemClock.elapsedRealtime()-pingAt<10000)state.put("p",new JSONObject().put("id",pingId).put("target",pingTarget));}catch(Exception ignored){}return state;};
        profiles.stateListener=(id,data,latency)->{
            Peer peer=peers.get(id);if(peer==null)return;peer.stateAt=SystemClock.elapsedRealtime();peer.hasGeo=false;
            JSONObject g=data.optJSONObject("g");
            if(g!=null)try{double lat=g.getDouble("la"),lon=g.getDouble("lo"),error=g.getDouble("ac");long age=Navigation.receivedFixAge(g.getLong("age"),latency,0);
                if(Navigation.validFix(lat,lon,error,age)){peer.lat=lat;peer.lon=lon;peer.error=error;peer.fixAge=age;peer.geoAt=SystemClock.elapsedRealtime();peer.hasGeo=true;}
            }catch(Exception ignored){}
            uwb.remote(id,data.optJSONObject("u"));
            JSONObject ping=data.optJSONObject("p");
            if(ping!=null&&ping.optInt("target") == ble.sessionId()&&ping.optLong("id")!=0&&ping.optLong("id")!=peer.lastPing){
                peer.lastPing=ping.optLong("id");signalPing(peer.alias);
            }
        };
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
        status=label("Kreiraj grupu ili se pridruži kolegama.",13,AMBER);
        groupLabel=label("",12,MUTED);memberLabel=label("",14,Color.WHITE);hardware=label("",11,MUTED);rangeMode=label("",11,CYAN);
        searchButton=button("POKRENI RADAR",this::toggleSearch);demoButton=button("DEMO IZGLEDA",this::toggleDemo);gpsButton=button("UKLJUČI GPS DIJELJENJE",this::toggleGps);
        dashboard=new Dashboard(this,this::dashboardAction);setContentView(dashboard.view);
        dashboard.view.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        updateGroup();refreshHardware();
    }
    void dashboardAction(String action,String value){
        switch(action){
            case "ready":break;
            case "radar":toggleSearch();break;
            case "demo":toggleDemo();break;
            case "create":createGroup(false,null);break;
            case "rename":if(groupKey==null)createGroup(false,null);else editGroupName();break;
            case "invite":if(groupKey==null)createGroup(false,this::invite);else invite();break;
            case "scan":scanInvite();break;
            case "host":startPair(true);break;
            case "join":startPair(false);break;
            case "paste":importGroup();break;
            case "leave":leaveGroup();break;
            case "profile":profileMenu();break;
            case "help":showHelp();break;
            case "gps":toggleGps();break;
            case "uwbStop":uwb.stop();status.setText("UWB zaustavljen.");break;
            case "select":try{selectPeer(Integer.parseInt(value));}catch(NumberFormatException ignored){}break;
            case "find":if(!searching){toggleSearch();}else choosePeer();break;
            case "ping":sendPing();break;
            default:break;
        }
    }
    void renderDashboard(){
        if(dashboard==null)return;
        try{
            JSONObject data=new JSONObject().put("group",groupName).put("hasGroup",groupKey!=null).put("own",own.name)
                .put("demo",demo).put("running",searching).put("status",status.getText()).put("version",appVersion())
                .put("heading",heading==null?JSONObject.NULL:heading).put("gps",gps.sharing).put("hardware",hardware.getText())
                .put("selected",selectedSession==null?JSONObject.NULL:selectedSession);
            org.json.JSONArray members=new org.json.JSONArray();
            if(demo){members.put(new JSONObject().put("id",1).put("name","Marko").put("source","UWB").put("meters",12).put("angle",50));
                members.put(new JSONObject().put("id",2).put("name","Ana").put("source","GPS").put("meters",38).put("angle",224));
                members.put(new JSONObject().put("id",3).put("name","Ivan").put("source","BLE").put("meters",JSONObject.NULL).put("angle",JSONObject.NULL));}
            else for(Peer peer:peers.values()){
                Estimate e=estimate(peer);boolean stale=SystemClock.elapsedRealtime()-peer.seen>12000;
                members.put(new JSONObject().put("id",peer.id).put("name",peer.alias).put("source",e.distanceSource.replace(" ~",""))
                    .put("meters",!stale&&Double.isFinite(e.meters)?e.meters:JSONObject.NULL).put("angle",!stale&&e.angle!=null?e.angle:JSONObject.NULL).put("stale",stale));
            }
            Peer target=selectedSession==null?null:peers.get(selectedSession);data.put("selectedName",target==null?"":target.alias);
            data.put("members",members);dashboard.render(data);
        }catch(org.json.JSONException ignored){}
    }
    void createGroup(boolean startAfter,Runnable after){
        EditText input=new EditText(this);input.setText(groupName);input.setSelectAllOnFocus(true);input.setSingleLine();input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(groupKey==null?"Kreiraj grupu":"Zamijeni postojeću grupu")
            .setMessage(groupKey==null?"Upiši naziv događaja ili grupe.":"Nova grupa zamjenjuje grupu na ovom telefonu. Kolege će trebati novu pozivnicu.")
            .setView(input).setPositiveButton("Kreiraj",null).setNegativeButton("Odustani",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=input.getText().toString().trim();if(name.isEmpty()){input.setError("Upiši naziv grupe");return;}
            byte[] key=new byte[32];new SecureRandom().nextBytes(key);
            if(installGroup(key,name,System.currentTimeMillis())){dialog.dismiss();if(startAfter)toggleSearch();if(after!=null)after.run();}
            else input.setError("Spremanje grupe nije uspjelo. Provjeri poruku aplikacije.");renderDashboard();
        }));dialog.show();
    }
    void firstRun(){
        new AlertDialog.Builder(this).setTitle("Pokreni radar • odaberi grupu")
            .setMessage("Za pronalaženje kolega oba telefona moraju biti u istoj grupi.")
            .setPositiveButton("Kreiraj grupu",(d,w)->createGroup(true,null))
            .setNeutralButton("Skeniraj QR",(d,w)->scanInvite())
            .setNegativeButton("Pridruži se brojem",(d,w)->startPair(false)).show();
    }
    void scanInvite(){stop();pairing.stop();demo=false;qrScanner.launch(new ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Skeniraj Phoenix Crowd Radar pozivnicu").setBeepEnabled(false).setOrientationLocked(false));}
    void leaveGroup(){new AlertDialog.Builder(this).setMessage("Napustiti grupu i zaustaviti radar?").setPositiveButton("Napusti",(d,w)->{
        stop();pairing.stop();demo=false;groupStore.clear();if(groupKey!=null)Arrays.fill(groupKey,(byte)0);groupKey=null;peers.clear();selectedSession=null;updateGroup();status.setText("Napustio si grupu.");refreshMembers();
    }).setNegativeButton("Odustani",null).show();}
    void choosePeer(){
        if(peers.isEmpty()){status.setText("Radar radi. Čekam kolege iz iste grupe.");return;}
        Peer[] list=peers.values().toArray(new Peer[0]);String[] names=new String[list.length];for(int i=0;i<list.length;i++)names[i]=list[i].alias;
        new AlertDialog.Builder(this).setTitle("Traži osobu").setItems(names,(d,i)->selectPeer(list[i].id)).setNegativeButton("Zatvori",null).show();
    }
    void selectPeer(int id){
        if(demo){status.setText("DEMO • odabir je simuliran.");return;}
        Peer peer=peers.get(id);if(peer==null)return;selectedSession=id;profiles.focus=id;renderDashboard();
        String details="BLE signal: "+Math.round(peer.rssi)+" dBm • "+RadarMath.band(peer.rssi)+"\n"+(peer.profile==null?"Profil još nije razmijenjen.":peer.profile.model+"\nPodržano: "+Capabilities.supported(peer.profile.caps));
        new AlertDialog.Builder(this).setTitle(peer.alias).setMessage(measurementText(peer)+"\n\n"+details+"\n\nZa UWB odaberite jedno drugo na oba telefona. Držite telefone uspravno u portretu.")
            .setPositiveButton("Traži UWB",(d,w)->{startUwb(peer);renderDashboard();}).setNeutralButton("Pošalji ping",(d,w)->{sendPing();renderDashboard();}).setNegativeButton("Zatvori",null).show();
    }
    void sendPing(){
        Peer peer=selectedSession==null?null:peers.get(selectedSession);
        if(demo||!searching||peer==null||peer.profile==null||SystemClock.elapsedRealtime()-peer.seen>12000){status.setText("Pokreni radar i odaberi aktivnog člana s razmijenjenim profilom.");return;}
        pingTarget=peer.id;pingId=new SecureRandom().nextInt(Integer.MAX_VALUE)+1L;pingAt=SystemClock.elapsedRealtime();status.setText("Ping pripremljen za "+peer.alias+". Dostava ovisi o aktivnoj vezi; nema potvrde primitka.");
    }
    void signalPing(String name){
        status.setText("PING od: "+name);
        android.media.ToneGenerator tone=new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,80);
        tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2,500);handler.postDelayed(tone::release,650);
    }
    String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException e){return "dev";}}
    void updateGroup() { groupLabel.setText(groupKey == null ? "NEMA GRUPE • OFFLINE" : groupName+"  ✎  •  "+own.name); }
    void saveGroupInfo(){getSharedPreferences("groupInfo",0).edit().putString("name",groupName).putLong("revision",groupRevision).apply();}
    void refreshHardware(){hardware.setText("TVOJ UREĐAJ: "+own.model+"\nPodržano: "+Capabilities.supported(own.caps)+"\n"+gps.state+"\n"+uwb.status);}
    void showHelp(){
        try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(getAssets().open("help.txt"),StandardCharsets.UTF_8))){
            StringBuilder text=new StringBuilder();String line;while((line=reader.readLine())!=null)text.append(line).append('\n');
            String full=text.toString();
            String[] titles={"Brzi početak","Bluetooth / BLE","GPS / GNSS","UWB traženje","Kompas","QR, kod i Wi-Fi","Radar i preciznost","Privatnost i ograničenja","Promjene verzije","Cijela pomoć"};
            String[] markers={"BRZI POČETAK","BLE — BLIZINA","GPS / GNSS —","UWB — IZRAVNO","KOMPAS I ORIJENTACIJA","QR, BROJČANI KOD","KAKO ČITATI RADAR","PRIVATNOST I OGRANIČENJA","PROMJENE —"};
            new AlertDialog.Builder(this).setTitle("Pomoć • "+appVersion()).setItems(titles,(d,which)->{
                String content=full;
                if(which<markers.length){int start=full.indexOf(markers[which]);int end=which+1<markers.length?full.indexOf(markers[which+1]):full.length();
                    if(start>=0&&end>start)content=full.substring(start,end).trim();}
                TextView view=label(content,14,Color.WHITE);view.setPadding(dp(18),dp(12),dp(18),dp(20));view.setTextIsSelectable(true);
                ScrollView scroll=new ScrollView(this);scroll.addView(view);
                new AlertDialog.Builder(this).setTitle(titles[which]).setView(scroll).setPositiveButton("Zatvori",null).setNeutralButton("Teme",(x,y)->showHelp()).show();
            }).setNegativeButton("Zatvori",null).show();
        }catch(java.io.IOException e){status.setText("Pomoć nije dostupna u instalaciji.");}
    }
    void toggleGps(){
        if(gps.sharing){gps.stop();gpsButton.setText("UKLJUČI GPS DIJELJENJE");status.setText("GPS dijeljenje isključeno.");return;}
        if(!searching){status.setText("Prvo pokreni radar, zatim uključi GPS dijeljenje.");return;}
        gps.start();gpsButton.setText("ISKLJUČI GPS DIJELJENJE");status.setText(gps.state);
    }
    void startUwb(Peer peer){
        if(!searching||peer.profile==null){status.setText("Pokreni radar i pričekaj razmjenu profila.");return;}
        selectedSession=peer.id;profiles.focus=peer.id;
        if((own.caps&peer.profile.caps&Capabilities.UWB)==0){status.setText("Oba telefona moraju imati UWB. GPS/BLE ostaju dostupni.");return;}
        if(!uwb.permitted()){requestPermissions(new String[]{Manifest.permission.UWB_RANGING},103);return;}
        uwb.start(ble.sessionId(),peer.id,own,peer.profile);status.setText(uwb.status);
    }
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
    void stop() { searching = false; ble.stop(); if(profiles!=null)profiles.stop();if(gps!=null)gps.stop();if(uwb!=null)uwb.stop();if(gpsButton!=null)gpsButton.setText("UKLJUČI GPS DIJELJENJE"); if (searchButton != null) searchButton.setText("POKRENI RADAR"); }
    boolean allowed() {
        for (String p : REQUIRED) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }
    void toggleSearch() {
        if (searching) { stop(); status.setText("Radar zaustavljen."); return; }
        if (groupKey == null) { firstRun(); return; }
        boolean askUwb=getPackageManager().hasSystemFeature("android.hardware.uwb")&&!uwb.permitted()&&!getSharedPreferences("identity",0).getBoolean("uwbAsked",false);
        if (!allowed()||askUwb) {
            ArrayList<String> permissions=new ArrayList<>(Arrays.asList(REQUIRED));
            if(askUwb){permissions.add(Manifest.permission.UWB_RANGING);getSharedPreferences("identity",0).edit().putBoolean("uwbAsked",true).apply();}
            requestPermissions(permissions.toArray(new String[0]),101);return;
        }
        pairing.stop(); demo=false; demoButton.setText("DEMO IZGLEDA"); peers.clear(); searching=true; searchButton.setText("ZAUSTAVI"); ble.start(groupKey); if(searching)profiles.start(groupKey,ble.sessionId(),own);
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==103){Peer peer=selectedSession==null?null:peers.get(selectedSession);if(uwb.permitted()&&peer!=null)startUwb(peer);else status.setText("UWB dozvola nije odobrena. Koristi GPS/BLE.");return;}
        if(requestCode==102){if(pairPermissions())startPair(pendingPairAction==1);else status.setText("Dozvole za brojčano uparivanje nisu odobrene.");return;}
        if (requestCode == 101 && allowed()) toggleSearch();
        else status.setText("Za BLE radar odobri obližnje uređaje i preciznu lokaciju. GPS dijeljenje uključuješ zasebnim gumbom.");
    }
    void toggleDemo() {
        stop(); demo=!demo; peers.clear(); selected=null;selectedSession=null;
        status.setText(demo?"DEMO • simulirane osobe i smjerovi; senzori ne lociraju te osobe.":"Demo isključen. Pokreni BLE radar.");
        demoButton.setText(demo?"ISKLJUČI DEMO":"DEMO IZGLEDA"); refreshMembers(); renderDashboard();
    }
    void groupMenu(){new AlertDialog.Builder(this).setTitle("Grupa").setItems(new String[]{"Kreiraj grupu","QR pozivnica","Skeniraj QR","Dodaj člana brojem","Pridruži se brojem"},(d,i)->dashboardAction(new String[]{"create","invite","scan","host","join"}[i],"")).show();}
    boolean pairPermissions(){if(!allowed())return false;return Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)==PackageManager.PERMISSION_GRANTED;}
    void startPair(boolean host){
        if(host&&groupKey==null){createGroup(false,()->startPair(true));return;}
        if(!pairPermissions()){
            pendingPairAction=host?1:2;ArrayList<String> permissions=new ArrayList<>(Arrays.asList(REQUIRED));
            if(Build.VERSION.SDK_INT>=33)permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);requestPermissions(permissions.toArray(new String[0]),102);return;
        }
        stop();demo=false;peers.clear();refreshMembers();pairing.start(host,groupName+" / "+own.name);
    }
    boolean installGroup(byte[] key,String name,long revision) {
        try {groupStore.save(key);stop();if(groupKey!=null)Arrays.fill(groupKey,(byte)0);groupKey=key;groupName=name;groupRevision=revision;saveGroupInfo();demo=false;peers.clear();updateGroup();status.setText("Grupa spremljena. Pokreni radar.");refreshMembers();return true;}
        catch(Exception e){status.setText("Ključ nije spremljen ("+e.getClass().getSimpleName()+"). Grupa nije promijenjena.");return false;}
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
        String token=invitationToken();if(token==null){createGroup(false,this::invite);return;}
        try {int size=640;BitMatrix matrix=new MultiFormatWriter().encode(token,BarcodeFormat.QR_CODE,size,size);int[] pixels=new int[size*size];
            for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
            Bitmap bitmap=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);ImageView image=new ImageView(this);image.setImageBitmap(bitmap);
            LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(16),dp(16),dp(16));
            body.addView(label(groupName+"\nPozivnica daje pristup grupi. Pokaži je samo kolegama.",14,Color.WHITE));body.addView(image,new LinearLayout.LayoutParams(-1,dp(260)));
            TextView text=label(token,10,MUTED);text.setTextIsSelectable(true);body.addView(text);ScrollView scroll=new ScrollView(this);scroll.addView(body);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("QR pozivnica").setView(scroll).setPositiveButton("Zatvori",null).create();dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }catch(Exception e){status.setText("QR kod nije izrađen.");}
    }
    static final class Estimate {
        double meters=Double.NaN;Double angle;String distanceSource="BLE",directionSource="bez smjera",note="";
    }
    Estimate estimate(Peer peer){
        Estimate e=new Estimate();long now=SystemClock.elapsedRealtime();
        boolean uwbLive=selectedSession!=null&&selectedSession==peer.id&&uwb.targetSession==peer.id&&uwb.distance!=null&&Navigation.fresh(uwb.measuredAt,now,3000);
        if(uwbLive){e.meters=uwb.distance;e.distanceSource="UWB";e.note="Pogreška UWB-a nije prijavljena";
            if(uwb.azimuth!=null&&Navigation.antennaPosture(backHorizontal,displayRotation)&&Navigation.fresh(postureAt,now,2000)){e.angle=RadarMath.normalize(uwb.azimuth);e.directionSource="UWB kut antene";}
            else if(uwb.azimuth!=null)e.note="Za UWB kut drži telefon uspravno u portretu";
        }
        Location mine=gps.fix;long theirAge=Navigation.receivedFixAge(peer.fixAge,0,Math.max(0,now-peer.geoAt));
        if(gps.sharing&&mine!=null&&peer.hasGeo&&Navigation.validFix(mine.getLatitude(),mine.getLongitude(),mine.getAccuracy(),gps.age())&&Navigation.validFix(peer.lat,peer.lon,peer.error,theirAge)){
            double d=RadarMath.distance(mine.getLatitude(),mine.getLongitude(),peer.lat,peer.lon),errors=mine.getAccuracy()+peer.error;
            if(!uwbLive){e.meters=d;e.distanceSource="GPS ~";e.note=String.format(Locale.ROOT,"Procjene pogreške telefona: %.0f + %.0f m",mine.getAccuracy(),peer.error);}
            boolean consistent=!uwbLive||Navigation.consistent(d,uwb.distance,errors);
            float declination=gps.declination();
            if(e.angle==null&&consistent&&heading!=null&&Float.isFinite(declination)&&Navigation.fresh(headingAt,now,2000)&&RadarMath.gpsDirectionUsable(d,mine.getAccuracy(),peer.error,gps.age(),theirAge)){
                e.angle=Navigation.relative(RadarMath.bearing(mine.getLatitude(),mine.getLongitude(),peer.lat,peer.lon),heading+declination);e.directionSource="GPS + kompas";
            }else if(!uwbLive&&e.angle==null)e.note+=" • smjer nije dovoljno pouzdan";
            else if(!consistent&&e.angle==null)e.note="GPS i UWB se ne slažu; GPS smjer skriven";
        }
        return e;
    }
    String measurementText(Peer peer){Estimate e=estimate(peer);String distance=Double.isFinite(e.meters)?String.format(Locale.ROOT,"%s %.1f m",e.distanceSource,e.meters):"BLE blizina • bez metara";
        return distance+" • "+e.directionSource+(e.note.isEmpty()?"":"\n"+e.note);}
    void refreshMembers(){
        long now=SystemClock.elapsedRealtime();peers.values().removeIf(p->now-p.seen>120000);
        refreshHardware();if(searching){if(selectedSession!=null&&uwb.targetSession==selectedSession)status.setText(uwb.status);else if(gps.sharing)status.setText(gps.state);}renderDashboard();
    }
    final Runnable tick=new Runnable(){public void run(){if(!visible)return;refreshMembers();handler.postDelayed(this,1000);}};
    @Override protected void onResume(){super.onResume();visible=true;if(dashboard!=null)dashboard.view.onResume();
        Sensor sensor=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if(sensor!=null)sensors.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI);handler.post(tick);}
    @Override protected void onPause(){visible=false;handler.removeCallbacks(tick);if(dashboard!=null)dashboard.view.onPause();if(sensors!=null)sensors.unregisterListener(this);super.onPause();}
    @Override protected void onStop(){stop();pairing.stop();renderDashboard();super.onStop();}
    @Override protected void onDestroy(){stop();pairing.stop();handler.removeCallbacksAndMessages(null);if(dashboard!=null)dashboard.destroy();super.onDestroy();}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){if(accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE){heading=null;headingAt=0;}}
    @Override public void onSensorChanged(SensorEvent event){
        if(event.accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE){heading=null;return;}
        float[] rotation=new float[9],remapped=new float[9],orientation=new float[3];SensorManager.getRotationMatrixFromVector(rotation,event.values);
        int display=getDisplay()==null?Surface.ROTATION_0:getDisplay().getRotation();
        int x=SensorManager.AXIS_X,y=SensorManager.AXIS_Y;
        if(display==Surface.ROTATION_90){x=SensorManager.AXIS_Y;y=SensorManager.AXIS_MINUS_X;}
        else if(display==Surface.ROTATION_180){x=SensorManager.AXIS_MINUS_X;y=SensorManager.AXIS_MINUS_Y;}
        else if(display==Surface.ROTATION_270){x=SensorManager.AXIS_MINUS_Y;y=SensorManager.AXIS_X;}
        SensorManager.remapCoordinateSystem(rotation,x,y,remapped);SensorManager.getOrientation(remapped,orientation);
        displayRotation=display;postureAt=SystemClock.elapsedRealtime();
        backHorizontal=Math.hypot(remapped[2],remapped[5]);
        double east,north;
        if(backHorizontal>.7){east=-remapped[2];north=-remapped[5];}else{east=remapped[1];north=remapped[4];}
        if(Math.hypot(east,north)<.25){heading=null;headingAt=0;}else{heading=(float)RadarMath.normalize(Math.toDegrees(Math.atan2(east,north)));headingAt=SystemClock.elapsedRealtime();}
    }
}
