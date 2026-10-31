package io.github.marouane4142.maros

import android.content.*
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.github.marouane4142.maros.tv.Profiles
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Deliberately small, owner-paired command surface. No shell or arbitrary key injection. */
object RoomData {
    @Volatile var launcherVisible = false
    const val ACTION_CONTROL = "io.github.marouane4142.maros.ROOM_CONTROL"
    @JvmStatic fun guest(c: Context) = LauncherPrefs(c).getBool("guest_mode", false)
    @JvmStatic fun setGuest(c: Context, enabled: Boolean) { val p=LauncherPrefs(c); p.prefs().edit().putBoolean("guest_mode", enabled).putInt("image_revision", p.getInt("image_revision",0)+1).apply(); p.changed() }
    @JvmStatic fun installed(c: Context, pkg: String) = runCatching { c.packageManager.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false)
    @JvmStatic fun status(c: Context): JSONObject {
        val audio = c.getSystemService(AudioManager::class.java)
        val media = MediaBridge(c).sessions().firstOrNull()
        val actions = media?.controller?.playbackState?.actions ?: 0
        return JSONObject().put("guest", guest(c)).put("profile", if (guest(c)) "Invités" else UserAccounts.label(c))
            .put("volume", audio.getStreamVolume(AudioManager.STREAM_MUSIC)).put("volume_max", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            .put("title", media?.title.orEmpty()).put("artist", media?.subtitle.orEmpty()).put("playing", media?.playing ?: false)
            .put("can_seek", actions and android.media.session.PlaybackState.ACTION_SEEK_TO != 0L)
            .put("media_available", media != null).put("can_next", actions and android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
            .put("can_previous", actions and android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L)
            .put("navigation_available", launcherVisible).put("cast_installed", installed(c, "com.google.android.apps.mediashell"))
            .put("device_name", Settings.Global.getString(c.contentResolver, "device_name") ?: Build.MODEL)
    }
    @JvmStatic fun control(c: Context, command: JSONObject): JSONObject {
        val action = command.getString("action")
        val media = MediaBridge(c).sessions().firstOrNull()
        val bridge = MediaBridge(c)
        val audio = c.getSystemService(AudioManager::class.java)
        when (action) {
            "up", "down", "left", "right", "ok", "back" -> { require(launcherVisible) { "Ouvrez l’accueil MarOS avant de naviguer" }; c.sendBroadcast(Intent(ACTION_CONTROL).setPackage(c.packageName).putExtra("command", action)) }
            "home" -> c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("room_home", true))
            "media_play_pause" -> { require(media != null) { "Aucun lecteur compatible actif" }; bridge.playPause(media) }
            "media_next" -> { require(media != null) { "Aucun lecteur compatible actif" }; bridge.next(media) }
            "media_previous" -> { require(media != null) { "Aucun lecteur compatible actif" }; bridge.previous(media) }
            "seek_back", "seek_forward" -> {
                require(media != null && media.duration > 0) { "Position indisponible" }
                val playback = media.controller.playbackState
                require(playback != null && playback.actions and android.media.session.PlaybackState.ACTION_SEEK_TO != 0L) { "Ce lecteur ne permet pas l’avance rapide" }
                media.controller.transportControls.seekTo((playback.position + if (action == "seek_forward") 10000 else -10000).coerceIn(0, media.duration))
            }
            "volume_up", "volume_down" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (action == "volume_up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "guest_on" -> setGuest(c, true)
            "guest_off" -> setGuest(c, false)
            "timer" -> { val minutes = command.getInt("minutes"); require(minutes in 1..1440); HubActivity.scheduleTimer(c, minutes) }
            "timer_cancel" -> HubActivity.cancelTimer(c)
            "app" -> {
                val pkg = command.getString("package")
                require(AppCatalog.query(c).any { it.activityInfo.packageName == pkg } && pkg !in LauncherPrefs(c).hidden()) { "Application absente ou masquée" }
                require(bridge.open(pkg)) { "Impossible d’ouvrir l’application" }
            }
            else -> throw IllegalArgumentException("Commande inconnue")
        }
        return status(c).put("message", "Commande transmise")
    }
    fun runScene(c: Context, id: String): String {
        require(id in setOf("cinema", "music", "game", "night"))
        val p = LauncherPrefs(c)
        if (id == "game" && p.getBool("scene_game_wake", false)) return ConnectedFeatures.wakeAndWait(c)
        val theme = when (id) { "cinema" -> "cinema"; "music" -> "aurora"; "night" -> "minimal"; else -> "nature" }
        p.prefs().edit().putString("theme", theme).apply(); p.changed()
        val results = mutableListOf("Ambiance appliquée")
        val entity = p.getString("scene_${id}_entity", if (id == "cinema") p.getString("cinema_entity", "") else "")
        if (entity.isNotBlank()) {
            results += runCatching { require(entity.matches(Regex("scene\\.[A-Za-z0-9_]+"))); HubData(c).homeAction("scene", "turn_on", entity); "Scène Home Assistant déclenchée" }
                .getOrElse { "Home Assistant : ${it.message ?: "injoignable"}" }
        }
        if (id == "night") c.startActivity(Intent(c, AmbientActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else {
            val pkg = p.getString("scene_${id}_app", when (id) { "cinema" -> "com.netflix.ninja"; "music" -> "com.spotify.tv.android"; else -> "com.limelight" })
            results += if (MediaBridge(c).open(pkg)) "Application ouverte" else "Application à choisir dans la configuration de la scène"
        }
        return results.joinToString(" · ")
    }
    fun testCast(): String {
        val conn = URL("http://127.0.0.1:8008/ssdp/device-desc.xml").openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 2500; conn.readTimeout = 2500; conn.instanceFollowRedirects = false
            require(conn.responseCode == 200) { "Récepteur non joignable" }
            "Le récepteur Google Cast répond. Depuis le téléphone, utilisez l’icône Cast d’une application compatible sur le même réseau."
        } finally { conn.disconnect() }
    }
}
