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

package de.telekom.dtagsyncpluskit.davx5.model

import de.telekom.dtagsyncpluskit.davx5.log.Logger
import java.util.logging.Level

class DaoTools<T: IdEntity>(dao: SyncableDao<T>): SyncableDao<T> by dao {

    /**
     * Synchronizes a list of "old" elements with a list of "new" elements so that the list
     * only contain equal elements.
     *
     * @param allOld      list of old elements
     * @param allNew      map of new elements (stored in key map)
     * @param selectKey   generates a unique key from the element (will be called on old elements)
     * @param prepareNew  prepares new elements (can be used to take over properties of old elements)
     */
    fun <K> syncAll(allOld: List<T>, allNew: Map<K,T>, selectKey: (T) -> K, prepareNew: (new: T, old: T) -> Unit = { _, _ -> }) {
        Logger.log.log(Level.FINE, "Syncing tables", arrayOf(allOld, allNew))
        val remainingNew = allNew.toMutableMap()
        allOld.forEach { old ->
            val key = selectKey(old)
            val matchingNew = remainingNew[key]
            if (matchingNew != null) {
                // keep this old item, but maybe update it
                matchingNew.id = old.id     // identity is proven by key
                prepareNew(matchingNew, old)

                if (matchingNew != old)
                    update(matchingNew)

                // remove from remainingNew
                remainingNew -= key
            } else {
                // this old item is not present anymore, delete it
                delete(old)
            }
        }
        insert(remainingNew.values.toList())
    }

}
