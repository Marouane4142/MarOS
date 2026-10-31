package io.github.marouane4142.maros

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Personal list is distinct from actual playback observations. TVmaze lookup is owner-initiated. */
class Watchlist(private val c: Context) {
    private val store = c.getSharedPreferences("maros_watchlist", 0)
    fun items(): JSONArray = if (RoomData.guest(c)) JSONArray() else runCatching { JSONArray(store.getString("items", "[]")) }.getOrDefault(JSONArray())
    private fun save(array: JSONArray) { store.edit().putString("items", array.toString()).apply(); LauncherPrefs(c).changed() }
    fun add(title: String, pkg: String = "", showId: Int = 0) {
        require(!RoomData.guest(c)) { "Liste masquée en mode invités" }
        require(title.trim().length in 1..120 && (pkg.isBlank() || LauncherPrefs.validPackage(pkg))) { "Titre ou application invalide" }
        val all = items(); require(all.length() < 100) { "Cent titres maximum" }
        if ((0 until all.length()).any { all.getJSONObject(it).optString("title").equals(title.trim(), true) }) return
        all.put(JSONObject().put("id", UUID.randomUUID().toString()).put("title", title.trim()).put("package", pkg)
            .put("season", 1).put("episode", 1).put("show_id", showId)); save(all)
    }
    fun change(id: String, action: String) {
        require(!RoomData.guest(c)); val all = items(); val result = JSONArray()
        for (i in 0 until all.length()) {
            val item = all.getJSONObject(i)
            if (item.optString("id") == id) {
                if (action == "remove") continue
                when (action) { "next" -> item.put("episode", (item.optInt("episode", 1) + 1).coerceAtMost(999)); "season" -> item.put("season", (item.optInt("season", 1) + 1).coerceAtMost(999)).put("episode", 1); else -> throw IllegalArgumentException("Action inconnue") }
            }
            result.put(item)
        }
        save(result)
    }
    fun search(query: String): JSONArray {
        require(!RoomData.guest(c))
        require(query.trim().length in 2..100 && !RoomData.guest(c))
        val results = JSONArray(HubData.request("https://api.tvmaze.com/search/shows?q=" + Uri.encode(query.trim()), "GET", null, null))
        val out = JSONArray()
        for (i in 0 until minOf(12, results.length())) {
            val show = results.getJSONObject(i).getJSONObject("show")
            out.put(JSONObject().put("id", show.getInt("id")).put("title", show.optString("name"))
                .put("summary", plain(show.optString("summary"))).put("image", show.optJSONObject("image")?.optString("medium").orEmpty())
                .put("premiered", show.optString("premiered")))
        }
        return out
    }
    fun setService(id: String, pkg: String) {
        require(LauncherPrefs.validPackage(pkg) && !RoomData.guest(c))
        val all = items(); for (i in 0 until all.length()) all.getJSONObject(i).takeIf { it.optString("id") == id }?.put("package", pkg)
        save(all)
    }
    fun refresh(): String {
        require(!RoomData.guest(c)); val all = items(); var count = 0
        for (i in 0 until all.length()) {
            val item = all.getJSONObject(i); val id = item.optInt("show_id")
            if (id <= 0 || count >= 20 || System.currentTimeMillis() - item.optLong("updated") < 6 * 3600000) continue
            val show = JSONObject(HubData.request("https://api.tvmaze.com/shows/$id?embed=nextepisode", "GET", null, null))
            val next = show.optJSONObject("_embedded")?.optJSONObject("nextepisode")
            item.put("summary", plain(show.optString("summary"))).put("image", show.optJSONObject("image")?.optString("medium").orEmpty())
                .put("next_title", next?.optString("name").orEmpty()).put("next_air", next?.optString("airdate").orEmpty())
                .put("next_time", next?.optString("airstamp")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0) } ?: 0)
                .put("updated", System.currentTimeMillis())
            count++; if (count < 20) Thread.sleep(550)
        }
        save(all); return "$count série(s) actualisée(s). Les dates viennent de TVmaze et peuvent varier selon le pays et le service."
    }
    fun importItems(raw: JSONArray) {
        require(raw.length() <= 100)
        val out = JSONArray()
        for (i in 0 until raw.length()) {
            val item = raw.getJSONObject(i); val title = item.getString("title").trim(); require(title.length in 1..120)
            val pkg = item.optString("package"); require(pkg.isEmpty() || LauncherPrefs.validPackage(pkg))
            val clean = JSONObject().put("id", item.optString("id").takeIf { it.matches(Regex("[a-fA-F0-9-]{36}")) } ?: UUID.randomUUID().toString()).put("title", title).put("package", pkg)
                .put("season", item.optInt("season", 1).coerceIn(1, 999)).put("episode", item.optInt("episode", 1).coerceIn(1, 999))
                .put("show_id", item.optInt("show_id").coerceAtLeast(0)).put("summary", plain(item.optString("summary")))
                .put("next_title", item.optString("next_title").take(200)).put("next_air", item.optString("next_air").takeIf { it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) }.orEmpty())
                .put("next_time", item.optLong("next_time").coerceIn(0, 7258118400000L)).put("updated", item.optLong("updated").coerceIn(0, System.currentTimeMillis()))
            item.optString("image").takeIf { it.startsWith("https://static.tvmaze.com/") && it.length < 2000 }?.let { clean.put("image", it) }
            out.put(clean)
        }
        save(out)
    }
    companion object { fun plain(text: String) = text.replace(Regex("<[^>]*>"), " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace(Regex("\\s+"), " ").trim().take(1000) }
}
