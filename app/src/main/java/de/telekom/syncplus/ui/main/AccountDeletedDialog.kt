package de.telekom.syncplus.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.telekom.syncplus.databinding.DialogAccountDeletedBinding

class AccountDeletedDialog : DialogFragment() {
    companion object {
        fun instantiate(): AccountDeletedDialog {
            return AccountDeletedDialog()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogAccountDeletedBinding.inflate(layoutInflater, null, false)
        val dialog =
            MaterialAlertDialogBuilder(requireContext(), theme)
                .setView(binding.root)
                .setCancelable(false)
                .create()
                .apply {
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                    setCanceledOnTouchOutside(false)
                }

        binding.button.setOnClickListener {
            dialog.dismiss()
        }

        return dialog
    }
}
