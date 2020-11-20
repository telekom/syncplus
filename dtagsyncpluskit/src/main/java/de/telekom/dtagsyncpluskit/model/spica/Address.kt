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
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.WriteWith

@Suppress("unused")
enum class AddressType {
    PRIVATE, BUSINESS, UNKNOWN
}

@Parcelize
data class Address(
    var addressType: AddressType? = null,
    var city: @WriteWith<UTF8StringParceler120> String? = null,
    var country: @WriteWith<UTF8StringParceler100> String? = null,
    var state: @WriteWith<UTF8StringParceler100> String? = null,
    var street: @WriteWith<UTF8StringParceler120> String? = null,
    var zipCode: @WriteWith<UTF8StringParceler40> String? = null
) : Parcelable
