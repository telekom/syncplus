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

package de.telekom.dtagsyncpluskit.davx5.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import okhttp3.HttpUrl

@Entity(tableName = "service",
    indices = [
        // only one service per type and account
        Index("accountName", "type", unique = true)
    ])
data class Service(
    @PrimaryKey(autoGenerate = true)
    var id: Long,

    var accountName: String,
    var type: String,

    var principal: HttpUrl?
) {

    companion object {
        const val TYPE_CALDAV = "caldav"
        const val TYPE_CARDDAV = "carddav"
    }

}
