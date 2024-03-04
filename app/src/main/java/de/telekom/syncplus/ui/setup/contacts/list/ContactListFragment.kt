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

package de.telekom.syncplus.ui.setup.contacts.list

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.model.spica.Contact
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.ContactsSublistItemBinding
import de.telekom.syncplus.databinding.FragmentContactslistBinding
import de.telekom.syncplus.extensions.inflater
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.launch

class ContactListFragment : BaseFragment(R.layout.fragment_contactslist) {
    override val TAG = "CONTACT_LIST_FRAGMENT"

    companion object {
        private const val ARG_GROUP_IDS = "ARG_GROUP_IDS"

        fun newInstance(groupIds: LongArray): ContactListFragment {
            val fragment = ContactListFragment()
            fragment.arguments = bundleOf(ARG_GROUP_IDS to groupIds)
            return fragment
        }
    }

    private val groupIds by extraNotNull<LongArray>(ARG_GROUP_IDS)
    private val binding by viewBinding(FragmentContactslistBinding::bind)
    private val viewModel: ContactListContract.ViewModel by viewModels<ContactListViewModel>()
    private val adapter by lazy { ContactsAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.viewEvent(ContactListContract.ViewEvent.ReadGroups(groupIds))
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.bottomBackButton.setOnClickListener { finish() }
        binding.list.adapter = adapter
        binding.list.addItemDecoration(
            DividerItemDecoration(requireContext(), RecyclerView.VERTICAL).apply {
                ContextCompat.getDrawable(requireContext(), R.drawable.list_divider)?.let {
                    setDrawable(it)
                }
            },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::handleState) }
            }
        }
    }

    private fun handleState(state: ContactListContract.State) {
        adapter.submitList(state.contacts)
    }

    private class ContactsAdapter : ListAdapter<Contact, ContactsAdapter.ViewHolder>(ContactsDiffCallback) {
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ContactsSublistItemBinding.inflate(parent.inflater, parent, false))
        }

        private class ViewHolder(
            private val binding: ContactsSublistItemBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Contact) {
                binding.text.text = item.formatName(
                    defaultName = binding.root.context.getString(
                        R.string.fallbackName
                    )
                )
            }
        }

        private object ContactsDiffCallback : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
                return oldItem.contactId == newItem.contactId
            }

            override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
                return oldItem == newItem
            }
        }
    }
}
