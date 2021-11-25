package de.telekom.syncplus.ui.main

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import de.telekom.syncplus.R
import de.telekom.syncplus.util.Prefs
import kotlinx.android.synthetic.main.dialog_energysaver.view.*

class EnergySaverDialog(private val ctx: Context) : DialogFragment() {
    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_energysaver, null)
        val dialog = object : Dialog(requireContext(), theme) {
            override fun onBackPressed() {}
        }

        view.button.setOnClickListener {
            val prefs = Prefs(ctx)
            prefs.energySavingDialogShown = true
            dialog.dismiss()
        }

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.setContentView(view)
        return dialog
    }
}