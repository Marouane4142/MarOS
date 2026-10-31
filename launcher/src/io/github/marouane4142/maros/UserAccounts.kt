package io.github.marouane4142.maros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.marouane4142.maros.tv.Profiles
import io.github.marouane4142.maros.tv.AccountPolicy
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/** Display identity and encrypted OAuth credentials are scoped to a launcher profile. */
object UserAccounts {
    private fun store(c: Context) = c.getSharedPreferences("maros_accounts", 0)
    fun slot(profile: String) = MessageDigest.getInstance("SHA-256").digest(profile.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }.take(24)
    fun identity(c: Context, profile: String = Profiles(c).current): JSONObject = runCatching {
        JSONObject(store(c).getString("identity_${slot(profile)}", "{}")!!)
    }.getOrDefault(JSONObject())
    fun label(c: Context, profile: String = Profiles(c).current) = identity(c, profile).optString("name").ifBlank { profile }
    fun email(c: Context): String {
        if (RoomData.guest(c)) return ""
        val info = identity(c); val email = info.optString("email")
        return if (info.optString("source") == "android" && !DeviceAccounts.contains(c, email)) "" else email
    }
    fun credentialKey(c: Context) = "google_tokens_${slot(Profiles(c).current)}"
    @Synchronized fun migrate(c: Context) {
        val secret = Secrets(c)
        if (!store(c).getBoolean("migrated", false)) {
            val previous = secret.get("google_profile")
            if (previous.isNotBlank()) {
                store(c).edit().putString("identity_${slot(Profiles(c).current)}", previous).apply()
                secret.put(credentialKey(c), secret.get("google_tokens"))
                secret.put("google_tokens", ""); secret.put("google_profile", "")
            }
            store(c).edit().putBoolean("migrated", true).apply()
        }
    }
    fun link(c: Context, info: JSONObject, token: JSONObject, addUser: Boolean): String {
        val profiles = Profiles(c)
        val existing = profiles.names().firstOrNull {
            val saved = identity(c, it)
            saved.optString("sub") == info.getString("sub") ||
                (saved.optString("source") == "android" && info.optBoolean("email_verified") &&
                    AccountPolicy.sameGoogleAccount(saved.optString("email"), saved.optString("account_type"), info.optString("email"), DeviceAccounts.GOOGLE))
        }
        if (existing != null) profiles.select(existing)
        else if (addUser) {
            val base = info.optString("name", "Google").replace(Regex("[\\p{Cntrl}]"), " ").trim().take(20).ifBlank { "Google" }
            var name = base; var count = 2
            while (name in profiles.names()) name = "$base ${count++}"
            profiles.create(name)
        }
        val key = slot(profiles.current)
        val identity = JSONObject().put("sub", info.getString("sub")).put("name", info.optString("name").take(120))
            .put("email", info.optString("email").take(254)).put("source", "oauth").put("account_type", DeviceAccounts.GOOGLE)
        Secrets(c).put(credentialKey(c), token.toString())
        store(c).edit().putString("identity_$key", identity.toString()).apply()
        RoomData.setGuest(c, false); LauncherPrefs(c).changed()
        return profiles.current
    }
    fun linkDevice(c: Context, account: android.accounts.Account, addUser: Boolean): String {
        require(account.type == DeviceAccounts.GOOGLE && DeviceAccounts.contains(c, account.name)) { "Compte non accessible à MarOS" }
        val profiles = Profiles(c)
        val existing = profiles.names().firstOrNull {
            val info = identity(c, it)
            AccountPolicy.sameGoogleAccount(info.optString("email"), info.optString("account_type", DeviceAccounts.GOOGLE), account.name, account.type)
        }
        if (existing != null) profiles.select(existing)
        else {
            val label = account.name.substringBefore('@').replace(Regex("[\\p{Cntrl}]"), "").take(20).ifBlank { "Google" }
            if (addUser) {
                var name = label; var suffix = 2
                while (name in profiles.names()) name = "$label ${suffix++}"
                profiles.create(name)
            }
            clear(c)
            val info = JSONObject().put("source", "android").put("account_type", account.type)
                .put("email", account.name).put("name", label)
            store(c).edit().putString("identity_${slot(profiles.current)}", info.toString()).apply()
        }
        RoomData.setGuest(c, false); LauncherPrefs(c).changed()
        return profiles.current
    }
    fun refreshAvatar(c: Context, profile: String, info: JSONObject) {
        val file = runCatching { saveAvatar(c, slot(profile), info.optString("picture")) }.getOrNull() ?: return
        val current = identity(c, profile)
        if (current.optString("sub") != info.optString("sub")) return
        current.put("avatar", file)
        store(c).edit().putString("identity_${slot(profile)}", current.toString()).apply(); LauncherPrefs(c).changed()
    }
    fun resetCredentials(c: Context) {
        val secrets=Secrets(c)
        for(profile in Profiles(c).names()) secrets.put("google_tokens_${slot(profile)}", "")
    }
    fun clear(c: Context) {
        clearProfile(c, Profiles(c).current)
    }
    fun clearProfile(c: Context, name: String) {
        Secrets(c).put("google_tokens_${slot(name)}", "")
        store(c).edit().remove("identity_${slot(name)}").apply()
        File(c.filesDir, "avatars/${slot(name)}.jpg").delete()
        LauncherPrefs(c).changed()
    }
    fun avatar(c: Context, profile: String = Profiles(c).current): Bitmap? {
        val id = identity(c, profile).optString("avatar")
        if (!id.matches(Regex("[a-f0-9]{24}\\.jpg"))) return null
        return BitmapFactory.decodeFile(File(c.filesDir, "avatars/$id").path)
    }
    private fun saveAvatar(c: Context, key: String, address: String): String? {
        val uri = URI(address)
        if (uri.scheme != "https" || uri.host?.let { it == "googleusercontent.com" || it.endsWith(".googleusercontent.com") } != true || uri.rawUserInfo != null) return null
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000; conn.instanceFollowRedirects = false
        try {
            if (conn.responseCode != 200) return null
            val data = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while(true) { val n=input.read(buffer); if(n<0)break; require(out.size()+n<=1024*1024);out.write(buffer,0,n) };out.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(data,0,data.size,bounds)
            require(bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048)
            val source = BitmapFactory.decodeByteArray(data,0,data.size) ?: return null
            val bitmap = Bitmap.createScaledBitmap(source,160,160,true)
            val folder=File(c.filesDir,"avatars");check(folder.isDirectory||folder.mkdirs())
            File(folder,"$key.jpg").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,88,it)) }
            if(bitmap!==source)bitmap.recycle();source.recycle()
            return "$key.jpg"
        } finally {conn.disconnect()}
    }
}
