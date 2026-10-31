package io.github.marouane4142.maros.tv

/** Exact account matching; never fall back to every calendar on a shared TV. */
object AccountPolicy {
    fun sameGoogleAccount(email: String, type: String, otherEmail: String, otherType: String) =
        email.isNotBlank() && otherEmail.isNotBlank() && type == "com.google" && otherType == "com.google" && email.equals(otherEmail, ignoreCase = true)

    fun includesCalendar(email: String, owner: String, type: String, visible: Boolean, synchronized: Boolean) =
        email.isNotBlank() && owner.equals(email, ignoreCase = true) && type == "com.google" && visible && synchronized
}
