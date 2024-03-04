package de.telekom.syncplus.ui.main.help

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.usabilla.sdk.ubform.UbConstants.INTENT_CLOSE_CAMPAIGN
import com.usabilla.sdk.ubform.UbConstants.INTENT_CLOSE_FORM
import com.usabilla.sdk.ubform.UbConstants.INTENT_ENTRIES
import com.usabilla.sdk.ubform.Usabilla
import com.usabilla.sdk.ubform.UsabillaFormCallback
import com.usabilla.sdk.ubform.sdk.form.FormClient
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.utils.openPlayStore
import de.telekom.syncplus.App
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentFeedbackBinding
import de.telekom.syncplus.util.viewbinding.viewBinding

class FeedbackFragment : BaseFragment(R.layout.fragment_feedback) {
    override val TAG: String
        get() = "FEEDBACK_FRAGMENT"

    companion object {
        fun newInstance() = FeedbackFragment()
    }

    private val binding by viewBinding(FragmentFeedbackBinding::bind)
    private var mFormClient: FormClient? = null

    private val filter: IntentFilter =
        IntentFilter().also {
            it.addAction(INTENT_CLOSE_FORM)
            it.addAction(INTENT_CLOSE_CAMPAIGN)
            it.addAction(INTENT_ENTRIES)
        }

    private val usabillaReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            @SuppressLint("CommitTransaction")
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    INTENT_CLOSE_FORM -> {
                        // Remove fragment from the screen
                        mFormClient?.let {
                            supportFragmentManager?.beginTransaction()?.remove(it.fragment)?.commit()
                        }
                    }
                }
            }
        }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(usabillaReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(usabillaReceiver)
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

        with(binding) {
            toPlayStoreButton.setOnClickListener {
                openPlayStore(this@FeedbackFragment, requireContext().packageName)
            }

            val fragmentManager = requireActivity().supportFragmentManager
            writeMessageButton.setOnClickListener {
                Logger.log.info("Usabilla | Write Message")
                val app = requireActivity().application as? App
                Logger.log.info("Usabilla | Initialized? ${app?.usabillaInitialized}")
                if (app?.usabillaInitialized == true) {
                    Usabilla.updateFragmentManager(fragmentManager)
                    Usabilla.loadFeedbackForm(
                        "61f00d93a4af1614f06adc25",
                        null,
                        null,
                        object :
                            UsabillaFormCallback {
                            override fun formLoadFail() {
                                Logger.log.severe("Usabilla | formLoadFail")
                            }

                            override fun formLoadSuccess(form: FormClient) {
                                Logger.log.info("Usabilla | formLoadSuccess | $form")
                                mFormClient = form
                                mFormClient?.fragment?.show(fragmentManager, "USABILLA")
                            }

                            override fun mainButtonTextUpdated(text: String) {
                                Logger.log.info("Usabilla | mainButtonTextUpdated($text)")
                            }
                        },
                    )
                }
            }
        }
    }
}
