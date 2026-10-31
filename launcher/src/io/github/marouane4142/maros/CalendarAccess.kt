package io.github.marouane4142.maros

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/** Uses visible, synchronized Google calendars matching the authenticated profile. */
object CalendarAccess {
    fun granted(c: Context) = c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun calendars(c: Context): JSONArray {
        val out = JSONArray()
        if (RoomData.guest(c) || !granted(c)) return out
        val email = UserAccounts.email(c)
        if (email.isBlank()) return out
        try { c.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
            arrayOf("_id", "calendar_displayName", "visible", "account_name", "account_type", "sync_events"),
            "account_type = ? AND account_name = ? COLLATE NOCASE", arrayOf(DeviceAccounts.GOOGLE, email), "calendar_displayName ASC")?.use { rows ->
            while (rows.moveToNext() && out.length() < 64) out.put(JSONObject().put("id", rows.getLong(0).toString()).put("name", rows.getString(1) ?: "Agenda").put("visible", rows.getInt(2) != 0).put("account", rows.getString(3)).put("type", rows.getString(4)).put("sync", rows.getInt(5) != 0))
        } } catch (_: RuntimeException) { /* Optional provider can be missing on a TV firmware. */ }
        return out
    }
    fun selected(c: Context): Set<String> {
        val email = UserAccounts.email(c)
        if (email.isBlank()) return emptySet()
        val calendars = calendars(c)
        return (0 until calendars.length()).map { calendars.getJSONObject(it) }.filter {
            io.github.marouane4142.maros.tv.AccountPolicy.includesCalendar(email,it.optString("account"),it.optString("type"),it.optBoolean("visible"),it.optBoolean("sync"))
        }.map { it.getString("id") }.toSet()
    }
    @Deprecated("Google calendars follow the connected profile automatically")
    fun select(c: Context, id: String, enabled: Boolean) = Unit
    fun events(c: Context): JSONArray {
        val out = JSONArray()
        if (LauncherPrefs(c).getBool("guest_mode", false)) return out
        val allowed = selected(c)
        if (!granted(c) || allowed.isEmpty()) return out
        val start = System.currentTimeMillis() - 86400000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start); ContentUris.appendId(it, start + 8 * 86400000L)
        }.build()
        try {
            c.contentResolver.query(uri, arrayOf("event_id", "title", "begin", "end", "allDay", "calendar_id"), null, null, "begin ASC")?.use { rows ->
                var count = 0
                while (rows.moveToNext() && count++ < 2000 && out.length() < 150) {
                    if (rows.getLong(3) < System.currentTimeMillis() || rows.getLong(5).toString() !in allowed) continue
                    out.put(JSONObject().put("id", "android:${rows.getLong(0)}:${rows.getLong(2)}")
                        .put("title", rows.getString(1) ?: "Rendez-vous").put("time", rows.getLong(2))
                        .put("end", rows.getLong(3)).put("all_day", rows.getInt(4) != 0).put("source", "android"))
                }
            }
        } catch (_: RuntimeException) { /* Provider disabled or access revoked while reading. */ }
        return sorted(out)
    }
    private fun sorted(array: JSONArray): JSONArray = JSONArray((0 until array.length()).mapNotNull { array.optJSONObject(it) }.sortedBy { it.optLong("time") })
    fun summary(c: Context): String {
        if (LauncherPrefs(c).getBool("guest_mode", false)) return ""
        val events = events(c)
        return (0 until events.length()).map { events.getJSONObject(it) }.firstOrNull { it.optLong("end", it.optLong("time")) >= System.currentTimeMillis() }
            ?.let { it.optString("title") + " · " + SimpleDateFormat("EEE d MMM HH:mm", Locale.FRANCE).format(Date(it.optLong("time"))) }.orEmpty()
    }
}
