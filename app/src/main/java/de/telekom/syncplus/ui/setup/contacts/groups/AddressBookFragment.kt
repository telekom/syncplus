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

package de.telekom.syncplus.ui.setup.contacts.groups

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import de.telekom.dtagsyncpluskit.extra
import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentAddressbookBinding
import de.telekom.syncplus.ui.setup.contacts.list.ContactListFragment
import de.telekom.syncplus.ui.setup.contacts.list.ContactsActivity
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.launch

class AddressBookFragment : BaseFragment(R.layout.fragment_addressbook) {
    override val TAG = Companion.TAG

    companion object {
        private const val ARG_SELECTED_GROUPS = "ARG_SELECTED_GROUPS"

        fun newInstance(selectedGroups: List<Group>?): AddressBookFragment {
            val args = Bundle()
            if (selectedGroups != null) {
                args.putParcelableArrayList(ARG_SELECTED_GROUPS, ArrayList(selectedGroups))
            }
            val fragment = AddressBookFragment()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "ADDRESS_BOOK_FRAGMENT"
    }

    private val mSelectedGroups by extra<List<Group>>(ARG_SELECTED_GROUPS, null)

    private val viewModel: AddressBookContract.ViewModel by viewModels<AddressBookViewModel>()
    private val binding by viewBinding(FragmentAddressbookBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            viewModel.fetchGroups(mSelectedGroups)
        }
    }

    private val adapter: AddressBookAdapter by lazy {
        AddressBookAdapter(
            onClicked = { viewModel.onGroupClicked(it) },
            onSelectionChanged = { viewModel.onGroupSelected(it) },
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.bottomBackButton.setOnClickListener {
            finishWithResult(
                Activity.RESULT_CANCELED,
                null,
            )
        }
        binding.bottomAcceptButton.setOnClickListener { viewModel.onAccepted() }

        binding.list.adapter = adapter
        binding.list.addItemDecoration(
            DividerItemDecoration(requireContext(), VERTICAL).apply {
                ContextCompat.getDrawable(requireContext(), R.drawable.list_divider)?.let {
                    setDrawable(it)
                }
            },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::handleState) }
                launch { viewModel.action.collect(::handleAction) }
            }
        }
    }

    private fun handleState(state: AddressBookContract.State) {
        binding.bottomAcceptButton.isEnabled = state.groupList.any { it.isSelected }
        adapter.submitList(state.groupList)
    }

    private fun handleAction(action: AddressBookContract.Action) {
        when (action) {
            is AddressBookContract.Action.NavigateToGroup -> {
                val fragment = ContactListFragment.newInstance(action.groupIds)
                push(R.id.container, fragment)
            }

            is AddressBookContract.Action.FinishWithSelection -> {
                finishWithResult(
                    Activity.RESULT_OK,
                    Intent().putParcelableArrayListExtra(
                        ContactsActivity.EXTRA_RESULT,
                        ArrayList(action.groups),
                    ),
                )
            }
        }
    }
}
