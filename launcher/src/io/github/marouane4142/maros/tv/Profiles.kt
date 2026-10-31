package io.github.marouane4142.maros.tv

import android.content.Context
import io.github.marouane4142.maros.LauncherPrefs
import org.json.JSONObject

/** Profiles affect the launcher only. Integration credentials never enter profile snapshots. */
class Profiles(private val context: Context) {
    private val prefs = LauncherPrefs(context)
    private val store = context.getSharedPreferences("maros_profiles", Context.MODE_PRIVATE)
    val current: String get() = store.getString("active", "Maison") ?: "Maison"
    fun names(): List<String> = listOf("Maison") + records().keys().asSequence().filter { it != "Maison" }.sorted().toList()
    fun create(name: String) {
        val clean = name.trim()
        require(clean.length in 1..24 && clean.none { it.isISOControl() }) { "Choisissez un nom de 1 à 24 caractères." }
        require(names().size < 6 || clean in names()) { "Six profils maximum." }
        require(clean !in names()) { "Ce profil existe déjà." }
        val all = records().put(clean, JSONObject(prefs.exportConfiguration()))
        store.edit().putString("profiles", all.toString()).apply()
        switch(clean)
    }
    fun switch(name: String) {
        require(name in names())
        val all = records().put(current, JSONObject(prefs.exportConfiguration()))
        val target = all.optJSONObject(name)
        // Save first, then apply atomically through the existing validated importer.
        store.edit().putString("profiles", all.toString()).putString("active", name).apply()
        if (target != null) {
            val edit = prefs.prefs().edit()
            prefs.prefs().all.keys.filter { it.startsWith("category_") }.forEach(edit::remove)
            edit.apply()
            prefs.importConfiguration(target.toString())
        } else prefs.changed()
    }
    fun select(name: String) = switch(name)
    fun delete(name: String) {
        require(name != "Maison")
        if (current == name) switch("Maison")
        io.github.marouane4142.maros.UserAccounts.clearProfile(context, name)
        val all = records(); all.remove(name)
        store.edit().putString("profiles", all.toString()).apply()
        prefs.changed()
    }
    private fun records() = runCatching { JSONObject(store.getString("profiles", "{}")!!) }.getOrDefault(JSONObject())
}
