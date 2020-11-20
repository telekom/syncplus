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

package de.telekom.dtagsyncpluskit.api

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class SpicaEnv(
    val baseUrl: String,
    val appId: String,
    val appSecret: String
) : Parcelable {
    companion object {
        fun fromBuildConfig(environ: Array<String>): SpicaEnv {
            return SpicaEnv(environ[0], environ[1], environ[2])
        }
    }
}
