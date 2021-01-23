package ca.chronofit.chrono.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import ca.chronofit.chrono.R
import ca.chronofit.chrono.databinding.FragmentSettingsBinding
import ca.chronofit.chrono.MainActivity
import ca.chronofit.chrono.util.constants.Constants
import ca.chronofit.chrono.util.objects.PreferenceManager
import ca.chronofit.chrono.util.objects.SettingsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.dialog_alert.view.cancel
import kotlinx.android.synthetic.main.dialog_alert.view.confirm
import kotlinx.android.synthetic.main.dialog_ready_time.view.*

class SettingsFrag : Fragment() {
    private lateinit var bind: FragmentSettingsBinding

    private val settingsViewModel: SettingsViewModel by activityViewModels()

    private var getReadyTime: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bind = DataBindingUtil.inflate(
            inflater, R.layout.fragment_settings,
            container, false
        )

        PreferenceManager.with(activity as MainActivity)

        // Load Settings (they're either default or have been messed with a bit)
        loadSettings()
        initMenus()

        return bind.root
    }

    private fun loadSettings() {
        if (PreferenceManager.get<Int>(Constants.READY_TIME) != null) {
            getReadyTime = PreferenceManager.get<Int>(Constants.READY_TIME)!!
            bind.readyTimeDisplay.text = (getReadyTime.toString() + "s")
        }

        if (PreferenceManager.get<Boolean>(Constants.AUDIO_PROMPTS) != null) {
            bind.audioSwitch.isChecked = PreferenceManager.get<Boolean>(Constants.AUDIO_PROMPTS)!!
            if (PreferenceManager.get<Boolean>(Constants.AUDIO_PROMPTS)!!) {
                bind.audioSwitch.text = getString(R.string.on)
            } else {
                bind.audioSwitch.text = getString(R.string.off)
            }
        }

        if (PreferenceManager.get<Boolean>(Constants.LAST_REST) != null) {
            bind.lastRestSwitch.isChecked = PreferenceManager.get<Boolean>(Constants.LAST_REST)!!
        }

        if (PreferenceManager.get<Boolean>(Constants.NOTIFICATIONS) != null) {
            bind.notificationSwitch.isChecked =
                PreferenceManager.get<Boolean>(Constants.NOTIFICATIONS)!!
            if (PreferenceManager.get<Boolean>(Constants.NOTIFICATIONS)!!) {
                bind.notificationSwitch.text = getString(R.string.on)
            } else {
                bind.notificationSwitch.text = getString(R.string.off)
            }
        }
    }

    private fun initMenus() {
        bind.rateApp.setOnClickListener {
            val packageName = requireContext().packageName
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$packageName")
                    )
                )
            } catch (e: ActivityNotFoundException) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    )
                )
            }
        }
        bind.privacyPolicy.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://chronofit.herokuapp.com/privacyPolicy")
            )
            startActivity(intent)
        }

        // Dark Mode Switch DISABLED
        bind.darkModeSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                buttonView.text = getString(R.string.on)
                settingsViewModel.onDarkModeChanged(true)
            } else {
                buttonView.text = getString(R.string.off)
                settingsViewModel.onDarkModeChanged(false)
            }
        }

        // TEMPORARY Dark Mode Not Ready
        bind.darkMode.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Dark Mode under construction \uD83D\uDEA7",
                Toast.LENGTH_SHORT
            ).show()
        }

        bind.darkModeTemp.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Dark Mode under construction \uD83D\uDEA7",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Notification Switch
        bind.notificationSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                buttonView.text = getString(R.string.on)
                settingsViewModel.onNotificationChanged(true)
                PreferenceManager.put(true, Constants.NOTIFICATIONS)
            } else {
                buttonView.text = getString(R.string.off)
                settingsViewModel.onNotificationChanged(false)
                PreferenceManager.put(false, Constants.NOTIFICATIONS)
            }
        }

        // Audio Prompt Switch
        bind.audioSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                buttonView.text = getString(R.string.on)
                settingsViewModel.onAudioPromptChanged(true)
                PreferenceManager.put(true, Constants.AUDIO_PROMPTS)
            } else {
                buttonView.text = getString(R.string.off)
                settingsViewModel.onAudioPromptChanged(false)
                PreferenceManager.put(false, Constants.AUDIO_PROMPTS)
            }
        }

        // Last Rest Switch
        bind.lastRestSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                settingsViewModel.onLastRestChanged(true)
                PreferenceManager.put(true, Constants.LAST_REST)
            } else {
                settingsViewModel.onLastRestChanged(false)
                PreferenceManager.put(false, Constants.LAST_REST)
            }
        }

        // Get Ready Time Selector
        bind.getReadyTime.setOnClickListener {
            val builder =
                MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialDialog).create()
            val dialogView = View.inflate(requireContext(), R.layout.dialog_ready_time, null)

            // Pre select the saved option (if it's 5s then make sure 5s is already checked)
            when (bind.readyTimeDisplay.text) {
                "5s" -> dialogView.ready_time_select.check(R.id.radio_5s)
                "10s" -> dialogView.ready_time_select.check(R.id.radio_10s)
                "15s" -> dialogView.ready_time_select.check(R.id.radio_15s)
            }

            // Button Logic
            dialogView.cancel.setOnClickListener {
                builder.dismiss()
            }

            dialogView.confirm.setOnClickListener {
                builder.dismiss()
                val selectedTime =
                    (dialogView.findViewById(dialogView.ready_time_select.checkedRadioButtonId) as RadioButton).text
                bind.readyTimeDisplay.text = selectedTime
                // Add to Settings that yo new get ready time
                settingsViewModel.onReadyTimeChanged(selectedTime.toString())
                PreferenceManager.put(
                    (selectedTime.toString().substring(0, selectedTime.toString().length - 1))
                        .toInt(), "readyTime"
                )
            }

            builder.setView(dialogView)
            builder.show()
        }
    }
}
