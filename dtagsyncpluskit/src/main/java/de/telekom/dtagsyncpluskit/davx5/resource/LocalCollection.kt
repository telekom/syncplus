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

package de.telekom.dtagsyncpluskit.davx5.resource

import android.provider.CalendarContract.Events
import de.telekom.dtagsyncpluskit.davx5.model.SyncState

interface LocalCollection<out T: LocalResource<*>> {

    /** collection title (used for user notifications etc.) **/
    val title: String

    var lastSyncState: SyncState?

    /**
     * Finds local resources of this collection which have been marked as *deleted* by the user
     * or an app acting on their behalf.
     *
     * @return list of resources marked as *deleted*
     */
    fun findDeleted(): List<T>

    /**
     * Finds local resources of this collection which have been marked as *dirty*, i.e. resources
     * which have been modified by the user or an app acting on their behalf.
     *
     * @return list of resources marked as *dirty*
     */
    fun findDirty(): List<T>

    /**
     * Finds local resources of this collection which do not have a file name and/or UID, but
     * need one for synchronization.
     *
     * For instance, exceptions of recurring events are local resources but do not need their
     * own file name/UID because they're sent with the same UID as the main event.
     *
     * @return list of resources which need file name and UID for synchronization, but don't have both of them
     */
    fun findDirtyWithoutNameOrUid(): List<T>

    /**
     * Finds a local resource of this collection with a given file name. (File names are assigned
     * by the sync adapter.)
     *
     * @param name file name to look for
     * @return resource with the given name, or null if none
     */
    fun findByName(name: String): T?


    /**
     * Sets the [LocalEvent.COLUMN_FLAGS] value for entries which are not dirty ([Events.DIRTY] is 0)
     * and have an [Events.ORIGINAL_ID] of null.
     *
     * @param flags    value of flags to set (for instance, [LocalResource.FLAG_REMOTELY_PRESENT]])
     *
     * @return         number of marked entries
     */
    fun markNotDirty(flags: Int): Int

    /**
     * Removes entries which are not dirty ([Events.DIRTY] is 0 and an [Events.ORIGINAL_ID] is null) with
     * a given flag combination.
     *
     * @param flags    exact flags value to remove entries with (for instance, if this is [LocalResource.FLAG_REMOTELY_PRESENT]],
     *                 all entries with exactly this flag will be removed)
     *
     * @return         number of removed entries
     */
    fun removeNotDirtyMarked(flags: Int): Int


    /**
     * Forgets the ETags of all members so that they will be reloaded from the server during sync.
     */
    fun forgetETags()

}
