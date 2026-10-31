package io.github.marouane4142.maros

import android.app.*
import android.app.job.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat

/** Opt-in work runs while idle on an unmetered network, never in the focus/render path. */
class MaintenanceJob : JobService() {
    private var worker: Thread? = null
    override fun onStartJob(params: JobParameters): Boolean {
        worker = Thread {
            var retry = false
            try {
                val p = LauncherPrefs(this)
                val messages = mutableListOf<String>()
                if (p.getBool("auto_photos", false)) messages += ConnectedFeatures.importImmich(this)
                if (p.getBool("auto_series", false) && !RoomData.guest(this)) {
                    messages += Watchlist(this).refresh()
                    if (p.getBool("series_notifications", false)) notifyReleases()
                }
                p.prefs().edit().putString("maintenance_status", messages.joinToString("\n")).putLong("maintenance_time", System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                retry = e !is InterruptedException
                LauncherPrefs(this).prefs().edit().putString("maintenance_status", e.message ?: "Actualisation interrompue").apply()
            } finally { jobFinished(params, retry) }
        }.apply { name = "MarOS-Maintenance"; start() }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { worker?.interrupt(); return true }
    private fun notifyReleases() {
        val p = LauncherPrefs(this)
        val day = java.time.LocalDate.now().toString()
        if (p.getString("series_notified_day", "") == day) return
        val items = Watchlist(this).items()
        val today = (0 until items.length()).map { items.getJSONObject(it) }.filter { it.optString("next_air") == day }
        if (today.isEmpty() || Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("series", "Sorties de séries", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(this, 6211, Intent(this, ExperienceActivity::class.java).putExtra("section", "watchlist"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(6211, NotificationCompat.Builder(this, "series").setSmallIcon(R.drawable.ui_play).setContentTitle("À suivre aujourd’hui")
            .setContentText(today.joinToString(" · ") { it.optString("title") }).setContentIntent(open).setAutoCancel(true).build())
        p.prefs().edit().putString("series_notified_day", day).apply()
    }
    companion object {
        fun schedule(c: Context) {
            val p = LauncherPrefs(c); val scheduler = c.getSystemService(JobScheduler::class.java)
            if (!p.getBool("auto_photos", false) && !p.getBool("auto_series", false)) { scheduler.cancel(6210); return }
            val info = JobInfo.Builder(6210, ComponentName(c, MaintenanceJob::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setRequiresDeviceIdle(true).setPersisted(true).setPeriodic(6 * 3600000L).build()
            check(scheduler.schedule(info) == JobScheduler.RESULT_SUCCESS) { "Actualisation en arrière-plan indisponible" }
        }
    }
}
