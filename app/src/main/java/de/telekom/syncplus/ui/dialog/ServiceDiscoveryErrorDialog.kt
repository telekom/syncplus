package de.telekom.syncplus.ui.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.telekom.syncplus.databinding.DialogServiceDiscoveryErrorBinding

class ServiceDiscoveryErrorDialog : DialogFragment() {
    companion object {
        const val ACTION_RETRY_SERVICE_DISCOVERY = "action_retry_service_discovery"

        fun instantiate(): ServiceDiscoveryErrorDialog {
            return ServiceDiscoveryErrorDialog()
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogServiceDiscoveryErrorBinding.inflate(layoutInflater, null, false)
        val dialog = MaterialAlertDialogBuilder(requireContext(), theme)
            .setView(binding.root)
            .setCancelable(false)
            .create()
            .apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCanceledOnTouchOutside(false)
            }

        binding.buttonAction.setOnClickListener {
            setFragmentResult(ACTION_RETRY_SERVICE_DISCOVERY, bundleOf())
            dialog.dismiss()
        }

        return dialog
    }
}
