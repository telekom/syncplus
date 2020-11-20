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

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.lang3.time.DateFormatUtils
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord

class PlainTextFormatter private constructor(
    private val logcat: Boolean
) : Formatter() {

    companion object {
        val LOGCAT = PlainTextFormatter(true)
        val DEFAULT = PlainTextFormatter(false)

        const val MAX_MESSAGE_LENGTH = 20000
    }

    override fun format(r: LogRecord): String {
        val builder = StringBuilder()

        if (!logcat) {
            builder
                .append(DateFormatUtils.format(r.millis, "yyyy-MM-dd HH:mm:ss"))
                .append(" ")
                .append(verbosity(r.level.intValue()))
                .append(r.threadID)
                .append(" ")
        }

        val className = shortClassName(r.sourceClassName)
        if (className != r.loggerName)
            builder.append("[").append(className).append("] ")

        builder.append(StringUtils.abbreviate(r.message, MAX_MESSAGE_LENGTH))

        r.thrown?.let {
            builder.append("\nEXCEPTION ")
                .append(ExceptionUtils.getStackTrace(it))
        }

        r.parameters?.let {
            for ((idx, param) in it.withIndex())
                builder.append("\n\tPARAMETER #").append(idx).append(" = ").append(param)
        }

        if (!logcat)
            builder.append("\n")

        return builder.toString()
    }

    private fun verbosity(level: Int): String {
        return when {
            level >= Level.SEVERE.intValue() -> "E/"
            level >= Level.WARNING.intValue() -> "W/"
            level >= Level.CONFIG.intValue() -> "I/"
            level >= Level.FINER.intValue() -> "D/"
            else -> ""
        }
    }

    private fun shortClassName(className: String) = className
        .replace(
            Regex("^de\\.telekom\\.(davdroid|cert4android|dav4android|ical4android|vcard4android|sync\\.plus|dtagsyncpluskit)\\."),
            ""
        )
        .replace(Regex("\\$.*$"), "")

}
