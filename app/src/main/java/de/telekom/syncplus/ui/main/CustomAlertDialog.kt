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

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.telekom.syncplus.R

@Suppress("unused")
open class CustomAlertDialog : DialogFragment() {
    private var mTitleView: TextView? = null
    private var mTextView: TextView? = null
    private var mCancelButton: MaterialButton? = null
    private var mSuccessButton: MaterialButton? = null

    private var mOnCancelListener: View.OnClickListener? = View.OnClickListener { dismiss() }
        set(value) {
            field = View.OnClickListener {
                dismiss()
                value?.onClick(it)
            }
        }

    private var mOnSuccessListener: View.OnClickListener? = View.OnClickListener { dismiss() }
        set(value) {
            field = View.OnClickListener {
                dismiss()
                value?.onClick(it)
            }
        }

    private var mOnCopyListener: View.OnClickListener? = null

    var title: String? = null
        set(value) {
            field = value
            mTitleView?.text = value
            mTitleView?.visibility = if (value == null) View.GONE else View.VISIBLE
        }

    var text: String? = null
        set(value) {
            field = value
            mTextView?.text = value
            mTextView?.visibility = if (value == null) View.GONE else View.VISIBLE
        }

    var cancelText: String? = null
        set(value) {
            field = value
            mCancelButton?.text = value
        }

    var successText: String? = null
        set(value) {
            field = value
            mSuccessButton?.text = value
        }

    var hasCancelButton: Boolean = true
        set(value) {
            field = value
            mCancelButton?.visibility = View.INVISIBLE
        }

    private var hasSuccessButton: Boolean = true
        set(value) {
            field = value
            mSuccessButton?.visibility = View.INVISIBLE
        }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_alert, null)

        mTitleView = view.findViewById(android.R.id.title)
        mTitleView?.text = title
        mTitleView?.visibility = if (title == null) View.GONE else View.VISIBLE

        mTextView = view.findViewById(android.R.id.text1)
        mTextView?.text = text
        mTextView?.visibility = if (text == null) View.GONE else View.VISIBLE
        mTextView?.setOnClickListener(mOnCopyListener)

        mCancelButton = view.findViewById(android.R.id.button1)
        mCancelButton?.text = cancelText
        mCancelButton?.visibility = if (hasCancelButton) View.VISIBLE else View.INVISIBLE
        mCancelButton?.setOnClickListener(mOnCancelListener)

        mSuccessButton = view.findViewById(android.R.id.button2)
        mSuccessButton?.text = successText
        mSuccessButton?.visibility = if (hasSuccessButton) View.VISIBLE else View.INVISIBLE
        mSuccessButton?.setOnClickListener(mOnSuccessListener)

        val dialog = MaterialAlertDialogBuilder(requireContext(), theme)
            .setView(view)
            .setCancelable(false)
            .create()
            .apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCanceledOnTouchOutside(false)
            }

        return dialog
    }

    fun setOnCancelListener(l: View.OnClickListener?) {
        mOnCancelListener = l
        mCancelButton?.setOnClickListener(mOnCancelListener)
    }

    fun setOnCancelListener(l: (View) -> Unit) {
        mOnCancelListener = View.OnClickListener(l)
        mCancelButton?.setOnClickListener(mOnCancelListener)
    }

    fun setOnSuccessListener(l: View.OnClickListener?) {
        mOnSuccessListener = l
        mSuccessButton?.setOnClickListener(mOnSuccessListener)
    }

    fun setOnSuccessListener(l: (View) -> Unit) {
        mOnSuccessListener = View.OnClickListener(l)
        mSuccessButton?.setOnClickListener(mOnSuccessListener)
    }

    fun setOnCopyListener(l: View.OnClickListener?) {
        mOnCopyListener = l
        mTextView?.setOnClickListener(mOnCopyListener)
    }

    fun setOnCopyListener(l: (View) -> Unit) {
        mOnCopyListener = View.OnClickListener(l)
        mTextView?.setOnClickListener(mOnCopyListener)
    }
}
