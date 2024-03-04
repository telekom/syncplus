package de.telekom.syncplus.util

import android.accounts.Account
import androidx.fragment.app.Fragment

interface AccountObserver {
    fun init(fragment: Fragment)

    fun getAddedAccounts(): Set<Account>
}
