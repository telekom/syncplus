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

import android.util.Log
import org.apache.commons.lang3.math.NumberUtils
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

object LogcatHandler : Handler() {

    private const val MAX_LINE_LENGTH = 3000

    init {
        formatter = PlainTextFormatter.LOGCAT
        level = Level.ALL
    }

    override fun publish(r: LogRecord) {
        val text = formatter.format(r)
        val level = r.level.intValue()

        val end = text.length
        var pos = 0
        while (pos < end) {
            val line = text.substring(pos, NumberUtils.min(pos + MAX_LINE_LENGTH, end))
            when {
                level >= Level.SEVERE.intValue() -> Log.e(r.loggerName, line)
                level >= Level.WARNING.intValue() -> Log.w(r.loggerName, line)
                level >= Level.CONFIG.intValue() -> Log.i(r.loggerName, line)
                level >= Level.FINER.intValue() -> Log.d(r.loggerName, line)
                else -> Log.v(r.loggerName, line)
            }
            pos += MAX_LINE_LENGTH
        }
    }

    override fun flush() {}
    override fun close() {}
}
