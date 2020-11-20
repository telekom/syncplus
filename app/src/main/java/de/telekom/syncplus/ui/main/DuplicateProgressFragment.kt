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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import de.telekom.dtagsyncpluskit.api.APIFactory
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.awaitResponseOrNull
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import de.telekom.dtagsyncpluskit.model.spica.Contact
import de.telekom.dtagsyncpluskit.model.spica.ContactIdentifiersResponse
import de.telekom.dtagsyncpluskit.model.spica.ContactList
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.App
import de.telekom.syncplus.BuildConfig
import de.telekom.syncplus.ContactsCopyActivity
import de.telekom.syncplus.R
import kotlinx.android.synthetic.main.fragment_copy_progress.view.*
import kotlinx.coroutines.launch
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DuplicateProgressFragment : BaseFragment() {
    override val TAG: String
        get() = "DUPLICATE_PROGRESS_FRAGMENT"

    companion object {
        fun newInstance(): DuplicateProgressFragment {
            val args = Bundle()
            val fragment = DuplicateProgressFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private val mOriginals: List<Contact>
        get() = (requireActivity().application as App).originals ?: emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_copy_progress, container, false)
        v.skipButton.setOnClickListener {
            DuplicatesFoundFragment.showSkipDialog(requireActivity()) {
                finishWithResult(Activity.RESULT_CANCELED, null)
            }
        }

        lifecycleScope.launch {
            val authHolder = (activity as? ContactsCopyActivity)?.authHolder
                ?: throw IllegalStateException("Missing AuthHolder")
            val redirectUri = Uri.parse(getString(R.string.REDIRECT_URI))
            val environ = BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!
            val serviceEnvironments = ServiceEnvironments.fromBuildConfig(redirectUri, environ)
            val account = Account(authHolder.accountName, getString(R.string.account_type))
            val credentials = Credentials(requireContext(), account, serviceEnvironments)
            val spicaAPI = APIFactory.spicaAPI(requireActivity().application, credentials)

            val contactList = ContactList(mOriginals)
            var retry = true
            var response: Response<ContactIdentifiersResponse>? = null
            while (retry) {
                response = spicaAPI.importAndMergeContacts(contactList).awaitResponseOrNull()
                if (response?.isSuccessful == true)
                    break

                Logger.log.severe("Error: Merging Contacts: $response")
                retry = showRetryDialog()
            }

            if (response?.isSuccessful == true)
                push(R.id.container, CopySuccessFragment.newInstance())
            else
                finishWithResult(Activity.RESULT_CANCELED, null)
        }

        return v
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

    private suspend fun showRetryDialog(errorDescription: String? = null): Boolean =
        suspendCoroutine { cont ->
            val dialog = CustomErrorAlert(
                getString(R.string.dialog_sync_error_title),
                getString(R.string.dialog_sync_error_text),
                errorDescription
            ) { retry -> cont.resume(retry) }
            dialog.show(parentFragmentManager, "DIALOG")
        }
}
