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
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.model.spica.*
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.ui.BaseListAdapter
import de.telekom.syncplus.DuplicatedContactsActivity
import de.telekom.syncplus.R
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.fragment_duplicate_contacts_list.view.*

class DuplicatesDetailsFragment : BaseFragment() {
    override val TAG: String
        get() = "DUPLICATES_DETAILS_FRAGMENT"

    companion object {
        private const val ARG_DUPLICATE = "ARG_DUPLICATE"
        fun newInstance(duplicate: Duplicate): DuplicatesDetailsFragment {
            val args = Bundle()
            args.putParcelable(ARG_DUPLICATE, duplicate)
            val fragment = DuplicatesDetailsFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private val mDuplicate by extraNotNull<Duplicate>(ARG_DUPLICATE)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_duplicate_contacts_list, container, false)
        v.backButton.setOnClickListener {
            finish()
        }
        v.header.visibility = View.GONE
        v.acceptButton.visibility = View.GONE
        v.list.divider = null
        v.list.dividerHeight = 0
        v.list.adapter = createAdapter()
        return v
    }

    override fun onStart() {
        super.onStart()
        val activity = activity as? DuplicatedContactsActivity
        activity?.setTitle(getString(R.string.contact_details_title))
    }

    private fun createAdapter(): DuplicateDetailsAdapter {
        val model = ArrayList<ContactDetail>()
        val mergedContact = mDuplicate.mergedContact
        val similarContacts = mDuplicate.similarContacts?.filter { it.fromRequest == false }
        val remoteContacts = similarContacts?.size
        val info = ContactInfo(
            mDuplicate.mergedContact?.formatName(),
            1,
            remoteContacts ?: 0
        )
        model.add(ContactDetail(null, null, info))

        /* Company */
        createCompanyData(mergedContact, similarContacts)?.let {
            model.add(ContactDetail(getString(R.string.company)))
            model.addAll(it)
        }

        /* Phone */
        createPhoneData(mergedContact, similarContacts)?.let {
            model.add(ContactDetail(getString(R.string.phone)))
            model.addAll(it)
        }

        /* Email */
        createEmailData(mergedContact, similarContacts)?.let {
            model.add(ContactDetail(getString(R.string.email)))
            model.addAll(it)
        }

        return DuplicateDetailsAdapter(requireContext(), model)
    }

    private fun createCompanyData(
        contact: Contact?,
        similarContacts: List<ContactWithSimilarity>?
    ): List<ContactDetail>? {
        val models = ArrayList<ContactDetail>()
        val companyString = getString(R.string.company)
        val positionString = getString(R.string.position)

        val companies = similarContacts?.mapNotNull { it.contact?.company }
        if (contact?.company != null && (companies != null && companies.count() > 0)) {
            models.add(ContactDetail(row = ContactRow(companyString, contact.company, false)))
            for (company in companies) {
                models.add(ContactDetail(row = ContactRow(companyString, company, true)))
            }
        }

        // AG: Removed, due to jobTitle being too long.
        /*
        val jobTitles = similarContacts?.mapNotNull { it.contact?.jobTitle }
        if (contact?.jobTitle != null && (jobTitles != null && jobTitles.count() > 0)) {
            models.add(ContactDetail(row = ContactRow(positionString, contact.jobTitle, false)))
            for (jobTitle in jobTitles) {
                models.add(ContactDetail(row = ContactRow(positionString, jobTitle, true)))
            }
        }
        */

        return if (models.size > 0) models else null
    }

    private fun createPhoneData(
        contact: Contact?,
        similarContacts: List<ContactWithSimilarity>?
    ): List<ContactDetail>? {
        val models = ArrayList<ContactDetail>()

        val remoteNumbers =
            similarContacts?.mapNotNull { it.contact?.telephoneNumbers }?.filter { it.count() > 0 }
        val phoneNumbers = contact?.telephoneNumbers
        if (phoneNumbers != null && phoneNumbers.count() > 0 && remoteNumbers != null && remoteNumbers.count() > 0) {
            for (number in phoneNumbers) {
                models.add(
                    ContactDetail(
                        row = ContactRow(
                            number.telephoneType?.toLocalized(),
                            number.number,
                            false
                        )
                    )
                )
            }
            for (numbers in remoteNumbers) {
                for (number in numbers) {
                    models.add(
                        ContactDetail(
                            row = ContactRow(
                                number.telephoneType?.toLocalized(),
                                number.number,
                                true
                            )
                        )
                    )
                }
            }
        }

        return if (models.size > 0) models else null
    }

    private fun createEmailData(
        contact: Contact?,
        similarContacts: List<ContactWithSimilarity>?
    ): List<ContactDetail>? {
        val models = ArrayList<ContactDetail>()

        val remoteEmails =
            similarContacts?.mapNotNull { it.contact?.emails }?.filter { it.count() > 0 }
        val emails = contact?.emails
        if (emails != null && emails.count() > 0 && remoteEmails != null && remoteEmails.count() > 0) {
            for (email in emails) {
                models.add(
                    ContactDetail(
                        row = ContactRow(
                            email.addressType?.toLocalized(),
                            email.email,
                            false
                        )
                    )
                )
            }
            for (@Suppress("NAME_SHADOWING") emails in remoteEmails) {
                for (email in emails) {
                    models.add(
                        ContactDetail(
                            row = ContactRow(
                                email.addressType?.toLocalized(),
                                email.email,
                                true
                            )
                        )
                    )
                }
            }
        }

        return if (models.size > 0) models else null
    }

    private fun TelephoneType.toLocalized(): String {
        return when (this) {
            TelephoneType.PRIVATE -> getString(R.string.phone_private)
            TelephoneType.BUSINESS -> getString(R.string.phone_business)
            TelephoneType.PRIVATE_MOBILE -> getString(R.string.phone_private_mobile)
            TelephoneType.BUSINESS_MOBILE -> getString(R.string.phone_business_mobile)
            TelephoneType.PRIVATE_FAX -> getString(R.string.phone_private_fax)
            TelephoneType.BUSINESS_FAX -> getString(R.string.phone_business_fax)
            TelephoneType.PRIVATE_VOIP -> getString(R.string.phone_private_voip)
            TelephoneType.BUSINESS_VOIP -> getString(R.string.phone_business_voip)
            TelephoneType.UNKNOWN -> getString(R.string.unknown)
        }
    }

    private fun AddressType.toLocalized(): String {
        return when (this) {
            AddressType.PRIVATE -> getString(R.string.phone_private)
            AddressType.BUSINESS -> getString(R.string.phone_business)
            AddressType.UNKNOWN -> getString(R.string.unknown)
        }
    }

    @Parcelize
    data class ContactInfo(
        val name: String? = null,
        val numLocal: Int? = null,
        val numRemote: Int? = null
    ) : Parcelable

    @Parcelize
    data class ContactRow(
        val title: String? = null,
        val text: String? = null,
        val isRemote: Boolean
    ) : Parcelable

    @Parcelize
    data class ContactDetail(
        val header: String? = null,
        val row: ContactRow? = null,
        val info: ContactInfo? = null
    ) : Parcelable

    class DuplicateDetailsAdapter(
        context: Context,
        dataSource: List<ContactDetail>
    ) : BaseListAdapter<ContactDetail>(context, dataSource) {

        private class ViewHolder(view: View?, val viewType: Int) {
            val title = view?.findViewById<TextView>(R.id.title)
            val subtitle = view?.findViewById<TextView>(R.id.subtitle)
            val text = view?.findViewById<TextView>(R.id.text)
            val icon = view?.findViewById<ImageView>(R.id.icon)
            val numLocal = view?.findViewById<TextView>(R.id.numLocal)
            val numRemote = view?.findViewById<TextView>(R.id.numRemote)
            val arrow = view?.findViewById<ImageView>(R.id.arrow)
        }

        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val viewHolder: ViewHolder?
            val rowView: View?

            val viewType = getItemViewType(position)
            val layout = when (viewType) {
                0 -> R.layout.contact_detail_header
                1 -> R.layout.duplicated_contacts_list_item
                2 -> R.layout.contact_detail_row
                else -> throw IllegalStateException("Unsupported viewType")
            }

            if (convertView == null) {
                rowView = inflater.inflate(layout, parent, false)
                viewHolder = ViewHolder(rowView, viewType)
                rowView.tag = viewHolder
            } else {
                rowView = convertView
                viewHolder = rowView.tag as ViewHolder
            }

            assert(viewType == viewHolder.viewType)

            when (viewType) {
                0 -> {
                    val header = getItem(position).header
                    viewHolder.title?.text = header
                }
                1 -> {
                    val info = getItem(position).info
                    viewHolder.title?.text = info?.name
                    viewHolder.subtitle?.visibility = View.GONE
                    viewHolder.numLocal?.text = "${info?.numLocal.toString()}x"
                    viewHolder.numRemote?.text = "${info?.numRemote.toString()}x"
                    viewHolder.arrow?.visibility = View.INVISIBLE
                }
                2 -> {
                    val row = getItem(position).row
                    viewHolder.title?.text = row?.title
                    viewHolder.text?.text = row?.text
                    viewHolder.icon?.setImageResource(
                        if (row?.isRemote == true) {
                            R.drawable.ic_cloud_computing_outline
                        } else {
                            R.drawable.ic_smartphone_outline
                        }
                    )
                }
            }


            return rowView!!
        }

        override fun getViewTypeCount(): Int {
            return 3
        }

        override fun getItemViewType(position: Int): Int {
            val item = dataSource[position]
            return when {
                item.header != null -> 0
                item.info != null -> 1
                item.row != null -> 2
                else -> 0
            }
        }
    }
}
