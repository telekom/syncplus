package de.telekom.syncplus.ui.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.telekom.dtagsyncpluskit.extra
import de.telekom.syncplus.databinding.DialogSingleActionBinding

class SingleActionDialog : DialogFragment() {
    companion object {
        const val ACTION_EVENT = "dialog_action_event"

        private const val TITLE_EXTRA = "dialog_title_extra"
        private const val MESSAGE_EXTRA = "dialog_message_extra"
        private const val ACTION_EXTRA = "dialog_action_extra"

        fun instantiate(
            titleText: String,
            messageText: String,
            actionText: String,
        ): SingleActionDialog {
            return SingleActionDialog().apply {
                arguments =
                    bundleOf(
                        TITLE_EXTRA to titleText,
                        MESSAGE_EXTRA to messageText,
                        ACTION_EXTRA to actionText,
                    )
            }
        }
    }

    private val title by extra<String>(TITLE_EXTRA)
    private val message by extra<String>(MESSAGE_EXTRA)
    private val action by extra<String>(ACTION_EXTRA)

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogSingleActionBinding.inflate(layoutInflater, null, false)
        val dialog =
            MaterialAlertDialogBuilder(requireContext(), theme)
                .setCancelable(false)
                .setView(binding.root)
                .create()
                .apply {
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                    setCanceledOnTouchOutside(false)
                }

        binding.textTitle.text = title
        binding.textMessage.text = message
        binding.buttonAction.text = action
        binding.buttonAction.setOnClickListener {
            setFragmentResult(ACTION_EVENT, bundleOf())
            dialog.dismiss()
        }

        return dialog
    }
}
