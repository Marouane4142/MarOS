package io.github.marouane4142.maros

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Official limited-input OAuth flow. Google credentials are entered only on Google's own page. */
object GoogleLink {
    private data class Pending(val code: String, val user: String, val url: String, val expiry: Long, var interval: Int, val generation: Int, val profile: String, val addUser: Boolean)
    @Volatile private var pending: Pending? = null
    private var generation = 0
    private const val SCOPES = "openid email profile https://www.googleapis.com/auth/drive.file"
    private fun secrets(c: Context) = Secrets(c)
    private fun tokens(c: Context) = runCatching { JSONObject(secrets(c).get(UserAccounts.credentialKey(c))) }.getOrDefault(JSONObject())
    private fun profile(c: Context) = UserAccounts.identity(c)
    fun configure(c: Context, clientId: String, clientSecret: String) {
        require(clientId.matches(Regex("[A-Za-z0-9_-]{5,220}\\.apps\\.googleusercontent\\.com"))) { "Identifiant OAuth Google invalide" }
        val old = secrets(c).get("google_client_id")
        require(clientSecret.length in 8..200 && clientSecret.none { it.isISOControl() }) { "Secret du client TV attendu" }
        if (old != clientId) { cancelPending(); UserAccounts.resetCredentials(c) }
        secrets(c).put("google_client_id", clientId); secrets(c).put("google_client_secret", clientSecret)
    }
    @JvmStatic fun status(c: Context): JSONObject {
        if (RoomData.guest(c)) return JSONObject().put("hidden", true).put("connected", false)
        val info = profile(c); val token = tokens(c)
        val connected = info.optString("sub").isNotBlank() && (token.optString("refresh_token").isNotBlank() || token.optLong("expiry") > System.currentTimeMillis())
        val result = JSONObject().put("configured", secrets(c).get("google_client_id").isNotBlank() && secrets(c).get("google_client_secret").isNotBlank())
            .put("connected", connected).put("name", info.optString("name")).put("email", info.optString("email")).put("avatar", info.optString("avatar"))
            .put("identity_source", info.optString("source", if(info.has("sub")) "oauth" else "local"))
            .put("system_account", info.optString("email").isNotBlank() && DeviceAccounts.contains(c, info.optString("email")))
        pending?.takeIf { it.expiry > SystemClock.elapsedRealtime() }?.let { result.put("user_code", it.user).put("verification_url", it.url) }
        return result
    }
    @Synchronized fun cancelPending() { generation++; pending = null }
    @JvmStatic fun begin(c: Context, addUser: Boolean = false): JSONObject {
        require(!RoomData.guest(c)) { "Quittez le mode invités" }
        val sourceProfile = io.github.marouane4142.maros.tv.Profiles(c).current
        val requestGeneration = synchronized(this) { generation++; pending=null; generation }
        val client = secrets(c).get("google_client_id"); require(client.isNotBlank() && secrets(c).get("google_client_secret").isNotBlank()) { "Configurez d’abord le client OAuth TV de votre projet Google Cloud" }
        val response = form("https://oauth2.googleapis.com/device/code", mapOf("client_id" to client, "scope" to SCOPES))
        val url = response.optString("verification_url", response.optString("verification_uri"))
        val parsed = java.net.URI(url)
        require(parsed.scheme == "https" && parsed.host in setOf("www.google.com", "google.com", "accounts.google.com")) { "Adresse de connexion Google invalide" }
        val deviceCode = response.getString("device_code"); val user = response.getString("user_code")
        require(deviceCode.length in 5..2000 && user.length in 3..40)
        synchronized(this) {
            require(generation == requestGeneration && sourceProfile == io.github.marouane4142.maros.tv.Profiles(c).current) { "Connexion annulée ; relancez depuis votre profil" }
            pending = Pending(deviceCode, user, url, SystemClock.elapsedRealtime() + response.optInt("expires_in", 1800).coerceIn(30, 1800) * 1000L, response.optInt("interval", 5).coerceIn(5, 30), generation, sourceProfile, addUser)
        }
        return status(c)
    }
    suspend fun awaitSignIn(c: Context): String {
        val session = pending ?: throw IllegalStateException("Connexion non démarrée")
        while (SystemClock.elapsedRealtime() < session.expiry) {
            delay(session.interval * 1000L); coroutineContext.ensureActive()
            require(pending === session) { "Connexion annulée" }
            val response = request("https://oauth2.googleapis.com/token", "POST", null, encoded(mapOf("client_id" to secrets(c).get("google_client_id"), "client_secret" to secrets(c).get("google_client_secret"), "device_code" to session.code, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code")), true)
            when (response.optString("error")) {
                "authorization_pending" -> continue
                "slow_down" -> { session.interval = (session.interval + 5).coerceAtMost(30); continue }
                "access_denied" -> throw IllegalStateException("Connexion refusée sur Google")
                "expired_token" -> throw IllegalStateException("Code expiré ; relancez la connexion")
                "" -> {
                    coroutineContext.ensureActive()
                    val access = response.getString("access_token")
                    val identity = request("https://openidconnect.googleapis.com/v1/userinfo", "GET", access)
                    require(identity.optString("sub").isNotBlank()) { "Identité Google invalide" }
                    val profileName = synchronized(this) {
                        require(pending === session && generation == session.generation) { "Connexion annulée" }
                        val token = JSONObject().put("access_token", access).put("refresh_token", response.optString("refresh_token"))
                            .put("expiry", System.currentTimeMillis() + response.optInt("expires_in", 3600).coerceIn(60, 86400) * 1000L)
                        require(io.github.marouane4142.maros.tv.Profiles(c).current == session.profile) { "Profil changé ; relancez la connexion" }
                        val linked = UserAccounts.link(c, identity, token, session.addUser)
                        pending = null
                        linked
                    }
                    UserAccounts.refreshAvatar(c, profileName, identity)
                    LauncherPrefs(c).changed(); return "Connexion Google établie. Les sauvegardes Drive sont disponibles sur demande."
                }
                else -> throw IllegalStateException("Google a refusé la connexion (${response.optString("error")}). Vérifiez le type du client TV, le consentement et les API activées.")
            }
        }
        throw IllegalStateException("Code expiré ; relancez la connexion")
    }
    @Synchronized private fun access(c: Context): String {
        require(!RoomData.guest(c)) { "Compte masqué en mode invités" }
        val key = UserAccounts.credentialKey(c)
        val saved = runCatching { JSONObject(secrets(c).get(key)) }.getOrDefault(JSONObject())
        if (saved.optLong("expiry") > System.currentTimeMillis() + 60000 && saved.optString("access_token").isNotBlank()) return saved.getString("access_token")
        require(saved.optString("refresh_token").isNotBlank()) { "Reconnectez votre compte Google" }
        val response = form("https://oauth2.googleapis.com/token", mapOf("client_id" to secrets(c).get("google_client_id"), "client_secret" to secrets(c).get("google_client_secret"), "refresh_token" to saved.getString("refresh_token"), "grant_type" to "refresh_token"))
        val access = response.getString("access_token"); saved.put("access_token", access).put("expiry", System.currentTimeMillis() + response.optInt("expires_in", 3600).coerceIn(60, 86400) * 1000L)
        require(key == UserAccounts.credentialKey(c)) { "Profil changé ; relancez l’action" }
        secrets(c).put(key, saved.toString()); return access
    }
    @Synchronized fun disconnect(c: Context): String {
        val saved = tokens(c); val token = saved.optString("refresh_token").ifBlank { saved.optString("access_token") }
        cancelPending(); secrets(c).put(UserAccounts.credentialKey(c), ""); UserAccounts.clear(c)
        return if (token.isBlank()) "MarOS déconnecté de Google" else runCatching {
            form("https://oauth2.googleapis.com/revoke", mapOf("token" to token)); "MarOS déconnecté et autorisation Google révoquée"
        }.getOrElse { "MarOS déconnecté localement. Vérifiez la révocation dans votre compte Google lorsque le réseau est disponible." }
    }
    fun listBackups(c: Context): JSONArray {
        val query = "trashed = false and appProperties has { key='maros_backup' and value='v1' }"
        val owner=UserAccounts.credentialKey(c)
        val files=request("https://www.googleapis.com/drive/v3/files?q=${encode(query)}&pageSize=20&orderBy=createdTime%20desc&fields=files(id,name,size,createdTime)", "GET", access(c)).optJSONArray("files") ?: JSONArray()
        require(owner==UserAccounts.credentialKey(c)) { "Profil changé ; relancez la liste" };return files
    }
    fun uploadBackup(c: Context): String {
        val owner = UserAccounts.credentialKey(c)
        val token = access(c); require(owner == UserAccounts.credentialKey(c))
        val file = PersonalBackup.create(c)
        try {
            require(owner == UserAccounts.credentialKey(c)) { "Profil changé ; relancez la sauvegarde" }
            val name = "MarOS-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())}.zip"
            val metadata = JSONObject().put("name", name).put("mimeType", "application/zip").put("appProperties", JSONObject().put("maros_backup", "v1")).toString().toByteArray(Charsets.UTF_8)
            val begin = connection("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name", "POST", token)
            val location = try {
                begin.doOutput = true; begin.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                begin.setRequestProperty("X-Upload-Content-Type", "application/zip"); begin.setRequestProperty("X-Upload-Content-Length", file.length().toString())
                begin.outputStream.use { it.write(metadata) }
                require(begin.responseCode in 200..299) { "Création de l’envoi Drive refusée (HTTP ${begin.responseCode}). Vérifiez l’API Drive et l’autorisation du compte." }
                requireNotNull(begin.getHeaderField("Location")) { "Session Drive manquante" }
            } finally { begin.disconnect() }
            val address = java.net.URI(location)
            require(address.scheme == "https" && address.host == "www.googleapis.com" && address.path.startsWith("/upload/drive/v3/")) { "Adresse d’envoi Drive invalide" }
            val conn = connection(location, "PUT", token)
            try {
                conn.readTimeout = 30000; conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/zip")
                conn.setFixedLengthStreamingMode(file.length())
                conn.outputStream.use { out -> FileInputStream(file).use { it.copyTo(out, 32768) } }
                require(conn.responseCode in 200..299) { "Envoi Drive interrompu (HTTP ${conn.responseCode}) ; relancez la sauvegarde." }
                return "Sauvegarde envoyée dans votre Drive : $name"
            } finally { conn.disconnect() }
        } finally { file.delete() }
    }
    fun downloadBackup(c: Context, id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{5,200}")))
        val owner=UserAccounts.credentialKey(c)
        val allowed = listBackups(c); require((0 until allowed.length()).any { allowed.getJSONObject(it).getString("id") == id }) { "Sauvegarde Drive inconnue" }
        val conn = connection("https://www.googleapis.com/drive/v3/files/$id?alt=media", "GET", access(c)); val file = File.createTempFile("google-backup-", ".zip", c.cacheDir)
        try {
            require(conn.responseCode == 200) { "Téléchargement Drive indisponible" }
            conn.inputStream.use { input -> FileOutputStream(file).use { out -> val buffer = ByteArray(32768); var size = 0L; while(true) { val n = input.read(buffer); if(n < 0)break; size += n; require(size <= PersonalBackup.MAX_ARCHIVE) { "Sauvegarde trop volumineuse" }; out.write(buffer,0,n) } } }
            require(owner==UserAccounts.credentialKey(c)) { "Profil changé ; relancez le téléchargement" }
            return file
        } catch(e: Exception) { file.delete(); throw e } finally { conn.disconnect() }
    }
    private fun connection(url: String, method: String, token: String? = null) = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method; connectTimeout = 8000; readTimeout = 10000; instanceFollowRedirects = false
        setRequestProperty("Accept", "application/json"); if (token != null) setRequestProperty("Authorization", "Bearer $token")
    }
    private fun request(url: String, method: String, token: String? = null, body: ByteArray? = null, includeError: Boolean = false): JSONObject {
        val conn = connection(url, method, token)
        try {
            if (body != null) { conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); conn.outputStream.use { it.write(body) } }
            val code = conn.responseCode; val input = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = input?.use { stream -> val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); while(true) { val n=stream.read(buffer);if(n<0)break;require(out.size()+n<=262144);out.write(buffer,0,n) };out.toString("UTF-8") }.orEmpty()
            val result = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (!includeError && code !in 200..299) throw IllegalStateException("Google : ${result.optString("error").takeIf { it.matches(Regex("[a-z_]{1,60}")) } ?: "HTTP $code"}. Vérifiez la configuration du projet et l’autorisation du compte.")
            return result
        } finally { conn.disconnect() }
    }
    private fun form(url: String, values: Map<String, String>) = request(url, "POST", body = encoded(values))
    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8")
    private fun encoded(values: Map<String, String>) = values.entries.joinToString("&") { encode(it.key) + "=" + encode(it.value) }.toByteArray(Charsets.UTF_8)
}
