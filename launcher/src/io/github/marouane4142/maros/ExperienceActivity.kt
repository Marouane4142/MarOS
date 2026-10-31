package io.github.marouane4142.maros

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.*
import io.github.marouane4142.maros.tv.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/** Owner-facing configuration and real actions, with connected/empty/error states. */
class ExperienceActivity : ComponentActivity() {
    private lateinit var prefs: LauncherPrefs
    private var page by mutableStateOf("overview")
    private var revision by mutableIntStateOf(0)
    private var status by mutableStateOf("")
    private var data by mutableStateOf(JSONObject())
    private var busy by mutableStateOf(false)
    private var results by mutableStateOf(JSONArray())
    private var selectedId by mutableStateOf("")
    private var driveFiles by mutableStateOf(JSONArray())
    private val configChanged = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "ui_revision") revision++ }
    private var drivePending: java.io.File? = null
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> prefs.putBool("series_notifications", allowed); revision++ }
    private val pages=linkedMapOf("watchlist" to "Ma liste", "photos" to "Cadre photo", "backup" to "Sauvegardes", "scenes" to "Raccourcis")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); prefs = LauncherPrefs(this); prefs.prefs().registerOnSharedPreferenceChangeListener(configChanged)
        val requested=intent.getStringExtra("section").orEmpty()
        if(requested=="phone"){HubActivity.open(this,"phone");finish();return}
        if(requested in setOf("google","guests","calendar")){startActivity(Intent(this,ProfileActivity::class.java));finish();return}
        if(requested !in pages){startActivity(Intent(this,StudioActivity::class.java));finish();return}
        page=requested
        window.decorView.systemUiVisibility = 5894
        setContent {
            LaunchedEffect(revision) { if (RoomData.guest(this@ExperienceActivity)) { data=JSONObject(); results=JSONArray(); driveFiles=JSONArray() } }
            LaunchedEffect(page, revision) {
                data = withContext(Dispatchers.IO) { runCatching { load() }.getOrElse { JSONObject().put("error", it.message ?: "Indisponible") } }
            }
            MarosTheme(prefs.getString("accent", "ice"), prefs.getBool("reduced_animations", false), prefs.getInt("text_scale", 100), prefs.theme()) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(30.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom=18.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Action("Retour",R.drawable.ui_back){finish()}
                        Spacer(Modifier.width(20.dp))
                        Text(if(page=="detail") data.optString("title","Votre fiche") else pages[page].orEmpty(),style=MaterialTheme.typography.headlineMedium)
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=28.dp)) {
                        if (busy) item { Text("En cours…", color = MaterialTheme.colorScheme.primary) }
                        if (status.isNotBlank()) item { Text(status, color = MaterialTheme.colorScheme.primary) }
                        if (data.has("error")) item { Text(data.optString("error"), color = Muted) }
                        when (page) {
                            "watchlist" -> {
                                item { Text("Votre liste personnelle reste distincte de l’historique des lecteurs. Les saisons et épisodes suivis ici sont sous votre contrôle.", color = Muted) }
                                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Action("Ajouter un titre", R.drawable.ui_plus) { field("Film ou série", "") { title -> Watchlist(this@ExperienceActivity).add(title); revision++ } }
                                    Action("Chercher une série", R.drawable.ui_search) { field("Recherche TVmaze", "") { query -> job { results = Watchlist(this@ExperienceActivity).search(query); "Choisissez la bonne série ci-dessous." } } }
                                } }
                                for (found in objects(results)) item { Action(found.optString("title") + " · " + found.optString("premiered"), modifier = Modifier.fillMaxWidth()) { job { Watchlist(this@ExperienceActivity).add(found.optString("title"), showId = found.getInt("id")); Watchlist(this@ExperienceActivity).refresh() } } }
                                item { Action("Actualiser les prochaines sorties") { job { Watchlist(this@ExperienceActivity).refresh() } } }
                                for (item in objects(data.optJSONArray("items"))) item(key = item.optString("id")) {
                                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(item.optString("title"), style = MaterialTheme.typography.titleLarge)
                                        Text("Vous suivez S${item.optInt("season", 1)} · E${item.optInt("episode", 1)}" + item.optString("next_air").takeIf { it.isNotBlank() }?.let { "  ·  Prochaine sortie : $it" }.orEmpty(), color = Muted)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Action("Fiche") { selectedId = item.optString("id"); page = "detail" }
                                            Action("Épisode suivant") { Watchlist(this@ExperienceActivity).change(item.optString("id"), "next"); revision++ }
                                            Action("Saison suivante") { Watchlist(this@ExperienceActivity).change(item.optString("id"), "season"); revision++ }
                                            Action("Retirer") { confirm("Retirer ce titre de votre liste ?") { Watchlist(this@ExperienceActivity).change(item.optString("id"), "remove"); revision++ } }
                                        }
                                    }
                                }
                                item { toggle("Actualisation en arrière-plan", "auto_series") }
                                item { Action("Rappels de sorties : ${if(prefs.getBool("series_notifications", false)) "oui" else "non"}") { if (Build.VERSION.SDK_INT >= 33 && !prefs.getBool("series_notifications", false)) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else { prefs.putBool("series_notifications", !prefs.getBool("series_notifications", false)); revision++ } } }
                                item { Text("Métadonnées : TVmaze · CC BY-SA. Les dates peuvent varier selon le pays et le service.", color = Muted) }
                                item { Action("TVmaze · source et licence", R.drawable.ui_link) { HubData.open(this@ExperienceActivity, "https://www.tvmaze.com/api", "") } }
                            }
                            "detail" -> {
                                if (data.optString("image").isNotBlank()) item {
                                    val record = ContentBridge.Item().apply { imageUri = data.optString("image"); title = data.optString("title") }
                                    AndroidView(factory = { ResumeCardView(it, record, null, null, true) }, modifier = Modifier.fillMaxWidth().height(200.dp))
                                }
                                item { Text(data.optString("summary", "Ajoutez une source de métadonnées pour afficher une description."), color = Muted) }
                                if (data.optString("next_air").isNotBlank()) item { Text("À venir : ${data.optString("next_title")} · ${data.optString("next_air")}") }
                                item { Action("Ouvrir mon service", R.drawable.ui_play) { val pkg = data.optString("package"); if (pkg.isBlank()) chooseApp { Watchlist(this@ExperienceActivity).setService(selectedId, it); revision++ } else MediaBridge(this@ExperienceActivity).open(pkg) } }
                                item { Action("Choisir le service") { chooseApp { Watchlist(this@ExperienceActivity).setService(selectedId, it); revision++ } } }
                                item { Action("Retour à ma liste", R.drawable.ui_back) { page = "watchlist" } }
                            }
                            "scenes" -> {
                                for ((id, name) in linkedMapOf("cinema" to "Soirée cinéma", "music" to "Ambiance musique", "game" to "Jouer sur PC", "night" to "Fin de soirée")) item {
                                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(name, style = MaterialTheme.typography.titleLarge)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Action("Lancer", R.drawable.ui_play) { job { RoomData.runScene(this@ExperienceActivity, id) } }
                                            if (id != "night") Action("Application") { chooseApp { prefs.putString("scene_${id}_app", it); revision++ } }
                                            Action("Scène Home Assistant") { field("Identifiant scene.nom", prefs.getString("scene_${id}_entity", "")) { require(it.isBlank() || it.matches(Regex("scene\\.[A-Za-z0-9_]+"))) { "Identifiant scene.nom attendu" }; prefs.putString("scene_${id}_entity", it); revision++ } }
                                        }
                                    }
                                }
                                item { toggle("Réveiller le PC avant d’ouvrir Moonlight", "scene_game_wake") }
                                item { Action("Configurer Home Assistant ou le PC", R.drawable.ui_link) { startActivity(Intent(this@ExperienceActivity, StudioActivity::class.java).putExtra("section", "scenes")) } }
                                item { Text("Fin de soirée ouvre le mode ambiant. La mise en veille et l’alimentation de la TV restent gérées par Android et la télécommande.", color = Muted) }
                            }
                            "photos" -> {
                                item { Action("Ouvrir le cadre photo", R.drawable.ui_photo) { startActivity(Intent(this@ExperienceActivity, AmbientActivity::class.java)) } }
                                item { Action("Choisir mes photos") { startActivity(Intent(this@ExperienceActivity, SettingsActivity::class.java).putExtra("section", "photos")) } }
                                item { Action("Configurer Immich") { startActivity(Intent(this@ExperienceActivity, StudioActivity::class.java).putExtra("section", "connections")) } }
                                item { toggle("Synchronisation automatique Immich", "auto_photos") }
                                item { Action("Importer maintenant") { job { ConnectedFeatures.importImmich(this@ExperienceActivity) } } }
                                item { Text("Les imports automatiques utilisent la déduplication Immich, les quotas de l’album et une réserve d’espace libre. Ils sont planifiés toutes les six heures, lorsque la box est inactive sur un réseau non limité.", color = Muted) }
                                item { Text(prefs.getString("maintenance_status", "Aucune actualisation automatique effectuée."), color = Muted) }
                            }
                            "backup" -> {
                                if(GoogleLink.status(this@ExperienceActivity).optBoolean("connected")) {
                                    item {Action("Sauvegarder sur mon Drive",R.drawable.ui_save){confirm("Envoyer votre archive personnelle sur votre Drive ?"){job{GoogleLink.uploadBackup(this@ExperienceActivity)}}}}
                                    item {Action("Mes sauvegardes Drive"){job{driveFiles=GoogleLink.listBackups(this@ExperienceActivity);"${driveFiles.length()} sauvegarde(s)"}}}
                                    for(file in objects(driveFiles))item {Action(file.optString("name")){inspectDriveBackup(file.getString("id"))}}
                                } else {
                                    item {Action("Google Drive · facultatif",R.drawable.ui_save) {startActivity(Intent(this@ExperienceActivity,ProfileActivity::class.java).putExtra("google_services",true))}}
                                }
                                item { Text("Depuis le téléphone appairé, téléchargez une archive contenant réglages, liste à regarder, agenda local et photos. Les jetons et mots de passe sont exclus. Les images personnelles rendent cette archive privée.", color = Muted) }
                                item { Action("Ouvrir les sauvegardes sur mon téléphone", R.drawable.ui_phone) { HubActivity.open(this@ExperienceActivity, "phone") } }
                                item { Action("Réglages portables sur la TV", R.drawable.ui_save) { startActivity(Intent(this@ExperienceActivity, SettingsActivity::class.java).putExtra("section", "backup")) } }
                            }
                        }
                    }
                }
            }
        }
    }
    private fun load(): JSONObject = when(page) {
        "watchlist" -> JSONObject().put("items", Watchlist(this).items())
        "detail" -> objects(Watchlist(this).items()).firstOrNull { it.optString("id") == selectedId } ?: JSONObject()
        else -> RoomData.status(this)
    }
    private fun job(block: () -> String) {
        if (busy) return
        busy = true
        lifecycleScope.launch { status = withContext(Dispatchers.IO) { runCatching(block).getOrElse { it.message ?: "Action indisponible" } }; busy = false; revision++ }
    }
    private fun inspectDriveBackup(id:String) {
        lifecycleScope.launch {
            busy=true
            try {
                val pair=withContext(Dispatchers.IO) { val file=GoogleLink.downloadBackup(this@ExperienceActivity,id); try { file to PersonalBackup.restore(this@ExperienceActivity,file,false) } catch(e:Exception){file.delete();throw e} }
                drivePending?.delete();drivePending=pair.first; val summary=pair.second
                AlertDialog.Builder(this@ExperienceActivity).setTitle("Restaurer cette sauvegarde ?").setMessage("${summary.optInt("photos")} photos · ${summary.optInt("titles")} titres · ${summary.optInt("events")} rendez-vous. Les données existantes seront conservées.")
                    .setNegativeButton("Annuler") { _,_->pair.first.delete();drivePending=null }
                    .setPositiveButton("Fusionner") { _,_->drivePending=null;job { try { PersonalBackup.restore(this@ExperienceActivity,pair.first,true).getString("message") } finally {pair.first.delete()} } }
                    .setOnCancelListener { pair.first.delete();drivePending=null }.show()
            } catch(e:Exception){status=e.message?:"Sauvegarde indisponible"}finally{busy=false}
        }
    }
    override fun onStop() { super.onStop() }
    override fun onDestroy() { prefs.prefs().unregisterOnSharedPreferenceChangeListener(configChanged); drivePending?.delete(); super.onDestroy() }
    @Composable private fun toggle(label: String, key: String) {
        Action("$label : ${if(prefs.getBool(key, false)) "oui" else "non"}") {
            runCatching {
                if (key == "auto_photos" && !prefs.getBool(key, false)) require(prefs.getString("immich_url", "").isNotBlank() && Secrets(this).get("immich_token").isNotBlank()) { "Configurez d’abord votre album Immich" }
                prefs.putBool(key, !prefs.getBool(key, false)); MaintenanceJob.schedule(this); revision++
            }.onFailure { status = it.message.orEmpty() }
        }
    }
    private fun field(title: String, initial: String, save: (String) -> Unit) {
        val input = EditText(this).apply { setText(initial); setSingleLine(true) }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Annuler", null).setPositiveButton("Enregistrer", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener { runCatching { save(input.text.toString().trim()); dialog.dismiss() }.onFailure { input.error = it.message } } }; dialog.show()
    }
    private fun chooseApp(save: (String) -> Unit) {
        val apps = AppCatalog.query(this).filter { it.activityInfo.packageName !in prefs.hidden() }
        AlertDialog.Builder(this).setTitle("Choisir l’application").setItems(apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()) { _, index -> save(apps[index].activityInfo.packageName) }.show()
    }
    private fun system(intent: Intent) { runCatching { startActivity(intent) }.onFailure { status = "Ce réglage n’est pas disponible sur ce firmware." } }
    private fun confirm(title: String, action: () -> Unit) { AlertDialog.Builder(this).setTitle(title).setNegativeButton("Annuler", null).setPositiveButton("Confirmer") { _, _ -> action() }.show() }
    private fun objects(array: JSONArray?): List<JSONObject> = if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    private fun date(time: Long) = SimpleDateFormat("EEE d MMM · HH:mm", Locale.FRANCE).format(Date(time))
    private val googleServices = linkedMapOf("com.google.android.gms" to "Google Play services", "com.google.android.apps.mediashell" to "Google Cast", "com.google.android.katniss" to "Recherche et voix Google", "com.google.android.tv.remote.service" to "Télécommande Google TV", "com.google.android.syncadapters.calendar" to "Synchronisation Agenda")
    private fun icon(id: String) = when(id) { "cast" -> R.drawable.ui_cast; "calendar" -> R.drawable.ui_calendar; "phone" -> R.drawable.ui_phone; "scenes" -> R.drawable.ui_spark; "photos" -> R.drawable.ui_photo; "backup" -> R.drawable.ui_save; else -> R.drawable.ui_sliders }
    companion object { fun open(c: android.content.Context, section: String = "overview") { c.startActivity(Intent(c, ExperienceActivity::class.java).putExtra("section", section).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}
