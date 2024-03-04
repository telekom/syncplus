package de.telekom.syncplus.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.telekom.syncplus.databinding.DialogEmailSetupErrorBinding

class EmailSetupErrorDialog : DialogFragment() {
    companion object {
        fun instantiate(): EmailSetupErrorDialog {
            return EmailSetupErrorDialog()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogEmailSetupErrorBinding.inflate(layoutInflater, null, false)
        val dialog =
            MaterialAlertDialogBuilder(requireContext(), theme)
                .setCancelable(false)
                .setView(binding.root)
                .create()
                .apply {
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                    setCanceledOnTouchOutside(false)
                }

        binding.buttonAction.setOnClickListener {
            dialog.dismiss()
        }

        return dialog
    }
}
