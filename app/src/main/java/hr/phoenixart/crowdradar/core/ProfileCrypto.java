package hr.phoenixart.crowdradar.core;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.GCMParameterSpec;

public final class ProfileCrypto {
    private ProfileCrypto() {}
    private static byte[] key(byte[] group) throws Exception {
        if (group.length != 32) throw new IllegalArgumentException("Invalid key");
        Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(group,"HmacSHA256"));
        return m.doFinal("phoenix-profile-v1".getBytes(StandardCharsets.UTF_8));
    }
    public static byte[] seal(byte[] group,byte[] clear) throws Exception {
        byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key(group),"AES"),new GCMParameterSpec(128,nonce));
        c.updateAAD("profile-v1".getBytes(StandardCharsets.UTF_8));
        byte[] body=c.doFinal(clear),out=Arrays.copyOf(nonce,12+body.length);System.arraycopy(body,0,out,12,body.length);return out;
    }
    public static byte[] open(byte[] group,byte[] sealed) throws Exception {
        if(sealed==null || sealed.length<28 || sealed.length>512)throw new IllegalArgumentException("Invalid profile frame");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key(group),"AES"),new GCMParameterSpec(128,sealed,0,12));
        c.updateAAD("profile-v1".getBytes(StandardCharsets.UTF_8));return c.doFinal(sealed,12,sealed.length-12);
    }
}
