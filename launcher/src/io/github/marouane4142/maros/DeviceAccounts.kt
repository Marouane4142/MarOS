package io.github.marouane4142.maros

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.ComponentName

/** Android grants account visibility through its own TV account picker. No OAuth token is read. */
object DeviceAccounts {
    const val GOOGLE = "com.google"

    fun visible(c: Context): List<Account> = runCatching {
        AccountManager.get(c).getAccountsByType(GOOGLE).toList()
    }.getOrDefault(emptyList())

    fun contains(c: Context, email: String) = visible(c).any { it.name.equals(email, ignoreCase = true) }

    fun picker(c: Context): Intent {
        val selected = visible(c).firstOrNull { it.name.equals(UserAccounts.identity(c).optString("email"), ignoreCase = true) }
        val intent = AccountManager.newChooseAccountIntent(selected, null, arrayOf(GOOGLE),
            "Choisissez le compte Google à utiliser dans MarOS", null, null, null)
        // Some Google TV firmwares delegate this UI to the disabled stock launcher.
        // Keep Android's chooser extras/consent, using its exported platform UI instead.
        val vendor = intent.component
        val stockChooser = ComponentName("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.profile.chooser.ProfileChooserActivity")
        if (!available(c, vendor) || (vendor?.className?.endsWith("NoTouchAccountPickerActivity") == true && !available(c, stockChooser))) {
            val platform = ComponentName("android", "android.accounts.ChooseTypeAndAccountActivity")
            require(available(c, platform)) { "Le sélecteur de comptes Android est indisponible sur cette box." }
            intent.component = platform
        }
        return intent
    }

    private fun available(c: Context, component: ComponentName?): Boolean = component != null && runCatching {
        c.packageManager.resolveActivity(Intent().setComponent(component), 0)?.activityInfo?.let {
            it.exported && it.enabled && it.applicationInfo.enabled
        } == true
    }.getOrDefault(false)

    fun result(c: Context, data: Intent?): Account {
        val name = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME).orEmpty()
        val type = data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE).orEmpty()
        require(type == GOOGLE && name.length in 3..254 && name.none { it.isISOControl() }) { "Aucun compte Google sélectionné" }
        return visible(c).firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw IllegalStateException("Android n’a pas autorisé MarOS à voir ce compte. Réessayez depuis le sélecteur de la box.")
    }
}
