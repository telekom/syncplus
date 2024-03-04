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
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import de.telekom.dtagsyncpluskit.api.APIFactory
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.api.SpicaAPI
import de.telekom.dtagsyncpluskit.api.error.ApiError
import de.telekom.dtagsyncpluskit.await
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import de.telekom.dtagsyncpluskit.model.AuthHolder
import de.telekom.dtagsyncpluskit.model.spica.Contact
import de.telekom.dtagsyncpluskit.model.spica.ContactIdentifiersResponse
import de.telekom.dtagsyncpluskit.model.spica.ContactList
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.utils.Err
import de.telekom.dtagsyncpluskit.utils.Ok
import de.telekom.dtagsyncpluskit.utils.ResultExt
import de.telekom.syncplus.App
import de.telekom.syncplus.BuildConfig
import de.telekom.syncplus.ContactsCopyActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentCopyProgressBinding
import de.telekom.syncplus.ui.main.DuplicateProgressViewModel.Action
import de.telekom.syncplus.ui.main.dialog.SingleActionDialog
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.Delegates

class DuplicateProgressFragment : BaseFragment(R.layout.fragment_copy_progress) {
    override val TAG: String
        get() = "DUPLICATE_PROGRESS_FRAGMENT"

    companion object {
        private const val RETRY_DUPLICATE_ACTION = "retry_duplicate_action"

        fun newInstance(): DuplicateProgressFragment {
            val args = Bundle()
            val fragment = DuplicateProgressFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private val viewModel by activityViewModels<DuplicateProgressViewModel>()
    private val binding by viewBinding(FragmentCopyProgressBinding::bind)
    private val authHolder by lazy {
        (activity as? ContactsCopyActivity)?.authHolder
            ?: throw IllegalStateException("Missing AuthHolder")
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.skipButton.setOnClickListener {
            DuplicatesFoundFragment.showSkipDialog(requireActivity()) {
                finishWithResult(Activity.RESULT_CANCELED, null)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.action.collect(::handleAction) }
            }
        }

        setFragmentResultListener(SingleActionDialog.ACTION_EVENT) { _, _ -> viewModel.skipUpload() }
        setFragmentResultListener(RETRY_DUPLICATE_ACTION) { _, extras ->
            val retry = extras.getBoolean(RETRY_DUPLICATE_ACTION)
            if (retry) {
                viewModel.retryUpload()
            } else {
                viewModel.cancelUpload()
            }
        }

        viewModel.uploadContacts(authHolder)
    }

    override fun onStart() {
        super.onStart()
        val currentStep = activity?.intent?.getIntExtra(ContactsCopyActivity.EXTRA_STEP, -1)
        val maxSteps = activity?.intent?.getIntExtra(ContactsCopyActivity.EXTRA_MAX_STEPS, -1)
        val topBar = (activity as TopBarActivity).topBar
        topBar.currentStep = currentStep ?: -1
        topBar.maxSteps = maxSteps ?: -1
        topBar.description = getString(R.string.copy_contacts_in_progress)
        topBar.hasBackButton = false
        topBar.hasHelpButton = false
        topBar.large = true
        topBar.extraDrawable = R.drawable.ic_cloud_progress
        topBar.extraTitle = null
        topBar.extraDrawableSmall = 0
        topBar.extraDescription = null
        topBar.extraSectionButtonTitle = null
    }

    private fun handleAction(action: Action) {
        when (action) {
            Action.ShowRetry -> showRetryDialog()
            Action.UploadCancelled -> finishWithResult(Activity.RESULT_CANCELED, null)
            Action.UploadFinished -> push(R.id.container, CopySuccessFragment.newInstance())
            Action.ShowContactsLimitError -> showContactLimitError()
        }
    }

    private fun showRetryDialog(errorDescription: String? = null) {
        val dialog =
            CustomErrorAlert(
                getString(R.string.dialog_sync_error_title),
                getString(R.string.dialog_sync_error_text),
                errorDescription,
            ) { retry ->
                setFragmentResult(RETRY_DUPLICATE_ACTION, bundleOf(RETRY_DUPLICATE_ACTION to retry))
            }

        dialog.show(parentFragmentManager, "DIALOG")
    }

    private fun showContactLimitError() {
        SingleActionDialog.instantiate(
            titleText = getString(R.string.dialog_max_contact_count_error_title),
            messageText = getString(R.string.dialog_max_contact_count_error_message),
            actionText = getString(R.string.dialog_max_contact_count_error_action),
        ).show(parentFragmentManager, "ContactLimitError")
    }
}

class DuplicateProgressViewModel(private val app: Application) : AndroidViewModel(app) {
    private val actionFlow = MutableSharedFlow<Action>()
    private var authHolder: AuthHolder by Delegates.notNull()
    private val contactsToUpload = ConcurrentLinkedQueue<Contact>()

    val action: SharedFlow<Action> = actionFlow.asSharedFlow()

    private val lastError = AtomicReference<ApiError>()

    fun uploadContacts(authHolder: AuthHolder) {
        this.authHolder = authHolder
        val originals = (app as App).originals

        viewModelScope.launch(Dispatchers.IO) {
            if (originals == null) {
                // Nothing to upload
                actionFlow.emit(Action.UploadFinished)
                return@launch
            }

            contactsToUpload.clear()
            contactsToUpload.addAll(originals)

            uploadContacts(authHolder.accountName)
        }
    }

    fun retryUpload() {
        viewModelScope.launch(Dispatchers.IO) {
            uploadContacts(authHolder.accountName)
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

    private suspend fun uploadContacts(accountName: String) {
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
