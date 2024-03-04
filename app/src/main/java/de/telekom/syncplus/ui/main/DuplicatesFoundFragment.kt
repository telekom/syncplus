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

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import de.telekom.dtagsyncpluskit.model.spica.Duplicate
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.App
import de.telekom.syncplus.DuplicatedContactsActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentDuplicatesFoundBinding
import de.telekom.syncplus.util.viewbinding.viewBinding

class DuplicatesFoundFragment : BaseFragment(R.layout.fragment_duplicates_found) {
    override val TAG: String
        get() = "COPY_DUPLICATES_FOUND_FRAGMENT"

    companion object {
        fun newInstance(): DuplicatesFoundFragment {
            val args = Bundle()
            val fragment = DuplicatesFoundFragment()
            fragment.arguments = args
            return fragment
        }

        fun showSkipDialog(
            activity: FragmentActivity,
            skip: () -> Unit,
        ) {
            val dialog = CustomAlertDialog()
            dialog.title = activity.getString(R.string.dialog_contacts_not_merged)
            dialog.text = activity.getString(R.string.dialog_contacts_not_merged_description)
            dialog.cancelText = activity.getString(R.string.button_title_back)
            dialog.successText = activity.getString(R.string.button_title_next)
            dialog.setOnSuccessListener {
                skip()
            }
            dialog.show(activity.supportFragmentManager, null)
        }
    }

    private val mDuplicates: List<Duplicate>
        get() = (requireActivity().application as App).duplicates ?: emptyList()

    private val binding by viewBinding(FragmentDuplicatesFoundBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.mergeButton.setOnClickListener {
            push(R.id.container, DuplicateProgressFragment.newInstance())
        }
        binding.doNotMergeButton.setOnClickListener {
            showSkipDialog(requireActivity()) {
                push(R.id.container, CopySuccessFragment.newInstance())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val topBar = (activity as? TopBarActivity)?.topBar
        topBar?.description =
            if (mDuplicates.count() > 1) {
                getString(R.string.duplicates_found_title_multiple, mDuplicates.count())
            } else {
                getString(R.string.duplicates_found_title_single, mDuplicates.count())
            }
        topBar?.large = true
        topBar?.extraDrawable = 0
        topBar?.extraDrawableSmall = R.drawable.ic_contact_users_icon
        topBar?.extraDescription = getString(R.string.contacts_duplicates_merge_description)
        topBar?.extraTitle = null
        topBar?.extraSectionButtonTitle = getString(R.string.button_title_show_duplicates)
        topBar?.hasHelpButton = false
        topBar?.hasBackButton = false
        topBar?.setOnLinkClickListener {
            val intent = DuplicatedContactsActivity.newIntent(requireContext())
            startActivity(intent)
        }
    }
}
