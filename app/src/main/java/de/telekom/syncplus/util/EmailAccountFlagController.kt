package de.telekom.syncplus.util

import android.os.SystemClock

// TODO: Remove commented code after successful tests
object EmailAccountFlagController {
    private const val ADD_ACCOUNT_TIMEOUT_MS = 20_000L

    @Volatile
    var isAddAccountStarted: Boolean = false

    @Volatile
    var isInternalAccountSelected: Boolean = false

    private var addAccountInitiatedAt: Long? = null

    fun isWhitelisted(accountType: String): Boolean {
        return listOf("de.telekom.mail").contains(accountType)
    }

    fun initiateAddAccount() {
        isAddAccountStarted = true
        addAccountInitiatedAt = SystemClock.elapsedRealtime()
    }

    fun isTimeoutExceed(): Boolean {
        val elapsedTime = SystemClock.elapsedRealtime() - (addAccountInitiatedAt ?: 0L)
        return ADD_ACCOUNT_TIMEOUT_MS < elapsedTime
    }
}
