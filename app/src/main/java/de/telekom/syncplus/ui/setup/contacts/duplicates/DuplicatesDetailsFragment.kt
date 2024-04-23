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
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.model.spica.*
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.ContactDetailHeaderBinding
import de.telekom.syncplus.databinding.ContactDetailRowBinding
import de.telekom.syncplus.databinding.DuplicatedContactsListItemBinding
import de.telekom.syncplus.databinding.FragmentDuplicateContactsListBinding
import de.telekom.syncplus.extensions.inflater
import de.telekom.syncplus.util.viewbinding.viewBinding

class DuplicatesDetailsFragment : BaseFragment(R.layout.fragment_duplicate_contacts_list) {
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
    private val binding by viewBinding(FragmentDuplicateContactsListBinding::bind)
    private val adapter by lazy { DuplicateDetailsAdapter() }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            backButton.setOnClickListener {
                finish()
            }
            header.visibility = View.GONE
            acceptButton.visibility = View.GONE
            list.adapter = adapter
        }
        adapter.submitList(getData())
    }

    override fun onStart() {
        super.onStart()
        val activity = activity as? DuplicatedContactsActivity
        activity?.setTitle(getString(R.string.contact_details_title))
    }

    private fun getData(): List<ContactDetail> {
        val model = ArrayList<ContactDetail>()
        val mergedContact = mDuplicate.mergedContact
        val similarContacts = mDuplicate.similarContacts?.filter { it.fromRequest == false }
        val remoteContacts = similarContacts?.size
        model.add(
            ContactDetail.ContactInfo(
                mDuplicate.mergedContact?.formatName(),
                1,
                remoteContacts ?: 0
            )
        )

        // Company
        createCompanyData(mergedContact, similarContacts)?.let {
            model.add(ContactDetail.Header(getString(R.string.company)))
            model.addAll(it)
        }

        // Phone
        createPhoneData(mergedContact, similarContacts)?.let {
            model.add(ContactDetail.Header(getString(R.string.phone)))
            model.addAll(it)
        }

        // Email
        createEmailData(mergedContact, similarContacts)?.let {
            model.add(ContactDetail.Header(getString(R.string.email)))
            model.addAll(it)
        }

        return model
    }

    private fun createCompanyData(
        contact: Contact?,
        similarContacts: List<ContactWithSimilarity>?,
    ): List<ContactDetail>? {
        val models = ArrayList<ContactDetail>()
        val companyString = getString(R.string.company)
        // val positionString = getString(R.string.position)

        val companies = similarContacts?.mapNotNull { it.contact?.company }
        if (contact?.company != null && !companies.isNullOrEmpty()) {
            models.add(ContactDetail.ContactRow(companyString, contact.company, false))
            for (company in companies) {
                models.add(ContactDetail.ContactRow(companyString, company, true))
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
        similarContacts: List<ContactWithSimilarity>?,
    ): List<ContactDetail>? {
        val models = ArrayList<ContactDetail>()

        val remoteNumbers =
            similarContacts?.mapNotNull { it.contact?.telephoneNumbers }?.filter { it.isNotEmpty() }
        val phoneNumbers = contact?.telephoneNumbers
        if (!phoneNumbers.isNullOrEmpty() && !remoteNumbers.isNullOrEmpty()) {
            for (number in phoneNumbers) {
                models.add(
                    ContactDetail.ContactRow(
                        number.telephoneType?.toLocalized(),
                        number.number,
                        false,
                    ),
                )
            }
            for (numbers in remoteNumbers) {
                for (number in numbers) {
                    models.add(
                        ContactDetail.ContactRow(
                            number.telephoneType?.toLocalized(),
                            number.number,
                            true
                        )
                    )
                }
            }
        }

        return if (models.size > 0) models else null
    }

    private fun createEmailData(
        contact: Contact?,
        similarContacts: List<ContactWithSimilarity>?,
    ): List<ContactDetail>? {
        val models = ArrayList<ContactDetail>()

        val remoteEmails =
            similarContacts?.mapNotNull { it.contact?.emails }?.filter { it.isNotEmpty() }
        val emails = contact?.emails

        if (!emails.isNullOrEmpty() && !remoteEmails.isNullOrEmpty()) {
            for (email in emails) {
                models.add(
                    ContactDetail.ContactRow(
                        email.addressType?.toLocalized(),
                        email.email,
                        false
                    )
                )
            }
            for (@Suppress("NAME_SHADOWING") emails in remoteEmails) {
                for (email in emails) {
                    models.add(
                        ContactDetail.ContactRow(
                            email.addressType?.toLocalized(),
                            email.email,
                            true,
                        ),
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

    sealed interface ContactDetail {
        data class ContactInfo(
            val name: String? = null,
            val numLocal: Int? = null,
            val numRemote: Int? = null,
        ) : ContactDetail

        data class ContactRow(
            val title: String? = null,
            val text: String? = null,
            val isRemote: Boolean,
        ) : ContactDetail

        data class Header(
            val header: String,
        ) : ContactDetail
    }

    private class DuplicateDetailsAdapter :
        ListAdapter<ContactDetail, RecyclerView.ViewHolder>(ContactDetailDiffCallback) {

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is ContactDetail.Header -> (holder as HeaderViewHolder).bind(item)
                is ContactDetail.ContactInfo -> (holder as InfoViewHolder).bind(item)
                is ContactDetail.ContactRow -> (holder as DetailViewHolder).bind(item)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                0 -> HeaderViewHolder(
                    ContactDetailHeaderBinding.inflate(parent.inflater, parent, false)
                )

                1 -> InfoViewHolder(
                    DuplicatedContactsListItemBinding.inflate(parent.inflater, parent, false)
                )

                2 -> DetailViewHolder(
                    ContactDetailRowBinding.inflate(parent.inflater, parent, false)
                )

                else -> throw IllegalStateException("Unsupported viewType")
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is ContactDetail.Header -> 0
                is ContactDetail.ContactInfo -> 1
                is ContactDetail.ContactRow -> 2
                else -> -1
            }
        }

        private class HeaderViewHolder(
            private val binding: ContactDetailHeaderBinding,
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: ContactDetail.Header) = with(binding) {
                title.text = item.header
            }
        }

        private class InfoViewHolder(
            private val binding: DuplicatedContactsListItemBinding,
        ) : RecyclerView.ViewHolder(binding.root) {

            @SuppressLint("SetTextI18n")
            fun bind(item: ContactDetail.ContactInfo) = with(binding) {
                title.text = item.name
                subtitle.isVisible = false
                numLocal.text = "${item.numLocal.toString()}x"
                numRemote.text = "${item.numRemote.toString()}x"
                arrow.isInvisible = true
            }
        }

        private class DetailViewHolder(
            private val binding: ContactDetailRowBinding,
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: ContactDetail.ContactRow) = with(binding) {
                title.text = item.title
                text.text = item.text
                icon.setImageResource(
                    if (item.isRemote) {
                        R.drawable.ic_cloud_computing_outline
                    } else {
                        R.drawable.ic_smartphone_outline
                    }
                )
            }
        }

        private object ContactDetailDiffCallback : DiffUtil.ItemCallback<ContactDetail>() {
            override fun areItemsTheSame(oldItem: ContactDetail, newItem: ContactDetail): Boolean {
                return oldItem::class == newItem::class
            }

            override fun areContentsTheSame(oldItem: ContactDetail, newItem: ContactDetail): Boolean {
                return oldItem == newItem
            }
        }
    }
}
