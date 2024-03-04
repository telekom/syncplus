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
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import de.telekom.dtagsyncpluskit.api.APIFactory
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.api.error.ApiError
import de.telekom.dtagsyncpluskit.awaitResponse
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import de.telekom.dtagsyncpluskit.model.spica.Contact
import de.telekom.dtagsyncpluskit.model.spica.ContactIdentifiersResponse
import de.telekom.dtagsyncpluskit.model.spica.ContactList
import de.telekom.dtagsyncpluskit.model.spica.ImportContactData
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.utils.ResultExt
import de.telekom.dtagsyncpluskit.utils.isErr
import de.telekom.dtagsyncpluskit.utils.isOk
import de.telekom.syncplus.*
import de.telekom.syncplus.databinding.FragmentCopySuccessBinding
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.launch
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CopySuccessFragment : BaseFragment(R.layout.fragment_copy_success) {

    override val TAG: String
        get() = "COPY_SUCCESS_FRAGMENT"

    companion object {
        private const val EXTRA_IMPORT_CONTACTS = "EXTRA_IMPORT_CONTACTS"

        fun newInstance(importContacts: Boolean = false) =
            CopySuccessFragment().apply {
                arguments = bundleOf(EXTRA_IMPORT_CONTACTS to importContacts)
            }
    }

    private val mOriginals: List<Contact>
        get() = (requireActivity().application as App).originals ?: emptyList()
    private val binding by viewBinding(FragmentCopySuccessBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.nextButton.visibility = View.GONE
        val importContacts = arguments?.getBoolean(EXTRA_IMPORT_CONTACTS) ?: false
        when {
            importContacts -> {
                onImportContacts()
            }

            else -> {
                binding.nextButton.visibility = View.VISIBLE
                binding.nextButton.setOnClickListener {
                    finishWithResult(Activity.RESULT_OK, null)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val topBar = (activity as? TopBarActivity)?.topBar
        topBar?.large = true
        topBar?.description = getString(R.string.copy_success)
        topBar?.extraDrawable = R.drawable.ic_cloud_check
        topBar?.extraDrawableSmall = 0
        topBar?.extraDescription = null
        topBar?.extraSectionButtonTitle = null
        topBar?.hasHelpButton = true
        topBar?.setOnHelpClickListener {
            startActivity(HelpActivity.newIntent(requireActivity()))
        }
    }

    private fun onImportContacts() {
        lifecycleScope.launch {
            val authHolder = (activity as? ContactsCopyActivity)?.authHolder
                ?: throw IllegalStateException("Missing AuthHolder")
            val environ = BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!
            val serviceEnvironments = ServiceEnvironments.fromBuildConfig(environ)
            val account = Account(authHolder.accountName, getString(R.string.account_type))
            val credentials = Credentials(requireContext(), account, serviceEnvironments)
            val spicaAPI = APIFactory.spicaAPI(requireActivity().application, credentials)

            val contactData = ImportContactData(contactList = ContactList(mOriginals))
            var response: ResultExt<Response<ContactIdentifiersResponse>, ApiError>

            var retry = true
            while (retry) {
                response = spicaAPI.importContacts(contactData).awaitResponse()
                when {
                    response.isOk() -> {
                        binding.nextButton.visibility = View.VISIBLE
                        binding.nextButton.setOnClickListener {
                            finishWithResult(Activity.RESULT_OK, null)
                        }
                        break
                    }

                    response.isErr() -> {
                        Logger.log.severe("Error: Importing Contacts: $response")
                        retry = showRetryDialog()
                    }
                }
            }
        }
    }

    private suspend fun showRetryDialog(errorDescription: String? = null): Boolean =
        suspendCoroutine { cont ->
            val dialog = CustomErrorAlert(
                getString(R.string.dialog_sync_error_title),
                getString(R.string.dialog_sync_error_text),
                errorDescription,
            ) { retry -> cont.resume(retry) }
            dialog.show(parentFragmentManager, "DIALOG")
        }
}
