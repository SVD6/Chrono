package ca.chronofit.chrono

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import ca.chronofit.chrono.circuit.CircuitDashboardFrag
import ca.chronofit.chrono.databinding.ActivityMainBinding
import ca.chronofit.chrono.databinding.DialogAlertBinding
import ca.chronofit.chrono.settings.SettingsFrag
import ca.chronofit.chrono.stopwatch.StopwatchFrag
import ca.chronofit.chrono.util.BaseActivity
import ca.chronofit.chrono.util.constants.Constants
import ca.chronofit.chrono.util.objects.PreferenceManager
import ca.chronofit.chrono.util.objects.SettingsViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

class MainActivity : BaseActivity(), BottomNavigationView.OnNavigationItemSelectedListener {
    private lateinit var bind: ActivityMainBinding
    private val settingsViewModel: SettingsViewModel by viewModels()
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var notificationManager: NotificationManager
    private lateinit var frag1: StopwatchFrag
    private lateinit var frag2: CircuitDashboardFrag
    private lateinit var frag3: SettingsFrag

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = DataBindingUtil.setContentView(this, R.layout.activity_main)

        PreferenceManager.with(this)

        initRemoteConfig()

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if(intent.getIntExtra("deeplinkResult", Activity.RESULT_CANCELED) == Activity.RESULT_OK){
            Toast.makeText(applicationContext, "Circuit added and saved!", Toast.LENGTH_SHORT)
                .show()
        }

        if (savedInstanceState == null) {
            val fragTransaction = supportFragmentManager.beginTransaction()

            frag1 = StopwatchFrag()
            frag2 = CircuitDashboardFrag()
            frag3 = SettingsFrag()
            fragTransaction.add(R.id.content, frag1, Constants.STOPWATCH_FRAG)
                .add(R.id.content, frag2, Constants.CIRCUIT_FRAG)
                .add(R.id.content, frag3, Constants.SETTINGS_FRAG)
                .commitAllowingStateLoss()

            bind.navBar.setOnNavigationItemSelectedListener(this)
            bind.navBar.selectedItemId = R.id.nav_circuit

        } else {
            frag2 = supportFragmentManager.getFragment(
                savedInstanceState,
                Constants.CIRCUIT_FRAG
            ) as CircuitDashboardFrag
            frag1 = supportFragmentManager.getFragment(
                savedInstanceState,
                Constants.STOPWATCH_FRAG
            ) as StopwatchFrag
            frag3 = supportFragmentManager.getFragment(
                savedInstanceState,
                Constants.SETTINGS_FRAG
            ) as SettingsFrag
            bind.navBar.setOnNavigationItemSelectedListener(this)
        }

        // Create Notification Channels
        createNotificationChannel(
            getString(R.string.stopwatch_notification_channel_id),
            getString(R.string.stopwatch_notification_channel_name),
            "Notifications from your circuit timers."
        )
        createNotificationChannel(
            getString(R.string.circuit_notification_channel_id),
            getString(R.string.circuit_notification_channel_name),
            "Notifications from your stopwatch."
        )

        // Initialization stuff
        observeSettings()

        // Check for an Update
        checkForUpdate()
    }

    private fun changeDarkMode(mode: String) {
        when (mode) {
            Constants.DARK_MODE -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                delegate.applyDayNight()
            }
            Constants.LIGHT_MODE -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                delegate.applyDayNight()
            }
            Constants.SYSTEM_DEFAULT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                delegate.applyDayNight()
            }
        }
    }

    private fun observeSettings() {
        settingsViewModel.darkMode.observe(this, { darkMode ->
            changeDarkMode(darkMode)
        })
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val fragTransaction = supportFragmentManager.beginTransaction()

        when (item.itemId) {
            R.id.nav_stopwatch -> {
                fragTransaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_right)
                fragTransaction.hide(frag2).hide(frag3).show(frag1).commitNow()
                return true
            }
            R.id.nav_circuit -> {
                if (frag3.isVisible) {
                    fragTransaction.setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_right
                    )
                } else {
                    fragTransaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_left)
                }
                fragTransaction.hide(frag1).hide(frag3).show(frag2).commitNow()
                return true
            }
            else -> {
                fragTransaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_left)
                fragTransaction.hide(frag1).hide(frag2).show(frag3).commitNow()
                return true
            }
        }
    }

    private fun createNotificationChannel(id: String, name: String, descriptionText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(id, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initRemoteConfig() {
        // Configure Remote Config
        remoteConfig = Firebase.remoteConfig

        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        // Activate Remote Config
        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val updated = task.result
                Log.d("remote_config", "Config params updated: $updated")
            } else {
                Log.d("remote_config", "Fetch and activate failed.")
            }
        }
    }

    private fun checkForUpdate() {
        val appVersion = getAppVersion(this)
        val currentVersion = remoteConfig.getString(Constants.CONFIG_LATEST_VERSION)

        if (currentVersion.isNotEmpty() && appVersion.isNotEmpty() && checkVersionUpdateAvailable(
                getAppVersionNumOnly(currentVersion),
                getAppVersionNumOnly(appVersion)
            )
        ) {
            showUpdateDialog()
        } else {
            Log.d("MainActivity", "App up to date.")
        }
    }

    private fun getAppVersion(context: Context): String {
        var appVersion: String? = ""
        try {
            appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("MainActivity", e.message!!)
        }
        return appVersion ?: ""
    }

    private fun getAppVersionNumOnly(result: String): String {
        return result.replace(".", "")
    }

    private fun checkVersionUpdateAvailable(currVersion: String, appVersion: String): Boolean {
        // Might have to add more to this later if minVersion is actually needed
        return (currVersion.toInt() > appVersion.toInt())
    }

    private fun showUpdateDialog() {
        val builder =
            MaterialAlertDialogBuilder(this, R.style.CustomMaterialDialog).create()
        val dialogBinding = DialogAlertBinding.inflate(LayoutInflater.from(this))

        // Set the Views
        dialogBinding.dialogTitle.text = getString(R.string.update_available_title)
        dialogBinding.dialogSubtitle.text = getString(R.string.update_available_subtitle)
        dialogBinding.confirm.text = getString(R.string.update)
        dialogBinding.cancel.visibility = View.GONE
        dialogBinding.confirm.setTextColor(ContextCompat.getColor(this, R.color.white))
        dialogBinding.confirm.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))

        // Button Logic
        dialogBinding.confirm.setOnClickListener {
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
        // For now the dialog is dismissible but before launch we should have it fixed.

        // Display the Dialog
        builder.setView(dialogBinding.root)
        builder.show()
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        supportFragmentManager.putFragment(state, Constants.STOPWATCH_FRAG, frag1)
        supportFragmentManager.putFragment(state, Constants.CIRCUIT_FRAG, frag2)
        supportFragmentManager.putFragment(state, Constants.SETTINGS_FRAG, frag3)
    }

    override fun onBackPressed() {
        if (frag2.isHidden) {
            bind.navBar.selectedItemId = R.id.nav_circuit
        } else {
            super.onBackPressed()
        }
    }
}