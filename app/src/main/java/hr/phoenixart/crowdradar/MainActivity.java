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
    RadarEngine engine;
    GroupStore groupStore;
    long headingAt,postureAt;
    double backHorizontal;
    int displayRotation;
    Button gpsButton;
    TextView rangeMode;
    boolean hadCompassAccuracy;
    String wireProblem="";
    NumberPairing pairing;
    ArOverlay ar;boolean findPending;
    int pendingPairAction;
    boolean demo, visible;
    Float heading;
    SensorManager sensors;
    TextView status, groupLabel, memberLabel, hardware;
    LinearLayout memberList;
    Button searchButton, demoButton;
    Dashboard dashboard;
    final hr.phoenixart.crowdradar.core.MotionState motion=new hr.phoenixart.crowdradar.core.MotionState();
    final float[] gravity=new float[3];long accelStarted;int motionSensorType;boolean gravityReady;
    String selected;
    static final String[] REQUIRED = { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION };
    final ActivityResultLauncher<ScanOptions> qrScanner=registerForActivityResult(new ScanContract(),result->{
        if(result.getContents()!=null){if(receiveInvite(result.getContents()))toggleSearch();else setStatus("Neispravna QR pozivnica.");}renderDashboard();
    });
    static class Peer {
        int id; double rssi, baseline; long seen; String alias; DeviceProfile profile;
        boolean canMessage; boolean hasGeo; double lat,lon,error; long fixAge,geoAt,stateAt,lastPing;
        Peer(int id, int signal) { this.id=id; rssi=baseline=signal; alias=String.format(Locale.ROOT,"Član %04X", id & 65535); }
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        groupStore = new GroupStore(this);
        engine=((RadarApp)getApplication()).engine;ar=new ArOverlay(this);
        pairing=new NumberPairing(this,new NumberPairing.Listener(){
            public String invitation(){return invitationToken();}
            public boolean received(String token){return receiveInvite(token);}
            public void status(String message){setStatus(message);}
        });
        sensors=getSystemService(SensorManager.class);
        build();
    }
    void setStatus(CharSequence text){engine.note=text.toString();if(status!=null)status.setText(text);}
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
        dashboard=new Dashboard(this,this::dashboardAction);
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(BG);frame.addView(dashboard.view,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);
        frame.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        updateGroup();refreshHardware();
    }
    void dashboardAction(String action,String value){
        switch(action){
            case "ready":break;
            case "radar":toggleSearch();break;
            case "demo":toggleDemo();break;
            case "create":createGroup(false,null);break;
            case "rename":if(engine.groupKey==null&&!demo)createGroup(false,null);else editGroupName();break;
            case "invite":if(engine.groupKey==null)createGroup(false,this::invite);else invite();break;
            case "scan":scanInvite();break;
            case "host":startPair(true);break;
            case "join":startPair(false);break;
            case "paste":importGroup();break;
            case "leave":leaveGroup();break;
            case "profile":profileMenu();break;
            case "help":showHelp();break;
            case "gps":toggleGps();break;
            case "uwbStop":engine.uwb.stop();engine.uwbAttempts=3;setStatus("UWB zaustavljen.");break;
            case "select":try{selectPeer(Integer.parseInt(value));}catch(NumberFormatException ignored){}break;
            case "find":if(!engine.searching){findPending=true;toggleSearch();}else choosePeer();break;
            case "ping":sendPing();break;
            case "message":composeMessage();break;
            case "inbox":showMessages();break;
            case "nearby":toggleNearby();break;
            case "ar":openAr();break;
            default:break;
        }
    }
    void renderDashboard(){
        if(dashboard==null)return;
        try{
            JSONObject data=new JSONObject().put("group",engine.groupName).put("hasGroup",engine.groupKey!=null).put("own",engine.own.name)
                .put("demo",demo).put("running",engine.searching).put("status",engine.note).put("version",appVersion())
                .put("heading",heading==null?JSONObject.NULL:heading).put("gps",engine.gps.sharing).put("hardware",hardware.getText())
                .put("transport",engine.searching?(engine.ble.running()?"BLE radi":"BLE ponovno povezivanje"):"Povezivanje isključeno").put("aware",engine.aware.status).put("starting",engine.starting).put("nearby",engine.nearWanted).put("nearStatus",engine.nearby.status).put("mailStatus",mailSummary()).put("unread",engine.unreadMessages).put("motion",motionSummary())
                .put("selected",engine.selectedSession==null?JSONObject.NULL:engine.selectedSession);
            org.json.JSONArray members=new org.json.JSONArray();
            if(demo){members.put(new JSONObject().put("id",1).put("name","Marko").put("source","UWB").put("meters",12).put("angle",50));
                members.put(new JSONObject().put("id",2).put("name","Ana").put("source","GPS").put("meters",38).put("angle",224));
                members.put(new JSONObject().put("id",3).put("name","Ivan").put("source","BLE").put("meters",JSONObject.NULL).put("angle",JSONObject.NULL));}
            else for(Peer peer:engine.peers.values()){
                Estimate e=estimate(peer);boolean stale=!peerFresh(peer);
                members.put(new JSONObject().put("id",peer.id).put("name",peer.alias).put("source",e.distanceSource.replace(" ~",""))
                    .put("meters",!stale&&Double.isFinite(e.meters)?e.meters:JSONObject.NULL).put("angle",!stale&&e.angle!=null?e.angle:JSONObject.NULL).put("stale",stale).put("age",Math.max(0,SystemClock.elapsedRealtime()-Math.max(peer.seen,peer.stateAt))/1000).put("link",engine.links(peer)).put("direction",e.directionSource).put("note",e.note));
            }
            Peer target=engine.selectedSession==null?null:engine.peers.get(engine.selectedSession);data.put("selectedName",target==null?"":target.alias);
            data.put("members",members);dashboard.render(data);
        }catch(org.json.JSONException ignored){}
    }
    void createGroup(boolean startAfter,Runnable after){
        EditText input=new EditText(this);input.setText(engine.groupName);input.setSelectAllOnFocus(true);input.setSingleLine();input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(engine.groupKey==null?"Kreiraj grupu":"Zamijeni postojeću grupu")
            .setMessage(engine.groupKey==null?"Upiši naziv događaja ili grupe.":"Nova grupa zamjenjuje grupu na ovom telefonu. Kolege će trebati novu pozivnicu.")
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
        stop();pairing.stop();engine.messages.clear();engine.mailStatus="";engine.unreadMessages=0;demo=false;groupStore.clear();if(engine.groupKey!=null)Arrays.fill(engine.groupKey,(byte)0);engine.groupKey=null;engine.peers.clear();engine.selectedSession=null;updateGroup();setStatus("Napustio si grupu.");refreshMembers();
    }).setNegativeButton("Odustani",null).show();}
    void choosePeer(){
        if(engine.peers.isEmpty()){setStatus("Radar radi. Čekam kolege iz iste grupe.");return;}
        Peer[] list=engine.peers.values().toArray(new Peer[0]);String[] names=new String[list.length];for(int i=0;i<list.length;i++)names[i]=list[i].alias;
        new AlertDialog.Builder(this).setTitle("Traži osobu").setItems(names,(d,i)->selectPeer(list[i].id)).setNegativeButton("Zatvori",null).show();
    }
    void selectPeer(int id){
        if(demo){setStatus("DEMO • odabir je simuliran.");return;}
        Peer peer=engine.peers.get(id);if(peer==null)return;engine.focus(id);renderDashboard();
        String details=(peer.seen>0?"Zadnji BLE signal: "+Math.round(peer.rssi)+" dBm • "+RadarMath.band(peer.rssi):"BLE RSSI nije izmjeren")+"\n"+engine.links(peer)+"\n"+(peer.profile==null?"Profil još nije razmijenjen.":peer.profile.model+"\nPodržano: "+Capabilities.supported(peer.profile.caps));
        new AlertDialog.Builder(this).setTitle(peer.alias).setMessage(measurementText(peer)+"\n\n"+details+"\n\nUWB se pokušava automatski za odabranu osobu. Držite telefone uspravno u portretu. Ručni pokušaj dostupan je dolje.")
            .setPositiveButton("Traži UWB",(d,w)->{startUwb(peer);renderDashboard();}).setNeutralButton("Pošalji ping",(d,w)->{sendPing();renderDashboard();}).setNegativeButton("Zatvori",null).show();
    }
    void sendPing(){
        Peer peer=engine.selectedSession==null?null:engine.peers.get(engine.selectedSession);
        if(demo||!engine.searching||peer==null||peer.profile==null||!peerFresh(peer)){setStatus("Pokreni radar i odaberi aktivnog člana s razmijenjenim profilom.");return;}
        engine.pingTarget=peer.id;engine.pingId=new SecureRandom().nextInt(Integer.MAX_VALUE)+1L;engine.pingAt=SystemClock.elapsedRealtime();setStatus("Ping pripremljen za "+peer.alias+". Dostava ovisi o aktivnoj vezi; nema potvrde primitka.");
    }
    String mailSummary(){
        java.util.List<hr.phoenixart.crowdradar.core.ShortMessages.Entry> history=engine.messages.history(SystemClock.elapsedRealtime());
        hr.phoenixart.crowdradar.core.ShortMessages.Entry pending=engine.messages.pending(SystemClock.elapsedRealtime());
        String last=pending!=null?"ČEKA POTVRDU • "+pending.name:history.isEmpty()?"Još nema poruka.":history.get(history.size()-1).state+" • "+history.get(history.size()-1).name;
        return (engine.unreadMessages>0?"NOVIH PORUKA: "+engine.unreadMessages+"\n":"")+last+(engine.mailStatus.isEmpty()?"":"\n"+engine.mailStatus);
    }
    void composeMessage(){
        if(demo||!engine.searching){setStatus("Za poruke pokreni stvarni radar.");return;}
        if(engine.messages.pending(SystemClock.elapsedRealtime())!=null){engine.mailStatus="Jedna poruka već čeka potvrdu (najviše 60 s).";return;}
        Peer peer=engine.selectedSession==null?null:engine.peers.get(engine.selectedSession);
        if(peer==null){
            java.util.List<Peer> candidates=new ArrayList<>();for(Peer p:engine.peers.values())if(canMessage(p)&&peerFresh(p))candidates.add(p);
            if(candidates.isEmpty()){engine.mailStatus="Nema člana s aktivnom podrškom za poruke. Na oba telefona treba verzija 0.5 ili novija.";return;}
            String[] names=new String[candidates.size()];for(int i=0;i<names.length;i++)names[i]=candidates.get(i).alias;
            new AlertDialog.Builder(this).setTitle("Poruka za člana").setItems(names,(d,i)->{engine.selectedSession=candidates.get(i).id;engine.focus(engine.selectedSession);composeMessage();}).setNegativeButton("Odustani",null).show();return;
        }
        if(!canMessage(peer)||!peerFresh(peer)){engine.mailStatus="Odabrani član nema aktivnu podatkovnu vezu za poruke. Oba telefona trebaju 0.5 ili noviju verziju.";return;}
        EditText input=new EditText(this);input.setSingleLine();input.setHint("Kratka poruka — do 48 znakova");input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(48)});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Poruka: "+peer.alias).setMessage("Šifrirani BLE / Nearby / Wi-Fi Aware • bez interneta. Potvrda znači primitak u aplikaciji, ne da je osoba pročitala poruku.")
            .setView(input).setPositiveButton("Pošalji",null).setNegativeButton("Odustani",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!engine.searching||!peerFresh(peer)||!canMessage(peer)){input.setError("Član više nema aktivnu vezu.");return;}
            try{engine.messages.send(peer.id,peer.alias,input.getText().toString(),SystemClock.elapsedRealtime());engine.profiles.focus=peer.id;engine.mailStatus="";dialog.dismiss();renderDashboard();}
            catch(IllegalArgumentException|IllegalStateException e){input.setError(e.getMessage());}
        }));dialog.show();
    }
    void showMessages(){
        engine.unreadMessages=0;java.util.List<hr.phoenixart.crowdradar.core.ShortMessages.Entry> entries=engine.messages.history(SystemClock.elapsedRealtime());StringBuilder text=new StringBuilder();
        for(hr.phoenixart.crowdradar.core.ShortMessages.Entry e:entries)text.append(e.outgoing?"ZA ":"OD ").append(e.name).append(" • ").append(e.state).append("\n").append(e.text).append("\n\n");
        TextView view=label(text.length()==0?"Još nema poruka.":text.toString(),14,Color.WHITE);view.setPadding(dp(18),dp(12),dp(18),dp(20));view.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(view);
        new AlertDialog.Builder(this).setTitle("Poruke • samo u memoriji").setView(scroll).setPositiveButton("Zatvori",null).show();renderDashboard();
    }
    String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException e){return "dev";}}
    void updateGroup() { groupLabel.setText(engine.groupKey == null ? "NEMA GRUPE • OFFLINE" : engine.groupName+"  ✎  •  "+engine.own.name); }
    void saveGroupInfo(){getSharedPreferences("groupInfo",0).edit().putString("name",engine.groupName).putLong("revision",engine.groupRevision).apply();}
    void refreshHardware(){hardware.setText("TVOJ UREĐAJ: "+engine.own.model+"\nPodržano: "+Capabilities.supported(engine.own.caps)+"\n"+engine.gps.state+"\n"+engine.uwb.status+"\n"+engine.nearby.status+"\n"+engine.aware.status+"\n"+motionSummary()+(motion.fresh(SystemClock.elapsedRealtime())?String.format(Locale.ROOT," • procjena RMS %.2f m/s²",motion.rms()):""));}
    void showHelp(){
        try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(getAssets().open("help.txt"),StandardCharsets.UTF_8))){
            StringBuilder text=new StringBuilder();String line;while((line=reader.readLine())!=null)text.append(line).append('\n');
            String full=text.toString();
            String[] titles={"Brzi početak","Bluetooth / BLE","GPS / GNSS","UWB traženje","Kompas","QR, kod i Wi-Fi","Poruke i PING","Radar i preciznost","AR smjer / kamera","Privatnost i ograničenja","Promjene verzije","Cijela pomoć"};
            String[] markers={"BRZI POČETAK","BLE — BLIZINA","GPS / GNSS —","UWB — IZRAVNO","KOMPAS I ORIJENTACIJA","QR, BROJČANI KOD","PORUKE I PING","KAKO ČITATI RADAR","AR — EKSPERIMENTALNI","PRIVATNOST I OGRANIČENJA","PROMJENE —"};
            new AlertDialog.Builder(this).setTitle("Pomoć • "+appVersion()).setItems(titles,(d,which)->{
                String content=full;
                if(which<markers.length){int start=full.indexOf(markers[which]);int end=which+1<markers.length?full.indexOf(markers[which+1]):full.length();
                    if(start>=0&&end>start)content=full.substring(start,end).trim();}
                TextView view=label(content,14,Color.WHITE);view.setPadding(dp(18),dp(12),dp(18),dp(20));view.setTextIsSelectable(true);
                ScrollView scroll=new ScrollView(this);scroll.addView(view);
                new AlertDialog.Builder(this).setTitle(titles[which]).setView(scroll).setPositiveButton("Zatvori",null).setNeutralButton("Teme",(x,y)->showHelp()).show();
            }).setNegativeButton("Zatvori",null).show();
        }catch(java.io.IOException e){setStatus("Pomoć nije dostupna u instalaciji.");}
    }
    boolean peerFresh(Peer peer){return engine.fresh(peer);}
    boolean canMessage(Peer peer){return engine.canMessage(peer);}
    void toggleNearby(){
        if(engine.nearby.active){engine.nearWanted=false;engine.nearby.stop();setStatus("Dodatna Nearby veza isključena. BLE ostaje aktivan.");return;}
        if(!engine.searching){setStatus("Prvo pokreni radar, zatim uključi BT + Wi-Fi vezu.");return;}
        if(!pairPermissions()&&Build.VERSION.SDK_INT>=33){requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES},104);return;}
        engine.nearWanted=true;engine.nearby.start(engine.groupKey,engine.session,engine.selectedSession);setStatus(engine.nearby.status);
    }
    void openAr(){
        if(demo||!engine.searching){setStatus("AR koristi stvarne članove. Pokreni radar i odaberi osobu.");return;}
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},105);return;}
        ar.show();
    }
    void toggleGps(){
        if(engine.gps.sharing){engine.gps.stop();gpsButton.setText("UKLJUČI GPS DIJELJENJE");setStatus("GPS dijeljenje isključeno.");return;}
        if(!engine.searching){setStatus("Prvo pokreni radar, zatim uključi GPS dijeljenje.");return;}
        engine.gps.start();gpsButton.setText("ISKLJUČI GPS DIJELJENJE");setStatus(engine.gps.state);
    }
    void startUwb(Peer peer){
        if(!engine.searching||peer.profile==null){setStatus("Pokreni radar i pričekaj razmjenu profila.");return;}
        engine.focus(peer.id);
        if((engine.own.caps&peer.profile.caps&Capabilities.UWB)==0){setStatus("Oba telefona moraju imati UWB. GPS/BLE ostaju dostupni.");return;}
        if(!engine.uwb.permitted()){requestPermissions(new String[]{Manifest.permission.UWB_RANGING},103);return;}
        engine.startUwb(peer);setStatus(engine.uwb.status);
    }
    void profileMenu(){
        EditText input=new EditText(this);input.setText(engine.own.name);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        new AlertDialog.Builder(this).setTitle("Moje ime / uređaj").setMessage(engine.own.model+"\nPodržano: "+Capabilities.supported(engine.own.caps)+"\n\nOvo je podrška hardvera, ne potvrda aktivnog preciznog mjerenja.")
            .setView(input).setPositiveButton("Spremi",(d,w)->{String name=input.getText().toString().trim();if(name.isEmpty())return;
                getSharedPreferences("identity",0).edit().putString("name",name).apply();engine.own=DeviceProfile.local(this);engine.profiles.engine.own=engine.own;updateGroup();refreshHardware();})
            .setNegativeButton("Odustani",null).show();
    }
    void editGroupName(){
        EditText input=new EditText(this);input.setText(engine.groupName);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        new AlertDialog.Builder(this).setTitle("Naziv grupe / događaja").setView(input).setPositiveButton("Spremi",(d,w)->{
            String name=input.getText().toString().trim();if(name.isEmpty())return;engine.groupName=name;engine.groupRevision=Math.max(System.currentTimeMillis(),engine.groupRevision+1);saveGroupInfo();updateGroup();
        }).setNegativeButton("Odustani",null).show();
    }
    void stop(){if(ar!=null)ar.close();engine.stop();stopService(new Intent(this,RadarService.class));if(gpsButton!=null)gpsButton.setText("UKLJUČI GPS DIJELJENJE");}
    boolean allowed() {
        for (String p : REQUIRED) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }
    void toggleSearch() {
        if (engine.searching||engine.starting) { stop(); setStatus("Radar zaustavljen."); return; }
        if (engine.groupKey == null) { firstRun(); return; }
        boolean askUwb=getPackageManager().hasSystemFeature("android.hardware.uwb")&&!engine.uwb.permitted()&&!getSharedPreferences("identity",0).getBoolean("uwbAsked",false);
        if (!allowed()||askUwb||(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getSharedPreferences("identity",0).getBoolean("notifiedAsked",false))||(Build.VERSION.SDK_INT>=33&&!getSharedPreferences("identity",0).getBoolean("nearAsked",false))) {
            ArrayList<String> permissions=new ArrayList<>(Arrays.asList(REQUIRED));
            if(Build.VERSION.SDK_INT>=33&&!getSharedPreferences("identity",0).getBoolean("nearAsked",false)){permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);getSharedPreferences("identity",0).edit().putBoolean("nearAsked",true).apply();}
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getSharedPreferences("identity",0).getBoolean("notifiedAsked",false)){permissions.add(Manifest.permission.POST_NOTIFICATIONS);getSharedPreferences("identity",0).edit().putBoolean("notifiedAsked",true).apply();}
            if(askUwb){permissions.add(Manifest.permission.UWB_RANGING);getSharedPreferences("identity",0).edit().putBoolean("uwbAsked",true).apply();}
            requestPermissions(permissions.toArray(new String[0]),101);return;
        }
        pairing.stop();demo=false;engine.starting=true;
        try{startForegroundService(new Intent(this,RadarService.class));setStatus("Pokrećem automatsko povezivanje i rad uz zaključan ekran…");}
        catch(RuntimeException e){engine.starting=false;setStatus("Servis nije pokrenut. Ponovno otvori aplikaciju i provjeri dozvole.");}

    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==104){if(pairPermissions()&&engine.searching){engine.nearWanted=true;engine.nearby.start(engine.groupKey,engine.session,engine.selectedSession);}else setStatus("Nearby dozvola nije odobrena; BLE ostaje aktivan.");return;}
        if(requestCode==105){if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)openAr();else setStatus("AR treba dopuštenje kamere.");return;}
        if(requestCode==103){Peer peer=engine.selectedSession==null?null:engine.peers.get(engine.selectedSession);if(engine.uwb.permitted()&&peer!=null)startUwb(peer);else setStatus("UWB dozvola nije odobrena. Koristi GPS/BLE.");return;}
        if(requestCode==102){if(pairPermissions())startPair(pendingPairAction==1);else setStatus("Dozvole za brojčano uparivanje nisu odobrene.");return;}
        if (requestCode == 101 && allowed()) toggleSearch();
        else setStatus("Za BLE radar odobri obližnje uređaje i preciznu lokaciju. GPS dijeljenje uključuješ zasebnim gumbom.");
    }
    void toggleDemo() {
        stop(); demo=!demo; engine.peers.clear(); selected=null;engine.selectedSession=null;
        setStatus(demo?"DEMO • simulirane osobe i smjerovi; senzori ne lociraju te osobe.":"Demo isključen. Pokreni BLE radar.");
        demoButton.setText(demo?"ISKLJUČI DEMO":"DEMO IZGLEDA"); refreshMembers(); renderDashboard();
    }
    void groupMenu(){new AlertDialog.Builder(this).setTitle("Grupa").setItems(new String[]{"Kreiraj grupu","QR pozivnica","Skeniraj QR","Dodaj člana brojem","Pridruži se brojem"},(d,i)->dashboardAction(new String[]{"create","invite","scan","host","join"}[i],"")).show();}
    boolean pairPermissions(){if(!allowed())return false;return Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)==PackageManager.PERMISSION_GRANTED;}
    void startPair(boolean host){
        if(host&&engine.groupKey==null){createGroup(false,()->startPair(true));return;}
        if(!pairPermissions()){
            pendingPairAction=host?1:2;ArrayList<String> permissions=new ArrayList<>(Arrays.asList(REQUIRED));
            if(Build.VERSION.SDK_INT>=33)permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);requestPermissions(permissions.toArray(new String[0]),102);return;
        }
        stop();demo=false;engine.peers.clear();refreshMembers();pairing.start(host,engine.groupName+" / "+engine.own.name);
    }
    boolean installGroup(byte[] key,String name,long revision) {
        try {groupStore.save(key);stop();if(engine.groupKey!=null)Arrays.fill(engine.groupKey,(byte)0);engine.groupKey=key;engine.groupName=name;engine.groupRevision=revision;saveGroupInfo();engine.messages.clear();engine.mailStatus="";engine.unreadMessages=0;demo=false;engine.peers.clear();updateGroup();setStatus("Grupa spremljena. Pokreni radar.");refreshMembers();return true;}
        catch(Exception e){setStatus("Ključ nije spremljen ("+e.getClass().getSimpleName()+"). Grupa nije promijenjena.");return false;}
    }
    String invitationToken(){
        if(engine.groupKey==null)return null;
        try {JSONObject json=new JSONObject().put("v",2).put("key",Base64.encodeToString(engine.groupKey,Base64.NO_WRAP)).put("name",engine.groupName).put("revision",engine.groupRevision);
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
            body.addView(label(engine.groupName+"\nPozivnica daje pristup grupi. Pokaži je samo kolegama.",14,Color.WHITE));body.addView(image,new LinearLayout.LayoutParams(-1,dp(260)));
            TextView text=label(token,10,MUTED);text.setTextIsSelectable(true);body.addView(text);ScrollView scroll=new ScrollView(this);scroll.addView(body);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("QR pozivnica").setView(scroll).setPositiveButton("Zatvori",null).create();dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }catch(Exception e){setStatus("QR kod nije izrađen.");}
    }
    static final class Estimate {
        double meters=Double.NaN;Double angle;String distanceSource="BLE",directionSource="bez smjera",note="";
    }
    Estimate estimate(Peer peer){
        Estimate e=new Estimate();if(engine.nearby.live(peer.id))e.distanceSource="Nearby";if(engine.aware.live(peer.id))e.distanceSource="Wi-Fi Aware";long now=SystemClock.elapsedRealtime();
        boolean uwbLive=engine.selectedSession!=null&&engine.selectedSession==peer.id&&engine.uwb.targetSession==peer.id&&engine.uwb.distance!=null&&Navigation.fresh(engine.uwb.measuredAt,now,3000);
        if(uwbLive){e.meters=engine.uwb.distance;e.distanceSource="UWB";e.note="Pogreška UWB-a nije prijavljena";
            if(engine.uwb.azimuth!=null&&Navigation.antennaPosture(backHorizontal,displayRotation)&&Navigation.fresh(postureAt,now,2000)){e.angle=RadarMath.normalize(engine.uwb.azimuth);e.directionSource="UWB kut antene";}
            else if(engine.uwb.azimuth!=null)e.note="Za UWB kut drži telefon uspravno u portretu";
            else e.note+=" • "+(engine.uwb.angleSupported?"UWB kut trenutačno nije izmjeren":"Lokalni UWB servis ne prijavljuje podršku za kut");
        }
        boolean rttLive=!uwbLive&&engine.aware.measuredPeer==peer.id&&engine.aware.distance!=null&&Navigation.fresh(engine.aware.measuredAt,now,8000);
        if(rttLive){e.meters=engine.aware.distance;e.distanceSource="Wi-Fi RTT";e.note="RTT bez izmjerenog smjera"+(engine.aware.spread==null?"":String.format(Locale.ROOT," • standardna devijacija %.2f m (nije granica pogreške)",engine.aware.spread));}
        boolean metricLive=uwbLive||rttLive;
        Location mine=engine.gps.fix;long theirAge=Navigation.receivedFixAge(peer.fixAge,0,Math.max(0,now-peer.geoAt));
        if(engine.gps.sharing&&mine!=null&&peer.hasGeo&&Navigation.validFix(mine.getLatitude(),mine.getLongitude(),mine.getAccuracy(),engine.gps.age())&&Navigation.validFix(peer.lat,peer.lon,peer.error,theirAge)){
            double d=RadarMath.distance(mine.getLatitude(),mine.getLongitude(),peer.lat,peer.lon),errors=mine.getAccuracy()+peer.error;
            if(!metricLive){e.meters=d;e.distanceSource="GPS ~";e.note=String.format(Locale.ROOT,"Procjene pogreške telefona: %.0f + %.0f m",mine.getAccuracy(),peer.error);}
            boolean consistent=!metricLive||Navigation.consistent(d,e.meters,errors);
            float declination=engine.gps.declination();
            if(e.angle==null&&consistent&&heading!=null&&Float.isFinite(declination)&&Navigation.fresh(headingAt,now,2000)&&RadarMath.gpsDirectionUsable(d,mine.getAccuracy(),peer.error,engine.gps.age(),theirAge)){
                e.angle=Navigation.relative(RadarMath.bearing(mine.getLatitude(),mine.getLongitude(),peer.lat,peer.lon),heading+declination);e.directionSource="GPS + kompas";
            }else if(!metricLive&&e.angle==null)e.note+=" • smjer nije dovoljno pouzdan";
            else if(!consistent&&e.angle==null)e.note="GPS i izravno mjerenje se ne slažu; GPS smjer skriven";
        }
        return e;
    }
    String measurementText(Peer peer){Estimate e=estimate(peer);String distance=Double.isFinite(e.meters)?String.format(Locale.ROOT,"%s %.1f m",e.distanceSource,e.meters):(engine.nearby.live(peer.id)?"Nearby veza • bez metara":"BLE blizina • bez metara");
        return distance+" • "+e.directionSource+(e.note.isEmpty()?"":"\n"+e.note);}
    void refreshMembers(){refreshHardware();if(findPending&&engine.searching&&!engine.peers.isEmpty()){findPending=false;choosePeer();}renderDashboard();}
    final Runnable tick=new Runnable(){public void run(){if(!visible)return;refreshMembers();handler.postDelayed(this,1000);}};
    @Override protected void onResume(){super.onResume();visible=true;engine.uiVisible=true;if(dashboard!=null)dashboard.view.onResume();
        Sensor sensor=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if(sensor!=null)sensors.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI);
        motion.reset();gravityReady=false;motionSensorType=0;
        Sensor acceleration=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if(acceleration==null&&sensors!=null)acceleration=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if(acceleration!=null&&sensors.registerListener(this,acceleration,SensorManager.SENSOR_DELAY_UI))motionSensorType=acceleration.getType();
        handler.post(tick);}
    @Override protected void onPause(){visible=false;heading=null;headingAt=postureAt=0;handler.removeCallbacks(tick);if(dashboard!=null)dashboard.view.onPause();motion.reset();gravityReady=false;if(sensors!=null)sensors.unregisterListener(this);super.onPause();}
    @Override protected void onStop(){if(ar!=null)ar.close();engine.uiVisible=false;pairing.stop();super.onStop();}
    @Override protected void onDestroy(){if(ar!=null)ar.close();pairing.stop();handler.removeCallbacksAndMessages(null);if(dashboard!=null)dashboard.destroy();super.onDestroy();}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){if(sensor.getType()==Sensor.TYPE_ROTATION_VECTOR&&accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE){heading=null;headingAt=0;}}
    String motionSummary(){
        long now=SystemClock.elapsedRealtime();
        if(motionSensorType==0)return "SENZOR KRETANJA NEDOSTUPAN";
        if(!motion.fresh(now))return "KRETANJE — ČEKAM SENZOR";
        return motion.aboveThreshold(now)?"POVIŠENO UBRZANJE":"NISKO UBRZANJE";
    }
    @Override public void onSensorChanged(SensorEvent event){
        int type=event.sensor.getType();
        if(type==motionSensorType){
            long now=SystemClock.elapsedRealtime();float x=event.values[0],y=event.values[1],z=event.values[2];
            if(type==Sensor.TYPE_ACCELEROMETER){
                if(!gravityReady){gravity[0]=x;gravity[1]=y;gravity[2]=z;gravityReady=true;accelStarted=now;}
                float alpha=.9f;gravity[0]=alpha*gravity[0]+(1-alpha)*x;gravity[1]=alpha*gravity[1]+(1-alpha)*y;gravity[2]=alpha*gravity[2]+(1-alpha)*z;
                x-=gravity[0];y-=gravity[1];z-=gravity[2];if(now-accelStarted<1000)return;
            }
            motion.sample(x,y,z,now);return;
        }
        if(type!=Sensor.TYPE_ROTATION_VECTOR)return;
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
