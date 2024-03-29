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
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HomeSetDao : SyncableDao<HomeSet> {
    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId")
    fun getByService(serviceId: Long): List<HomeSet>

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId AND privBind")
    fun getBindableByService(serviceId: Long): List<HomeSet>

    @Query("SELECT COUNT(*) FROM homeset WHERE serviceId=:serviceId AND privBind")
    fun hasBindableByService(serviceId: Long): LiveData<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(homeSet: HomeSet): Long
}
