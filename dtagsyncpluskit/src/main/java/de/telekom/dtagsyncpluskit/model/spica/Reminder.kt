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
import kotlinx.parcelize.Parcelize

@Suppress("unused")
enum class Action {
    EMAIL,
    SMS,
    DISPLAY,
    AUDIO,
    PROCEDURE,
}

@Parcelize
data class Reminder(
    var action: Action? = null,
    var days: Int? = null,
    var hours: Int? = null,
    var minutes: Int? = null,
    var seconds: Int? = null,
    var weeks: Int? = null,
) : Parcelable
