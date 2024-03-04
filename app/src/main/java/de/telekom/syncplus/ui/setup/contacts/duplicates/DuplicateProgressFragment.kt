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

package de.telekom.syncplus.ui.setup.contacts.duplicates

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentCopyProgressBinding
import de.telekom.syncplus.ui.dialog.CustomErrorAlert
import de.telekom.syncplus.ui.dialog.SingleActionDialog
import de.telekom.syncplus.ui.main.TopBarActivity
import de.telekom.syncplus.ui.setup.contacts.copy.ContactsCopyActivity
import de.telekom.syncplus.ui.setup.contacts.copy.CopySuccessFragment
import de.telekom.syncplus.ui.setup.contacts.duplicates.DuplicateProgressViewModel.Action
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.launch

class DuplicateProgressFragment : BaseFragment(R.layout.fragment_copy_progress) {
    override val TAG: String
        get() = "DUPLICATE_PROGRESS_FRAGMENT"

    companion object {
        private const val RETRY_DUPLICATE_ACTION = "retry_duplicate_action"
        private const val EXTRA_ACCOUNT_NAME = "extra_account_name"

        fun newInstance(accountName: String): DuplicateProgressFragment {
            return DuplicateProgressFragment().apply {
                arguments = bundleOf(EXTRA_ACCOUNT_NAME to accountName)
            }
        }
    }

    private val viewModel by activityViewModels<DuplicateProgressViewModel>()
    private val binding by viewBinding(FragmentCopyProgressBinding::bind)
    private val accountName by extraNotNull<String>(EXTRA_ACCOUNT_NAME)

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

        viewModel.uploadContacts(accountName)
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
            Action.UploadFinished -> push(R.id.container, CopySuccessFragment.newInstance(accountName, false))
            Action.ShowContactsLimitError -> showContactLimitError()
        }
    }

    private fun showRetryDialog(errorDescription: String? = null) {
        val dialog = CustomErrorAlert(
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