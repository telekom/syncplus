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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.model.spica.Duplicate
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.ui.BaseListAdapter
import de.telekom.syncplus.App
import de.telekom.syncplus.DuplicatedContactsActivity
import de.telekom.syncplus.R
import kotlinx.android.synthetic.main.fragment_duplicate_contacts_list.view.*

class DuplicatesListFragment : BaseFragment() {
    override val TAG: String
        get() = "DUPLICATES_LIST_FRAGMENT"

    companion object {
        fun newInstance(): DuplicatesListFragment {
            val args = Bundle()
            val fragment = DuplicatesListFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private val mDuplicates: List<Duplicate>
        get() = (requireActivity().application as App).duplicates ?: emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_duplicate_contacts_list, container, false)
        v.backButton.setOnClickListener {
            finishActivity()
        }
        v.acceptButton.setOnClickListener {
            // TODO: Finish with results?
            finishActivity()
        }
        v.list.adapter = DuplicatedContactsAdapter(requireContext(), mDuplicates)
        v.list.setOnItemClickListener { _, _, position, _ ->
            val duplicate = v.list.adapter.getItem(position) as? Duplicate
            if (duplicate != null) {
                push(R.id.container, DuplicatesDetailsFragment.newInstance(duplicate))
            }
        }
        return v
    }

    override fun onStart() {
        super.onStart()
        val activity = activity as? DuplicatedContactsActivity
        activity?.setTitle(getString(R.string.title_activity_duplicated_contacts))
    }

    class DuplicatedContactsAdapter(
        context: Context,
        dataSource: List<Duplicate>
    ) : BaseListAdapter<Duplicate>(context, dataSource) {

        private class ViewHolder(view: View?) {
            val nameView = view?.findViewById<TextView>(R.id.title)
            val companyView = view?.findViewById<TextView>(R.id.subtitle)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val viewHolder: ViewHolder?
            val rowView: View?

            if (convertView == null) {
                rowView = inflater.inflate(R.layout.duplicated_contacts_list_item, parent, false)
                viewHolder = ViewHolder(rowView)
                rowView.tag = viewHolder
            } else {
                rowView = convertView
                viewHolder = rowView.tag as ViewHolder
            }

            val duplicate = getItem(position) as? Duplicate
            viewHolder.nameView?.text = duplicate?.mergedContact?.formatName()
            viewHolder.companyView?.text = duplicate?.mergedContact?.company

            return rowView!!
        }
    }
}
