package de.telekom.syncplus.ui.setup.contacts.copy

import android.accounts.Account
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.telekom.dtagsyncpluskit.api.APIFactory
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.api.SpicaAPI
import de.telekom.dtagsyncpluskit.api.error.ApiError
import de.telekom.dtagsyncpluskit.await
import de.telekom.dtagsyncpluskit.contacts.ContactsFetcher
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.model.spica.Contact
import de.telekom.dtagsyncpluskit.model.spica.ContactList
import de.telekom.dtagsyncpluskit.model.spica.Duplicate
import de.telekom.dtagsyncpluskit.model.spica.DuplicatesResponse
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


class CopyProgressViewModel(private val app: Application) : AndroidViewModel(app) {

    private val actionFlow = MutableSharedFlow<Action>()
    private var accountName: String by Delegates.notNull()
    private val contactsToUpload = ConcurrentLinkedQueue<Contact>()
    private val duplicates = ConcurrentLinkedQueue<Duplicate>()
    private val originals = ConcurrentLinkedQueue<Contact>()

    private var lastError = AtomicReference<ApiError>()

    val action: SharedFlow<Action> = actionFlow.asSharedFlow()

    fun uploadContacts(
        accountName: String,
        groups: List<Group>?
    ) {
        this.accountName = accountName
        val fetcher = ContactsFetcher(getApplication())

        viewModelScope.launch(Dispatchers.IO) {
            val contacts = groups?.flatMap {
                fetcher.allContacts(it.groupId)
            } ?: fetcher.allContacts()

            duplicates.clear()
            originals.clear()
            originals.addAll(contacts)

            contactsToUpload.clear()
            contactsToUpload.addAll(contacts)

            uploadContacts(accountName)
        }
    }

    fun retryUpload() {
        viewModelScope.launch(Dispatchers.IO) {
            uploadContacts(accountName)
        }
    }

    fun cancelUpload() {
        viewModelScope.launch {
            actionFlow.emit(Action.UploadCancelled)
        }
    }

    fun skipUpload() {
        viewModelScope.launch {
            actionFlow.emit(Action.NavigateToCopySuccess(false))
        }
    }

    private suspend fun uploadContacts(accountName: String) {
        val chunks = contactsToUpload.chunked(DEFAULT_CHUNK_SIZE)

        run loop@{
            chunks.forEach { chunk ->
                when (val response = uploadContactsChunk(accountName, chunk)) {
                    is Ok -> {
                        // Remove uploaded chunk
                        contactsToUpload.removeAll(chunk.toSet())
                        response.value.duplicates?.let {
                            duplicates.addAll(it)
                        }
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
            with(app as App) {
                duplicates = this@CopyProgressViewModel.duplicates.toList()
                originals = this@CopyProgressViewModel.originals.toList()
            }

            if (duplicates.isEmpty()) {
                actionFlow.emit(Action.NavigateToCopySuccess(true))
            } else {
                actionFlow.emit(Action.NavigateToDuplicates)
            }
            return
        }
        // show retry otherwise
        when (lastError.get()) {
            ApiError.ContactError.TooManyContacts -> actionFlow.emit(Action.ShowContactLimitError)
            else -> actionFlow.emit(Action.ShowRetry)
        }
    }

    private suspend fun uploadContactsChunk(
        accountName: String,
        contacts: List<Contact>,
    ): ResultExt<DuplicatesResponse, ApiError> {
        return buildSpicaApi(accountName)
            .checkDuplicates(duplicates = ContactList(contacts))
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
        class NavigateToCopySuccess(val importContacts: Boolean) : Action
        object NavigateToDuplicates : Action
        object ShowContactLimitError : Action
        object ShowRetry : Action
        object UploadCancelled : Action
    }

    private companion object {
        private const val DEFAULT_CHUNK_SIZE = 9999
    }
}
