package com.vela.rw6totp;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.json.JSONArray;

/** Account data stays in private storage, encrypted with a non-exportable device key. */
final class AccountVault {
    private static final String ALIAS = "rw6_totp_vault_v1";
    private final AtomicFile file;
    AccountVault(Context context) { file = new AtomicFile(new File(context.getNoBackupFilesDir(), "accounts.v1")); }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    List<OtpAccount> read() throws Exception {
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile().getPath() + ".bak").exists()) return new ArrayList<>();
        if (file.getBaseFile().length() > 128 * 1024 || new File(file.getBaseFile().getPath() + ".bak").length() > 128 * 1024)
            throw new Exception("Invalid vault size");
        byte[] blob = file.readFully();
        byte[] plain = VaultCipher.decrypt(key(),blob);
        try {
            JSONArray array = new JSONArray(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
            if (array.length() > 50) throw new Exception("Invalid vault");
            List<OtpAccount> accounts = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) accounts.add(OtpAccount.fromJson(array.getJSONObject(i)));
            return accounts;
        } finally { Arrays.fill(plain, (byte)0); }
    }
    void write(List<OtpAccount> accounts) throws Exception {
        if (accounts.size() > 50) throw new Exception("Too many accounts");
        JSONArray array = new JSONArray();
        for (OtpAccount account : accounts) array.put(account.toJson());
        byte[] plain = array.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FileOutputStream out = null;
        try {
            byte[] encrypted = VaultCipher.encrypt(key(),plain);
            out = file.startWrite(); out.write(encrypted); file.finishWrite(out);
        } catch (Exception e) { if (out != null) file.failWrite(out); throw e; }
        finally { Arrays.fill(plain, (byte)0); }
    }
}
