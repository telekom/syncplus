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

import java.util.logging.Handler
import java.util.logging.LogRecord

class StringHandler: Handler() {

    val builder = StringBuilder()

    init {
        formatter = PlainTextFormatter.DEFAULT
    }

    override fun publish(record: LogRecord) {
        builder.append(formatter.format(record))
    }

    override fun flush() {}
    override fun close() {}

    override fun toString() = builder.toString()

}
