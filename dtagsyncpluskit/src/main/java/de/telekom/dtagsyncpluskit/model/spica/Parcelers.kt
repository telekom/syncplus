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

import android.os.Parcel
import kotlinx.android.parcel.Parceler

object UTF8StringParceler20 : Parceler<String?> {
    override fun create(parcel: Parcel): String? = parcel.readString()

    override fun String?.write(parcel: Parcel, flags: Int) {
        this?.let {
            parcel.writeString(it.substring(0, kotlin.math.min(it.length, 20)))
        }
    }
}

object UTF8StringParceler38 : Parceler<String?> {
    override fun create(parcel: Parcel): String? = parcel.readString()

    override fun String?.write(parcel: Parcel, flags: Int) {
        this?.let {
            parcel.writeString(it.substring(0, kotlin.math.min(it.length, 38)))
        }
    }
}

object UTF8StringParceler40 : Parceler<String?> {
    override fun create(parcel: Parcel): String? = parcel.readString()

    override fun String?.write(parcel: Parcel, flags: Int) {
        this?.let {
            parcel.writeString(it.substring(0, kotlin.math.min(it.length, 40)))
        }
    }
}

object UTF8StringParceler100 : Parceler<String?> {
    override fun create(parcel: Parcel): String? = parcel.readString()

    override fun String?.write(parcel: Parcel, flags: Int) {
        this?.let {
            parcel.writeString(it.substring(0, kotlin.math.min(it.length, 100)))
        }
    }
}

object UTF8StringParceler120 : Parceler<String?> {
    override fun create(parcel: Parcel): String? = parcel.readString()

    override fun String?.write(parcel: Parcel, flags: Int) {
        this?.let {
            parcel.writeString(it.substring(0, kotlin.math.min(it.length, 120)))
        }
    }
}

object UTF8StringParceler200 : Parceler<String?> {
    override fun create(parcel: Parcel): String? = parcel.readString()

    override fun String?.write(parcel: Parcel, flags: Int) {
        this?.let {
            parcel.writeString(it.substring(0, kotlin.math.min(it.length, 200)))
        }
    }
}
object UTF8StringParceler400 : Parceler<String?> {
    override fun create(parcel: Parcel): String? = parcel.readString()

    override fun String?.write(parcel: Parcel, flags: Int) {
        this?.let {
            parcel.writeString(it.substring(0, kotlin.math.min(it.length, 400)))
        }
    }
}

object UTF8StringParceler4000 : Parceler<String?> {
    override fun create(parcel: Parcel): String? = parcel.readString()

    override fun String?.write(parcel: Parcel, flags: Int) {
        this?.let {
            parcel.writeString(it.substring(0, kotlin.math.min(it.length, 4000)))
        }
    }
}

object UTF8StringParceler6000 : Parceler<String?> {
    override fun create(parcel: Parcel): String? = parcel.readString()

    override fun String?.write(parcel: Parcel, flags: Int) {
        this?.let {
            parcel.writeString(it.substring(0, kotlin.math.min(it.length, 6000)))
        }
    }
}

object ByteBase64StringParceler : Parceler<String?> {
    override fun create(parcel: Parcel): String? = parcel.readString()

    override fun String?.write(parcel: Parcel, flags: Int) {
        this?.let {
            parcel.writeString(it.substring(0, kotlin.math.min(it.length, 13981016)))
        }
    }
}
