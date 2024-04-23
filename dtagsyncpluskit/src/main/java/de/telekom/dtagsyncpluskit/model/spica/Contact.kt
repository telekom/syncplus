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

package de.telekom.dtagsyncpluskit.model.spica

import android.os.Parcelable
import de.telekom.dtagsyncpluskit.model.Group
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith

@Suppress("unused")
enum class Flag {
    PROFILE,
    FAVORITE,
    PRIVATE,
    BUSINESS,
    EXCLUDED_FROM_SYNCHRONIZATION,
}

@Parcelize
data class Contact(
    var addresses: List<Address>? = null,
    var anniversary: String? = null,
    var anniversaryReminders: List<Reminder>? = null,
    var birthday: String? = null,
    var birthdayReminders: List<Reminder>? = null,
    var communities: List<Community>? = null,
    var company: @WriteWith<UTF8StringParceler100> String? = null,
    var contactId: @WriteWith<UTF8StringParceler38> String? = null,
    var deMails: List<DeMail>? = null,
    var emails: List<EmailAddress>? = null,
    var first: @WriteWith<UTF8StringParceler100> String? = null,
    var flags: List<Flag>? = null,
    var homepages: List<Homepage>? = null,
    var instantMessagings: List<InstantMessaging>? = null,
    var jobTitle: @WriteWith<UTF8StringParceler40> String? = null,
    var last: @WriteWith<UTF8StringParceler100> String? = null,
    var modifiedDate: String? = null,
    var notes: @WriteWith<UTF8StringParceler6000> String? = null,
    var picture: @WriteWith<ByteBase64StringParceler> String? = null, // base64
    var prefix: @WriteWith<UTF8StringParceler20> String? = null,
    var telephoneNumbers: List<TelephoneNumber>? = null,
    var title: @WriteWith<UTF8StringParceler40> String? = null,
    var version: String? = null,
    var groups: List<Group>? = null,
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Contact

        if (contactId != other.contactId) return false
        if (first != other.first) return false
        if (last != other.last) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contactId?.hashCode() ?: 0
        result = 31 * result + (first?.hashCode() ?: 0)
        result = 31 * result + (last?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "{ID: $contactId, $first, $last ($groups)}"
    }

    fun formatName(defaultName: String = ""): String {
        return when {
            first != null && last != null -> {
                return "$first $last"
            }

            first != null -> first!!
            last != null -> last!!
            !emails.isNullOrEmpty() && emails!![0].email != null -> emails!![0].email!!
            else -> buildNameFromNumber() ?: defaultName
        }
    }

    private fun buildNameFromNumber(): String? {
        return telephoneNumbers?.find {
            it.telephoneType == TelephoneType.PRIVATE || it.telephoneType == TelephoneType.PRIVATE_MOBILE
        }?.number
    }
}
