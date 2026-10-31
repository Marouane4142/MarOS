package io.github.marouane4142.maros

import android.content.Context
import android.graphics.BitmapFactory
import android.os.StatFs
import io.github.marouane4142.maros.tv.Profiles
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*

/** Explicit owner export. ZIP paths are a finite format; secrets and arbitrary app files never enter it. */
object PersonalBackup {
    const val MAX_ARCHIVE = 80 * 1024 * 1024
    private const val MAX_EXPANDED = 96L * 1024 * 1024
    @JvmStatic @Synchronized fun create(c: Context): File {
        require(!RoomData.guest(c)) { "Quittez le mode invités pour sauvegarder les données personnelles" }
        val output = File.createTempFile("maros-backup-", ".zip", c.cacheDir)
        try {
            val p = LauncherPrefs(c); val photos = PhotoStore(c); val photoEntries = JSONArray(); val bannerEntries = JSONArray()
            val scenes = JSONObject()
            for ((key, value) in p.prefs().all) if (key.matches(Regex("scene_(cinema|music|game|night)_(app|entity|wake)")) && (value is String || value is Boolean)) scenes.put(key, value)
            val profileData = JSONObject(c.getSharedPreferences("maros_profiles", 0).getString("profiles", "{}"))
            profileData.put(Profiles(c).current, JSONObject(p.exportConfiguration()))
            ZipOutputStream(FileOutputStream(output)).use { zip ->
                zip.setLevel(0)
                var index = 0
                for (photo in photos.all().filter { !it.builtin }) {
                    val path = "photos/${index++.toString().padStart(3, '0')}.jpg"
                    photos.openPortablePhoto(photo.id).use { add(zip, path, it) }
                    photoEntries.put(JSONObject().put("path", path).put("title", photo.title).put("favorite", photo.favorite).put("excluded", photo.excluded))
                }
                for (app in AppCatalog.query(c)) {
                    val pkg = app.activityInfo.packageName; val banner = photos.portableBanner(pkg)
                    if (banner?.isFile == true) { val path = "banners/$pkg.jpg"; FileInputStream(banner).use { add(zip, path, it) }; bannerEntries.put(JSONObject().put("path", path).put("package", pkg)) }
                }
                val manifest = JSONObject().put("format", "maros-personal-backup").put("version", 1).put("created", System.currentTimeMillis())
                    .put("config", JSONObject(p.exportConfiguration())).put("scenes", scenes).put("profiles", profileData)
                    .put("watchlist", Watchlist(c).items()).put("agenda", HubData(c).events()).put("history", ContentBridge(c).diagnostics())
                    .put("photos", photoEntries).put("banners", bannerEntries)
                add(zip, "manifest.json", ByteArrayInputStream(manifest.toString().toByteArray(Charsets.UTF_8)))
            }
            require(output.length() <= MAX_ARCHIVE) { "Archive trop volumineuse" }; return output
        } catch (e: Exception) { output.delete(); throw e }
    }
    private fun add(zip: ZipOutputStream, path: String, input: InputStream) { zip.putNextEntry(ZipEntry(path)); input.copyTo(zip, 32768); zip.closeEntry() }
    @JvmStatic @Synchronized fun restore(c: Context, archive: File, apply: Boolean): JSONObject {
        require(!RoomData.guest(c)) { "Quittez le mode invités avant une restauration" }
        require(archive.length() in 1..MAX_ARCHIVE.toLong()) { "Archive invalide" }
        val root = File(c.cacheDir, "restore-${UUID.randomUUID()}"); check(root.mkdirs())
        try {
            val files = mutableMapOf<String, File>(); var expanded = 0L
            ZipInputStream(FileInputStream(archive)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory && files.size < 256 && name !in files &&
                        (name == "manifest.json" || name.matches(Regex("photos/[0-9]{3}\\.jpg")) || name.matches(Regex("banners/[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+\\.jpg")))) { "Contenu d’archive non autorisé" }
                    val target = File(root, name.replace('/', '_')); var size = 0L
                    FileOutputStream(target).use { out ->
                        val buffer = ByteArray(32768)
                        while (true) { val count = zip.read(buffer); if (count < 0) break; size += count; expanded += count
                            require(size <= (if (name == "manifest.json") 2 * 1024 * 1024 else 8 * 1024 * 1024) && expanded <= MAX_EXPANDED) { "Archive décompressée trop volumineuse" }; out.write(buffer, 0, count) }
                    }
                    files[name] = target; zip.closeEntry()
                }
            }
            val manifest = JSONObject(requireNotNull(files["manifest.json"]) { "Manifeste absent" }.readText())
            require(manifest.optString("format") == "maros-personal-backup" && manifest.optInt("version") == 1) { "Format de sauvegarde inconnu" }
            val config = manifest.getJSONObject("config")
            LauncherPrefs.validatePortable(config.toString())
            require(config.optString("format") == "tvhome-settings" && config.optInt("version") == 2 && config.getJSONObject("settings").toString().length <= 262144)
            val photos = manifest.getJSONArray("photos"); val banners = manifest.getJSONArray("banners")
            val watchlist = manifest.getJSONArray("watchlist"); val agenda = manifest.getJSONArray("agenda")
            require(photos.length() <= 100 && banners.length() <= 150 && watchlist.length() <= 100 && agenda.length() <= 150)
            val expected = mutableSetOf("manifest.json")
            for (i in 0 until photos.length()) {
                val photo = photos.getJSONObject(i); val path = photo.getString("path"); require(path.matches(Regex("photos/[0-9]{3}\\.jpg")) && expected.add(path)); require(photo.optString("title").length <= 200)
            }
            for (i in 0 until banners.length()) { val item = banners.getJSONObject(i); require(LauncherPrefs.validPackage(item.getString("package"))); require(item.getString("path") == "banners/${item.getString("package")}.jpg" && expected.add(item.getString("path"))) }
            require(files.keys == expected) { "Fichiers manquants ou supplémentaires" }
            for ((name, file) in files) if (name != "manifest.json") {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, options)
                require(options.outWidth in 32..1920 && options.outHeight in 32..1920) { "Image incompatible avec une sauvegarde MarOS" }
            }
            for (i in 0 until watchlist.length()) { val item = watchlist.getJSONObject(i); require(item.optString("title").length in 1..120); require(item.optString("package").isEmpty() || LauncherPrefs.validPackage(item.optString("package"))) }
            for (i in 0 until agenda.length()) { val item = agenda.getJSONObject(i); require(item.optString("title").length in 1..300 && item.optLong("time") in 1..7258118400000L) }
            val history = manifest.optJSONObject("history")?.optJSONArray("items") ?: JSONArray(); require(history.length() <= 64)
            val profileData = manifest.optJSONObject("profiles") ?: JSONObject(); require(profileData.length() <= 6)
            for (name in profileData.keys()) { require(name.length in 1..24 && name.none { it.isISOControl() }); LauncherPrefs.validatePortable(profileData.getJSONObject(name).toString()) }
            val result = JSONObject().put("photos", photos.length()).put("banners", banners.length()).put("titles", watchlist.length()).put("events", agenda.length()).put("applied", false)
            require(StatFs(c.filesDir.path).availableBytes > expanded + 64L * 1024 * 1024) { "Espace insuffisant pour restaurer en conservant les données actuelles" }
            // Preflight the album quota before creating the rollback and applying changes.
            val photoStore = PhotoStore(c); val existingPhotos = photoStore.all().filter { !it.builtin }
            val hashes = mutableSetOf<String>(); var existingBytes = 0L
            for (photo in existingPhotos) photoStore.openPortablePhoto(photo.id).use { input ->
                val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(32768)
                while(true) { val n=input.read(buffer); if(n<0)break; existingBytes+=n; digest.update(buffer,0,n) }
                hashes.add(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
            }
            val incomingHashes = mutableSetOf<String>(); var incomingBytes = 0L
            for (i in 0 until photos.length()) {
                val file=files.getValue(photos.getJSONObject(i).getString("path")); val digest=FileInputStream(file).use { hash(it) }
                if(digest !in hashes && incomingHashes.add(digest)) incomingBytes+=file.length()
            }
            require(existingPhotos.size + incomingHashes.size <= 100 && existingBytes + incomingBytes <= 64L*1024*1024) { "La fusion dépasserait la limite de 100 photos ou 64 Mo. Libérez de la place dans l’album avant de restaurer." }
            if (!apply) return result
            // Keep a private automatic copy of the configuration before changes.
            val previous = create(c); val rollback = File(c.filesDir, "last-restore-backup.zip")
            check(previous.copyTo(rollback, true).isFile); previous.delete()
            var imported = 0
            for (i in 0 until photos.length()) {
                val item = photos.getJSONObject(i); val file = files.getValue(item.getString("path")); if (!hashes.add(FileInputStream(file).use { hash(it) })) continue
                val photo = FileInputStream(file).use { photoStore.importImage(it, item.optString("title", "Photo restaurée")) }
                photoStore.setFavorite(photo.id, item.optBoolean("favorite")); photoStore.setExcluded(photo.id, item.optBoolean("excluded")); imported++
            }
            for (i in 0 until banners.length()) { val item = banners.getJSONObject(i); if (RoomData.installed(c, item.getString("package"))) FileInputStream(files.getValue(item.getString("path"))).use { photoStore.importBanner(item.getString("package"), it) } }
            val list = Watchlist(c); val existing = list.items(); val allTitles = JSONArray()
            val seen = mutableSetOf<String>()
            for (array in listOf(existing, watchlist)) for (i in 0 until array.length()) { val item = array.getJSONObject(i); if (seen.add(item.optString("title").lowercase()) && allTitles.length() < 100) allTitles.put(item) }
            list.importItems(allTitles)
            val combined = JSONArray(); val ids = mutableSetOf<String>()
            for (array in listOf(HubData(c).events(), agenda)) for (i in 0 until array.length()) { val event = array.getJSONObject(i); if (ids.add(event.optString("title") + event.optLong("time")) && combined.length() < 150) combined.put(event) }
            HubData(c).saveArray("agenda_events", combined); PlaybackHistory.importObservations(c, history)
            val p = LauncherPrefs(c); p.importConfiguration(config.toString())
            val scenes = manifest.optJSONObject("scenes") ?: JSONObject(); val edit = p.prefs().edit()
            for (key in scenes.keys()) if (key.matches(Regex("scene_(cinema|music|game|night)_(app|entity|wake)"))) when(val value = scenes.get(key)) { is String -> if (value.length < 200) edit.putString(key, value); is Boolean -> edit.putBoolean(key, value) }
            edit.apply()
            val profiles = c.getSharedPreferences("maros_profiles", 0); val current = JSONObject(profiles.getString("profiles", "{}"))
            val allowed = JSONObject(p.exportConfiguration()).getJSONObject("settings")
            for (name in profileData.keys()) if (!current.has(name) && current.length() < 6) {
                val settings = profileData.optJSONObject(name)?.optJSONObject("settings") ?: continue; val clean = JSONObject()
                for (key in settings.keys()) if ((allowed.has(key) || key.startsWith("category_") && LauncherPrefs.validPackage(key.removePrefix("category_"))) && settings.get(key).toString().length < 10000) clean.put(key, settings.get(key))
                current.put(name, JSONObject().put("format", "tvhome-settings").put("version", 2).put("settings", clean))
            }
            profiles.edit().putString("profiles", current.toString()).apply(); p.changed()
            return result.put("applied", true).put("photos_imported", imported).put("message", "Données fusionnées. Les éléments existants et les identifiants de services ont été conservés.")
        } finally { if (root.canonicalPath.startsWith(c.cacheDir.canonicalPath + File.separator)) root.deleteRecursively() }
    }
    private fun hash(input: InputStream): String { val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(32768); while(true) { val n = input.read(buffer); if(n < 0) break; digest.update(buffer, 0, n) }; return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } }
}
