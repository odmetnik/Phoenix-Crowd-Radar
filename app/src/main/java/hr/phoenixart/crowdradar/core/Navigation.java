package hr.phoenixart.crowdradar.core;

public final class Navigation {
    private Navigation(){}
    public static boolean fresh(long stamp,long now,long max){return stamp>0&&now>=stamp&&now-stamp<=max;}
    public static boolean validFix(double lat,double lon,double error,long age){return Double.isFinite(lat)&&Double.isFinite(lon)&&Double.isFinite(error)&&lat>=-90&&lat<=90&&lon>=-180&&lon<=180&&error>0&&error<=100&&age>=0&&age<=10000;}
    public static long receivedFixAge(long transmitted,long latency,long sinceReceive){
        if(transmitted<0||latency<0||sinceReceive<0||transmitted>10000||latency>6000||sinceReceive>10000)return Long.MAX_VALUE;
        return transmitted+latency+sinceReceive;
    }
    public static boolean consistent(double gps,double uwb,double errors){return Double.isFinite(gps)&&Double.isFinite(uwb)&&Double.isFinite(errors)&&errors>0&&Math.abs(gps-uwb)<=Math.max(5,errors);}
    public static double relative(double bearing,double trueHeading){return RadarMath.normalize(bearing-trueHeading);}
    public static boolean antennaPosture(double backHorizontal,int rotation){return Double.isFinite(backHorizontal)&&backHorizontal>.85&&rotation==0;}
}
