package hr.phoenixart.crowdradar.core;

/** A horizontal direction HUD, deliberately not a camera projection or a 3-D anchor. */
public final class ArGuide {
    private ArGuide(){}
    public static double range(double far){if(!Double.isFinite(far)||far<0)return .5;double wanted=Math.max(.5,far*1.25),base=Math.pow(10,Math.floor(Math.log10(wanted)));for(double step:new double[]{1,2,5,10})if(base*step>=wanted)return base*step;return wanted;}
    public static String format(double value){return String.format(java.util.Locale.ROOT,value<10?"%.1f":"%.0f",value);}
    public static double signed(double angle){return Double.isFinite(angle)?((angle%360+540)%360)-180:Double.NaN;}
    public static String turn(double angle){double a=signed(angle);if(!Double.isFinite(a))return "SMJER NIJE IZMJEREN";
        if(Math.abs(a)<=10)return "ISPRED TEBE";if(Math.abs(a)>=150)return "IZA TEBE • OKRENI SE";
        return a<0?"OKRENI ULIJEVO":"OKRENI UDESNO";
    }
    public static double position(double angle){double a=signed(angle);return Double.isFinite(a)?Math.max(-1,Math.min(1,a/90)):Double.NaN;}
}
