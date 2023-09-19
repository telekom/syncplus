package de.telekom.syncplus.util

import android.accounts.Account
import android.accounts.AccountManager
import androidx.fragment.app.Fragment

class AccountObserverDelegate : AccountObserver {

    private lateinit var accountManager: AccountManager

    private val storedAccounts = mutableSetOf<Account>()

    override fun init(fragment: Fragment) {
        accountManager = AccountManager.get(fragment.requireContext())

        storedAccounts.addAll(accountManager.accounts)
    }

    override fun getAddedAccounts(): Set<Account> {
        return accountManager.accounts
            .toSet()
            .subtract(storedAccounts)
    }
}