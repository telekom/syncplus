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

package de.telekom.dtagsyncpluskit.api

import de.telekom.dtagsyncpluskit.model.spica.*
import retrofit2.Call
import retrofit2.http.*

@Suppress("unused")
interface SpicaAPI {
    enum class Filter {
        HARD,
        SOFT,
    }

    @PUT("spica/rest/contacts/v1/import")
    fun importContacts(
        @Body importContactData: ImportContactData,
    ): Call<ContactIdentifiersResponse>

    @Headers(
        "group: true",
        "showImportResult: true",
    )
    @POST("spica/rest/contacts/v1/import")
    fun importAndMergeContacts(
        @Body contacts: ContactList,
    ): Call<ContactIdentifiersResponse>

    @POST("spica/rest/contacts/v1/duplicates")
    fun checkDuplicates(
        @Body duplicates: ContactList,
        @Header("filter") filter: Filter = Filter.HARD,
        @Header("full") full: Boolean = true,
        @Header("pictures") pictures: Boolean = false,
    ): Call<DuplicatesResponse>
}
