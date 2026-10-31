package io.github.marouane4142.maros

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.marouane4142.maros.tv.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** HOME activity owns one persistent composition, including after returning from another app. */
class MainActivity : ComponentActivity() {
    private val model: HomeViewModel by viewModels()
    private var foreground = false
    private val controls = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, intent: Intent) {
            if (!foreground) return
            interaction = android.os.SystemClock.elapsedRealtime()
            if (intent.getStringExtra("command") in setOf("phone_search", "timer_fired")) { checkPhone(); return }
            val key = when(intent.getStringExtra("command")) {
                "up" -> KeyEvent.KEYCODE_DPAD_UP; "down" -> KeyEvent.KEYCODE_DPAD_DOWN
                "left" -> KeyEvent.KEYCODE_DPAD_LEFT; "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
                "ok" -> KeyEvent.KEYCODE_DPAD_CENTER; "back" -> KeyEvent.KEYCODE_BACK; else -> return
            }
            val now = android.os.SystemClock.uptimeMillis()
            if (key == KeyEvent.KEYCODE_BACK) onBackPressedDispatcher.onBackPressed() else {
                window.decorView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0))
                window.decorView.dispatchKeyEvent(KeyEvent(now, now + 1, KeyEvent.ACTION_UP, key, 0))
            }
        }
    }
    private var interaction = android.os.SystemClock.elapsedRealtime()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894
        androidx.core.content.ContextCompat.registerReceiver(this, controls, android.content.IntentFilter(RoomData.ACTION_CONTROL), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        LauncherPrefs(this).prefs().edit().remove("recent_apps").apply()
        UserAccounts.migrate(this)
        handle(intent)
        setContent { MarosHome(model) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.refresh(); model.refreshServices()
                while (true) {
                    model.clock(); checkPhone()
                    val minutes = model.prefs.getInt("ambient_minutes", 5)
                    if (minutes > 0 && android.os.SystemClock.elapsedRealtime() - interaction > minutes * 60000L) {
                        interaction = android.os.SystemClock.elapsedRealtime()
                        startActivity(Intent(this@MainActivity, AmbientActivity::class.java))
                    }
                    delay((60_000 - System.currentTimeMillis() % 60_000).coerceAtLeast(1000))
                }
            }
        }
    }
    override fun onUserInteraction() { super.onUserInteraction(); interaction = android.os.SystemClock.elapsedRealtime() }
    override fun onResume() { super.onResume(); foreground = true; RoomData.launcherVisible = true; model.visible(true); interaction = android.os.SystemClock.elapsedRealtime() }
    override fun onPause() { foreground = false; RoomData.launcherVisible = false; model.visible(false); model.persistFocus(); super.onPause() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent); interaction = android.os.SystemClock.elapsedRealtime() }
    private fun handle(value: Intent) {
        if(value.getBooleanExtra("open_profile",false)) startActivity(Intent(this,ProfileActivity::class.java))
        value.getStringExtra("experience")?.takeIf { it in setOf("overview","google","phone","cast","calendar","watchlist","scenes","guests","photos","quick","backup") }?.let { ExperienceActivity.open(this, it) }
        if (value.getBooleanExtra("room_home", false)) model.returnHome()
        if (value.action == Intent.ACTION_MAIN && value.hasCategory(Intent.CATEGORY_HOME)) model.returnHome()
        if (value.getBooleanExtra("open_apps", false) || value.action == "io.github.marouane4142.maros.SHOW_APPS") model.selectTab("apps")
        if (value.getStringExtra("collection") == "reprendre") HubActivity.open(this, "history")
        value.getStringExtra("collection")?.let { if (it in listOf("accueil", "regarder", "musique", "jeux", "outils", "apps")) model.selectTab(it) }
    }
    fun requestViewingAccess() {
        requestPermissions(arrayOf("android.permission.READ_TV_LISTINGS"), 812)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 812) model.refresh()
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> { startActivity(Intent(this, StudioActivity::class.java)); return true }
                KeyEvent.KEYCODE_SEARCH -> { HubActivity.open(this, "search"); return true }
                KeyEvent.KEYCODE_ALL_APPS -> { model.selectTab("apps"); return true }
            }
        }
        return super.onKeyUp(keyCode, event)
    }
    private fun checkPhone() {
        val query = model.prefs.getString("phone_pending_search", "")
        if (query.isNotEmpty()) {
            model.prefs.prefs().edit().remove("phone_pending_search").apply()
            startActivity(Intent(this, HubActivity::class.java).putExtra("section", "search").putExtra("query", query))
        }
        val p = model.prefs.prefs()
        val fired = p.getLong("timer_last_fired", 0)
        if (fired > p.getLong("timer_last_ack", 0)) {
            p.edit().putLong("timer_last_ack", fired).apply()
            android.app.AlertDialog.Builder(this).setTitle("Votre minuteur est terminé")
                .setPositiveButton("Fermer", null).show()
        }
    }
    override fun onDestroy() { unregisterReceiver(controls); super.onDestroy() }
    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if (level >= 20) model.trim() }
}
