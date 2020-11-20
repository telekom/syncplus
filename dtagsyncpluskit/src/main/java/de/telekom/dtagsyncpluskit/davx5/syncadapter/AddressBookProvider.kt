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

package de.telekom.dtagsyncpluskit.davx5.syncadapter

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri

@Suppress("ImplicitNullableNothingType")
open class AddressBookProvider: ContentProvider() {

    override fun onCreate() = false
    override fun insert(p0: Uri, p1: ContentValues?) = null
    override fun query(p0: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?) = null
    override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?) = 0
    override fun delete(p0: Uri, p1: String?, p2: Array<out String>?) = 0
    override fun getType(p0: Uri) = null

}
