package de.telekom.syncplus.ui.main.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import de.telekom.dtagsyncpluskit.extra
import de.telekom.syncplus.R
import kotlinx.android.synthetic.main.dialog_energysaver.view.*
import kotlinx.android.synthetic.main.dialog_single_action.view.*

class SingleActionDialog : DialogFragment() {

    companion object {
        const val ACTION_EVENT = "dialog_action_event"

        private const val TITLE_EXTRA = "dialog_title_extra"
        private const val MESSAGE_EXTRA = "dialog_message_extra"
        private const val ACTION_EXTRA = "dialog_action_extra"

        fun instantiate(
            titleText: String,
            messageText: String,
            actionText: String
        ): SingleActionDialog {
            return SingleActionDialog().apply {
                arguments = bundleOf(
                    TITLE_EXTRA to titleText,
                    MESSAGE_EXTRA to messageText,
                    ACTION_EXTRA to actionText
                )
            }
        }
    }

    private val title by extra<String>(TITLE_EXTRA)
    private val message by extra<String>(MESSAGE_EXTRA)
    private val action by extra<String>(ACTION_EXTRA)

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_single_action, null)
        val dialog = object : Dialog(requireContext(), theme) {
            override fun onBackPressed() {}
        }

        view.text_title.text = title
        view.text_message.text = message
        view.button_action.text = action

        view.button_action.setOnClickListener {
            setFragmentResult(ACTION_EVENT, bundleOf())
            dialog.dismiss()
        }

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.setContentView(view)
        return dialog
    }
}