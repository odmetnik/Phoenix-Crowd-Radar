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
            scenario.onActivity(a->{a.groupStore.clear();a.groupKey=null;a.createGroup(false,null);});
            onView(isAssignableFrom(EditText.class)).inRoot(isDialog()).perform(replaceText("QA događaj"),closeSoftKeyboard());
            onView(withText("Kreiraj")).inRoot(isDialog()).perform(click());
            scenario.onActivity(a->{
                assertNotNull(a.groupKey);assertEquals("QA događaj",a.groupName);
                try{assertArrayEquals(a.groupKey,new GroupStore(a).load());}catch(Exception e){throw new AssertionError(e);}
                String token=a.invitationToken();assertTrue(token.startsWith("phoenix2:"));
                a.groupKey=null;assertTrue(a.receiveInvite(token));assertEquals("QA događaj",a.groupName);
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
            scenario.onActivity(a->{a.groupName="Interliber";a.dashboard.view.evaluateJavascript("document.getElementById('demo').click()",null);});
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
            scenario.onActivity(a->{a.stop();a.searching=true;MainActivity.Peer peer=new MainActivity.Peer(321,-70);peer.alias="QA — smjer nije izmjeren";peer.seen=android.os.SystemClock.elapsedRealtime();a.peers.put(321,peer);a.openAr();assertTrue(a.ar.open);});
            java.util.concurrent.atomic.AtomicBoolean bound=new java.util.concurrent.atomic.AtomicBoolean();long deadline=android.os.SystemClock.elapsedRealtime()+20000;
            do{scenario.onActivity(a->bound.set(a.ar.preview!=null&&a.ar.preview.getResolutionInfo()!=null&&a.ar.cameraStatus.startsWith("Kamera uživo")));if(!bound.get())android.os.SystemClock.sleep(100);}while(!bound.get()&&android.os.SystemClock.elapsedRealtime()<deadline);
            assertTrue("CameraX bound the back-camera preview",bound.get());
            java.util.concurrent.CountDownLatch frame=new java.util.concurrent.CountDownLatch(1);scenario.onActivity(a->a.ar.hud.postOnAnimation(()->a.ar.hud.postOnAnimation(frame::countDown)));assertTrue(frame.await(5,java.util.concurrent.TimeUnit.SECONDS));
            android.graphics.Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(bitmap);
            android.content.Context context=instrumentation.getTargetContext();android.content.ContentValues values=new android.content.ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"ar-hud.png");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Phoenix");
            android.net.Uri uri=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);
            try(java.io.OutputStream out=context.getContentResolver().openOutputStream(uri)){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}
            scenario.onActivity(a->{a.ar.close();assertFalse(a.ar.open);assertNull(a.ar.preview);assertTrue(a.searching);a.stop();});
        }
    }
    @Test public void nearbyAndArGuardsDoNotStartWithoutRadar(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitFocus(scenario);
            scenario.onActivity(a->{a.stop();a.demo=false;a.toggleNearby();assertFalse(a.nearby.active);assertTrue(a.status.getText().toString().contains("Prvo pokreni"));a.openAr();assertFalse(a.ar.open);assertTrue(a.status.getText().toString().contains("Pokreni radar"));});
        }
    }
    @Test public void nearbyPeerRemainsFreshWithoutBleAdvertisement(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{a.stop();MainActivity.Peer peer=new MainActivity.Peer(123,-127);a.peers.put(123,peer);
                assertFalse(a.peerFresh(peer));assertFalse(a.canMessage(peer));
                a.nearby.active=true;a.nearby.authenticated=true;a.nearby.remote=123;a.nearby.lastReceived=android.os.SystemClock.elapsedRealtime();
                assertTrue(a.peerFresh(peer));assertTrue(a.canMessage(peer));
                a.nearby.lastReceived=android.os.SystemClock.elapsedRealtime()-7000;assertFalse(a.peerFresh(peer));assertFalse(a.canMessage(peer));
                a.nearby.stop();
            });
        }
    }
    @Test public void startWithoutGroupHasSpecificOnboarding(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitFocus(scenario);
            scenario.onActivity(a->{a.stop();a.groupStore.clear();a.groupKey=null;a.toggleSearch();assertFalse(a.searching);});
            onView(withText("Pokreni radar • odaberi grupu")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Skeniraj QR")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Pridruži se brojem")).inRoot(isDialog()).check(matches(isDisplayed()));
        }
    }
}
