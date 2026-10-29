package io.github.marouane4142.maros;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-bound credentials; never part of LauncherPrefs' portable configuration. */
public final class Secrets {
    private static final String ALIAS="tvhome.integration.v2";
    private static final Object KEY_LOCK=new Object();
    private final SharedPreferences prefs;
    public Secrets(Context context) { prefs=context.getSharedPreferences("tvhome_secrets",Context.MODE_PRIVATE); }
    private SecretKey key() throws Exception {
        synchronized(KEY_LOCK){
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(store.containsAlias(ALIAS)) return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
        }
    }
    public synchronized void put(String name,String value) throws Exception {
        if(value==null||value.isEmpty()) { prefs.edit().remove(name).commit(); return; }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        if(!prefs.edit().putString(name,Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit()) throw new java.io.IOException("Échec de l’enregistrement du jeton.");
    }
    public synchronized String get(String name) {
        try { String saved=prefs.getString(name,""); if(saved.isEmpty()) return ""; String[] parts=saved.split(":",2);if(parts.length!=2)return "";Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP))); return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8); }
        catch(Exception e) { return ""; }
    }
}
