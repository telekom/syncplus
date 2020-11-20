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

package de.telekom.dtagsyncpluskit.contacts

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.model.spica.*
import de.telekom.dtagsyncpluskit.toArray

@Suppress("unused")
class ContactsFetcher(val context: Context) {
    private val contentResolver: ContentResolver = context.contentResolver

    // Cache
    // contactId -> contact
    private var mContactsMap: HashMap<Long, Contact>? = null

    // groupId -> [contactId]
    private var mGroupsMap: HashMap<Long, ArrayList<Long>>? = null

    private var mGroups: ArrayList<Group>? = null

    // Don't EVER change the order:
    private val mDataProjection: Array<out String> =
        arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.DATA5,
            ContactsContract.Data.DATA6,
            ContactsContract.Data.DATA7,
            ContactsContract.Data.DATA8,
            ContactsContract.Data.DATA9,
            ContactsContract.Data.DATA10,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.RAW_CONTACT_ID,
            // Available via implicit join:
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.IN_VISIBLE_GROUP
        )

    private val mGroupProjection: Array<out String> =
        arrayOf(
            ContactsContract.Groups._ID,
            ContactsContract.Groups.TITLE,
            ContactsContract.Groups.SUMMARY_COUNT
        )

    fun allGroups(): List<Group> {
        return mGroups ?: {
            val cursor = contentResolver.query(
                ContactsContract.Groups.CONTENT_SUMMARY_URI,
                mGroupProjection,
                null,
                null,
                null
            )

            val groups = ArrayList<Group>()
            while (cursor?.moveToNext() == true) {
                val groupId = cursor.getLong(0)
                val title = cursor.getString(1)
                val summaryCount = cursor.getInt(2)
                groups.add(Group(groupId, title, summaryCount))
            }

            cursor?.close()

            mGroups = groups
            groups
        }()
    }

    fun contactSum(): Int {
        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            null,
            null,
            null
        )

        val count = cursor?.count ?: 0
        cursor?.close()
        return count
    }

    fun allContacts(groupId: Long? = null): List<Contact> {
        if (mContactsMap == null || mGroupsMap == null) {
            refreshCache()
        }

        val ret = when (groupId) {
            null -> {
                mContactsMap!!.toArray()
            }
            (-1).toLong() -> {
                mContactsMap!!.toArray()
            }
            else -> {
                val contactIdsArray = mGroupsMap!![groupId] ?: return ArrayList()
                val contactIds = HashMap<Long, Boolean>()
                for (id in contactIdsArray) {
                    contactIds[id] = true
                }
                mContactsMap!!.filterKeys { contactIds[it] == true }.toArray()
            }
        }
        ret.sortWith(compareBy({ it.last }, { it.first }))
        return ret
    }

    // CACHE

    fun invalidateCache() {
        mContactsMap = null
        mGroupsMap = null
    }

    private fun <T> filterPrivateBusinessLists(
        private: List<T>,
        business: List<T>,
        maxPrivate: Int,
        maxBusiness: Int,
        maxCombined: Int
    ): List<T> {
        val numPrivate = private.count()
        val numBusiness = business.count()
        val listPrivate = if (numPrivate > maxPrivate) {
            private.dropLast(numPrivate - maxPrivate)
        } else {
            private
        }
        val listBusiness = if (numBusiness > maxBusiness) {
            business.dropLast(numBusiness - maxBusiness)
        } else {
            business
        }

        val numAll = listPrivate.count() + listBusiness.count()
        return if (numAll > maxCombined) {
            val dropEach = (numAll - maxCombined) / 2
            val ret = listPrivate.dropLast(dropEach) + listBusiness.dropLast(dropEach)
            if (ret.count() > maxCombined) {
                ret.dropLast(ret.count() - maxCombined)
            } else {
                ret
            }
        } else {
            listPrivate + listBusiness
        }
    }

    private fun filterPhoneNumberGroups(list: List<TelephoneNumber>): List<TelephoneNumber> {
        // privateOnly 2, businessOnly 2, additional 10 => max 14 Elemente, max 12 von jedem Typ
        val phoneNumbersPrivate = list.filter { it.telephoneType == TelephoneType.PRIVATE }
        val phoneNumbersBusiness = list.filter { it.telephoneType == TelephoneType.BUSINESS }
        val filteredPhoneNumbers =
            filterPrivateBusinessLists(phoneNumbersPrivate, phoneNumbersBusiness, 12, 12, 14)

        // privateOnly 2, businessOnly 1, additional 10 => max 13 Elemente, max 12 von jedem Typ PRIVATE_MOBILE, max 11 von jedem Typ BUSINESS_MOBILE
        val mobileNumbersPrivate = list.filter { it.telephoneType == TelephoneType.PRIVATE_MOBILE }
        val mobileNumbersBusiness =
            list.filter { it.telephoneType == TelephoneType.BUSINESS_MOBILE }
        val filteredMobileNumbers =
            filterPrivateBusinessLists(mobileNumbersPrivate, mobileNumbersBusiness, 12, 11, 13)

        // privateOnly 1, businessOnly 1, additional 10 => max 12 Elemente, max 11 von jedem Typ
        val faxNumbersPrivate = list.filter { it.telephoneType == TelephoneType.PRIVATE_FAX }
        val faxNumbersBusiness = list.filter { it.telephoneType == TelephoneType.BUSINESS_FAX }
        val filteredFaxNumbers =
            filterPrivateBusinessLists(faxNumbersPrivate, faxNumbersBusiness, 11, 11, 12)

        // privateOnly 1, businessOnly 1, additional 10 => max 12 Elemente, max 11 von jedem Typ
        val voipNumbersPrivate = list.filter { it.telephoneType == TelephoneType.PRIVATE_VOIP }
        val voipNumbersBusiness = list.filter { it.telephoneType == TelephoneType.BUSINESS_VOIP }
        val filteredVoipNumbers =
            filterPrivateBusinessLists(voipNumbersPrivate, voipNumbersBusiness, 11, 11, 12)

        return filteredPhoneNumbers + filteredMobileNumbers + filteredFaxNumbers + filteredVoipNumbers
    }

    private fun filterAddressGroups(list: List<Address>): List<Address> {
        // privateOnly 1, businessOnly 1, additional 10 => max 12
        val addressPrivate = list.filter { it.addressType == AddressType.PRIVATE }
        val addressBusiness = list.filter { it.addressType == AddressType.BUSINESS }

        return filterPrivateBusinessLists(addressPrivate, addressBusiness, 11, 11, 12)
    }

    private fun filterEmailGroups(list: List<EmailAddress>): List<EmailAddress> {
        // privateOnly 2, businessOnly 1, additional 10 => max 13
        val emailsPrivate = list.filter { it.addressType == AddressType.PRIVATE }
        val emailsBusiness = list.filter { it.addressType == AddressType.BUSINESS }

        return filterPrivateBusinessLists(emailsPrivate, emailsBusiness, 12, 11, 13)
    }

    private fun filterHomepageGroups(list: List<Homepage>): List<Homepage> {
        // privateOnly 1, businessOnly 1, additional 10 => max 12
        val homepagePrivate = list.filter { it.addressType == AddressType.PRIVATE }
        val homepageBusiness = list.filter { it.addressType == AddressType.BUSINESS }

        return filterPrivateBusinessLists(homepagePrivate, homepageBusiness, 11, 11, 12)
    }

    private fun refreshCache() {
        val contacts = HashMap<Long, Contact>()
        val groups = HashMap<Long, ArrayList<Long>>() // groupId -> [contactId]
        val selection =
            "${ContactsContract.Data.MIMETYPE} IN ('${ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}', '${ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE}', '${ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE}', '${ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE}', '${ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE}', '${ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE}', '${ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE}')"
        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            mDataProjection,
            selection,
            null,
            null
        )

        while (cursor?.moveToNext() == true) {
            //val _id = cursor.getLong(0)
            val mimetype = cursor.getString(11)
            val contactId = cursor.getLong(13) // non-raw contactId
            //val lookupKey = cursor.getString(14)
            //val displayName = cursor.getString(15)
            //val inVisibleGroup = cursor.getInt(16)

            if (!contacts.containsKey(contactId)) {
                val contact = Contact()
                // Setting a contactId will fail the upload.
                //contact.contactId = contactId.toString()
                contacts[contactId] = contact
            }

            val contact = contacts[contactId]!!
            when (mimetype) {
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                    contact.first = cursor.getString(2)
                    contact.last = cursor.getString(3)
                    contact.prefix = cursor.getString(4)
                }

                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                    val type = when (cursor.getInt(2)) {
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> {
                            TelephoneType.PRIVATE
                        }
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> {
                            TelephoneType.PRIVATE_MOBILE
                        }
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> {
                            TelephoneType.BUSINESS
                        }
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE -> {
                            TelephoneType.BUSINESS_MOBILE
                        }
                        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> {
                            TelephoneType.PRIVATE_FAX
                        }
                        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> {
                            TelephoneType.BUSINESS_FAX
                        }
                        else -> {
                            // Formerly 'UNKNOWN', which will be converted to 'PRIVATE' in the
                            // backend, anyways.
                            TelephoneType.PRIVATE
                        }
                    }
                    val numbers = contact.telephoneNumbers?.toMutableList() ?: ArrayList()
                    numbers.add(TelephoneNumber(cursor.getString(1), type))
                    contact.telephoneNumbers = numbers
                }

                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                    val type: AddressType = when (cursor.getInt(2)) {
                        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> {
                            AddressType.PRIVATE
                        }
                        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> {
                            AddressType.BUSINESS
                        }
                        else -> {
                            // Default to 'PRIVATE', rather than 'UNKNOWN'
                            AddressType.PRIVATE
                        }
                    }
                    val emails = contact.emails?.toMutableList() ?: ArrayList()
                    emails.add(EmailAddress(type, cursor.getString(1)))
                    contact.emails = emails
                }

                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                    contact.company = cursor.getString(1)
                    // AG: Omitted, due to exceeding the 40-byte limit for some users.
                    // contact.jobTitle = cursor.getString(4)
                }

                ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE -> {
                    val groupId = cursor.getLong(1)
                    if (!groups.containsKey(groupId)) {
                        groups[groupId] = ArrayList()
                    }

                    groups[groupId]?.add(contactId)

                    val groupMembership = contact.groups?.toMutableList() ?: ArrayList()
                    groupMembership.add(Group(groupId))
                    contact.groups = groupMembership
                }

                ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> {
                    val type: AddressType = when (cursor.getInt(2)) {
                        ContactsContract.CommonDataKinds.Website.TYPE_HOME -> {
                            AddressType.PRIVATE
                        }
                        ContactsContract.CommonDataKinds.Website.TYPE_WORK -> {
                            AddressType.BUSINESS
                        }
                        else -> {
                            // Default to 'PRIVATE', rather than 'UNKNOWN'
                            AddressType.PRIVATE
                        }
                    }
                    val homepages = contact.homepages?.toMutableList() ?: ArrayList()
                    homepages.add(Homepage(type, cursor.getString(1)))
                    contact.homepages = homepages
                }

                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                    val type: AddressType = when (cursor.getInt(2)) {
                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> {
                            AddressType.PRIVATE
                        }
                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> {
                            AddressType.BUSINESS
                        }
                        else -> {
                            // Default to 'PRIVATE', rather than 'UNKNOWN'
                            AddressType.PRIVATE
                        }
                    }
                    val address = Address(
                        type,
                        cursor.getString(7),
                        cursor.getString(10),
                        cursor.getString(8),
                        cursor.getString(4),
                        cursor.getString(9)
                    )
                    val addresses = contact.addresses?.toMutableList() ?: ArrayList()
                    addresses.add(address)
                    contact.addresses = addresses
                }
            }

            // Filter contact lists.
            contact.telephoneNumbers?.let {
                contact.telephoneNumbers = filterPhoneNumberGroups(it)
            }

            contact.addresses?.let {
                contact.addresses = filterAddressGroups(it)
            }

            contact.emails?.let {
                contact.emails = filterEmailGroups(it)
            }

            contact.communities?.let {
                if (it.count() > 20) {
                    contact.communities = it.dropLast(it.count() - 20)
                }
            }

            contact.homepages?.let {
                contact.homepages = filterHomepageGroups(it)
            }

            contact.instantMessagings?.let {
                if (it.count() > 10) {
                    contact.instantMessagings = it.dropLast(it.count() - 10)
                }
            }
        }

        cursor?.close()

        mGroupsMap = groups
        mContactsMap = contacts
    }
}
