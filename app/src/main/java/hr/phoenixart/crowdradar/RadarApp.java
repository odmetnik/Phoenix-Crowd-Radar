package hr.phoenixart.crowdradar;

public final class RadarApp extends android.app.Application {
    RadarEngine engine;
    @Override public void onCreate(){super.onCreate();engine=new RadarEngine(this);}
}
