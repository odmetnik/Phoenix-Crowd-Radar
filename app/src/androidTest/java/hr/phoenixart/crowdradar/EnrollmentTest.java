package hr.phoenixart.crowdradar;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.junit.Assert.*;
import android.widget.EditText;

/** Emulator smoke tests exercise the actual Keystore and native enrollment dialogs. */
@RunWith(AndroidJUnit4.class)
public class EnrollmentTest {
    void captureFocusFailure(){
        try{
            android.app.UiAutomation automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation();
            android.graphics.Bitmap bitmap=automation.takeScreenshot();
            android.content.Context context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
            if(bitmap!=null){android.content.ContentValues values=new android.content.ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"focus-failure.png");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Phoenix");android.net.Uri uri=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                if(uri!=null)try(java.io.OutputStream out=context.getContentResolver().openOutputStream(uri)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}}
            try(android.os.ParcelFileDescriptor fd=automation.executeShellCommand("dumpsys window windows");java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(fd.getFileDescriptor())))){
                String line;while((line=reader.readLine())!=null)if(line.contains("mCurrentFocus")||line.contains("mFocusedApp"))System.out.println("UI focus diagnostic: "+line);
            }
        }catch(Exception ignored){}
    }
    void awaitFocus(ActivityScenario<MainActivity> scenario){
        java.util.concurrent.atomic.AtomicBoolean focused=new java.util.concurrent.atomic.AtomicBoolean();
        long deadline=android.os.SystemClock.elapsedRealtime()+15000;
        do{scenario.onActivity(a->focused.set(a.hasWindowFocus()));if(!focused.get())android.os.SystemClock.sleep(100);}while(!focused.get()&&android.os.SystemClock.elapsedRealtime()<deadline);
        if(!focused.get())captureFocusFailure();
        assertTrue("Activity has foreground window focus before dialog input",focused.get());
    }
    @Test public void createGroupQrAndImportRoundTrip() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitFocus(scenario);
            scenario.onActivity(a->{a.groupStore.clear();a.engine.groupKey=null;a.createGroup(false,null);});
            onView(isAssignableFrom(EditText.class)).inRoot(isDialog()).perform(replaceText("QA događaj"),closeSoftKeyboard());
            onView(withText("Kreiraj")).inRoot(isDialog()).perform(click());
            scenario.onActivity(a->{
                assertNotNull(a.engine.groupKey);assertEquals("QA događaj",a.engine.groupName);
                try{assertArrayEquals(a.engine.groupKey,new GroupStore(a).load());}catch(Exception e){throw new AssertionError(e);}
                String token=a.invitationToken();assertTrue(token.startsWith("phoenix2:"));
                a.engine.groupKey=null;assertTrue(a.receiveInvite(token));assertEquals("QA događaj",a.engine.groupName);
                assertFalse(a.receiveInvite("phoenix2:broken"));a.invite();
            });
            onView(withText("QR pozivnica")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Zatvori")).inRoot(isDialog()).perform(click());
        }
    }
    @Test public void captureDemoDashboard() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
            long deadline=android.os.SystemClock.elapsedRealtime()+15000;
            do{scenario.onActivity(a->ready.set(a.dashboard.ready&&a.hasWindowFocus()));if(!ready.get())android.os.SystemClock.sleep(100);}while(!ready.get()&&android.os.SystemClock.elapsedRealtime()<deadline);
            if(!ready.get())captureFocusFailure();
            assertTrue("Packaged WebView page loaded",ready.get());
            scenario.onActivity(a->{a.engine.groupName="Interliber";a.dashboard.view.evaluateJavascript("document.getElementById('demo').click()",null);});
            java.util.concurrent.atomic.AtomicBoolean demoActive=new java.util.concurrent.atomic.AtomicBoolean();
            deadline=android.os.SystemClock.elapsedRealtime()+5000;
            do{scenario.onActivity(a->demoActive.set(a.demo));if(!demoActive.get())android.os.SystemClock.sleep(100);}while(!demoActive.get()&&android.os.SystemClock.elapsedRealtime()<deadline);
            assertTrue("HTML button called native Android bridge",demoActive.get());
            java.util.concurrent.CountDownLatch rendered=new java.util.concurrent.CountDownLatch(1);
            scenario.onActivity(a->a.dashboard.view.evaluateJavascript("document.getElementById('badge').textContent",v->{assertTrue(v.contains("DEMO"));rendered.countDown();}));
            assertTrue(rendered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.CountDownLatch painted=new java.util.concurrent.CountDownLatch(1);
            scenario.onActivity(a->a.dashboard.view.postVisualStateCallback(1,new android.webkit.WebView.VisualStateCallback(){
                @Override public void onComplete(long requestId){a.dashboard.view.postOnAnimation(()->a.dashboard.view.postOnAnimation(painted::countDown));}
            }));
            assertTrue("WebView committed demo frame",painted.await(10,java.util.concurrent.TimeUnit.SECONDS));
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(500,5000);
            android.graphics.Bitmap bitmap=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
            android.content.Context context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
            android.content.ContentValues values=new android.content.ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"dashboard.png");
            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");
            values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Phoenix");
            android.net.Uri uri=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);
            try(java.io.OutputStream out=context.getContentResolver().openOutputStream(uri)){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}
        }
    }
    @Test public void cameraHudOpensAndReleasesWithoutStartingRadio()throws Exception{
        var instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        instrumentation.getUiAutomation().grantRuntimePermission(instrumentation.getTargetContext().getPackageName(),android.Manifest.permission.CAMERA);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitFocus(scenario);
            scenario.onActivity(a->{a.stop();a.engine.peers.clear();a.engine.searching=true;MainActivity.Peer peer=new MainActivity.Peer(321,-70);peer.alias="QA — smjer nije izmjeren";peer.seen=android.os.SystemClock.elapsedRealtime();a.engine.peers.put(321,peer);a.openAr();a.ar.close();a.openAr();assertTrue(a.ar.open);});
            java.util.concurrent.atomic.AtomicBoolean bound=new java.util.concurrent.atomic.AtomicBoolean();long deadline=android.os.SystemClock.elapsedRealtime()+20000;
            do{scenario.onActivity(a->bound.set(a.ar.preview!=null&&a.ar.preview.getResolutionInfo()!=null&&a.ar.cameraStatus.startsWith("Kamera uživo")));if(!bound.get())android.os.SystemClock.sleep(100);}while(!bound.get()&&android.os.SystemClock.elapsedRealtime()<deadline);
            scenario.onActivity(a->System.out.println("Camera HUD: open="+a.ar.open+" status="+a.ar.cameraStatus+" preview="+(a.ar.preview!=null)));
            if(!bound.get())captureFocusFailure();
            assertTrue("CameraX bound the back-camera preview",bound.get());
            scenario.onActivity(a->{long now=android.os.SystemClock.elapsedRealtime();MainActivity.Peer p=a.engine.peers.get(321);p.alias="QA UWB — 3,2 m";p.seen=now;a.engine.selectedSession=321;
                a.engine.uwb.targetSession=321;a.engine.uwb.distance=3.2;a.engine.uwb.azimuth=25.;a.engine.uwb.measuredAt=now;a.backHorizontal=.99;a.displayRotation=0;a.postureAt=now;
                MainActivity.Peer other=new MainActivity.Peer(322,-60);other.alias="QA — bez metara";other.seen=now;a.engine.peers.put(322,other);
                assertEquals(3.2,a.estimate(p).meters,0);assertNotNull(a.estimate(p).angle);
                android.graphics.Bitmap grid=android.graphics.Bitmap.createBitmap(a.ar.hud.getWidth(),a.ar.hud.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                a.ar.hud.draw(new android.graphics.Canvas(grid));
                android.content.ContentValues values=new android.content.ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"ar-grid.png");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Phoenix");
                android.net.Uri uri=a.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);
                try(java.io.OutputStream out=a.getContentResolver().openOutputStream(uri)){assertTrue(grid.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}catch(Exception e){throw new AssertionError(e);}a.ar.hud.invalidate();
            });
            java.util.concurrent.CountDownLatch frame=new java.util.concurrent.CountDownLatch(1);scenario.onActivity(a->a.ar.hud.postOnAnimation(()->a.ar.hud.postOnAnimation(frame::countDown)));assertTrue(frame.await(5,java.util.concurrent.TimeUnit.SECONDS));
            android.graphics.Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(bitmap);
            android.content.Context context=instrumentation.getTargetContext();android.content.ContentValues values=new android.content.ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"ar-hud.png");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Phoenix");
            android.net.Uri uri=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);
            try(java.io.OutputStream out=context.getContentResolver().openOutputStream(uri)){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}
            scenario.onActivity(a->{a.ar.close();assertFalse(a.ar.open);assertNull(a.ar.preview);assertTrue(a.engine.searching);a.stop();});
        }
    }
    @Test public void nearbyAndArGuardsDoNotStartWithoutRadar(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitFocus(scenario);
            scenario.onActivity(a->{a.stop();a.demo=false;a.toggleNearby();assertFalse(a.engine.nearby.active);assertTrue(a.status.getText().toString().contains("Prvo pokreni"));a.openAr();assertFalse(a.ar.open);assertTrue(a.status.getText().toString().contains("Pokreni radar"));});
        }
    }
    @Test public void nearbyPeerRemainsFreshWithoutBleAdvertisement(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{a.stop();MainActivity.Peer peer=new MainActivity.Peer(123,-127);a.engine.peers.put(123,peer);
                assertFalse(a.peerFresh(peer));assertFalse(a.canMessage(peer));
                a.engine.searching=true;a.engine.nearby.active=true;a.engine.nearby.authenticated=true;a.engine.nearby.remote=123;a.engine.nearby.lastReceived=android.os.SystemClock.elapsedRealtime();
                assertTrue(a.peerFresh(peer));assertTrue(a.canMessage(peer));
                a.engine.nearby.lastReceived=android.os.SystemClock.elapsedRealtime()-7000;assertFalse(a.peerFresh(peer));assertFalse(a.canMessage(peer));
                a.stop();
            });
        }
    }
    @Test public void foregroundSessionSurvivesBackgroundAndActivityRecreation()throws Exception{
        var instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();var context=instrumentation.getTargetContext();
        for(String permission:new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.BLUETOOTH_SCAN,android.Manifest.permission.BLUETOOTH_ADVERTISE,android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.NEARBY_WIFI_DEVICES,android.Manifest.permission.POST_NOTIFICATIONS})instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(),permission);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitFocus(scenario);
            scenario.onActivity(a->{a.stop();try{a.groupStore.save(new byte[32]);}catch(Exception e){throw new AssertionError(e);}a.engine.loadGroup();a.getSharedPreferences("identity",0).edit().putBoolean("nearAsked",true).putBoolean("notifiedAsked",true).putBoolean("uwbAsked",true).apply();a.toggleSearch();});
            java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean();long deadline=android.os.SystemClock.elapsedRealtime()+10000;
            do{scenario.onActivity(a->active.set(a.engine.searching&&a.engine.service!=null));if(!active.get())android.os.SystemClock.sleep(100);}while(!active.get()&&android.os.SystemClock.elapsedRealtime()<deadline);assertTrue("User-started foreground service is active",active.get());
            RadarEngine engine=((RadarApp)context.getApplicationContext()).engine;int session=engine.session;
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);instrumentation.runOnMainSync(()->{assertTrue(engine.searching);assertNotNull(engine.service);assertEquals(session,engine.session);});
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);scenario.recreate();scenario.onActivity(a->{assertSame(engine,a.engine);assertTrue(a.engine.searching);assertEquals(session,a.engine.session);a.stop();assertFalse(a.engine.searching);});
        }finally{instrumentation.runOnMainSync(()->{((RadarApp)context.getApplicationContext()).engine.stop();context.stopService(new android.content.Intent(context,RadarService.class));});}
    }
    @Test public void rttMeasurementHasNoInventedBearingAndExpires(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{a.stop();a.engine.searching=true;MainActivity.Peer p=new MainActivity.Peer(42,-75);p.seen=android.os.SystemClock.elapsedRealtime();a.engine.peers.put(42,p);
                a.engine.aware.measuredPeer=42;a.engine.aware.distance=4.25;a.engine.aware.spread=.1;a.engine.aware.measuredAt=android.os.SystemClock.elapsedRealtime();
                MainActivity.Estimate value=a.estimate(p);assertEquals("Wi-Fi RTT",value.distanceSource);assertEquals(4.25,value.meters,0);assertNull(value.angle);
                a.engine.aware.measuredAt-=9000;assertTrue(Double.isNaN(a.estimate(p).meters));a.stop();
            });
        }
    }
    static org.json.JSONObject relayPacket(RadarEngine engine,char kind,int origin)throws Exception{
        for(int i=0;i<100;i++){hr.phoenixart.crowdradar.core.RelayRouter.Packet p=engine.relay.outgoing(android.os.SystemClock.elapsedRealtime());if(p!=null&&p.kind==kind&&p.origin==origin)return RelayWire.json(p);}throw new AssertionError("No relay packet");
    }
    static org.json.JSONObject matchingRelay(RadarEngine engine,char kind,int origin,String id)throws Exception{
        for(int i=0;i<100;i++){var p=engine.relay.outgoing(android.os.SystemClock.elapsedRealtime());if(p!=null&&p.kind==kind&&p.origin==origin&&p.id.equals(id))return RelayWire.json(p);}throw new AssertionError("No matching relay packet");
    }
    static void transferRelay(RadarEngine a,RadarEngine b,RadarEngine c)throws Exception{
        org.json.JSONObject request=relayPacket(a,'q',11);String id=request.getString("i");b.receiveRelay(11,request);c.receiveRelay(22,matchingRelay(b,'q',11,id));b.receiveRelay(33,matchingRelay(c,'r',33,id));a.receiveRelay(22,matchingRelay(b,'r',33,id));
    }
    @Test public void encryptedGattMeshReadInvokesRelayHandler(){
        android.content.Context context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            ProfileLink link=new ProfileLink(context,(id,p,n,r)->{});link.active=true;link.secret=new byte[32];ProfileLink.Client client=link.new Client(22,link.generation);link.clients.put(22,client);client.nonce=new byte[16];client.requested=android.os.SystemClock.elapsedRealtime();
            java.util.concurrent.atomic.AtomicInteger delivered=new java.util.concurrent.atomic.AtomicInteger();link.meshListener=(id,data,latency)->{assertEquals(22,id);assertEquals("a",data.optString("k"));delivered.incrementAndGet();};
            try{org.json.JSONObject frame=RelayWire.json(new hr.phoenixart.crowdradar.core.RelayRouter.Packet('a',"1234567890123456",22,0,java.util.List.of(22),"{}"));frame.put("kind","x").put("session",22).put("n",android.util.Base64.encodeToString(client.nonce,android.util.Base64.NO_WRAP));
                client.read(new android.bluetooth.BluetoothGattCharacteristic(ProfileLink.MESH,android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ,android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ),hr.phoenixart.crowdradar.core.ProfileCrypto.seal(link.secret,frame.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),android.bluetooth.BluetoothGatt.GATT_SUCCESS);
                assertEquals(1,delivered.get());
            }catch(Exception e){throw new AssertionError(e);}finally{link.stop();}
        });
    }
    @Test public void threeNodeRelayUsesEndpointCoordinatesAndEndpointAcknowledgement(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{RadarEngine original=activity.engine;activity.stop();RadarEngine a=new RadarEngine(activity),b=new RadarEngine(activity),c=new RadarEngine(activity);
                try{a.searching=b.searching=c.searching=true;a.session=11;b.session=22;c.session=33;a.relay=new hr.phoenixart.crowdradar.core.RelayRouter(11);b.relay=new hr.phoenixart.crowdradar.core.RelayRouter(22);c.relay=new hr.phoenixart.crowdradar.core.RelayRouter(33);
                    MainActivity.Peer bridge=new MainActivity.Peer(22,-50);bridge.alias="Marko";bridge.seen=android.os.SystemClock.elapsedRealtime();a.peers.put(22,bridge);
                    c.relay.advertise(new org.json.JSONObject().put("u",java.util.UUID.randomUUID().toString()).put("n","Ana").put("c",63).toString(),android.os.SystemClock.elapsedRealtime());
                    b.receiveRelay(33,relayPacket(c,'a',33));a.receiveRelay(22,relayPacket(b,'a',33));MainActivity.Peer target=a.peers.get(33);assertNotNull(target);assertEquals("Ana",target.alias);assertTrue(a.relayed(target));assertTrue(a.routeLabel(target).contains("Marko"));
                    android.location.Location mine=new android.location.Location("gps");mine.setLatitude(46.2);mine.setLongitude(16.1);mine.setAccuracy(3);mine.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());a.gps.fix=mine;a.gps.sharing=true;
                    android.location.Location theirs=new android.location.Location(mine);theirs.setLatitude(46.201);c.gps.fix=theirs;c.gps.sharing=true;
                    a.relay.query(33,"{}",android.os.SystemClock.elapsedRealtime());transferRelay(a,b,c);assertTrue(a.fresh(target));
                    activity.engine=a;a.selectedSession=33;a.uwb.targetSession=33;a.uwb.distance=2.;a.uwb.azimuth=0.;a.uwb.measuredAt=android.os.SystemClock.elapsedRealtime();
                    MainActivity.Estimate estimate=activity.estimate(target);assertEquals("GPS preko mreže ~",estimate.distanceSource);assertTrue(estimate.meters>100&&estimate.meters<120);
                    activity.heading=0f;activity.headingAt=android.os.SystemClock.elapsedRealtime();activity.backHorizontal=.99;activity.displayRotation=0;activity.postureAt=activity.headingAt;activity.ar.cameraStatus="QA • simulirani A → B → C";
                    ArOverlay.Hud hud=activity.ar.new Hud();hud.layout(0,0,1080,2400);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(1080,2400,android.graphics.Bitmap.Config.ARGB_8888);hud.draw(new android.graphics.Canvas(bitmap));
                    android.content.ContentValues values=new android.content.ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"relay-grid.png");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Phoenix");android.net.Uri uri=activity.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);try(java.io.OutputStream out=activity.getContentResolver().openOutputStream(uri)){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));} // endpoint coordinate separation, never the synthetic 2 m UWB segment
                    var sent=a.messages.send(33,"Ana","Kod izlaza.",android.os.SystemClock.elapsedRealtime());a.relayUrgent(33);transferRelay(a,b,c);
                    assertEquals("DOSTAVLJENO",sent.state);assertEquals(1,c.messages.history(android.os.SystemClock.elapsedRealtime()).size());assertTrue(b.messages.history(android.os.SystemClock.elapsedRealtime()).isEmpty());
                    c.gps.stop();a.relayUrgent(33);transferRelay(a,b,c);assertTrue(Double.isNaN(activity.estimate(target).meters));
                    a.startUwb(target);assertNull(a.uwb.token); // indirect reachability never starts direct ranging
                }catch(Exception e){throw new AssertionError(e);}finally{activity.engine=original;a.stop();b.stop();c.stop();}
            });
        }
    }
    @Test public void startWithoutGroupHasSpecificOnboarding(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitFocus(scenario);
            scenario.onActivity(a->{a.stop();a.groupStore.clear();a.engine.groupKey=null;a.toggleSearch();assertFalse(a.engine.searching);});
            onView(withText("Pokreni radar • odaberi grupu")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Skeniraj QR")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Pridruži se brojem")).inRoot(isDialog()).check(matches(isDisplayed()));
        }
    }
}
