@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.marouane4142.maros.tv

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import androidx.tv.material3.*
import io.github.marouane4142.maros.*
import io.github.marouane4142.maros.R
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val tabs = linkedMapOf("accueil" to "Accueil", "regarder" to "Regarder", "musique" to "Musique",
    "jeux" to "Jeux", "outils" to "Outils", "apps" to "Applications")

@Composable
fun MarosHome(model: HomeViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val tab by model.tab.collectAsStateWithLifecycle()
    val homeRequest by model.homeRequests.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val owner = LocalLifecycleOwner.current
    val life by owner.lifecycle.currentStateFlow.collectAsState()
    var wallpaper by remember { mutableStateOf<Pair<PhotoStore.Photo, Bitmap>?>(null) }
    var photoStep by remember { mutableIntStateOf(0) }
    var consumedPhotoStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.imageRevision, model.prefs.getString("photo_mode", "all"), model.prefs.getInt("wallpaper_minutes", 10), photoStep, life) {
        if (life.isAtLeast(Lifecycle.State.RESUMED)) {
            wallpaper = model.wallpaper(if (photoStep == consumedPhotoStep) 0 else 1)
            consumedPhotoStep = photoStep
            val interval = model.prefs.getInt("wallpaper_minutes", 10).coerceIn(1, 1440) * 60000L
            val elapsed = (System.currentTimeMillis() - model.prefs.prefs().getLong("last_photo_time", System.currentTimeMillis())).coerceAtLeast(0)
            delay((interval - elapsed).coerceAtLeast(1000))
            photoStep++
        }
    }
    BackHandler { model.returnHome() }
    CompositionLocalProvider(LocalDensity provides Density(density.density * state.uiScale.coerceIn(75, 110) / 100f, density.fontScale * state.textScale.coerceIn(85, 140) / 100f)) {
        MarosTheme(state.accent, state.reduced, theme = state.theme) {
            val backdrop = MaterialTheme.colorScheme.background
            Box(Modifier.fillMaxSize().background(backdrop)) {
                // Cache the static photograph and gradients separately from frequently changing focus.
                Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                wallpaper?.takeIf { !state.guest || it.first.builtin }?.let { Image(it.second.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                val dim = state.contrast.coerceIn(0, 80) / 100f
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(backdrop.copy(alpha = .84f + dim * .15f), backdrop.copy(alpha = .12f + dim * .5f), backdrop.copy(alpha = .25f + dim * .5f)))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x6B080F1B), Color.Transparent, Color(0x99090F1A)), startY = 0f)))
                }
                Column(Modifier.fillMaxSize()) {
                    Header(state)
                    HomeNavigation(tabs.entries.filter { (model.prefs.getBool("show_collections", true) || it.key in listOf("accueil", "apps")) && (model.prefs.getBool("show_games", true) || it.key != "jeux") }.map { it.key to it.value }, model, tab, homeRequest)
                    MotionReveal(tab, Modifier.weight(1f).fillMaxWidth()) {
                        if (tab == "accueil") HomeFeed(model, state, wallpaper?.first?.title.orEmpty()) { photoStep++ }
                        else Catalog(model, state, tab)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: HomeState) {
    val context = LocalContext.current
    val compact = state.textScale > 110 || state.uiScale >= 100
    Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Action("", R.drawable.ui_settings, Modifier.padding(end = 16.dp)) {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                .onFailure { android.widget.Toast.makeText(context, "Paramètres Android indisponibles", android.widget.Toast.LENGTH_SHORT).show() }
        }
        Glyph(R.drawable.maros_mark, Modifier.size(28.dp))
        Text("MarOS", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.headlineMedium)
        if (!compact) Text("VOTRE TÉLÉ. VOTRE ESPACE.", Modifier.padding(start = 16.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
        Spacer(Modifier.weight(1f))
        if (LauncherPrefs(context).getBool("show_clock", true)) Text(
            SimpleDateFormat(if (LauncherPrefs(context).getBool("clock_24h", true)) "HH:mm" else "h:mm a", Locale.FRANCE).format(Date(state.now)),
            Modifier.padding(end = 18.dp), style = MaterialTheme.typography.titleLarge)
        Action("", R.drawable.ui_search, Modifier.padding(end = 8.dp)) { HubActivity.open(context, "search") }
        Action("", when(state.network) { "ethernet" -> R.drawable.ui_ethernet; "wifi" -> R.drawable.ui_wifi; else -> R.drawable.ui_offline }, Modifier.padding(end = 8.dp)) {
            runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        }
        Action("", R.drawable.ui_sliders, Modifier.padding(end = 12.dp)) { context.startActivity(Intent(context, StudioActivity::class.java)) }
        var profileFocused by remember { mutableStateOf(false) }
        val profileMotion = focusProgress(profileFocused)
        Row(Modifier.graphicsLayer { val scale=1f+.045f*profileMotion.value;scaleX=scale;scaleY=scale }
            .onFocusChanged { profileFocused=it.isFocused }
            .border(2.dp, if(profileFocused) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick={context.startActivity(Intent(context,ProfileActivity::class.java))},interactionSource=null,indication=null)
            .semantics {contentDescription="Profil ${state.userName}, changer d’utilisateur";role=androidx.compose.ui.semantics.Role.Button}
            .padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
            if(!compact) Text(state.userName,Modifier.widthIn(max=110.dp).padding(start=10.dp,end=10.dp),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelLarge)
            UserAvatar(state.profile,state.userName,state.revision,38.dp,state.guest)
        }
    }
}

@Composable
private fun HomeFeed(model: HomeViewModel, state: HomeState, photo: String, nextPhoto: () -> Unit) {
    val homeRequest by model.homeRequests.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var lowerPage by remember { mutableStateOf(false) }
    var focusDock by remember { mutableIntStateOf(0) }
    val lowerEntry = remember { FocusRequester() }
    var lastDock by remember { mutableStateOf(model.focusId) }
    val returnToDock: () -> Unit = { model.focused(lastDock); lowerPage = false; focusDock++ }
    LaunchedEffect(homeRequest) { lowerPage = false }
    BackHandler(enabled = lowerPage, onBack = returnToDock)
    MotionReveal(lowerPage, Modifier.fillMaxSize(), animateInitial=false) {
    if (!lowerPage) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
            Row(Modifier.fillMaxSize().padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 20.dp)) {
                    Text(SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date(state.now)).uppercase(Locale.FRANCE),
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    Text(state.title.ifBlank { when(state.theme) { "minimal" -> "L’essentiel, pour vous."; "cinema" -> "Votre séance\ncommence ici."; else -> "Votre soirée,\nà votre rythme." } },
                        Modifier.padding(top = 9.dp, bottom = 12.dp), style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 36.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (state.weather.isNotBlank()) Action(state.weather, R.drawable.ui_sun) { HubActivity.open(context, "weather") }
                    if (model.prefs.getBool("show_info", true)) {
                        if (state.event.isNotBlank()) Text(state.event, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val remaining = model.prefs.prefs().getLong("timer_deadline", 0) - state.now
                        if (remaining > 0 && model.prefs.getBool("show_timer", true)) Text("Minuteur · ${(remaining + 59999) / 60000} min", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (model.prefs.getBool("show_music", true)) {
                    val musicDensity = LocalDensity.current
                    key(state.uiScale, state.textScale, state.accent) {
                        AndroidView(factory = { MusicPanelView(legacyContext(it, musicDensity, state.textScale), false, true) }, update = { it.refreshSession() },
                            modifier = Modifier.width(400.dp).height((144 + (state.textScale - 100).coerceAtLeast(0) * .9f).dp))
                    }
                }
            }

            }
            if ("favorites" !in state.hiddenRows) {
                val byPackage = state.apps.associateBy { it.pkg }
                Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 22.dp)) {
                    AppShelf("Vos essentiels", state.dock.mapNotNull { byPackage[it] }, model, state, "favorites",
                        "Maintenir OK pour organiser · Bas pour vos lectures", dock = true, enter = focusDock,
                        onDown = { lastDock = model.focusId; lowerPage = true })
                }
            } else Action("Mes lectures et outils", R.drawable.ui_history, Modifier.padding(24.dp)) { lowerPage = true }
        }
    } else {
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { lowerEntry.requestFocus() } }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "back-to-essentials") {
                Action("Vos essentiels", R.drawable.ui_back, Modifier.padding(horizontal = 48.dp, vertical = 8.dp).focusRequester(lowerEntry)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionUp) {
                            if (event.type == KeyEventType.KeyDown) { returnToDock() }
                            true
                        } else false
                    }) { returnToDock() }
            }
        for (row in (listOf("continue") + state.rows).distinct().filterNot { it in state.hiddenRows || it == "favorites" || it == "recent" }) {
            when(row) {
                "continue" -> item(key = row) { HistoryShelf(model, state) }
                "library" -> if (state.library.isNotEmpty()) item(key = row) { LibraryShelf(state) }
                "shortcuts" -> item(key = row) { QuickShelf(model, state) }
            }
        }
        item(key = "footer") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(photo.ifBlank { "MarOS 5  ·  Chez vous." }, Modifier.weight(1f).padding(end = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, style = MaterialTheme.typography.bodySmall)
                Action("Paysage suivant", R.drawable.ui_next, Modifier.padding(end = 10.dp), onClick = nextPhoto)
                Action("Ambiant", R.drawable.ui_sun, Modifier.padding(end = 10.dp)) { context.startActivity(Intent(context, AmbientActivity::class.java)) }
                if (model.prefs.getBool("show_storage", true)) Action(String.format(Locale.FRANCE, "%.1f Go libres", state.available / 1e9), R.drawable.ui_sliders) {
                    context.startActivity(Intent(context, StudioActivity::class.java).putExtra("section", "diagnostics"))
                }
            }
        }
        }
    }
    }
}

@Composable
private fun LibraryShelf(state: HomeState) {
    val context = LocalContext.current
    Column {
        ShelfHeading("Votre vidéothèque", "Jellyfin · reprise et ajouts récents")
        LazyRow(contentPadding = PaddingValues(horizontal = 44.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(state.library, key = { it.uri }) { item ->
                val record = remember(item) { ContentBridge.Item().apply {
                    title = item.title; subtitle = item.subtitle; imageUri = item.imageUrl
                    packageName = "org.jellyfin.androidtv"
                } }
                AndroidView(factory = { ResumeCardView(it, record, { HubData.open(context as android.app.Activity, item.uri, item.pkg) }, null) }, modifier = Modifier.width(210.dp).height(160.dp))
            }
        }
    }
}

@Composable
private fun ShelfHeading(title: String, hint: String = "") {
    Row(Modifier.fillMaxWidth().padding(start = 44.dp, end = 44.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

/** Preserve the row while focus moves inside it; scroll only to reveal an offscreen tile. */
internal object EdgeScrollSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset < 24f -> offset - 24f
        offset + size > containerSize - 24f -> offset + size - containerSize + 24f
        else -> 0f
    }
}

@Composable
private fun AppShelf(title: String, apps: List<AppEntry>, model: HomeViewModel, state: HomeState, row: String, hint: String = "", dock: Boolean = false, enter: Int = 0, onDown: (() -> Unit)? = null) {
    Column(Modifier.onPreviewKeyEvent { event ->
        if (onDown != null && event.key == Key.DirectionDown) {
            if (event.type == KeyEventType.KeyDown) onDown()
            true
        } else false
    }) {
        if (dock) Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(Locale.FRANCE), style = MaterialTheme.typography.labelSmall, color = Muted)
            Spacer(Modifier.weight(1f)); Text(hint, style = MaterialTheme.typography.bodySmall, color = Muted)
        } else ShelfHeading(title, hint)
        if (apps.isEmpty()) {
            val context = LocalContext.current
            Action("Ajouter des favoris", R.drawable.ui_plus, Modifier.padding(start = 44.dp)) {
                context.startActivity(Intent(context, SettingsActivity::class.java).putExtra("section", "apps"))
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(enter) {
                val focused = apps.indexOfFirst { "$row:${it.pkg}" == model.focusId }
                if (focused >= 0) listState.scrollToItem(focused)
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides EdgeScrollSpec) {
            LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 44.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(apps, key = { it.pkg }) { AppTile(it, model, state.imageRevision, "$row:${it.pkg}", enter = enter, width = if (dock) when(state.tileSize) { "small" -> 104.dp; "large" -> 136.dp; else -> 116.dp } else when(state.tileSize) { "small" -> 112.dp; "large" -> 160.dp; else -> 136.dp }) }
            }
            }
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, model: HomeViewModel, revision: Int, key: String, width: Dp = 136.dp, enter: Int = 0) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, app.pkg, revision) { value = model.art(app, revision) }
    val tileImage = remember(bitmap) { bitmap?.asImageBitmap() }
    val requester = remember { FocusRequester() }
    LaunchedEffect(enter) { if (model.focusId == key) { delay(180); runCatching { requester.requestFocus() } } }
    var focused by remember { mutableStateOf(false) }
    val motion = focusProgress(focused)
    val outline = MaterialTheme.colorScheme.primary
    Column(Modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).graphicsLayer {
                scaleX = 1f + .045f * motion.value; scaleY = scaleX
                translationY = -2.dp.toPx() * motion.value
            }.focusRequester(requester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) model.focused(key) }
            // Focus changes invalidate drawing only, without rebuilding the image/composition.
            .drawWithContent {
                drawContent()
                if (motion.value > .001f) {
                    val stroke = 2.dp.toPx()
                    drawRoundRect(outline.copy(alpha = motion.value), Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), CornerRadius(12.dp.toPx()), style = Stroke(stroke))
                }
            }
            .clip(RoundedCornerShape(12.dp)).background(Panel)
            .combinedClickable(onClick = { model.open(app) }, onLongClick = {
                context.startActivity(Intent(context, SettingsActivity::class.java).putExtra("app_package", app.pkg))
            }, role = androidx.compose.ui.semantics.Role.Button, interactionSource = null, indication = null)) {
            if (tileImage != null) Image(tileImage, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(app.label, maxLines = 1) }
        }
        Text(app.label, Modifier.padding(top = 8.dp, start = 2.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Keeps legacy TV controls at the same visual density as the Compose canvas. */
private fun legacyContext(context: android.content.Context, density: Density, ownTextScale: Int = 100): android.content.Context =
    context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
        densityDpi = (density.density * 160).toInt()
        // MusicPanel's MarOSUI.text already applies this preference itself.
        fontScale = density.fontScale / (ownTextScale.coerceIn(85, 140) / 100f)
    })

@Composable
private fun HistoryShelf(model: HomeViewModel, state: HomeState, records: List<ContentBridge.Item> = state.history) {
    val context = LocalContext.current
    val nativeDensity = LocalDensity.current
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("À reprendre", style = MaterialTheme.typography.titleLarge)
            }
            Action("Tout voir", R.drawable.ui_history, Modifier.height(32.dp)) { HubActivity.open(context, "history") }
        }
        if (context.checkSelfPermission("android.permission.READ_TV_LISTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Action("Autoriser les visuels et programmes TV", R.drawable.ui_photo, Modifier.padding(start = 48.dp, bottom = 12.dp)) {
                (context as? MainActivity)?.requestViewingAccess()
            }
        }
        if (records.isEmpty()) {
            Column(Modifier.padding(horizontal = 48.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Text("Vos prochaines lectures apparaîtront ici.", style = MaterialTheme.typography.titleMedium)
                Text("Crunchyroll, Netflix, TiviMate… selon les informations partagées par leur lecteur.",
                    Modifier.padding(top = 4.dp), color = Muted, style = MaterialTheme.typography.bodyMedium)
                if (!MediaBridge(context).isAuthorized) Action("Autoriser le suivi des lectures", R.drawable.ui_play, Modifier.padding(top = 10.dp)) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                }
            }
        } else LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(records, key = { "${it.packageName}:${it.id}:${it.title}" }) { item ->
                EpisodeCard(model, state, item)
            }
        }
    }
}

@Composable
private fun QuickShelf(model: HomeViewModel, state: HomeState) {
    val context = LocalContext.current
    Column {
        ShelfHeading("À portée de main")
        LazyRow(contentPadding = PaddingValues(horizontal = 44.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Action("Mon téléphone", R.drawable.ui_phone) { HubActivity.open(context, "phone") } }
            item { Action("Mes photos", R.drawable.ui_photo) { context.startActivity(Intent(context, SettingsActivity::class.java).putExtra("section", "photos")) } }
            item { Action("Minuteur", R.drawable.ui_timer) { HubActivity.open(context, "timer") } }
            item { Action("Bibliothèque", R.drawable.ui_play) { HubActivity.open(context, "library") } }
            item { Action("Utilisateurs", R.drawable.ui_heart) { context.startActivity(Intent(context, ProfileActivity::class.java)) } }
        }
    }
}

@Composable
private fun Catalog(model: HomeViewModel, state: HomeState, tab: String) {
    val context = LocalContext.current
    val apps = state.apps.filter { tab == "apps" || it.category == tab }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 44.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tabs[tab].orEmpty(), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            if (tab == "regarder") Action("Ma liste", R.drawable.ui_heart) { ExperienceActivity.open(context,"watchlist") }
            if (tab == "jeux") Action("Mon PC", R.drawable.ui_game) { HubActivity.open(context, "pc") }
            if (tab == "musique") Action("Lecteur", R.drawable.ui_music) { HubActivity.open(context, "search") }
        }
        if (tab == "musique") {
            val nativeDensity = LocalDensity.current
            key(state.uiScale, state.textScale, state.accent) {
                AndroidView(factory = { MusicPanelView(legacyContext(it, nativeDensity, state.textScale), true) },
                    modifier = Modifier.padding(horizontal = 48.dp).height(230.dp).fillMaxWidth())
            }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(when(state.tileSize) { "small" -> 6; "large" -> 4; else -> 5 }), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            items(apps, key = { it.pkg }) { app -> BoxWithConstraints { AppTile(app, model, state.imageRevision, "grid:${app.pkg}", maxWidth) } }
        }
    }
}
