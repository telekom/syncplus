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

package de.telekom.dtagsyncpluskit.calendars

import android.content.ContentResolver
import android.content.Context
import android.os.Parcelable
import android.provider.CalendarContract
import androidx.annotation.RequiresPermission
import kotlinx.android.parcel.Parcelize

@Suppress("unused")
class CalendarFetcher(context: Context) {
    private val contentResolver: ContentResolver = context.contentResolver

    @Parcelize
    data class Calendar(
        val id: Long,
        val name: String,
        val protected: Boolean
    ) : Parcelable

    @RequiresPermission(value = android.Manifest.permission.READ_CALENDAR)
    fun allCalendars(): List<Calendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID, // 0
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, // 1
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL // 2
        )
        val selection = "((${CalendarContract.Calendars.VISIBLE} = ?))"
        val selectionArgs = arrayOf("1")
        val cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        val calendars = ArrayList<Calendar>()
        while (cursor?.moveToNext() == true) {
            val id = cursor.getLong(0)
            val displayName = cursor.getString(1)
            val accessLevel = cursor.getInt(2)
            calendars.add(Calendar(id, displayName, calendarIsReadOnly(accessLevel)))
        }

        cursor?.close()
        return calendars
    }

    private fun calendarIsReadOnly(accessLevel: Int): Boolean {
        return when (accessLevel) {
            CalendarContract.Calendars.CAL_ACCESS_OWNER,
            CalendarContract.Calendars.CAL_ACCESS_EDITOR,
            CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
            CalendarContract.Calendars.CAL_ACCESS_ROOT -> false
            else -> true
        }
    }
}
