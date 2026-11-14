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
    @Test public void createGroupQrAndImportRoundTrip() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{a.groupStore.clear();a.groupKey=null;a.createGroup(false,null);});
            onView(isAssignableFrom(EditText.class)).perform(replaceText("QA događaj"),closeSoftKeyboard());
            onView(withText("Kreiraj")).perform(click());
            scenario.onActivity(a->{
                assertNotNull(a.groupKey);assertEquals("QA događaj",a.groupName);
                try{assertArrayEquals(a.groupKey,new GroupStore(a).load());}catch(Exception e){throw new AssertionError(e);}
                String token=a.invitationToken();assertTrue(token.startsWith("phoenix2:"));
                a.groupKey=null;assertTrue(a.receiveInvite(token));assertEquals("QA događaj",a.groupName);
                assertFalse(a.receiveInvite("phoenix2:broken"));a.invite();
            });
            onView(withText("QR pozivnica")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Zatvori")).perform(click());
        }
    }
    @Test public void captureDemoDashboard() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
            long deadline=android.os.SystemClock.elapsedRealtime()+15000;
            do{scenario.onActivity(a->ready.set(a.dashboard.ready));if(!ready.get())android.os.SystemClock.sleep(100);}while(!ready.get()&&android.os.SystemClock.elapsedRealtime()<deadline);
            assertTrue("Packaged WebView page loaded",ready.get());
            scenario.onActivity(a->a.toggleDemo());
            java.util.concurrent.CountDownLatch rendered=new java.util.concurrent.CountDownLatch(1);
            scenario.onActivity(a->a.dashboard.view.evaluateJavascript("document.getElementById('badge').textContent",v->{assertTrue(v.contains("DEMO"));rendered.countDown();}));
            assertTrue(rendered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(500,5000);
            android.graphics.Bitmap bitmap=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
            android.content.Context context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),"dashboard.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
        }
    }
    @Test public void startWithoutGroupHasSpecificOnboarding(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{a.stop();a.groupStore.clear();a.groupKey=null;a.toggleSearch();assertFalse(a.searching);});
            onView(withText("Pokreni radar • odaberi grupu")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Skeniraj QR")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Pridruži se brojem")).inRoot(isDialog()).check(matches(isDisplayed()));
        }
    }
}
