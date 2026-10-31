package io.github.marouane4142.maros.tv

import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.marouane4142.maros.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray

data class AppEntry(val pkg: String, val label: String, val activity: String, val category: String)
data class HomeState(
    val apps: List<AppEntry> = emptyList(), val dock: List<String> = emptyList(),
    val history: List<ContentBridge.Item> = emptyList(),
    val rows: List<String> = HomeLayout.rows, val hiddenRows: Set<String> = emptySet(),
    val userName: String = "Maison", val revision: Int = 0, val imageRevision: Int = 0, val profile: String = "Maison", val weather: String = "",
    val network: String = "offline", val available: Long = 0,
    val title: String = "", val theme: String = "nature", val accent: String = "ice",
    val contrast: Int = 45, val tileSize: String = "normal", val event: String = "",
    val uiScale: Int = 85, val textScale: Int = 100, val reduced: Boolean = false, val now: Long = System.currentTimeMillis(),
    val guest: Boolean = false, val library: List<HubData.Item> = emptyList()
)

/** UI state survives HOME returns. Catalog discovery occurs only on package/config changes. */
@OptIn(FlowPreview::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    val context: Context = application
    val prefs = LauncherPrefs(context)
    private val photos = PhotoStore(context)
    private val hub = HubData(context)
    private val mutable = MutableStateFlow(HomeState())
    val state = mutable.asStateFlow()
    val homeRequests = MutableStateFlow(0)
    private val dirty = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    private val artwork = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val imageDispatcher = Dispatchers.IO.limitedParallelism(2)
    private var catalog: List<AppEntry> = emptyList()
    private var catalogDirty = true
    private var foreground = true
    private var wallpaperCache: Pair<String, Pair<PhotoStore.Photo, Bitmap>>? = null
    private var reload: Job? = null
    private val rememberedFocus = mutableMapOf<String, String>()
    var focusId = if (prefs.getBool("remember_navigation", true)) prefs.getString("compose_focus", "nav:accueil") else "nav:accueil"
    var tab = MutableStateFlow(if (prefs.getBool("remember_navigation", true)) prefs.getString("compose_tab", "accueil").takeIf { it in setOf("accueil", "regarder", "musique", "jeux", "outils", "apps") } ?: "accueil" else "accueil")
        private set
    private val changed = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "image_revision") artwork.evictAll()
        if (key == "ui_revision") dirty.tryEmit(false)
    }
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) { artwork.evictAll(); dirty.tryEmit(true) }
    }
    init {
        prefs.prefs().registerOnSharedPreferenceChangeListener(changed)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(packages, filter, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(packages, filter)
        viewModelScope.launch { dirty.debounce(120).collect { if (it) catalogDirty = true; refresh(it) } }
        refresh(true)
    }
    fun selectTab(value: String) {
        if (tab.value == value) return
        rememberedFocus[tab.value] = focusId
        focusId = rememberedFocus[value] ?: if (value == "reprendre") "nav:reprendre" else if (value == "accueil") "favorites:${state.value.dock.firstOrNull().orEmpty()}"
            else "grid:${state.value.apps.firstOrNull { value == "apps" || it.category == value }?.pkg.orEmpty()}"
        tab.value = value
        if (prefs.getBool("remember_navigation", true)) prefs.prefs().edit().putString("compose_tab", value).apply()
    }
    fun focused(value: String) { focusId = value }
    fun returnHome() {
        selectTab("accueil")
        focusId = "nav:accueil"
        homeRequests.value++
    }
    fun persistFocus() { if (prefs.getBool("remember_navigation", true)) prefs.prefs().edit().putString("compose_focus", focusId).apply() }
    fun visible(value: Boolean) {
        foreground = value
        if (!value) reload?.cancel()
    }
    fun refresh(apps: Boolean = false) {
        if (!foreground) { if (apps) catalogDirty = true; return }
        reload?.cancel()
        // Apply appearance immediately; provider/Binder reads must not delay a theme or text-size change.
        mutable.value = appearance(mutable.value)
        reload = viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                if (apps || catalogDirty || catalog.isEmpty()) {
                    catalog = AppCatalog.query(context).map {
                        AppEntry(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString(),
                            it.activityInfo.name, prefs.category(it.activityInfo.packageName))
                    }
                    catalogDirty = false
                }
                val hidden = prefs.hidden()
                appearance(HomeState(
                    apps = catalog.filterNot { it.pkg in hidden }.map { it.copy(category = prefs.category(it.pkg)) },
                    history = if (RoomData.guest(context)) emptyList() else ContentBridge(context).load(),
                    network = network(), available = StatFs(context.filesDir.path).availableBytes,
                    event = if (prefs.getBool("show_calendar", true)) CalendarAccess.summary(context) else "",
                    library = if (RoomData.guest(context)) emptyList() else hub.libraryItems()
                ))
            }
            if (focusId.isEmpty()) {
                val first = data.dock.firstOrNull { pkg -> data.apps.any { it.pkg == pkg } }
                if (first != null) focusId = "favorites:$first"
            }
            mutable.value = data
        }
    }
    private fun appearance(base: HomeState): HomeState {
        val guest = RoomData.guest(context)
        val order = runCatching { JSONArray(prefs.getString("home_rows", "[]")) }.getOrDefault(JSONArray())
        val hiddenRows = prefs.prefs().getStringSet("hidden_rows", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!prefs.getBool("show_continue", true)) hiddenRows.add("continue")
        if (!prefs.getBool("show_info", true)) hiddenRows.add("shortcuts")
        return base.copy(dock = prefs.dock(), rows = HomeLayout.normalize((0 until order.length()).map { order.optString(it) }), hiddenRows = hiddenRows,
            revision = prefs.getInt("ui_revision", 0), imageRevision = prefs.getInt("image_revision", 0),
            profile = if (guest) "Invités" else Profiles(context).current, guest = guest, userName = if(guest) "Invité" else UserAccounts.label(context),
            weather = if (!guest && prefs.getBool("show_weather", true)) hub.weatherSummary() else "",
            title = if (guest) "Bienvenue chez vous." else prefs.getString("home_title", ""), theme = prefs.theme(), accent = prefs.getString("accent", "ice"),
            contrast = prefs.getInt("contrast", 45), tileSize = prefs.getString("tile_size", "normal"),
            uiScale = prefs.getInt("ui_scale", 85), textScale = prefs.getInt("text_scale", 100), reduced = prefs.getBool("reduced_animations", false),
            history = if (guest) emptyList() else base.history, library = if (guest) emptyList() else base.library,
            event = if (guest || !prefs.getBool("show_calendar", true)) "" else base.event)
    }
    fun refreshServices() { hub.refresh { refresh() } }
    fun clock() { mutable.update { it.copy(now = System.currentTimeMillis()) } }
    fun open(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(app.pkg, app.activity))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent); ContentBridge.recordLaunch(context, app.pkg) }
            .onFailure { toast("Cette application est indisponible.") }
    }
    suspend fun art(app: AppEntry, revision: Int): Bitmap? = withContext(imageDispatcher) {
        val key = "${app.pkg}:480:$revision"
        artwork.get(key)?.let { return@withContext it }
        ensureActive()
        val bitmap = (if (RoomData.guest(context)) null else photos.getBanner(app.pkg, 480, 270)) ?: runCatching {
            val pm = context.packageManager
            val banner = pm.getActivityBanner(ComponentName(app.pkg, app.activity)) ?: pm.getApplicationBanner(app.pkg)
            val d = banner ?: pm.getApplicationIcon(app.pkg)
            Bitmap.createBitmap(480, 270, Bitmap.Config.RGB_565).apply {
                val canvas = Canvas(this); canvas.drawColor(0xFF17243A.toInt())
                if (banner != null) d.setBounds(0, 0, 480, 270) else d.setBounds(145, 40, 335, 230)
                d.draw(canvas)
            }
        }.getOrNull()
        ensureActive()
        if (bitmap != null) artwork.put(key, bitmap)
        bitmap
    }
    suspend fun wallpaper(step: Int = 0): Pair<PhotoStore.Photo, Bitmap>? = withContext(imageDispatcher) {
        val album = photos.list()
        if (album.isEmpty()) return@withContext null
        val last = prefs.getString("last_photo_id", "")
        val requested = prefs.getString("next_photo_id", "")
        val expired = last.isNotBlank() && System.currentTimeMillis() - prefs.prefs().getLong("last_photo_time", 0) >= prefs.getInt("wallpaper_minutes", 10).coerceIn(1, 1440) * 60000L
        var at = album.indexOfFirst { it.id == last }.coerceAtLeast(0)
        if (requested.isNotEmpty()) {
            at = album.indexOfFirst { it.id == requested }.takeIf { it >= 0 } ?: ((at + 1) % album.size)
            prefs.prefs().edit().remove("next_photo_id").apply()
        } else at = (at + if (step > 0 || expired) 1 else 0) % album.size
        val photo = album[at]
        val key = "${photo.id}:${prefs.getInt("image_revision", 0)}"
        if (step > 0 || expired || requested.isNotEmpty()) prefs.prefs().edit().putLong("last_photo_time", System.currentTimeMillis()).apply()
        wallpaperCache?.takeIf { it.first == key && !it.second.second.isRecycled }?.let { return@withContext it.second }
        val bitmap = photos.load(photo, 1920, 1080) ?: return@withContext null
        ensureActive()
        prefs.prefs().edit().putString("last_photo_id", photo.id).putLong("last_photo_time", System.currentTimeMillis()).apply()
        (photo to bitmap).also { wallpaperCache = key to it }
    }
    fun trim() { artwork.evictAll() }
    private fun network(): String {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "offline"
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return "offline"
        return if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) "ethernet" else "wifi"
    }
    private fun toast(message: String) { android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show() }
    override fun onCleared() {
        context.unregisterReceiver(packages)
        prefs.prefs().unregisterOnSharedPreferenceChangeListener(changed)
        artwork.evictAll()
    }
}
