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

package de.telekom.syncplus.ui.main

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Bundle
import android.widget.Toast
import de.telekom.syncplus.BuildConfig
import de.telekom.syncplus.R

class CustomErrorAlert(
    private val errorTitle: String,
    private val errorDescription: String?,
    private val detailedDescription: String? = null,
    private val callback: ((Boolean) -> Unit)? = null
) : CustomAlertDialog() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        title = errorTitle
        text = errorDescription
        cancelText = getString(R.string.button_title_cancel)
        successText = getString(R.string.button_title_retry)
        setOnSuccessListener {
            callback?.invoke(true)
        }
        setOnCancelListener {
            callback?.invoke(false)
        }
        setOnCopyListener {
            val clipboard =
                requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("", detailedDescription)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Fehlerinformation kopiert", Toast.LENGTH_LONG).show()
        }
        return dialog
    }
}
