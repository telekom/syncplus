package de.telekom.syncplus.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.telekom.syncplus.databinding.DialogEnergysaverBinding
import de.telekom.syncplus.util.Prefs

class EnergySaverDialog : DialogFragment() {
    companion object {
        fun instantiate(): EnergySaverDialog {
            return EnergySaverDialog()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogEnergysaverBinding.inflate(layoutInflater, null, false)
        val dialog =
            MaterialAlertDialogBuilder(requireContext(), theme)
                .setCancelable(false)
                .setView(binding.root)
                .create()
                .apply {
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                    setCanceledOnTouchOutside(false)
                }

        binding.button.setOnClickListener {
            requireActivity().let {
                val prefs = Prefs(it)
                prefs.energySavingDialogShown = true
                dialog.dismiss()
            }
        }

        return dialog
    }
}
