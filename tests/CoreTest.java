import hr.phoenixart.crowdradar.core.*;
import java.util.Arrays;

public class CoreTest {
    static int count;
    static void check(boolean b, String message) { count++; if (!b) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        byte[] key = new byte[32], other = new byte[32]; Arrays.fill(other, (byte)7);
        long now = 1794663200000L;
        byte[] p = Presence.packet(key, 42, now);
        check(p.length == 12, "BLE payload budget");
        check(Presence.verify(key, p, now) == 42, "Valid group");
        check(Presence.verify(other, p, now) == null, "Wrong group rejected");
        byte[] changed = p.clone(); changed[0] ^= 1;
        check(Presence.verify(key, changed, now) == null, "Tamper rejected");
        check(Presence.verify(key, p, now + 90000) == null, "Stale advertisement rejected");
        check(Presence.verify(key, new byte[13], now) == null, "Malformed packet rejected");
        check(!Presence.service(key).equals(Presence.service(other)), "Separate discovery UUID");
        long wrap = 65536L * 30000L;
        check(Presence.verify(key, Presence.packet(key, 9, wrap - 1000), wrap + 1000) == 9, "Epoch rollover");
        check(Math.abs(RadarMath.distance(0,0,0,1) - 111195) < 2, "Distance units");
        check(Math.abs(RadarMath.bearing(0,0,1,0)) < .001, "North");
        check(Math.abs(RadarMath.bearing(0,0,0,1)-90) < .001, "East");
        check(RadarMath.normalize(-90) == 270, "Rotation wrap");
        check(!RadarMath.gpsDirectionUsable(5,10,10,0,0), "Indoor error hides direction");
        check(!RadarMath.gpsDirectionUsable(100,5,5,11000,0), "Stale fix hides direction");
        check(RadarMath.gpsDirectionUsable(100,5,5,500,500), "Good outdoor fix");
        check(!RadarMath.gpsDirectionUsable(100,Double.NaN,5,0,0), "Invalid accuracy");
        check(RadarMath.trend(-80,-74).equals("Signal jača"), "Less negative means stronger");
        byte[] profile="Željko / S24 Ultra".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] sealed=ProfileCrypto.seal(key,profile);
        check(Arrays.equals(profile,ProfileCrypto.open(key,sealed)),"Encrypted profile roundtrip");
        check(!Arrays.equals(sealed,ProfileCrypto.seal(key,profile)),"Unique encryption nonce");
        boolean rejected=false;try{ProfileCrypto.open(other,sealed);}catch(Exception e){rejected=true;}
        check(rejected,"Wrong profile key rejected");
        sealed[sealed.length-1]^=1;rejected=false;try{ProfileCrypto.open(key,sealed);}catch(Exception e){rejected=true;}
        check(rejected,"Profile ciphertext tampering rejected");
        check(!Capabilities.pairSummary(Capabilities.BLE|Capabilities.UWB,Capabilities.BLE).contains("Zajednički UWB"),"One UWB phone is insufficient");
        check(Capabilities.pairSummary(Capabilities.BLE|Capabilities.UWB,Capabilities.BLE|Capabilities.UWB).contains("još nije integrirano"),"Hardware does not imply active precision");
        check(!Capabilities.pairSummary(Capabilities.AWARE|Capabilities.RTT,Capabilities.AWARE).contains("Zajednički Wi-Fi"),"Aware alone does not imply RTT");
        System.out.println("PASS: " + count + " core checks");
    }
}
