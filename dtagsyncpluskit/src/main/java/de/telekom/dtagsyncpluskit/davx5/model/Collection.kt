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

import androidx.room.*
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.property.*
import de.telekom.dtagsyncpluskit.davx5.DavUtils
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Entity(tableName = "collection",
    foreignKeys = [
        ForeignKey(entity = Service::class, parentColumns = arrayOf("id"), childColumns = arrayOf("serviceId"), onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index("serviceId","type")
    ]
)
data class Collection(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,

    var serviceId: Long = 0,

    var type: String,
    var url: HttpUrl,

    var privWriteContent: Boolean = true,
    var privUnbind: Boolean = true,
    var forceReadOnly: Boolean = false,

    var displayName: String? = null,
    var description: String? = null,

    // CalDAV only
    var color: Int? = null,

    /** timezone definition (full VTIMEZONE) - not a TZID! **/
    var timezone: String? = null,

    /** whether the collection supports VEVENT; in case of calendars: null means true */
    var supportsVEVENT: Boolean? = null,

    /** whether the collection supports VTODO; in case of calendars: null means true */
    var supportsVTODO: Boolean? = null,

    /** whether the collection supports VJOURNAL; in case of calendars: null means true */
    var supportsVJOURNAL: Boolean? = null,

    /** Webcal subscription source URL */
    var source: HttpUrl? = null,

    /** whether this collection has been selected for synchronization */
    var sync: Boolean = false

): IdEntity() {

    companion object {

        const val TYPE_ADDRESSBOOK = "ADDRESS_BOOK"
        const val TYPE_CALENDAR = "CALENDAR"
        const val TYPE_WEBCAL = "WEBCAL"

        /**
         * Generates a collection entity from a WebDAV response.
         * @param dav WebDAV response
         * @return null if the response doesn't represent a collection
         */
        fun fromDavResponse(dav: Response): Collection? {
            val url = UrlUtils.withTrailingSlash(dav.href)
            val type: String = dav[ResourceType::class.java]?.let { resourceType ->
                when {
                    resourceType.types.contains(ResourceType.ADDRESSBOOK) -> TYPE_ADDRESSBOOK
                    resourceType.types.contains(ResourceType.CALENDAR)    -> TYPE_CALENDAR
                    resourceType.types.contains(ResourceType.SUBSCRIBED)  -> TYPE_WEBCAL
                    else -> null
                }
            } ?: return null

            var privWriteContent = true
            var privUnbind = true
            dav[CurrentUserPrivilegeSet::class.java]?.let { privilegeSet ->
                privWriteContent = privilegeSet.mayWriteContent
                privUnbind = privilegeSet.mayUnbind
            }

            var displayName: String? = null
            dav[DisplayName::class.java]?.let {
                if (!it.displayName.isNullOrEmpty())
                    displayName = it.displayName
            }

            var description: String? = null
            var color: Int? = null
            var timezone: String? = null
            var supportsVEVENT: Boolean? = null
            var supportsVTODO: Boolean? = null
            var supportsVJOURNAL: Boolean? = null
            var source: HttpUrl? = null
            when (type) {
                TYPE_ADDRESSBOOK -> {
                    dav[AddressbookDescription::class.java]?.let { description = it.description }
                }
                TYPE_CALENDAR, TYPE_WEBCAL -> {
                    dav[CalendarDescription::class.java]?.let { description = it.description }
                    dav[CalendarColor::class.java]?.let { color = it.color }
                    dav[CalendarTimezone::class.java]?.let { timezone = it.vTimeZone }

                    if (type == TYPE_CALENDAR) {
                        supportsVEVENT = true
                        supportsVTODO = true
                        supportsVJOURNAL = true
                        dav[SupportedCalendarComponentSet::class.java]?.let {
                            supportsVEVENT = it.supportsEvents
                            supportsVTODO = it.supportsTasks
                            supportsVJOURNAL = it.supportsJournal
                        }
                    } else { // Type.WEBCAL
                        dav[Source::class.java]?.let { source = it.hrefs.firstOrNull()?.let { rawHref ->
                            val href = rawHref
                                .replace("^webcal://".toRegex(), "http://")
                                .replace("^webcals://".toRegex(), "https://")
                            href.toHttpUrlOrNull()
                        } }
                        supportsVEVENT = true
                    }
                }
            }

            return Collection(
                type = type,
                url = url,
                privWriteContent = privWriteContent,
                privUnbind = privUnbind,
                displayName = displayName,
                description = description,
                color = color,
                timezone = timezone,
                supportsVEVENT = supportsVEVENT,
                supportsVTODO = supportsVTODO,
                supportsVJOURNAL = supportsVJOURNAL,
                source = source
            )
        }

    }


    // non-persistent properties
    @Ignore
    var confirmed: Boolean = false


    // calculated properties
    fun title() = displayName ?: DavUtils.lastSegmentOfUrl(url)
    fun readOnly() = forceReadOnly || !privWriteContent

}
