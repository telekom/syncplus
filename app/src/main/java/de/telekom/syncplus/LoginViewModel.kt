package de.telekom.syncplus

import android.accounts.Account
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.telekom.dtagsyncpluskit.api.IDMEnv
import de.telekom.dtagsyncpluskit.api.TokenStore
import de.telekom.dtagsyncpluskit.auth.IDMAuth
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.model.AuthHolder
import de.telekom.dtagsyncpluskit.utils.CountlyWrapper
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.dtagsyncpluskit.utils.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    sealed interface Action {
        class StartLogIn(val intent: Intent) : Action

        class ShowLoginError(val message: String? = null) : Action

        object ShowLoginErrorWithBooking : Action

        object ShowLoginDuplicated : Action

        object Finish : Action

        class NavigateNext(val authHolder: AuthHolder) : Action
    }

    private val _action = MutableSharedFlow<Action>()
    val action: SharedFlow<Action> = _action.asSharedFlow()

    private val idmAuth by lazy { IDMAuth(app) }

    fun processAuthResult(activityResult: ActivityResult) {
        try {
            idmAuth.onActivityResult(
                IDMAuth.RC_AUTH,
                activityResult.resultCode,
                activityResult.data,
            )
        } catch (ex: Exception) {
            Logger.log.severe("Login Error 0: $ex")
            Log.e("SyncPlus", "Login Error 0", ex)
            emitAction(Action.ShowLoginError(ex.localizedMessage))
        }
    }

    fun startLogin(
        account: Account?,
        isReloging: Boolean,
        loginHint: String?,
    ) {
        idmAuth.setErrorHandler { ex ->
            Logger.log.severe("Login Error 1: $ex")
            Log.e("SyncPlus", "Login Error 1", ex)
            emitAction(Action.ShowLoginError(ex.localizedMessage))
        }
        idmAuth.setSuccessHandler { store ->
            try {
                Logger.log.severe("Login Success! (${store.getAlias()})")
                if (isReloging) {
                    reloginAccount(store, account)
                } else {
                    createAccount(store)
                }
            } catch (ex: Exception) {
                Logger.log.severe("Login Error 2: $ex")
                Log.e("SyncPlus", "Login Error 2", ex)
                emitAction(Action.ShowLoginError(ex.localizedMessage))
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val redirectUri = Uri.parse(getApplication<App>().getString(R.string.REDIRECT_URI))
                val env = IDMEnv.fromBuildConfig(BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!)
                val intent = idmAuth.getLoginProcessIntent(redirectUri, loginHint, env)

                emitAction(Action.StartLogIn(intent))
            } catch (ex: Exception) {
                Logger.log.severe("Login Error 3: $ex")
                Log.e("SyncPlus", "Login Error 3", ex)
                emitAction(Action.ShowLoginError(ex.localizedMessage))
            }
        }
    }

    private fun reloginAccount(
        store: TokenStore,
        account: Account?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // Logged in account differs from old account.
            if (account == null || account.name != store.getAlias()) {
                emitAction(Action.ShowLoginError())
                return@launch
            }

            val accountSettings = AccountSettings(getApplication(), account)
            val credentials = accountSettings.getCredentials()
            credentials.idToken = store.getIdToken()
            credentials.accessToken = store.getAccessToken()
            credentials.setRefreshToken(store.getRefreshToken())

            emitAction(Action.Finish)
        }
    }

    private fun createAccount(store: TokenStore) {
        viewModelScope.launch(Dispatchers.IO) {
            val accountName = store.getAlias()
            if (accountName == null) {
                emitAction(Action.ShowLoginErrorWithBooking)
                return@launch
            }

            val serviceEnvironments = App.serviceEnvironments()
            val accountManager = IDMAccountManager(getApplication())
            if (accountManager.getAccounts().find { it.name == accountName } != null) {
                emitAction(Action.ShowLoginDuplicated)
                return@launch
            }

            getApplication<App>().inSetup = true
            val authHolder = AuthHolder(accountName, store)
            if (authHolder.createAccount(getApplication(), serviceEnvironments) == null) {
                Logger.log.severe("Error: Creating Account")
                getApplication<App>().inSetup = false
                emitAction(Action.ShowLoginError("Error creating account."))
                return@launch
            }

            // Set the ID for countly.
            val deviceId = accountManager.getAccounts().first().name.sha256()
            CountlyWrapper.changeDeviceIdWithMerge(deviceId)

            emitAction(Action.NavigateNext(authHolder))
        }
    }

    private fun emitAction(action: Action) {
        viewModelScope.launch {
            _action.emit(action)
        }
    }
}
