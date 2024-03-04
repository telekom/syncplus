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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.telekom.dtagsyncpluskit.davx5.AndroidSingleton

@Suppress("ClassName")
@Database(
    entities = [
        Service::class,
        HomeSet::class,
        Collection::class,
    ],
    exportSchema = false,
    version = 2,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serviceDao(): ServiceDao

    abstract fun homeSetDao(): HomeSetDao

    abstract fun collectionDao(): CollectionDao

    companion object : AndroidSingleton<AppDatabase>() {
        override fun createInstance(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "services.db")
                .addMigrations(*migrations)
                .fallbackToDestructiveMigration() // as a last fallback, recreate database instead of crashing
                .build()

        private val migrations: Array<Migration> =
            arrayOf(
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE homeset ADD COLUMN personal INTEGER NOT NULL DEFAULT 1")
                        db.execSQL(
                            "ALTER TABLE collection ADD COLUMN homeSetId INTEGER DEFAULT NULL REFERENCES homeset(id) ON DELETE SET NULL",
                        )
                        db.execSQL("ALTER TABLE collection ADD COLUMN owner TEXT DEFAULT NULL")
                        db.execSQL("CREATE INDEX index_collection_homeSetId_type ON collection(homeSetId, type)")
                    }
                },
            )
    }
}
