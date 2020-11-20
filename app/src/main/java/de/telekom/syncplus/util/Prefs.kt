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

package de.telekom.syncplus.util

import android.content.Context

class Prefs(context: Context?) {
    companion object {
        private const val PREFS = "de.telekom.syncplus.PREFERENCES"
        private const val PREFS_STARTS = "PREFS_STARTS"
        private const val PREFS_LOG_TO_FILE = "log_to_file"
    }

    private val prefs = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var starts: Int
        get() = prefs?.getInt(PREFS_STARTS, 0) ?: 0
        set(value) = prefs?.edit()?.putInt(PREFS_STARTS, value)?.apply() ?: Unit

    var loggingEnabled: Boolean
        get() = prefs?.getBoolean(PREFS_LOG_TO_FILE, false) ?: false
        set(value) = prefs?.edit()?.putBoolean(PREFS_LOG_TO_FILE, value)?.apply() ?: Unit
}
