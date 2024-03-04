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
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import okhttp3.HttpUrl

@Entity(
    tableName = "homeset",
    foreignKeys = [
        ForeignKey(
            entity = Service::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("serviceId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // index by service; no duplicate URLs per service
        Index("serviceId", "url", unique = true),
    ],
)
data class HomeSet(
    @PrimaryKey(autoGenerate = true)
    override var id: Long,
    var serviceId: Long,
    /**
     * Whether this homeset belongs to the [Service.principal] given by [serviceId].
     */
    var personal: Boolean,
    var url: HttpUrl,
    var privBind: Boolean = true,
    var displayName: String? = null,
) : IdEntity()
