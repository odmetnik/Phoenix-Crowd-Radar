package hr.phoenixart.crowdradar.core;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Separate domain from GATT. All frames are bounded; fresh request challenges bind replies. */
public final class NearCrypto {
    private NearCrypto(){}
    private static byte[] derive(byte[] group,String label)throws Exception{
        if(group==null||group.length!=32)throw new IllegalArgumentException();
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(group,"HmacSHA256"));return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
    }
    public static String service(byte[] group)throws Exception{
        byte[] id=derive(group,"phoenix-nearby-service-v1");StringBuilder hex=new StringBuilder("hr.phoenixart.crowdradar.live.");
        for(int i=0;i<16;i++)hex.append(String.format(java.util.Locale.ROOT,"%02x",id[i]&255));return hex.toString();
    }
    public static byte[] seal(byte[] group,byte[] clear)throws Exception{
        if(clear==null||clear.length>4068)throw new IllegalArgumentException();byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(derive(group,"phoenix-nearby-key-v1"),"AES"),new GCMParameterSpec(128,iv));
        cipher.updateAAD("nearby-v1".getBytes(StandardCharsets.UTF_8));byte[] body=cipher.doFinal(clear),out=Arrays.copyOf(iv,12+body.length);System.arraycopy(body,0,out,12,body.length);return out;
    }
    public static byte[] open(byte[] group,byte[] frame)throws Exception{
        if(frame==null||frame.length<28||frame.length>4096)throw new IllegalArgumentException();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(derive(group,"phoenix-nearby-key-v1"),"AES"),new GCMParameterSpec(128,frame,0,12));
        cipher.updateAAD("nearby-v1".getBytes(StandardCharsets.UTF_8));return cipher.doFinal(frame,12,frame.length-12);
    }
}
