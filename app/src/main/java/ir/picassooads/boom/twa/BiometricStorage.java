package ir.picassooads.boom.twa;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * ═══════════════════════════════════════════════════════════════
 * BiometricStorage — ذخیره‌ی امن device_key با Android Keystore
 * 
 * • device_key یک رشته‌ی ۶۴ کاراکتری از سرور است که هنگام
 *   فعال‌سازی بیومتریک صادر می‌شود.
 * • این کلید با یک AES-256 کلید که در Keystore سخت‌افزاری
 *   ذخیره می‌شود رمزنگاری می‌گردد.
 * • خود device_key رمزنگاری‌شده در SharedPreferences می‌ماند.
 * • در دستگاه‌های Root شده، Keystore محافظت بیشتری می‌کند.
 * ═══════════════════════════════════════════════════════════════
 */
public class BiometricStorage {

    private static final String TAG = "BiometricStorage";

    private static final String PREFS_NAME       = "boom_biometric";
    private static final String KEY_DEVICE_KEY   = "encrypted_device_key";
    private static final String KEY_DEVICE_NAME  = "device_name";
    private static final String KEY_USERNAME     = "bound_username";
    private static final String KEY_USER_ID      = "bound_user_id";
    private static final String KEY_ACTIVATED_AT = "activated_at";

    private static final String KEYSTORE_ALIAS   = "boom_bio_master_key_v1";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final Context appContext;
    private final SharedPreferences prefs;

    public BiometricStorage(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /* ═══════════════════════════════════════════════════════════
       بررسی فعال بودن بیومتریک
       ═══════════════════════════════════════════════════════════ */
    public boolean isEnabled() {
        String encrypted = prefs.getString(KEY_DEVICE_KEY, null);
        if (encrypted == null || encrypted.isEmpty()) return false;
        // چک می‌کنیم که username هم ذخیره شده باشد
        String boundUser = prefs.getString(KEY_USERNAME, "");
        return boundUser != null && !boundUser.isEmpty();
    }

    public boolean isEnabledFor(String username) {
        if (!isEnabled()) return false;
        String bound = prefs.getString(KEY_USERNAME, "");
        return bound != null && bound.equalsIgnoreCase(username);
    }

    /* ═══════════════════════════════════════════════════════════
       ذخیره‌ی device_key (فعال‌سازی)
       ═══════════════════════════════════════════════════════════ */
    public boolean save(String deviceKey, String username, int userId, String deviceName) {
        if (deviceKey == null || deviceKey.isEmpty()) return false;
        if (username == null || username.isEmpty()) return false;

        try {
            String encrypted = encrypt(deviceKey);
            if (encrypted == null) return false;

            prefs.edit()
                    .putString(KEY_DEVICE_KEY, encrypted)
                    .putString(KEY_USERNAME, username)
                    .putInt(KEY_USER_ID, userId)
                    .putString(KEY_DEVICE_NAME,
                            deviceName != null ? deviceName : android.os.Build.MODEL)
                    .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
                    .apply();

            Log.d(TAG, "Biometric device_key saved for user: " + username);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "save failed", e);
            return false;
        }
    }

    /* ═══════════════════════════════════════════════════════════
       خواندن device_key (رمزگشایی)
       ═══════════════════════════════════════════════════════════ */
    public String getDeviceKey() {
        String encrypted = prefs.getString(KEY_DEVICE_KEY, null);
        if (encrypted == null || encrypted.isEmpty()) return null;

        try {
            return decrypt(encrypted);
        } catch (Exception e) {
            Log.e(TAG, "decrypt failed — clearing storage", e);
            clear();
            return null;
        }
    }

    public String getBoundUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public int getBoundUserId() {
        return prefs.getInt(KEY_USER_ID, 0);
    }

    public String getDeviceName() {
        return prefs.getString(KEY_DEVICE_NAME, "");
    }

    public long getActivatedAt() {
        return prefs.getLong(KEY_ACTIVATED_AT, 0L);
    }

    /* ═══════════════════════════════════════════════════════════
       پاک کردن (غیرفعال‌سازی)
       ═══════════════════════════════════════════════════════════ */
    public void clear() {
        try {
            prefs.edit().clear().apply();
            Log.d(TAG, "Biometric storage cleared");
        } catch (Exception e) {
            Log.e(TAG, "clear failed", e);
        }
    }

    /* ═══════════════════════════════════════════════════════════
       رمزنگاری با AES-256-GCM + کلید Keystore
       ═══════════════════════════════════════════════════════════ */
    private String encrypt(String plain) throws Exception {
        SecretKey key = getOrCreateMasterKey();
        if (key == null) return null;

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

        // فرمت: base64(iv || encrypted)
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    private String decrypt(String encoded) throws Exception {
        byte[] combined = Base64.decode(encoded, Base64.NO_WRAP);
        if (combined.length < GCM_IV_LENGTH_BYTES) {
            throw new IllegalStateException("invalid payload");
        }

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);

        byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH_BYTES];
        System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

        SecretKey key = getOrCreateMasterKey();
        if (key == null) throw new IllegalStateException("no master key");

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

        byte[] plain = cipher.doFinal(cipherText);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /* ═══════════════════════════════════════════════════════════
       ساخت/خواندن کلید اصلی در Keystore
       ═══════════════════════════════════════════════════════════ */
    private SecretKey getOrCreateMasterKey() {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER);
            ks.load(null);

            if (ks.containsAlias(KEYSTORE_ALIAS)) {
                KeyStore.Entry entry = ks.getEntry(KEYSTORE_ALIAS, null);
                if (entry instanceof KeyStore.SecretKeyEntry) {
                    return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
                }
                return null;
            }

            KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);

            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256);

            // در API 23+ می‌توانیم نیاز به تأیید بیومتریک را هم اجبار کنیم.
            // ولی چون خود فراخوانی بیومتریک انجام می‌شود، اینجا اجبار نمی‌کنیم.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(false);
            }

            kg.init(builder.build());
            return kg.generateKey();

        } catch (Exception e) {
            Log.e(TAG, "getOrCreateMasterKey failed", e);
            return null;
        }
    }
}
