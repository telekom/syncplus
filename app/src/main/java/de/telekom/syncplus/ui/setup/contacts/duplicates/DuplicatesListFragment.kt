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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.telekom.dtagsyncpluskit.model.spica.Duplicate
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.DuplicatedContactsListItemBinding
import de.telekom.syncplus.databinding.FragmentDuplicateContactsListBinding
import de.telekom.syncplus.extensions.inflater
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.launch

class DuplicatesListFragment : BaseFragment(R.layout.fragment_duplicate_contacts_list) {
    override val TAG: String
        get() = "DUPLICATES_LIST_FRAGMENT"

    companion object {
        fun newInstance(): DuplicatesListFragment {
            return DuplicatesListFragment()
        }
    }

    private val binding by viewBinding(FragmentDuplicateContactsListBinding::bind)
    private val viewModel by viewModels<DuplicatesListViewModel>()
    private val adapter by lazy {
        DuplicatedContactsAdapter { item ->
            viewModel.onEvent(DuplicatesListViewModel.ViewEvent.NavigateToDetails(item))
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            backButton.setOnClickListener {
                finishActivity()
            }
            acceptButton.setOnClickListener {
                // TODO: Finish with results?
                finishActivity()
            }
            list.adapter = adapter
            list.addItemDecoration(DividerItemDecoration(requireContext(), RecyclerView.VERTICAL).apply {
                ContextCompat.getDrawable(requireContext(), R.drawable.list_divider)?.let {
                    setDrawable(it)
                }
            })
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::handleState) }
                launch { viewModel.action.collect(::handleAction) }
            }
        }
    }

    private fun handleState(state: DuplicatesListViewModel.State) {
        adapter.submitList(state.duplicates)
    }

    private fun handleAction(action: DuplicatesListViewModel.Action) {
        when (action) {
            is DuplicatesListViewModel.Action.NavigateToDetails -> {
                push(R.id.container, DuplicatesDetailsFragment.newInstance(action.duplicate))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val activity = activity as? DuplicatedContactsActivity
        activity?.setTitle(getString(R.string.title_activity_duplicated_contacts))
    }

    private class DuplicatedContactsAdapter(
        private val doOnClick: (item: Duplicate) -> Unit
    ) : ListAdapter<Duplicate, DuplicatedContactsAdapter.ViewHolder>(DuplicateContactDiffCallback) {

        private inner class ViewHolder(
            private val binding: DuplicatedContactsListItemBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            @SuppressLint("SetTextI18n")
            fun bind(item: Duplicate) {
                with(binding) {
                    root.setOnClickListener { doOnClick(item) }
                    title.text = item.mergedContact?.formatName()
                    subtitle.text = item.mergedContact?.company
                    numRemote.text = "${item.similarContacts?.size ?: 0}x"
                }
            }
        }

        object DuplicateContactDiffCallback : DiffUtil.ItemCallback<Duplicate>() {
            override fun areContentsTheSame(oldItem: Duplicate, newItem: Duplicate): Boolean {
                return oldItem == newItem
            }

            override fun areItemsTheSame(oldItem: Duplicate, newItem: Duplicate): Boolean {
                return oldItem.mergedContact == newItem.mergedContact
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(DuplicatedContactsListItemBinding.inflate(parent.inflater, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }
}
