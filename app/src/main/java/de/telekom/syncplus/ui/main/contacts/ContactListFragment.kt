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

package de.telekom.syncplus.ui.main.contacts

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.model.spica.Contact
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.ui.BaseListAdapter
import de.telekom.syncplus.R
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.fragment_contactslist.view.*

class ContactListFragment : BaseFragment() {
    override val TAG = "CONTACT_LIST_FRAGMENT"

    companion object {
        private const val ARG_CONTACTS = "ARG_CONTACTS"
        fun newInstance(contacts: List<Contact>): ContactListFragment {
            val args = Bundle()
            args.putParcelableArrayList(ARG_CONTACTS, ArrayList(contacts))
            val fragment = ContactListFragment()
            fragment.arguments = args
            return fragment
        }
    }

    @Parcelize
    data class RowItem(
        var text: String
    ) : Parcelable

    private lateinit var mListView: ListView
    private val mContacts by extraNotNull<List<Contact>>(ARG_CONTACTS)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_contactslist, container, false)
        v.bottomBackButton.setOnClickListener { finish() }

        val rows = ArrayList<RowItem>()
        for (contact in mContacts) {
            rows.add(RowItem(contact.formatName(defaultName = getString(R.string.fallbackName))))
        }

        val adapter = ContactsAdapter(requireContext(), rows)
        mListView = v.findViewById(R.id.list)
        mListView.adapter = adapter

        return v
    }

    class ViewHolder(view: View?) {
        val titleTextView = view?.findViewById<TextView>(R.id.text)
    }

    inner class ContactsAdapter(
        context: Context,
        dataSource: List<RowItem>
    ) : BaseListAdapter<RowItem>(context, dataSource) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val viewHolder: ViewHolder?
            val rowView: View?

            if (convertView == null) {
                rowView = inflater.inflate(R.layout.contacts_sublist_item, parent, false)
                viewHolder = ViewHolder(rowView)
                rowView.tag = viewHolder
            } else {
                rowView = convertView
                viewHolder = rowView.tag as ViewHolder
            }

            val rowItem = getItem(position)
            viewHolder.titleTextView?.text = rowItem.text

            return rowView!!
        }
    }
}
