package hr.phoenixart.crowdradar.core;

/** Local acceleration indicator only; low acceleration does not imply zero velocity. */
public final class MotionState {
    private double energy;private long at,started;
    public void reset(){energy=0;at=started=0;}
    public void sample(double x,double y,double z,long now){
        double n=x*x+y*y+z*z;if(!Double.isFinite(n)||now<0)return;
        if(at==0||now<at||now-at>2000){started=now;energy=n;}else{double alpha=1-Math.exp(-(now-at)/350.0);energy+=alpha*(n-energy);}
        at=now;
    }
    public boolean fresh(long now){return at>0&&now>=at&&now-at<=2000&&now-started>=700;}
    public boolean aboveThreshold(long now){return fresh(now)&&energy>.16;}
    public double rms(){return Math.sqrt(Math.max(0,energy));}
}
