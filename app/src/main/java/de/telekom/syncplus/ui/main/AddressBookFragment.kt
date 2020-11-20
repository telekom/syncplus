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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.button.MaterialButton
import de.telekom.dtagsyncpluskit.contacts.ContactsFetcher
import de.telekom.dtagsyncpluskit.extra
import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.ui.BaseListAdapter
import de.telekom.syncplus.ContactsActivity
import de.telekom.syncplus.R
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.fragment_addressbook.view.*

class AddressBookFragment : BaseFragment() {
    override val TAG = AddressBookFragment.TAG

    companion object {
        private const val ARG_SELECTED_GROUPS = "ARG_SELECTED_GROUPS"
        fun newInstance(selectedGroups: List<Group>?): AddressBookFragment {
            val args = Bundle()
            if (selectedGroups != null)
                args.putParcelableArrayList(ARG_SELECTED_GROUPS, ArrayList(selectedGroups))
            val fragment = AddressBookFragment()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "ADDRESS_BOOK_FRAGMENT"
    }

    @Parcelize
    data class RowItem(
        var group: Group,
        var selected: Boolean = true
    ) : Parcelable

    private lateinit var mListView: ListView
    private lateinit var mFetcher: ContactsFetcher
    private lateinit var mGroups: List<Group>

    private val mSelectedGroups by extra<List<Group>>(ARG_SELECTED_GROUPS, null)
    private var mSelection by instanceState<List<RowItem>?>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_addressbook, container, false)
        v.bottomBackButton.setOnClickListener { finishWithResult(Activity.RESULT_CANCELED, null) }
        v.bottomAcceptButton.setOnClickListener { accept() }

        mFetcher = ContactsFetcher(requireContext())
        mGroups = mFetcher.allGroups()
        if (mSelection == null)
            mSelection = getRows()

        if (mSelectedGroups != null) {
            mSelection?.forEach { it.selected = false }
            for (group in mSelectedGroups!!) {
                mSelection?.find { it.group.groupId == group.groupId }?.selected = true
            }
        }

        val adapter = ContactsAdapter(requireContext(), mSelection!!)
        mListView = v.findViewById(R.id.list)
        mListView.adapter = adapter

        return v
    }

    private fun getRows(): List<RowItem> {
        var sum = mFetcher.contactSum()
        val rows = ArrayList<RowItem>()
        for (group in mGroups) {
            rows.add(RowItem(group))
            sum -= group.numberOfContacts ?: 0
        }
        rows.add(0, RowItem(Group(-1, getString(R.string.all_contacts), sum)))
        return rows
    }

    private fun accept() {
        val adapter = mListView.adapter as? ContactsAdapter ?: return
        val result = ArrayList<Group>()
        for (item in adapter.dataSource) {
            if (item.selected) {
                result.add(item.group)
            }
        }

        val intent = Intent()
        intent.putExtra(ContactsActivity.EXTRA_RESULT, result)
        finishWithResult(Activity.RESULT_OK, intent)
    }

    private fun onClickGroup(group: Group) {
        val contacts = mFetcher.allContacts(group.groupId)
        val fragment = ContactListFragment.newInstance(contacts)
        push(R.id.container, fragment)
    }

    private fun selectionChanged(@Suppress("UNUSED_PARAMETER") position: Int) {
        val adapter = mListView.adapter as? ContactsAdapter
        view?.bottomAcceptButton?.isEnabled = adapter?.dataSource?.find { it.selected } != null
    }

    class ViewHolder(view: View?) {
        val container = view?.findViewById<LinearLayout>(R.id.rowContainer)
        val titleTextView = view?.findViewById<TextView>(R.id.text)
        val arrowButton = view?.findViewById<MaterialButton>(R.id.arrowButton)
        val checkBox = view?.findViewById<CheckBox>(R.id.contact_checkbox)
    }

    inner class ContactsAdapter(
        context: Context,
        dataSource: List<RowItem>
    ) : BaseListAdapter<RowItem>(context, dataSource) {

        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val viewHolder: ViewHolder?
            val rowView: View?

            if (convertView == null) {
                rowView = inflater.inflate(R.layout.contacts_list_item, parent, false)
                viewHolder = ViewHolder(rowView)
                rowView.tag = viewHolder
            } else {
                rowView = convertView
                viewHolder = rowView.tag as ViewHolder
            }

            val rowItem = getItem(position)
            viewHolder.titleTextView?.text =
                "${rowItem.group.name} (${rowItem.group.numberOfContacts ?: 0})"
            viewHolder.checkBox?.isChecked = rowItem.selected
            if (position == 0) {
                viewHolder.titleTextView?.typeface =
                    ResourcesCompat.getFont(context, R.font.telegrotesknext_bold)
            }

            viewHolder.arrowButton?.setOnClickListener {
                onClickGroup(rowItem.group)
            }
            viewHolder.checkBox?.setOnCheckedChangeListener { _, isChecked ->
                rowItem.selected = isChecked
                selectionChanged(position)
            }
            viewHolder.container?.setOnClickListener {
                onClickGroup(rowItem.group)
            }

            return rowView!!
        }
    }
}
