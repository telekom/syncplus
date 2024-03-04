package de.telekom.syncplus

import android.accounts.Account
import android.app.Application
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.telekom.dtagsyncpluskit.davx5.Constants.DEFAULT_SYNC_INTERVAL
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class WelcomeViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface Action {
        class StartAccountsActivity(val wasAccountsDeleted: Boolean) : Action
        class DisplayWelcomeFragment(val wasAccountsDeleted: Boolean) : Action
    }

    sealed interface ViewEvent {
        class TryContinueWithApp(val noRedirect: Boolean) : ViewEvent
    }

    private val _action = MutableSharedFlow<Action>()
    val action: SharedFlow<Action> = _action.asSharedFlow()

    var wasPermissionsRequested = false

    fun viewEvent(event: ViewEvent) {
        when (event) {
            is ViewEvent.TryContinueWithApp -> processContinueEvent(event.noRedirect)
        }
    }

    private fun processContinueEvent(noRedirect: Boolean) {
        val accountManager = IDMAccountManager(getApplication())

        viewModelScope.launch {
            // Verify that all accounts have been setup completely.
            var deletedAccounts = 0
            for (account in accountManager.getAccounts()) {
                if (accountManager.isSetupCompleted(account)) {
                    migrateAccountIfNeeded(account)
                    continue
                }
                if (accountManager.removeAccount(account)) {
                    Logger.log.info("Account deleted: $account")
                    deletedAccounts++
                }
            }

            Logger.log.info("Accounts: ${accountManager.getAccounts().map { it.name }}")
            if (!noRedirect && accountManager.getAccounts().isNotEmpty()) {
                _action.emit(Action.StartAccountsActivity(deletedAccounts > 0))
            } else {
                _action.emit(Action.DisplayWelcomeFragment(deletedAccounts > 0))
            }
        }
    }

    private fun migrateAccountIfNeeded(account: Account) {
        Logger.log.info("Account (${account}) migration in progress")
        with(AccountSettings(getApplication(), account)) {
            setContactSyncEnabled(isContactSyncEnabled())

            val addressBookAuthority = getApplication<App>().getString(R.string.address_books_authority)
            val oldSyncInterval = getSyncInterval(addressBookAuthority)
            if (getSyncInterval(ContactsContract.AUTHORITY) == null) {
                setSyncInterval(ContactsContract.AUTHORITY, oldSyncInterval ?: DEFAULT_SYNC_INTERVAL)
            }
        }
    }
}
