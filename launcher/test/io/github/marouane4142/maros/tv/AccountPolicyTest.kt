package io.github.marouane4142.maros.tv

import org.junit.Assert.*
import org.junit.Test

class AccountPolicyTest {
    @Test fun nativeAndAuthorizedAccountsOnlyMergeOnExactGoogleIdentity() {
        assertTrue(AccountPolicy.sameGoogleAccount("Alice@example.test", "com.google", "alice@example.test", "com.google"))
        assertFalse(AccountPolicy.sameGoogleAccount("alice@example.test", "com.google", "bob@example.test", "com.google"))
        assertFalse(AccountPolicy.sameGoogleAccount("alice@example.test", "local", "alice@example.test", "com.google"))
        assertFalse(AccountPolicy.sameGoogleAccount("", "com.google", "", "com.google"))
        assertFalse(AccountPolicy.sameGoogleAccount("a.lice@example.test", "com.google", "alice@example.test", "com.google"))
    }
    @Test fun calendarFollowsOnlyTheSignedInGoogleAccount() {
        assertTrue(AccountPolicy.includesCalendar("alice@example.test","Alice@example.test","com.google",true,true))
        assertFalse(AccountPolicy.includesCalendar("bob@example.test","alice@example.test","com.google",true,true))
    }
    @Test fun disconnectedAndLocalAccountsCannotReadTheGoogleAgenda() {
        assertFalse(AccountPolicy.includesCalendar("","alice@example.test","com.google",true,true))
        assertFalse(AccountPolicy.includesCalendar("alice@example.test","alice@example.test","local",true,true))
    }
    @Test fun androidVisibilityAndSyncChoicesAreRespected() {
        assertFalse(AccountPolicy.includesCalendar("alice@example.test","alice@example.test","com.google",false,true))
        assertFalse(AccountPolicy.includesCalendar("alice@example.test","alice@example.test","com.google",true,false))
    }
}
