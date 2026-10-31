package io.github.marouane4142.maros

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import io.github.marouane4142.maros.tv.*

/** Daily settings stay small; account/guest selection belongs to the home avatar. */
class StudioActivity : ComponentActivity() {
    private lateinit var prefs: LauncherPrefs
    private var revision by mutableIntStateOf(0)
    private var page by mutableStateOf("appearance")
    private var status by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);prefs=LauncherPrefs(this);window.decorView.systemUiVisibility=5894
        page=when(intent.getStringExtra("section")){"connections","updates","diagnostics","scenes"->"advanced";"phone"->"phone";else->"appearance"}
        if(intent.getStringExtra("section")=="profiles"){startActivity(Intent(this,ProfileActivity::class.java));finish();return}
        setContent {
            val version=revision
            MarosTheme(prefs.getString("accent","ice"),prefs.getBool("reduced_animations",false),prefs.getInt("text_scale",100),prefs.theme()) {
                Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(30.dp)) {
                    Column(Modifier.width(180.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("Réglages",Modifier.padding(bottom=18.dp),style=MaterialTheme.typography.headlineMedium)
                        for((id,label) in listOf("appearance" to "Apparence","apps" to "Applications","phone" to "Téléphone")) Action(label,modifier=Modifier.fillMaxWidth(),selected=page==id){page=id;status=""}
                        Spacer(Modifier.weight(1f))
                        Action("Retour",R.drawable.ui_back){finish()}
                    }
                    Spacer(Modifier.width(28.dp))
                    MotionReveal(page,Modifier.weight(1f).fillMaxHeight()) {
                        LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                            item {Text(when(page){"apps"->"Vos applications";"phone"->"Votre téléphone";"advanced"->"Options avancées";else->"À votre image"},style=MaterialTheme.typography.headlineMedium)}
                            if(status.isNotBlank())item {Text(status,color=MaterialTheme.colorScheme.primary)}
                            when(page){
                                "appearance"->{
                                    item {Text("Ambiance",color=Muted)}
                                    item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        for((id,label) in listOf("nature" to "Glacier","cinema" to "Cinéma","aurora" to "Aurore","dune" to "Dune","minimal" to "Minimal")) Action(label,selected=prefs.theme()==id){prefs.putString("theme",id);changed()}
                                    }}
                                    item {Text("Couleur d’accent",color=Muted)}
                                    item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        for((id,label) in listOf("ice" to "Glacier","sage" to "Sauge","amber" to "Ambre","rose" to "Rose"))Action(label,selected=prefs.getString("accent","ice")==id){prefs.putString("accent",id);changed()}
                                    }}
                                    item {Action("Photos et fond d’écran",R.drawable.ui_photo){editor("photos")}}
                                    item {Action("Taille de l’interface : ${prefs.getInt("ui_scale",85)} %",R.drawable.ui_eye){val values=listOf(85,75,100,110);prefs.putInt("ui_scale",values[(values.indexOf(prefs.getInt("ui_scale",85))+1)%values.size]);changed()}}
                                    item {Action("Taille du texte : ${prefs.getInt("text_scale",100)} %"){val values=listOf(100,110,120,130,140,85);prefs.putInt("text_scale",values[(values.indexOf(prefs.getInt("text_scale",100))+1)%values.size]);changed()}}
                                    item {Action("Animations : ${if(prefs.getBool("reduced_animations",false)) "réduites" else "fluides"}"){prefs.putBool("reduced_animations",!prefs.getBool("reduced_animations",false));changed()}}
                                    item {Action("Plus d’options",R.drawable.ui_sliders){page="advanced"}}
                                }
                                "apps"->{
                                    item {Text("Organisez vos essentiels, choisissez les applications visibles et leurs images.",color=Muted)}
                                    item {Action("Organiser mes applications",R.drawable.ui_apps){editor("apps")}}
                                }
                                "phone"->{
                                    item {Text("Navigation MarOS, clavier, photos et commandes du lecteur depuis votre téléphone.",color=Muted)}
                                    item {Action("Connecter mon téléphone",R.drawable.ui_phone){HubActivity.open(this@StudioActivity,"phone")}}
                                    item {Text("Pour le clavier et les autres applications, la télécommande de l’application Google TV reste disponible.",color=Muted)}
                                }
                                "advanced"->{
                                    item {Action("Comptes et utilisateurs",R.drawable.ui_heart){startActivity(Intent(this@StudioActivity,ProfileActivity::class.java))}}
                                    item {Action("Sauvegardes",R.drawable.ui_save){ExperienceActivity.open(this@StudioActivity,"backup")}}
                                    item {Action("Album Immich",R.drawable.ui_photo){immichForm()}}
                                    if(prefs.getString("immich_url","").isNotBlank())item {Action("Importer mon album"){job{ConnectedFeatures.importImmich(this@StudioActivity)}}}
                                    item {Action("Connexions personnelles",R.drawable.ui_link){editor("connections")}}
                                    item {Action("Affichage détaillé",R.drawable.ui_sliders){editor("appearance")}}
                                    item {Action("Réglages Android TV",R.drawable.ui_settings){runCatching {startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))}}}
                                    item {Action("Diagnostic et version",R.drawable.ui_apps){status="MarOS ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE}\n${android.os.Build.MODEL}"}}
                                    item {Action("À propos et licences"){editor("advanced")}}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private fun changed(){prefs.changed();revision++}
    private fun editor(section:String){startActivity(Intent(this,SettingsActivity::class.java).putExtra("section",section))}
    private fun immichForm() {
        val form = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(32, 12, 32, 12) }
        fun input(hint: String, value: String, secret: Boolean = false): EditText = EditText(this).apply {
            this.hint = hint; setText(value); isSingleLine = true
            if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            form.addView(this)
        }
        val server = input("Serveur HTTPS", prefs.getString("immich_url", ""))
        val album = input("Identifiant de l’album", prefs.getString("immich_album", ""))
        val token = input("Clé API de lecture · vide = conserver", "", true)
        val dialog = AlertDialog.Builder(this).setTitle("Album Immich").setView(form).setNegativeButton("Annuler", null)
            .setNeutralButton("Déconnecter") { _, _ ->
                prefs.prefs().edit().remove("immich_url").remove("immich_album").apply()
                runCatching { Secrets(this).put("immich_token", "") }
                status = "Immich déconnecté. Vos copies locales sont conservées."
            }.setPositiveButton("Enregistrer", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            runCatching {
                val base = HubData.normalizeServer(server.text.toString())
                require(base.startsWith("https://")) { "Une adresse HTTPS est attendue." }
                val id = album.text.toString().trim()
                require(id.matches(Regex("[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}"))) { "Identifiant d’album invalide." }
                if (token.text.isNotEmpty()) Secrets(this).put("immich_token", token.text.toString())
                require(Secrets(this).get("immich_token").isNotEmpty()) { "Ajoutez une clé API autorisée à lire cet album et ses aperçus." }
                prefs.prefs().edit().putString("immich_url", base).putString("immich_album", id).apply()
                dialog.dismiss(); status = "Immich enregistré. Lancez l’import pour lire les photos."; revision++
            }.onFailure { server.error = it.message }
        } }; dialog.show()
    }
    private fun confirm(message: String, action: () -> Unit) { AlertDialog.Builder(this).setMessage(message).setNegativeButton("Annuler", null).setPositiveButton("Confirmer") { _, _ -> action() }.show() }
    private fun field(title: String, value: String, save: (String) -> Unit) {
        val input = EditText(this).apply { setText(value); isSingleLine = true }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Annuler", null).setPositiveButton("Enregistrer", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            runCatching { save(input.text.toString().trim()); dialog.dismiss(); revision++ }
                .onFailure { input.error = it.message }
        } }; dialog.show()
    }
    private fun job(work: () -> String) {
        status = "Connexion en cours…"
        HubData.work(work) { result, success ->
            if (!isDestroyed) { status = result; revision++ }
        }
    }
    override fun onResume() { super.onResume(); revision++ }
}
