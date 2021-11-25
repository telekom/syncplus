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
import kotlinx.serialization.*
import kotlinx.serialization.json.*

class Prefs(context: Context?) {
    companion object {
        private const val PREFS = "de.telekom.syncplus.PREFERENCES"
        private const val PREFS_STARTS = "PREFS_STARTS"
        private const val PREFS_LOG_TO_FILE = "log_to_file"
        private const val PREFS_ENERGY_SAVING_DIALOG_SHOWN = "energy_saving_dialog_shown"
        private const val PREFS_CURRENT_VERSION = "prefs_current_version"
        private const val PREFS_LAST_SYNCS = "prefs_last_syncs"

        @Serializable
        data class LastSyncs(val lastSyncs: HashMap<String, Long>)
    }

    private val prefs = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var starts: Int
        get() = prefs?.getInt(PREFS_STARTS, 0) ?: 0
        set(value) = prefs?.edit()?.putInt(PREFS_STARTS, value)?.apply() ?: Unit

    var loggingEnabled: Boolean
        get() = prefs?.getBoolean(PREFS_LOG_TO_FILE, false) ?: false
        set(value) = prefs?.edit()?.putBoolean(PREFS_LOG_TO_FILE, value)?.apply() ?: Unit

    var energySavingDialogShown: Boolean
        get() = prefs?.getBoolean(PREFS_ENERGY_SAVING_DIALOG_SHOWN, false) ?: false
        set(value) = prefs?.edit()?.putBoolean(PREFS_ENERGY_SAVING_DIALOG_SHOWN, value)?.apply()
            ?: Unit

    var currentVersionCode: Int
        get() = prefs?.getInt(PREFS_CURRENT_VERSION, 0) ?: 0
        set(value) = prefs?.edit()?.putInt(PREFS_CURRENT_VERSION, value)?.apply() ?: Unit

    var lastSyncs: LastSyncs
        get() {
            val default = LastSyncs(lastSyncs = HashMap())
            val defaultEncoded = Json.encodeToString(default)
            val encoded = prefs?.getString(PREFS_LAST_SYNCS, defaultEncoded) ?: defaultEncoded
            return Json.decodeFromString(encoded)
        }
        set(value) {
            val encoded = Json.encodeToString(value)
            prefs?.edit()?.putString(PREFS_LAST_SYNCS, encoded)?.apply() ?: Unit
        }
}
