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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import de.telekom.dtagsyncpluskit.api.APIFactory
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
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
import de.telekom.dtagsyncpluskit.runOnMain
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.utils.Err
import de.telekom.dtagsyncpluskit.utils.Ok
import de.telekom.syncplus.App
import de.telekom.syncplus.BuildConfig
import de.telekom.syncplus.ContactsCopyActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.ui.main.CopyViewModel.UploadProgress.*
import kotlinx.android.synthetic.main.fragment_copy_progress.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

class CopyProgressFragment : BaseFragment() {
    override val TAG = "COPY_PROGRESS_FRAGMENT"

    companion object {
        private const val ARG_AUTH_HOLDER = "ARG_AUTH_HOLDER"
        fun newInstance(authHolder: AuthHolder): CopyProgressFragment {
            val args = Bundle()
            args.putParcelable(ARG_AUTH_HOLDER, authHolder)
            val fragment = CopyProgressFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private val mAuthHolder by extraNotNull<AuthHolder>(ARG_AUTH_HOLDER)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_copy_progress, container, false)
        v.skipButton.setOnClickListener {
            showConfirmationDialog {
                showCancelledDialog {
                    finishWithResult(Activity.RESULT_CANCELED, null)
                }
            }
        }

        val intent = activity?.intent ?: throw IllegalStateException("Intent must not be null")
        val groups = intent.getParcelableArrayListExtra<Group>(ContactsCopyActivity.EXTRA_GROUPS)
        val model: CopyViewModel by activityViewModels()
        model.uploadContacts(groups, mAuthHolder)
        model.progress.observe(viewLifecycleOwner) { progress ->
            when (progress) {
                SUCCESS -> {
                    next(model.getOriginals()!!, model.getDuplicates())
                }
                ERROR -> {
                    Logger.log.severe("ERROR: UPLOADING CONTACTS: ${model.getLastError()}")
                    showErrorDialog(model.getLastError()) {
                        model.uploadContacts(groups, mAuthHolder)
                    }
                }
                else -> {
                    /* Is the first state and therefore ignored. */
                }
            }
        }
        return v
    }

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

    private fun showConfirmationDialog(yes: () -> Unit) {
        val dialog = CustomAlertDialog()
        dialog.title = getString(R.string.dialog_sync_confirm_title)
        dialog.text = getString(R.string.dialog_sync_confirm_text)
        dialog.cancelText = getString(R.string.button_title_back)
        dialog.successText = getString(R.string.button_title_cancel_copy)
        dialog.setOnCancelListener {}
        dialog.setOnSuccessListener { yes() }
        dialog.show(parentFragmentManager, "DIALOG")
    }

    private fun showCancelledDialog(next: () -> Unit) {
        val dialog = CustomAlertDialog()
        dialog.title = getString(R.string.dialog_sync_cancel)
        dialog.hasCancelButton = false
        dialog.successText = getString(R.string.dialog_button_title_close)
        dialog.setOnSuccessListener { next() }
        dialog.show(parentFragmentManager, "DIALOG")
    }

    private fun showErrorDialog(errorDescription: String? = null, callback: () -> Unit) {
        val dialog = CustomErrorAlert(
            getString(R.string.dialog_sync_error_title),
            getString(R.string.dialog_sync_error_text),
            errorDescription
        ) { retry ->
            if (retry) callback()
            else finishWithResult(Activity.RESULT_CANCELED, null)
        }
        dialog.show(parentFragmentManager, "DIALOG")
    }

    private fun next(originals: List<Contact>, duplicates: List<Duplicate?>?) {
        /* // DEBUG
        val duplicates = ArrayList<Duplicate>()
        val similarContacts = ArrayList<ContactWithSimilarity>()
        similarContacts.add(
            ContactWithSimilarity(
                originals[0],
                false,
                null,
                null
            )
        )
        duplicates.add(Duplicate(originals[0], similarContacts))
        push(R.id.container, DuplicatesFoundFragment.newInstance(originals, duplicates))
        return*/

        (requireActivity().application as App).originals = originals
        (requireActivity().application as App).duplicates = duplicates?.filterNotNull()
        if (duplicates == null) {
            push(R.id.container, CopySuccessFragment.newInstance())
        } else {
            push(R.id.container, DuplicatesFoundFragment.newInstance())
        }
    }
}
