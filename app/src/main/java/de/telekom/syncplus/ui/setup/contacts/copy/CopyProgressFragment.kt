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

package de.telekom.syncplus.ui.setup.contacts.copy

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
import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentCopyProgressBinding
import de.telekom.syncplus.ui.dialog.CustomAlertDialog
import de.telekom.syncplus.ui.dialog.CustomErrorAlert
import de.telekom.syncplus.ui.dialog.SingleActionDialog
import de.telekom.syncplus.ui.main.TopBarActivity
import de.telekom.syncplus.ui.setup.contacts.duplicates.DuplicatesFoundFragment
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.launch

class CopyProgressFragment : BaseFragment(R.layout.fragment_copy_progress) {
    override val TAG = "COPY_PROGRESS_FRAGMENT"

    private val viewModel: CopyProgressViewModel by activityViewModels()
    private val groups: List<Group>? by lazy {
        val intent = activity?.intent ?: throw IllegalStateException("Intent must not be null")
        intent.getParcelableArrayListExtra<Group>(ContactsCopyActivity.EXTRA_GROUPS)
    }

    companion object {
        private const val EXTRA_ACCOUNT_NAME = "EXTRA_ACCOUNT_NAME"

        private const val CONFIRM_ACTION = "confirm_action"
        private const val CANCEL_ACTION = "cancel_action"
        private const val RETRY_COPY_ACTION = "retry_copy_action"

        fun newInstance(accountName: String): CopyProgressFragment {
            return CopyProgressFragment().apply {
                arguments = bundleOf(EXTRA_ACCOUNT_NAME to accountName)
            }
        }
    }

    private val accountName by extraNotNull<String>(EXTRA_ACCOUNT_NAME)
    private val binding by viewBinding(FragmentCopyProgressBinding::bind)

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

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.skipButton.setOnClickListener {
            showConfirmationDialog()
        }
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

        viewModel.uploadContacts(accountName, groups)
    }

    private fun handleAction(action: CopyProgressViewModel.Action) {
        when (action) {
            is CopyProgressViewModel.Action.NavigateToCopySuccess ->
                push(
                    R.id.container,
                    CopySuccessFragment.newInstance(accountName, action.importContacts),
                )

            CopyProgressViewModel.Action.NavigateToDuplicates ->
                push(
                    R.id.container,
                    DuplicatesFoundFragment.newInstance(accountName),
                )

            CopyProgressViewModel.Action.ShowRetry -> showErrorDialog()
            CopyProgressViewModel.Action.ShowContactLimitError -> showContactLimitError()
            CopyProgressViewModel.Action.UploadCancelled ->
                finishWithResult(
                    Activity.RESULT_CANCELED,
                    null,
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
            actionText = getString(R.string.dialog_max_contact_count_error_action),
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
