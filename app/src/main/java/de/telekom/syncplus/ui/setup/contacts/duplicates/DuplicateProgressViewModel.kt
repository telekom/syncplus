package de.telekom.syncplus.ui.setup.contacts.duplicates

import android.accounts.Account
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.telekom.dtagsyncpluskit.api.APIFactory
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.api.SpicaAPI
import de.telekom.dtagsyncpluskit.api.error.ApiError
import de.telekom.dtagsyncpluskit.await
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import de.telekom.dtagsyncpluskit.model.spica.Contact
import de.telekom.dtagsyncpluskit.model.spica.ContactIdentifiersResponse
import de.telekom.dtagsyncpluskit.model.spica.ContactList
import de.telekom.dtagsyncpluskit.utils.Err
import de.telekom.dtagsyncpluskit.utils.Ok
import de.telekom.dtagsyncpluskit.utils.ResultExt
import de.telekom.syncplus.App
import de.telekom.syncplus.BuildConfig
import de.telekom.syncplus.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.Delegates


class DuplicateProgressViewModel(private val app: Application) : AndroidViewModel(app) {
    private val actionFlow = MutableSharedFlow<Action>()
    private var accountName: String by Delegates.notNull()
    private val contactsToUpload = ConcurrentLinkedQueue<Contact>()

    val action: SharedFlow<Action> = actionFlow.asSharedFlow()

    private val lastError = AtomicReference<ApiError>()

    fun uploadContacts(accountName: String) {
        this.accountName = accountName
        val originals = (app as App).originals

        viewModelScope.launch(Dispatchers.IO) {
            if (originals == null) {
                // Nothing to upload
                actionFlow.emit(Action.UploadFinished)
                return@launch
            }

            contactsToUpload.clear()
            contactsToUpload.addAll(originals)

            uploadContactsChunked(accountName)
        }
    }

    fun retryUpload() {
        viewModelScope.launch(Dispatchers.IO) {
            uploadContactsChunked(accountName)
        }
    }

    fun skipUpload() {
        viewModelScope.launch {
            actionFlow.emit(Action.UploadFinished)
        }
    }

    fun cancelUpload() {
        viewModelScope.launch {
            actionFlow.emit(Action.UploadCancelled)
        }
    }

    private suspend fun uploadContactsChunked(accountName: String) {
        val chunks = contactsToUpload.chunked(DEFAULT_CHUNK_SIZE)

        run loop@{
            chunks.forEach { chunk ->
                when (val response = uploadContactsChunk(accountName, chunk)) {
                    is Ok -> {
                        // Remove uploaded chunk
                        contactsToUpload.removeAll(chunk.toSet())
                    }

                    is Err -> {
                        Logger.log.severe("Error: Merging Contacts: ${response.error}")
                        lastError.set(response.error)
                        // Break a loop in case of failure
                        return@loop
                    }
                }
            }
        }

        // If there no chunk to upload - all ok
        if (contactsToUpload.isEmpty()) {
            actionFlow.emit(Action.UploadFinished)
            return
        }
        // show retry otherwise
        when (lastError.get()) {
            ApiError.ContactError.TooManyContacts -> actionFlow.emit(Action.ShowContactsLimitError)
            else -> actionFlow.emit(Action.ShowRetry)
        }
    }

    private suspend fun uploadContactsChunk(
        accountName: String,
        contacts: List<Contact>,
    ): ResultExt<ContactIdentifiersResponse, ApiError> {
        return buildSpicaApi(accountName)
            .importAndMergeContacts(contacts = ContactList(contacts))
            .await()
    }

    private fun buildSpicaApi(accountName: String): SpicaAPI {
        val environ = BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!
        val serviceEnvironments = ServiceEnvironments.fromBuildConfig(environ)
        val account = Account(accountName, app.getString(R.string.account_type))
        val credentials = Credentials(app, account, serviceEnvironments)
        return APIFactory.spicaAPI(app, credentials)
    }

    sealed interface Action {
        object UploadFinished : Action

        object UploadCancelled : Action

        object ShowContactsLimitError : Action

        object ShowRetry : Action
    }

    private companion object {
        private const val DEFAULT_CHUNK_SIZE = 9999
    }
}
