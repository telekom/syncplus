/**
 * This file is part of SyncPlus.
 *
 * Copyright (C) 2020  Deutsche Telekom AG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.telekom.syncplus.ui.main

import android.accounts.Account
import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.*
import de.telekom.dtagsyncpluskit.api.APIFactory
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.api.SpicaAPI
import de.telekom.dtagsyncpluskit.api.error.ApiError
import de.telekom.dtagsyncpluskit.await
import de.telekom.dtagsyncpluskit.awaitResponse
import de.telekom.dtagsyncpluskit.contacts.ContactsFetcher
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.model.AuthHolder
import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.model.spica.Contact
import de.telekom.dtagsyncpluskit.model.spica.ContactList
import de.telekom.dtagsyncpluskit.model.spica.Duplicate
import de.telekom.dtagsyncpluskit.model.spica.DuplicatesResponse
import de.telekom.dtagsyncpluskit.runOnMain
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.utils.Err
import de.telekom.dtagsyncpluskit.utils.Ok
import de.telekom.dtagsyncpluskit.utils.ResultExt
import de.telekom.syncplus.App
import de.telekom.syncplus.BuildConfig
import de.telekom.syncplus.ContactsCopyActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.ui.main.CopyViewModel.UploadProgress.*
import de.telekom.syncplus.ui.main.dialog.SingleActionDialog
import kotlinx.android.synthetic.main.fragment_copy_progress.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.Delegates

@Deprecated(message = "Use CopyProgressViewModel instead", ReplaceWith("CopyProgressViewModel"))
class CopyViewModel(private val app: Application) : AndroidViewModel(app) {
    enum class UploadProgress {
        SUCCESS,
        ERROR,
        UNKNOWN
    }

    private val _progress = MutableLiveData(UNKNOWN)
    val progress: LiveData<UploadProgress> = _progress

    private var _originals: List<Contact>? = null
    fun getOriginals() = _originals

    private var _duplicates: List<Duplicate>? = null
    fun getDuplicates() = _duplicates

    private var _lastError: String? = null
    fun getLastError() = _lastError

    fun uploadContacts(groups: List<Group>?, authHolder: AuthHolder) {
        val fetcher = ContactsFetcher(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            val redirectUri = Uri.parse(app.getString(R.string.REDIRECT_URI))
            val environ = BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!
            val serviceEnvironments = ServiceEnvironments.fromBuildConfig(redirectUri, environ)
            val account = Account(authHolder.accountName, app.getString(R.string.account_type))
            val credentials = Credentials(app, account, serviceEnvironments)
            val spicaAPI = APIFactory.spicaAPI(app, credentials)
            val contacts = if (groups == null) {
                ContactList(fetcher.allContacts())
            } else {
                val contacts = groups.flatMap { fetcher.allContacts(it.groupId) }
                ContactList(contacts)
            }

            // AG: Removed, due to creating too many duplicates in SPICA.
            /*
            val contactData = ImportContactData(contacts, null)
            val importResponse = spicaAPI.importContacts(contactData).awaitResponseOrNull()
            if (importResponse == null || !importResponse.isSuccessful) {
                Log.e("SyncPlus", "Error: importContacts: $importResponse")
                _lastError = importResponse.toString()
                runOnMain { _progress.value = ERROR }
                return@launch
            }
            */

            when (val duplicatesResponse = spicaAPI.checkDuplicates(contacts).awaitResponse()) {
                is Err -> {
                    Logger.log.severe("Error: checkDuplicates: ${duplicatesResponse.error}")
                    _lastError = duplicatesResponse.error.toString()
                    runOnMain { _progress.value = ERROR }
                    return@launch
                }

                is Ok -> {
                    val duplicates = duplicatesResponse.value.body()
                    val count = duplicates?.count ?: 0
                    runOnMain {
                        if (count > 0) {
                            _duplicates = duplicates?.duplicates
                            _originals = contacts.contacts
                            _progress.value = SUCCESS
                        } else {
                            _duplicates = null
                            _originals = contacts.contacts
                            _progress.value = SUCCESS
                        }
                    }
                }
            }
        }
    }
}


class CopyProgressViewModel(private val app: Application) : AndroidViewModel(app) {

    private val actionFlow = MutableSharedFlow<Action>()
    private var authHolder: AuthHolder by Delegates.notNull()
    private val contactsToUpload = ConcurrentLinkedQueue<Contact>()
    private val duplicates = ConcurrentLinkedQueue<Duplicate>()
    private val originals = ConcurrentLinkedQueue<Contact>()

    private var lastError = AtomicReference<ApiError>()

    val action: SharedFlow<Action> = actionFlow.asSharedFlow()

    fun uploadContacts(authHolder: AuthHolder, groups: List<Group>?) {
        this.authHolder = authHolder
        val fetcher = ContactsFetcher(getApplication())

        viewModelScope.launch(Dispatchers.IO) {
            val contacts = groups?.flatMap {
                fetcher.allContacts(it.groupId)
            } ?: fetcher.allContacts()

            originals.clear()
            originals.addAll(contacts)

            contactsToUpload.clear()
            contactsToUpload.addAll(contacts)

            uploadContacts(authHolder.accountName)
        }
    }

    fun retryUpload() {
        viewModelScope.launch(Dispatchers.IO) {
            uploadContacts(authHolder.accountName)
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
        contacts: List<Contact>
    ): ResultExt<DuplicatesResponse, ApiError> {
        return buildSpicaApi(accountName)
            .checkDuplicates(duplicates = ContactList(contacts))
            .await()
    }

    private fun buildSpicaApi(accountName: String): SpicaAPI {
        val redirectUri = Uri.parse(app.getString(R.string.REDIRECT_URI))
        val environ = BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!
        val serviceEnvironments = ServiceEnvironments.fromBuildConfig(redirectUri, environ)
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

class CopyProgressFragment : BaseFragment() {
    override val TAG = "COPY_PROGRESS_FRAGMENT"

    private val viewModel: CopyProgressViewModel by activityViewModels()
    private val groups: List<Group>? by lazy {
        val intent = activity?.intent ?: throw IllegalStateException("Intent must not be null")
        intent.getParcelableArrayListExtra<Group>(ContactsCopyActivity.EXTRA_GROUPS)
    }

    companion object {
        private const val ARG_AUTH_HOLDER = "ARG_AUTH_HOLDER"

        private const val CONFIRM_ACTION = "confirm_action"
        private const val CANCEL_ACTION = "cancel_action"
        private const val RETRY_COPY_ACTION = "retry_copy_action"

        fun newInstance(authHolder: AuthHolder): CopyProgressFragment {
            val args = Bundle()
            args.putParcelable(ARG_AUTH_HOLDER, authHolder)
            val fragment = CopyProgressFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private val mAuthHolder by extraNotNull<AuthHolder>(ARG_AUTH_HOLDER)

    override fun onStart() {
        super.onStart()

        val topBar = (activity as TopBarActivity).topBar
        topBar.currentStep = activity?.intent?.getIntExtra(ContactsCopyActivity.EXTRA_STEP, 0) ?: -1
        topBar.maxSteps =
            activity?.intent?.getIntExtra(ContactsCopyActivity.EXTRA_MAX_STEPS, 0) ?: -1
        topBar.description = getString(R.string.copy_contacts_in_progress)
        topBar.hasBackButton = false
        topBar.hasHelpButton = false
        topBar.large = true
        topBar.extraDrawable = R.drawable.ic_cloud_progress
        topBar.extraTitle = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_copy_progress, container, false)
        v.skipButton.setOnClickListener {
            showConfirmationDialog()
        }
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.action.collect(::handleAction) }
            }
        }

        setFragmentResultListener(SingleActionDialog.ACTION_EVENT) { _, _ -> viewModel.skipUpload() }
        setFragmentResultListener(CONFIRM_ACTION) { _, _ -> showCancelledDialog() }
        setFragmentResultListener(CANCEL_ACTION) { _, _ -> viewModel.cancelUpload() }
        setFragmentResultListener(RETRY_COPY_ACTION) { _, extras ->
            val retry = extras.getBoolean(RETRY_COPY_ACTION)
            if (retry) {
                viewModel.retryUpload()
            } else {
                viewModel.cancelUpload()
            }
        }

        viewModel.uploadContacts(mAuthHolder, groups)
    }

    private fun handleAction(action: CopyProgressViewModel.Action) {
        when (action) {
            is CopyProgressViewModel.Action.NavigateToCopySuccess -> push(
                R.id.container,
                CopySuccessFragment.newInstance(importContacts = action.importContacts)
            )
            CopyProgressViewModel.Action.NavigateToDuplicates -> push(
                R.id.container,
                DuplicatesFoundFragment.newInstance()
            )
            CopyProgressViewModel.Action.ShowRetry -> showErrorDialog()
            CopyProgressViewModel.Action.ShowContactLimitError -> showContactLimitError()
            CopyProgressViewModel.Action.UploadCancelled -> finishWithResult(
                Activity.RESULT_CANCELED,
                null
            )
        }
    }

    private fun showConfirmationDialog() {
        val dialog = CustomAlertDialog()
        dialog.title = getString(R.string.dialog_sync_confirm_title)
        dialog.text = getString(R.string.dialog_sync_confirm_text)
        dialog.cancelText = getString(R.string.button_title_back)
        dialog.successText = getString(R.string.button_title_cancel_copy)
        dialog.setOnCancelListener {}
        dialog.setOnSuccessListener { setFragmentResult(CONFIRM_ACTION, bundleOf()) }
        dialog.show(parentFragmentManager, "DIALOG")
    }

    private fun showCancelledDialog() {
        val dialog = CustomAlertDialog()
        dialog.title = getString(R.string.dialog_sync_cancel)
        dialog.hasCancelButton = false
        dialog.successText = getString(R.string.dialog_button_title_close)
        dialog.setOnSuccessListener { setFragmentResult(CANCEL_ACTION, bundleOf()) }
        dialog.show(parentFragmentManager, "DIALOG")
    }

    private fun showContactLimitError() {
        SingleActionDialog.instantiate(
            titleText = getString(R.string.dialog_max_contact_count_error_title),
            messageText = getString(R.string.dialog_max_contact_count_error_message),
            actionText = getString(R.string.dialog_max_contact_count_error_action)
        ).show(parentFragmentManager, "ContactLimitError")
    }

    private fun showErrorDialog() {
        val dialog = CustomErrorAlert(
            getString(R.string.dialog_sync_error_title),
            getString(R.string.dialog_sync_error_text)
        ) { retry ->
            setFragmentResult(RETRY_COPY_ACTION, bundleOf(RETRY_COPY_ACTION to retry))
        }
        dialog.show(parentFragmentManager, "DIALOG")
    }
}
