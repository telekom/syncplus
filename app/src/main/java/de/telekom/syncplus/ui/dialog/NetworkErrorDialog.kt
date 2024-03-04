package de.telekom.syncplus.ui.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.telekom.syncplus.databinding.DialogNetworkErrorBinding

class NetworkErrorDialog : DialogFragment() {

    companion object {
        fun instantiate(): NetworkErrorDialog {
            return NetworkErrorDialog()
        }
    }


    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogNetworkErrorBinding.inflate(layoutInflater, null, false)
        val dialog = MaterialAlertDialogBuilder(requireContext(), theme)
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