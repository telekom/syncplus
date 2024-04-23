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

package de.telekom.dtagsyncpluskit.davx5.log

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import at.bitfire.vcard4android.Constants
import de.telekom.dtagsyncpluskit.utils.CountlyWrapper
import java.io.File
import java.io.IOException
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger

@SuppressLint("StaticFieldLeak")
object Logger : SharedPreferences.OnSharedPreferenceChangeListener {
    val log: Logger = Logger.getLogger("SyncPlus")
    private val cardLogger: Logger = Logger.getLogger("vcard4android")

    private const val LOG_TO_FILE = "log_to_file"
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var notificationHandler: (logDir: File?, logFile: File?, cancel: Boolean) -> Unit

    fun initialize(
        context: Context,
        notificationHandler: (logDir: File?, logFile: File?, cancel: Boolean) -> Unit,
    ) {
        this.context = context.applicationContext
        this.preferences =
            this.context.getSharedPreferences(
                "de.telekom.syncplus.PREFERENCES",
                Context.MODE_PRIVATE,
            )
        this.preferences.registerOnSharedPreferenceChangeListener(this)
        this.notificationHandler = notificationHandler

        reInit()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        if (key == LOG_TO_FILE) {
            log.info("Logging settings changed; re-initializing logger")
            reInit()
        }
    }

    private fun reInit() {
        Log.d("SyncPlus", "---- INIT LOGGER")
        val logToFile = preferences.getBoolean(LOG_TO_FILE, false)
        val logVerbose = logToFile || Log.isLoggable(log.name, Log.DEBUG)
        val rootLogger = Logger.getLogger("")
        rootLogger.level = if (logVerbose) Level.ALL else Level.INFO

        // remove all handlers and add our own logcat handler
        rootLogger.useParentHandlers = false
        rootLogger.handlers.forEach { rootLogger.removeHandler(it) }
        rootLogger.addHandler(LogcatHandler)
        cardLogger.addHandler(LogcatHandler)

        // log to external file according to preferences
        if (logToFile) {
            val logDir = debugDir(context) ?: return
            val logFile = File(logDir, "syncplus-log.txt")

            try {
                val fileHandler = FileHandler(logFile.toString(), true)
                fileHandler.formatter = PlainTextFormatter.DEFAULT
                rootLogger.addHandler(fileHandler)
                cardLogger.addHandler(fileHandler)
            } catch (e: IOException) {
                CountlyWrapper.recordHandledException(e)
                log.log(Level.SEVERE, "Couldn't create log file", e)
            }

            notificationHandler(logDir, logFile, false)
        } else {
            notificationHandler(null, null, true)

            // delete old logs
            debugDir(context)?.deleteRecursively()
        }
    }

    private fun debugDir(context: Context): File? {
        val dir = File(context.filesDir, "debug")
        if (dir.exists() && dir.isDirectory) {
            return dir
        }

        if (dir.mkdir()) {
            return dir
        }

        return null
    }
}
