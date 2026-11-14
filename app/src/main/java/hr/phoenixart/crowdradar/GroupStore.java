package hr.phoenixart.crowdradar;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypt group secret with a non-exportable Android Keystore key. Backups disabled. */
final class GroupStore {
    private final Context context;
    GroupStore(Context context) { this.context = context; }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias("phoenix-group-v1")) {
            KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("phoenix-group-v1", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) store.getEntry("phoenix-group-v1", null)).getSecretKey();
    }
    byte[] load() throws Exception {
        String text = context.getSharedPreferences("group", 0).getString("sealed", null);
        if (text == null) return null;
        byte[] bytes = Base64.decode(text, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, bytes, 0, 12));
        byte[] result = cipher.doFinal(bytes, 12, bytes.length - 12);
        if (result.length != 32) throw new IllegalStateException("Invalid stored group");
        return result;
    }
    void save(byte[] secret) throws Exception {
        if (secret.length != 32) throw new IllegalArgumentException("Invalid key");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] enc = cipher.doFinal(secret), iv = cipher.getIV(), all = new byte[iv.length + enc.length];
        System.arraycopy(iv, 0, all, 0, iv.length); System.arraycopy(enc, 0, all, iv.length, enc.length);
        if (!context.getSharedPreferences("group", 0).edit().putString("sealed", Base64.encodeToString(all, Base64.NO_WRAP)).commit())
            throw new IllegalStateException("Storage write failed");
    }
    void clear() { context.getSharedPreferences("group", 0).edit().clear().commit(); }
}
