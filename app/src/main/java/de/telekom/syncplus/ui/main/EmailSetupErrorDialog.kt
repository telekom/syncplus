package de.telekom.syncplus.ui.main

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import de.telekom.syncplus.R
import kotlinx.android.synthetic.main.dialog_energysaver.view.*
import kotlinx.android.synthetic.main.dialog_service_discovery_error.view.*

class EmailSetupErrorDialog : DialogFragment() {

    companion object {
        fun instantiate(): EmailSetupErrorDialog {
            return EmailSetupErrorDialog()
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_email_setup_error, null)
        val dialog = object : Dialog(requireContext(), theme) {
            override fun onBackPressed() {}
        }

        view.button_action.setOnClickListener {
            dialog.dismiss()
        }

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.setContentView(view)
        return dialog
    }
}