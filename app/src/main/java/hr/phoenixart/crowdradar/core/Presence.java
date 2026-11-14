package hr.phoenixart.crowdradar.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Private group discovery, not individual identity or secure ranging. */
public final class Presence {
    private Presence() {}
    private static byte[] mac(byte[] key, byte[] message) {
        if (key.length != 32) throw new IllegalArgumentException("Group key must be 256 bits");
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return m.doFinal(message);
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public static UUID service(byte[] key) {
        ByteBuffer b = ByteBuffer.wrap(mac(key, "phoenix-presence-v1".getBytes(StandardCharsets.UTF_8)));
        return new UUID(b.getLong(), b.getLong());
    }
    public static byte[] packet(byte[] key, int sessionId, long nowMillis) {
        byte[] body = ByteBuffer.allocate(6).putInt(sessionId).putShort((short)(nowMillis / 30000)).array();
        byte[] out = Arrays.copyOf(body, 12);
        System.arraycopy(mac(key, body), 0, out, 6, 6);
        return out;
    }
    public static Integer verify(byte[] key, byte[] packet, long nowMillis) {
        if (packet == null || packet.length != 12) return null;
        byte[] body = Arrays.copyOf(packet, 6);
        byte[] expected = Arrays.copyOf(mac(key, body), 6);
        if (!MessageDigest.isEqual(expected, Arrays.copyOfRange(packet, 6, 12))) return null;
        ByteBuffer b = ByteBuffer.wrap(body);
        int id = b.getInt(), slot = b.getShort() & 65535;
        int current = (int)(nowMillis / 30000) & 65535;
        int delta = (slot - current + 65536) & 65535;
        return (delta == 0 || delta == 1 || delta == 65535) ? id : null;
    }
}
