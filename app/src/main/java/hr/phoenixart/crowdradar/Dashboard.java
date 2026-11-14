package hr.phoenixart.crowdradar;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.webkit.*;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONObject;

/** The bridge is reachable only from packaged, offline UI; never loads external pages. */
final class Dashboard {
    interface Actions { void run(String action,String value); }
    final WebView view;
    final MainActivity activity;
    boolean ready;
    @SuppressLint("SetJavaScriptEnabled")
    Dashboard(MainActivity activity,Actions actions){
        this.activity=activity;view=new WebView(activity);view.setBackgroundColor(MainActivity.BG);
        WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(activity)).build();
        view.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();if("https".equals(u.getScheme())&&"appassets.androidplatform.net".equals(u.getHost())){
                    WebResourceResponse result=loader.shouldInterceptRequest(u);if(result!=null)return result;
                }
                return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView v,String url){ready=true;activity.renderDashboard();}
        });
        view.addJavascriptInterface(new Object(){
            @JavascriptInterface public void action(String action,String value){
                if(action==null||value==null||action.length()>24||value.length()>40)return;
                activity.runOnUiThread(()->{actions.run(action,value);activity.renderDashboard();});
            }
        },"Phoenix");
        view.loadUrl("https://appassets.androidplatform.net/assets/ui/index.html");
    }
    void render(JSONObject data){if(ready)view.evaluateJavascript("window.receiveState("+data.toString()+")",null);}
    void destroy(){ready=false;view.removeJavascriptInterface("Phoenix");view.destroy();}
}
