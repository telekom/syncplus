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

package de.telekom.dtagsyncpluskit.utils

import android.content.Intent
import android.net.Uri
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import okhttp3.Request
import okio.Buffer

fun openPlayStore(
    fragment: BaseFragment?,
    packageName: String,
    resultCode: Int? = null,
) {
    try {
        val uri = Uri.parse("market://details?id=$packageName")
        if (resultCode == null) {
            fragment?.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } else {
            fragment?.startActivityForResult(Intent(Intent.ACTION_VIEW, uri), resultCode)
        }
    } catch (e: Exception) {
        CountlyWrapper.recordHandledException(e)
        val uri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        if (resultCode == null) {
            fragment?.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } else {
            fragment?.startActivityForResult(Intent(Intent.ACTION_VIEW, uri), resultCode)
        }
    }
}

internal fun Request.getBodyAsString(): String {
    return try{
        val requestCopy = this.newBuilder().build()
        val buffer = Buffer()
        requestCopy.body?.writeTo(buffer)
        buffer.readUtf8()
    } catch (t: Throwable){
        ""
    }
}
