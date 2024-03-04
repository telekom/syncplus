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

package de.telekom.syncplus

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import de.telekom.dtagsyncpluskit.extra
import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.databinding.ContactsActivityBinding
import de.telekom.syncplus.ui.main.contacts.AddressBookFragment
import de.telekom.syncplus.util.viewbinding.viewBinding

class ContactsActivity : BaseActivity(R.layout.contacts_activity) {
    companion object {
        const val SELECTED_ADDRESS_BOOKS = 101
        const val EXTRA_RESULT = "EXTRA_RESULT"
        const val ARG_SELECTED_GROUPS = "ARG_SELECTED_GROUPS"

        fun newIntent(
            activity: Activity,
            selectedGroups: List<Group>?,
        ): Intent {
            val intent = Intent(activity, ContactsActivity::class.java)
            if (selectedGroups != null) {
                intent.putExtra(ARG_SELECTED_GROUPS, ArrayList(selectedGroups))
            }
            return intent
        }
    }

    private val selectedGroups by extra<List<Group>>(ARG_SELECTED_GROUPS)
    private val binding by viewBinding(R.id.root) { ContactsActivityBinding.bind(it) }

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.layoutSmallTopbar.topbarTitle.text = getString(R.string.all_contacts)
        binding.layoutSmallTopbar.helpButtonSmall.visibility = View.GONE
        binding.layoutSmallTopbar.backButtonSmall.setOnClickListener {
            handleBackPressed()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.container,
                    AddressBookFragment.newInstance(selectedGroups),
                    AddressBookFragment.TAG,
                )
                .commitNow()
        }
    }

    override fun onBackPressed() {
        handleBackPressed()
    }

    private fun handleBackPressed() {
        val addressBookFragment =
            supportFragmentManager.findFragmentByTag(AddressBookFragment.TAG) as? BaseFragment
        if (addressBookFragment?.isVisible == true) {
            finishWithResult()
        } else {
            addressBookFragment?.finish()
        }
    }

    private fun finishWithResult() {
        setResult(Activity.RESULT_CANCELED, Intent())
        finish()
    }
}
