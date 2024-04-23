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

import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao : SyncableDao<Collection> {
    @Query("SELECT * FROM collection WHERE id=:id")
    fun get(id: Long): Collection?

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId")
    fun getByService(serviceId: Long): List<Collection>

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND type=:type ORDER BY displayName, url")
    fun getByServiceAndType(
        serviceId: Long,
        type: String,
    ): List<Collection>

    /**
     * Returns collections which
     *   - support VEVENT and/or VTODO (= supported calendar collections), or
     *   - have supportsVEVENT = supportsVTODO = null (= address books)
     */
    @Query(
        """SELECT * FROM collection WHERE serviceId=:serviceId AND type=:type
           AND (supportsVTODO OR supportsVEVENT OR (supportsVEVENT IS NULL AND supportsVTODO IS NULL))
           ORDER BY displayName, URL
           """,
    )
    fun flowByServiceAndType(
        serviceId: Long,
        type: String
    ): Flow<List<Collection>>

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND sync")
    fun getByServiceAndSync(serviceId: Long): List<Collection>

    @Query(
        "SELECT collection.* FROM collection, homeset WHERE collection.serviceId=:serviceId AND type=:type AND homeSetId=homeset.id AND homeset.personal ORDER BY collection.displayName, collection.url",
    )
    fun pagePersonalByServiceAndType(
        serviceId: Long,
        type: String,
    ): DataSource.Factory<Int, Collection>

    @Query("SELECT COUNT(*) FROM collection WHERE serviceId=:serviceId AND sync")
    fun observeHasSyncByService(serviceId: Long): LiveData<Boolean>

    @Query(
        "SELECT * FROM collection WHERE serviceId=:serviceId AND type='${Collection.TYPE_CALENDAR}' AND supportsVEVENT AND sync ORDER BY displayName, url",
    )
    fun getSyncCalendars(serviceId: Long): List<Collection>

    @Query(
        "SELECT * FROM collection WHERE serviceId=:serviceId AND type='${Collection.TYPE_CALENDAR}' AND supportsVTODO AND sync ORDER BY displayName, url",
    )
    fun getSyncTaskLists(serviceId: Long): List<Collection>

    @Insert(onConflict = REPLACE)
    fun insertOrReplace(collection: Collection)

    @Insert
    fun insert(collection: Collection)

    @Update(onConflict = REPLACE)
    override fun update(item: Collection)
}
