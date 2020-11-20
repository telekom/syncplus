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

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.apache.commons.collections4.keyvalue.MultiKey
import org.apache.commons.collections4.map.HashedMap
import org.apache.commons.collections4.map.MultiKeyMap
import java.util.*

/**
 * Primitive cookie store that stores cookies in a (volatile) hash map.
 * Will be sufficient for session cookies.
 */
class MemoryCookieStore: CookieJar {

    /**
     * Stored cookies. The multi-key consists of three parts: name, domain, and path.
     * This ensures that cookies can be overwritten. [RFC 6265 5.3 Storage Model]
     * Not thread-safe!
     */
    private val storage = MultiKeyMap.multiKeyMap(HashedMap<MultiKey<out String>, Cookie>())!!

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(storage) {
            for (cookie in cookies)
                storage.put(cookie.name, cookie.domain, cookie.path, cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = LinkedList<Cookie>()

        synchronized(storage) {
            val iter = storage.mapIterator()
            while (iter.hasNext()) {
                iter.next()
                val cookie = iter.value

                // remove expired cookies
                if (cookie.expiresAt <= System.currentTimeMillis()) {
                    iter.remove()
                    continue
                }

                // add applicable cookies
                if (cookie.matches(url))
                    cookies += cookie
            }
        }

        return cookies
    }

}
