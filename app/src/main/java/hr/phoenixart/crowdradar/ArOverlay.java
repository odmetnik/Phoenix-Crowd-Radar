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
import hr.phoenixart.crowdradar.core.*;
import java.util.*;

/** Camera HUD + explicitly schematic horizontal distance grid, never a false image-space anchor. */
final class ArOverlay {
    final MainActivity activity;Dialog dialog;ProcessCameraProvider provider;Preview preview;PreviewView cameraView;Hud hud;boolean open;int generation,page;
    String cameraStatus="Pokrećem kameru…";
    ArOverlay(MainActivity a){activity=a;}
    void show(){if(open)return;open=true;page=0;int ticket=++generation;cameraStatus="Pokrećem kameru…";
        dialog=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen);FrameLayout root=new FrameLayout(activity);root.setBackgroundColor(Color.BLACK);
        cameraView=new PreviewView(activity);cameraView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);cameraView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        PreviewView currentCamera=cameraView;root.addView(cameraView,new FrameLayout.LayoutParams(-1,-1));hud=new Hud();root.addView(hud,new FrameLayout.LayoutParams(-1,-1));
        Button close=activity.button("ZATVORI AR",this::close);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,activity.dp(48),Gravity.BOTTOM);cp.setMargins(activity.dp(16),0,activity.dp(16),activity.dp(12));root.addView(close,cp);
        Button next=activity.button("SLJEDEĆI ČLANOVI",()->{page++;if(hud!=null)hud.invalidate();});FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,activity.dp(36),Gravity.BOTTOM);np.setMargins(activity.dp(16),0,activity.dp(16),activity.dp(64));root.addView(next,np);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        dialog.setContentView(root);dialog.setOnDismissListener(d->{if(generation==ticket)release();});dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        var future=ProcessCameraProvider.getInstance(activity);future.addListener(()->{if(!open||generation!=ticket)return;
            try{provider=future.get();preview=new Preview.Builder().build();preview.setSurfaceProvider(currentCamera.getSurfaceProvider());if(!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))throw new IllegalStateException("No back camera");
                provider.bindToLifecycle(activity,CameraSelector.DEFAULT_BACK_CAMERA,preview);cameraStatus="Kamera uživo • bez snimanja";
            }catch(Exception e){cameraStatus="Kamera nije dostupna ("+e.getClass().getSimpleName()+")";}if(hud!=null)hud.invalidate();},ContextCompat.getMainExecutor(activity));
    }
    void close(){Dialog current=dialog;release();if(current!=null){current.setOnDismissListener(null);current.dismiss();}}
    void release(){open=false;generation++;if(provider!=null&&preview!=null)provider.unbind(preview);preview=null;provider=null;cameraView=null;dialog=null;hud=null;}
    final class Hud extends View {
        final Paint paint=new Paint(3);
        Hud(){super(activity);}
        void text(Canvas c,String value,float x,float y,float size,int color,float max){paint.setColor(color);paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            if(paint.measureText(value)>max){while(value.length()>1&&paint.measureText(value+"…")>max)value=value.substring(0,value.offsetByCodePoints(value.length(),-1));value+="…";}
            paint.setShadowLayer(4,0,0,Color.BLACK);c.drawText(value,x,y,paint);paint.clearShadowLayer();}
        void arrow(Canvas c,float x,float y,double angle,float size,int color){paint.setColor(color);c.save();c.translate(x,y);c.rotate((float)ArGuide.signed(angle));Path p=new Path();p.moveTo(0,-size);p.lineTo(size*.65f,size*.65f);p.lineTo(0,size*.25f);p.lineTo(-size*.65f,size*.65f);p.close();c.drawPath(p,paint);c.restore();}
        String meters(MainActivity.Estimate e,boolean fresh){return fresh&&Double.isFinite(e.meters)?(e.distanceSource.startsWith("GPS")?"~ ":"")+String.format(Locale.ROOT,"%.1f m",e.meters):"— m";}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),pad=14*d;
            RadarEngine engine=activity.engine;long now=android.os.SystemClock.elapsedRealtime();boolean upright=Navigation.antennaPosture(activity.backHorizontal,activity.displayRotation)&&Navigation.fresh(activity.postureAt,now,2000);
            List<MainActivity.Peer> list=new ArrayList<>(engine.peers.values());list.sort(Comparator.comparingInt(p->Objects.equals(p.id,engine.selectedSession)?0:1));
            List<MainActivity.Estimate> estimates=new ArrayList<>();double far=0;boolean metric=false;for(var p:list){var e=activity.estimate(p);estimates.add(e);if(activity.peerFresh(p)&&Double.isFinite(e.meters)){far=Math.max(far,e.meters);metric=true;}}
            double scale=ArGuide.range(far);paint.setColor(0xD0050B13);c.drawRect(0,0,w,90*d,paint);
            text(c,"PHOENIX • AR SMJER",pad,26*d,17*d,MainActivity.CYAN,w-2*pad);text(c,cameraStatus,pad,47*d,11*d,Color.WHITE,w-2*pad);
            text(c,"Shematska udaljenost • nije 3D položaj na slici",pad,67*d,11*d,MainActivity.AMBER,w-2*pad);
            float cardsTop=h-290*d,cy=Math.max(250*d,cardsTop-22*d),radius=Math.min(w*.44f,Math.max(60*d,cy-190*d));
            paint.setColor(0x70000D16);c.drawRect(0,90*d,w,cardsTop-6*d,paint);
            if(metric){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);RectF ring=new RectF();for(int i=1;i<=4;i++){float r=radius*i/4;paint.setColor(0x9951CCD5);ring.set(w/2-r,cy-r,w/2+r,cy+r);c.drawArc(ring,180,180,false,paint);
                    paint.setStyle(Paint.Style.FILL);text(c,ArGuide.format(scale*i/4)+" m",w/2+5*d,cy-r+12*d,10*d,MainActivity.CYAN,w*.4f);paint.setStyle(Paint.Style.STROKE);}
                for(int angle=-90;angle<=90;angle+=30){double a=Math.toRadians(angle);paint.setColor(0x6651CCD5);c.drawLine(w/2,cy,w/2+(float)Math.sin(a)*radius,cy-(float)Math.cos(a)*radius,paint);}paint.setStyle(Paint.Style.FILL);
                text(c,"LIJEVO",pad,cy+16*d,10*d,MainActivity.CYAN,w/3);text(c,"DESNO",w-pad-42*d,cy+16*d,10*d,MainActivity.CYAN,w/3);
            }else text(c,"NEMA AKTUALNE METARSKE SKALE",pad,cy-30*d,12*d,MainActivity.AMBER,w-2*pad);
            if(!list.isEmpty()){
                var target=list.get(0);var e=estimates.get(0);boolean fresh=activity.peerFresh(target);Double angle=upright&&fresh?e.angle:null;
                text(c,target.alias+" • "+meters(e,fresh),pad,118*d,20*d,MainActivity.GREEN,w-2*pad);
                if(angle!=null){arrow(c,w/2,159*d,angle,22*d,MainActivity.CYAN);text(c,ArGuide.turn(angle)+" • "+Math.round(Math.abs(ArGuide.signed(angle)))+"°",pad,194*d,12*d,MainActivity.CYAN,w-2*pad);}
                else text(c,!fresh?"VEZA ZASTARJELA":!upright?"DRŽI USPRAVNO U PORTRETU":"SMJER NIJE IZMJEREN",pad,159*d,12*d,MainActivity.AMBER,w-2*pad);
            }
            if(!list.isEmpty()&&engine.relayed(list.get(0)))text(c,engine.routeLabel(list.get(0)),pad,214*d,11*d,MainActivity.GREEN,w-2*pad);
            if(metric&&upright)for(int i=0;i<list.size();i++){
                var peer=list.get(i);var e=estimates.get(i);if(!activity.peerFresh(peer)||e.angle==null||!Double.isFinite(e.meters))continue;
                double a=ArGuide.signed(e.angle);if(Math.abs(a)>90)continue;double norm=Math.min(1,e.meters/scale),r=Math.toRadians(a);float x=w/2+(float)(Math.sin(r)*radius*norm),y=cy-(float)(Math.cos(r)*radius*norm);
                paint.setColor(Objects.equals(engine.selectedSession,peer.id)?MainActivity.GREEN:MainActivity.CYAN);c.drawCircle(x,y,5*d,paint);float lx=Math.max(pad,Math.min(w-120*d,x+8*d));text(c,peer.alias+" "+meters(e,true),lx,y-7*d,11*d,MainActivity.CYAN,w-lx-pad);
            }
            paint.setColor(0xE0050B13);c.drawRect(0,cardsTop,w,h,paint);int pages=Math.max(1,(list.size()+2)/3);page=Math.floorMod(page,pages);int start=page*3;
            text(c,"ČLANOVI "+(list.isEmpty()?0:start+1)+"–"+Math.min(start+3,list.size())+" / "+list.size()+(metric?" • RADIJUS "+ArGuide.format(scale)+" m":""),pad,cardsTop+16*d,11*d,MainActivity.CYAN,w-2*pad);
            for(int j=0;j<3&&start+j<list.size();j++){int i=start+j;var peer=list.get(i);var e=estimates.get(i);boolean fresh=activity.peerFresh(peer);float y=cardsTop+42*d+j*46*d;
                text(c,peer.alias+" • "+meters(e,fresh)+" • "+e.distanceSource,pad,y,14*d,Color.WHITE,w-2*pad);
                String direction=!fresh?"ZASTARJELO • ČEKAM NOVI PODATAK":!upright?"USPRAVAN PORTRET ZA STRELICU":e.angle==null?"SMJER NIJE IZMJEREN":ArGuide.turn(e.angle);
                text(c,direction,pad,y+16*d,10*d,MainActivity.AMBER,w-2*pad);
            }
            if(list.isEmpty())text(c,"Čekam članove iste grupe…",pad,cardsTop+55*d,15*d,Color.WHITE,w-2*pad);
            if(open)postInvalidateDelayed(100);
        }
    }
}
