package io.github.marouane4142.maros

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.*
import io.github.marouane4142.maros.tv.*
import kotlinx.coroutines.*
import org.json.JSONObject

/** One place to choose a Google-linked user or the guest session. */
class ProfileActivity : ComponentActivity() {
    private lateinit var prefs: LauncherPrefs
    private var revision by mutableIntStateOf(0)
    private var message by mutableStateOf("")
    private var pending by mutableStateOf(JSONObject())
    private var linking: Job? = null
    private var nativeAdd = false
    private var nativeProfile = ""
    private var extraServices by mutableStateOf(false)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if(granted) "Lecture des agendas autorisée. Les calendriers synchronisés de ce compte suivent le profil." else "L’agenda reste désactivé. Vous pourrez l’autoriser ici."
        revision++
    }
    private val accountPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode == RESULT_OK) runCatching {
            require(Profiles(this).current == nativeProfile && !RoomData.guest(this)) { "Le profil a changé ; choisissez de nouveau votre compte." }
            val account = DeviceAccounts.result(this, result.data)
            UserAccounts.linkDevice(this, account, nativeAdd)
            message = "Compte de la box associé. Vos réglages MarOS sont conservés."
            revision++
        }.onFailure { message = it.message ?: "Le compte n’a pas pu être associé." }
        nativeAdd = false
    }
    private val changes = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if(key == "ui_revision") revision++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); prefs=LauncherPrefs(this); UserAccounts.migrate(this)
        nativeAdd=savedInstanceState?.getBoolean("native_add")?:false
        nativeProfile=savedInstanceState?.getString("native_profile").orEmpty()
        extraServices=intent.getBooleanExtra("google_services",false)
        prefs.prefs().registerOnSharedPreferenceChangeListener(changes)
        window.decorView.systemUiVisibility=5894
        setContent {
            val profiles=remember(revision) { Profiles(this).names() }
            val account=remember(revision) { GoogleLink.status(this) }
            val identity=remember(revision) { UserAccounts.identity(this) }
            val linked=identity.optString("email").isNotBlank()
            val nativeAvailable=remember(revision) { linked && DeviceAccounts.contains(this,identity.optString("email")) }
            MarosTheme(prefs.getString("accent","ice"),prefs.getBool("reduced_animations",false),prefs.getInt("text_scale",100),prefs.theme()) {
                MotionReveal("profiles",Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=44.dp,vertical=32.dp),verticalArrangement=Arrangement.Top) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Qui regarde ?",style=MaterialTheme.typography.displaySmall)
                                Text("Votre compte, votre espace.",Modifier.padding(top=8.dp),color=Muted)
                            }
                            Action("Retour",R.drawable.ui_back) {finish()}
                        }
                        Spacer(Modifier.height(24.dp))
                        LazyRow(state=rememberLazyListState(initialFirstVisibleItemIndex=if(RoomData.guest(this@ProfileActivity))profiles.size else profiles.indexOf(Profiles(this@ProfileActivity).current).coerceAtLeast(0)),contentPadding=PaddingValues(horizontal=8.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            items(profiles,key={it}) { name ->
                                val label=remember(name,revision) {UserAccounts.label(this@ProfileActivity,name)}
                                ProfileTile(name,label,revision,active=!RoomData.guest(this@ProfileActivity)&&name==Profiles(this@ProfileActivity).current) {
                                    linking?.cancel();GoogleLink.cancelPending();RoomData.setGuest(this@ProfileActivity,false);Profiles(this@ProfileActivity).select(name);finish()
                                }
                            }
                            item { ProfileTile("guest","Invité",revision,guest=true,active=RoomData.guest(this@ProfileActivity)) {
                                linking?.cancel();GoogleLink.cancelPending();RoomData.setGuest(this@ProfileActivity,true);finish()
                            } }
                            if(profiles.size<6&&!RoomData.guest(this@ProfileActivity)) item { ProfileTile("add","Ajouter",revision,guest=true) {chooseDeviceAccount(true)} }
                        }
                        if(pending.has("user_code")) {
                            Spacer(Modifier.height(12.dp))
                            Text("Sur votre téléphone : ${pending.optString("verification_url")}",color=Muted)
                            Text(pending.optString("user_code"),Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.displaySmall)
                            Action("Annuler") {linking?.cancel();GoogleLink.cancelPending();pending=JSONObject()}
                        } else if(!RoomData.guest(this@ProfileActivity)) {
                            Spacer(Modifier.height(12.dp))
                            if(linked) {
                                Text(identity.optString("email"),style=MaterialTheme.typography.titleMedium)
                                Text(if(nativeAvailable) "Compte Google de la box" else if(identity.optString("source")=="android") "Compte retiré de la box ou accès révoqué" else "Compte Google autorisé",Modifier.padding(top=6.dp),color=Muted)
                            }
                            Action(if(linked) "Choisir un autre compte de la box" else "Utiliser un compte de la box",R.drawable.ui_link,Modifier.padding(top=12.dp)) {chooseDeviceAccount(false)}
                            if(!linked) Text("Choisissez un compte déjà présent sur Android TV. Aucun mot de passe à saisir dans MarOS.",Modifier.padding(top=10.dp),color=Muted)
                            if(linked && UserAccounts.email(this@ProfileActivity).isNotBlank() && !CalendarAccess.granted(this@ProfileActivity))
                                Action("Afficher mon agenda Google",R.drawable.ui_calendar,Modifier.padding(top=10.dp)) {permission.launch(Manifest.permission.READ_CALENDAR)}
                            if(linked) Row(Modifier.padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Action("Comptes Android") {androidAccounts()}
                                Action("Dissocier ce profil") {unlink(account.optBoolean("connected"))}
                            }
                            if(linked && CalendarAccess.granted(this@ProfileActivity)) Text("Agenda : calendriers visibles et synchronisés de ce compte uniquement.",Modifier.padding(top=10.dp),color=Muted)
                            if(account.optBoolean("configured") || intent.getBooleanExtra("google_services",false)) {
                                Action(if(extraServices) "Masquer les services facultatifs" else "Services Google facultatifs",R.drawable.ui_more,Modifier.padding(top=18.dp)) {extraServices=!extraServices}
                                if(extraServices) {
                                    Text("Drive permet une sauvegarde cloud sur demande. Votre autorisation Google sera demandée séparément.",Modifier.padding(top=10.dp),color=Muted)
                                    if(account.optBoolean("connected")) Text("Accès Google supplémentaires autorisés",Modifier.padding(top=10.dp),color=Muted)
                                    else if(account.optBoolean("configured")) Action("Autoriser les sauvegardes Drive",R.drawable.ui_save,Modifier.padding(top=10.dp)) {connect(false)}
                                    else {
                                        Text("Cette option demande un client Google TV personnel. Les comptes de la box et les sauvegardes locales fonctionnent sans cette configuration.",Modifier.padding(top=10.dp),color=Muted)
                                        Action("Configurer le client Google TV",R.drawable.ui_link,Modifier.padding(top=10.dp)) {setup()}
                                    }
                                }
                            }
                        }
                        if(message.isNotBlank()) Text(message,Modifier.padding(top=14.dp),color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    @Composable private fun ProfileTile(id:String,name:String,revision:Int,guest:Boolean=false,active:Boolean=false,onClick:()->Unit) {
        var focused by remember {mutableStateOf(false)}
        val motion=focusProgress(focused)
        val requester=remember {FocusRequester()}
        LaunchedEffect(Unit) {if(active){withFrameNanos{};runCatching {requester.requestFocus()}}}
        Column(Modifier.width(132.dp).graphicsLayer {val scale=1f+.055f*motion.value;scaleX=scale;scaleY=scale}
            .focusRequester(requester).onFocusChanged {focused=it.isFocused}
            .border(2.dp,if(focused) MaterialTheme.colorScheme.primary else if(active) Muted else androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface,RoundedCornerShape(22.dp))
            .combinedClickable(onClick=onClick,interactionSource=null,indication=null).semantics {role=Role.Button;contentDescription=if(id=="guest") "Choisir le mode invité" else "Profil $name"}
            .padding(vertical=18.dp,horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            UserAvatar(id,name,revision,72.dp,guest)
            Text(name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)
            Text(if(active) "Actif" else if(id=="guest") "Une session privée" else if(id=="add") "Compte de la box" else if(UserAccounts.identity(this@ProfileActivity,id).optString("email").isNotBlank()) "Google" else "Local",color=Muted,style=MaterialTheme.typography.bodySmall)
        }
    }
    private fun connect(add:Boolean) {
        if(!GoogleLink.status(this).optBoolean("configured")){message="La connexion Google demande la configuration initiale du projet MarOS. Le guide Google Cloud est prêt dans le dépôt.";return}
        linking?.cancel();linking=lifecycleScope.launch {
            try {
                pending=withContext(Dispatchers.IO) {GoogleLink.begin(this@ProfileActivity,add)}
                withContext(Dispatchers.IO) {GoogleLink.awaitSignIn(this@ProfileActivity)}
                pending=JSONObject();revision++
                message="Accès Google supplémentaires autorisés. Les sauvegardes Drive sont disponibles sur demande."
            } catch(_:CancellationException) {} catch(e:Exception) {pending=JSONObject();message=e.message?:"Connexion indisponible"}
        }
    }
    private fun chooseDeviceAccount(add: Boolean) {
        linking?.cancel();GoogleLink.cancelPending();pending=JSONObject();message=""
        nativeAdd=add;nativeProfile=Profiles(this).current
        runCatching {accountPicker.launch(DeviceAccounts.picker(this))}.onFailure {
            message="Le sélecteur de comptes est indisponible. Vérifiez les comptes dans Android TV, puis réessayez."
        }
    }
    private fun androidAccounts() {
        runCatching {startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))}.recoverCatching {startActivity(Intent(Settings.ACTION_SETTINGS))}
            .onFailure {message="Les paramètres Android sont indisponibles."}
    }
    private fun unlink(cloud: Boolean) {
        AlertDialog.Builder(this).setTitle("Dissocier ce profil ?")
            .setMessage("Le compte reste connecté à Android TV. ${if(cloud) "Les accès Google accordés à MarOS seront révoqués." else "L’association et les données d’identité de ce profil seront effacées."}")
            .setNegativeButton("Annuler",null).setPositiveButton("Dissocier") {_,_->lifecycleScope.launch {
                message=if(cloud) withContext(Dispatchers.IO) {GoogleLink.disconnect(this@ProfileActivity)}
                    else {UserAccounts.clear(this@ProfileActivity);"Profil dissocié ; le compte Android est conservé."}
                revision++
            }}.show()
    }
    private fun setup() {
        val column=android.widget.LinearLayout(this).apply {orientation=android.widget.LinearLayout.VERTICAL;setPadding(24,12,24,12)}
        val client=EditText(this).apply {hint="ID client OAuth TV";setSingleLine(true);setText(Secrets(this@ProfileActivity).get("google_client_id"))}
        val secret=EditText(this).apply {hint="Secret du client TV";setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD}
        column.addView(client);column.addView(secret)
        val dialog=AlertDialog.Builder(this).setTitle("Configuration initiale Google").setMessage("Client TV et appareils à saisie limitée de votre projet MarOS. Le mot de passe Google reste saisi chez Google.").setView(column).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",null).create()
        dialog.setOnShowListener {dialog.getButton(-1).setOnClickListener {runCatching {GoogleLink.configure(this,client.text.toString().trim(),secret.text.toString().trim());message="Connexion prête";revision++;dialog.dismiss()}.onFailure {secret.error=it.message}}};dialog.show()
    }
    override fun onStop(){linking?.cancel();GoogleLink.cancelPending();pending=JSONObject();super.onStop()}
    override fun onResume(){super.onResume();revision++}
    override fun onSaveInstanceState(outState:Bundle){outState.putBoolean("native_add",nativeAdd);outState.putString("native_profile",nativeProfile);super.onSaveInstanceState(outState)}
    override fun onDestroy(){prefs.prefs().unregisterOnSharedPreferenceChangeListener(changes);super.onDestroy()}
}
