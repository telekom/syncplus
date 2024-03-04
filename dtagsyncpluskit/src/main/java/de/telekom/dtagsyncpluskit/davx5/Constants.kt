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

/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package de.telekom.dtagsyncpluskit.davx5

object Constants {
    // TODO: Change from DavDroid green to another color!
    const val CALENDAR_DEFAULT_COLOR = 0xFF8bc34a.toInt()

    const val DEFAULT_SYNC_INTERVAL = 2 * 3600L // 2 hours

    /**
     * Context label for [org.apache.commons.lang3.exception.ContextedException].
     * Context value is the [at.bitfire.davdroid.resource.LocalResource]
     * which is related to the exception cause.
     */
    const val EXCEPTION_CONTEXT_LOCAL_RESOURCE = "localResource"

    /**
     * Context label for [org.apache.commons.lang3.exception.ContextedException].
     * Context value is the [okhttp3.HttpUrl] of the remote resource
     * which is related to the exception cause.
     */
    const val EXCEPTION_CONTEXT_REMOTE_RESOURCE = "remoteResource"
}
