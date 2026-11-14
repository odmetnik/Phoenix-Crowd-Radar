package hr.phoenixart.crowdradar;

import android.app.Dialog;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import hr.phoenixart.crowdradar.core.ArGuide;
import hr.phoenixart.crowdradar.core.Navigation;
import java.util.*;

/** Foreground camera plus schematic azimuth HUD. No recording, recognition, or false 3-D anchors. */
final class ArOverlay {
    final MainActivity activity;Dialog dialog;ProcessCameraProvider provider;Preview preview;Hud hud;boolean open;
    String cameraStatus="Pokrećem kameru…";
    ArOverlay(MainActivity a){activity=a;}
    void show(){
        if(open)return;open=true;
        dialog=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        FrameLayout root=new FrameLayout(activity);root.setBackgroundColor(Color.BLACK);
        PreviewView camera=new PreviewView(activity);camera.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);camera.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        root.addView(camera,new FrameLayout.LayoutParams(-1,-1));hud=new Hud();root.addView(hud,new FrameLayout.LayoutParams(-1,-1));
        Button close=activity.button("ZATVORI AR",this::close);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,activity.dp(52),Gravity.BOTTOM);cp.setMargins(activity.dp(16),0,activity.dp(16),activity.dp(20));root.addView(close,cp);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        dialog.setContentView(root);dialog.setOnDismissListener(d->release());dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        var future=ProcessCameraProvider.getInstance(activity);
        future.addListener(()->{if(!open)return;try{provider=future.get();preview=new Preview.Builder().build();preview.setSurfaceProvider(camera.getSurfaceProvider());
            provider.bindToLifecycle(activity,CameraSelector.DEFAULT_BACK_CAMERA,preview);cameraStatus="Kamera uživo • bez snimanja";
        }catch(Exception e){cameraStatus="Kamera nije dostupna. Zatvori AR i provjeri dozvolu / drugu aplikaciju.";}hud.invalidate();},ContextCompat.getMainExecutor(activity));
    }
    void close(){if(dialog!=null)dialog.dismiss();else release();}
    void release(){open=false;if(provider!=null&&preview!=null)provider.unbind(preview);preview=null;provider=null;dialog=null;hud=null;}
    final class Hud extends View{
        final Paint paint=new Paint(3);
        Hud(){super(activity);}
        void text(Canvas c,String value,float x,float y,float size,int color){paint.setColor(color);paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setShadowLayer(4,0,0,Color.BLACK);c.drawText(value,x,y,paint);paint.clearShadowLayer();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight();
            paint.setColor(0xC9050B13);c.drawRect(0,0,w,136*d,paint);c.drawRect(0,h-138*d,w,h,paint);
            text(c,"PHOENIX • AR SMJER / EKSPERIMENTALNO",16*d,30*d,14*d,MainActivity.CYAN);
            text(c,cameraStatus,16*d,54*d,12*d,Color.WHITE);
            text(c,"Shematski azimut • oznaka nije položaj u slici",16*d,78*d,12*d,MainActivity.AMBER);
            text(c,"Uspravan portret • bez procjene visine osobe",16*d,100*d,12*d,Color.WHITE);
            text(c,"LIJEVO          ISPRED TEBE          DESNO",16*d,124*d,12*d,MainActivity.CYAN);
            long now=android.os.SystemClock.elapsedRealtime();boolean upright=Navigation.antennaPosture(activity.backHorizontal,activity.displayRotation)&&Navigation.fresh(activity.postureAt,now,2000);
            List<MainActivity.Peer> list=new ArrayList<>(activity.peers.values());
            if(activity.selectedSession!=null)list.sort(Comparator.comparingInt(p->p.id==activity.selectedSession?0:1));
            int row=0,hidden=0;float maxBottom=h-154*d;
            for(MainActivity.Peer peer:list){
                float y=164*d+row*88*d;if(y+72*d>maxBottom){hidden++;continue;}row++;
                MainActivity.Estimate estimate=activity.estimate(peer);boolean fresh=activity.peerFresh(peer);
                Double angle=upright&&fresh?estimate.angle:null;
                paint.setColor(0xD0051720);c.drawRoundRect(12*d,y-22*d,w-12*d,y+60*d,8*d,8*d,paint);
                String name=peer.alias;paint.setTextSize(16*d);while(name.length()>1&&paint.measureText(name)>w-40*d)name=name.substring(0,name.offsetByCodePoints(name.length(),-1));
                text(c,name,22*d,y,16*d,peer.id==(activity.selectedSession==null?Integer.MAX_VALUE:activity.selectedSession)?MainActivity.GREEN:Color.WHITE);
                String distance=fresh&&Double.isFinite(estimate.meters)?String.format(Locale.ROOT,"%s %.1f m",estimate.distanceSource,estimate.meters):"Bez aktualnih metara";
                if(angle!=null){
                    double a=ArGuide.signed(angle);float x=(float)(w/2+ArGuide.position(angle)*(w/2-28*d));
                    paint.setColor(MainActivity.CYAN);paint.setStrokeWidth(2*d);c.drawLine(28*d,y+36*d,w-28*d,y+36*d,paint);
                    c.save();c.translate(x,y+36*d);c.rotate((float)a);Path arrow=new Path();arrow.moveTo(0,-12*d);arrow.lineTo(8*d,8*d);arrow.lineTo(0,4*d);arrow.lineTo(-8*d,8*d);arrow.close();c.drawPath(arrow,paint);c.restore();
                    text(c,distance+" • "+ArGuide.turn(angle)+" • "+Math.round(Math.abs(a))+"°",22*d,y+22*d,11*d,MainActivity.CYAN);
                }else text(c,distance+" • "+(!fresh?"VEZA ZASTARJELA":!upright?"DRŽI USPRAVNO U PORTRETU":"SMJER NIJE IZMJEREN"),22*d,y+24*d,11*d,MainActivity.AMBER);
            }
            String hint=list.isEmpty()?"Nema članova. Pokreni radar na oba telefona.":hidden>0?"Još članova: "+hidden+" • odaberi osobu na radaru":"Strelica traži svjež UWB kut ili GPS + kompas.";
            text(c,hint,16*d,h-110*d,12*d,Color.WHITE);
            if(open)postInvalidateDelayed(100);
        }
    }
}
