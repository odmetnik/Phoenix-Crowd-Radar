package hr.phoenixart.crowdradar.core;

/** No Android dependency: source selection and uncertainty can be tested on the JVM. */
public final class RadarMath {
    private RadarMath() {}
    public static double normalize(double d) { return (d % 360 + 360) % 360; }
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double p = Math.toRadians(lat1), q = Math.toRadians(lat2);
        double a = Math.pow(Math.sin((q-p)/2), 2) + Math.cos(p)*Math.cos(q)*Math.pow(Math.sin(Math.toRadians(lon2-lon1)/2), 2);
        return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double p = Math.toRadians(lat1), q = Math.toRadians(lat2), dl = Math.toRadians(lon2-lon1);
        return normalize(Math.toDegrees(Math.atan2(Math.sin(dl)*Math.cos(q), Math.cos(p)*Math.sin(q)-Math.sin(p)*Math.cos(q)*Math.cos(dl))));
    }
    /** Conservative policy: suppress GPS direction when error circles overlap or fixes are stale. */
    public static boolean gpsDirectionUsable(double distanceM, double myError, double peerError, long myAgeMs, long peerAgeMs) {
        return Double.isFinite(distanceM) && Double.isFinite(myError) && Double.isFinite(peerError)
            && distanceM > 0 && myError > 0 && peerError > 0 && myError <= 25 && peerError <= 25
            && distanceM > 2 * (myError + peerError) && myAgeMs >= 0 && peerAgeMs >= 0 && myAgeMs <= 10000 && peerAgeMs <= 10000;
    }
    public static double smooth(double previous, double current) { return previous * .75 + current * .25; }
    // Signal bands only. These are not calibrated distances or meters.
    public static String band(double rssi) {
        if (rssi >= -60) return "JAK SIGNAL";
        if (rssi >= -75) return "SREDNJI SIGNAL";
        return "SLAB SIGNAL";
    }
    public static String trend(double before, double after) {
        double d = after - before;
        return d > 3 ? "Signal jača" : d < -3 ? "Signal slabi" : "Signal stabilan";
    }
}
