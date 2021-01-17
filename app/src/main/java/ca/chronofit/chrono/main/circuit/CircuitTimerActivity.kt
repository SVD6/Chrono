package ca.chronofit.chrono.main.circuit

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import ca.chronofit.chrono.R
import ca.chronofit.chrono.databinding.ActivityCircuitTimerBinding
import ca.chronofit.chrono.util.objects.CircuitObject
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.ads.MobileAds
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.GsonBuilder
import kotlinx.android.synthetic.main.dialog_alert.view.*
import kotlin.math.roundToInt

class CircuitTimerActivity : AppCompatActivity() {
    private var bind: ActivityCircuitTimerBinding? = null

    enum class TimerState { INIT, RUNNING, PAUSED }
    enum class RunningState { READY, INIT, WORK, REST }

    private lateinit var mInterstitialAd: InterstitialAd

    private val mInterstitialAdUnitId: String by lazy {
//        "ca-app-pub-5592526048202421/8639444717" // ACTUAL
        "ca-app-pub-3940256099942544/1033173712" // TEST
    }

    private val celebrateTimeout = 2500L // Timeout delay

    private lateinit var countdown: CountDownTimer
    private var secondsLeft: Float = 0.0f

    private var timerState: TimerState = TimerState.INIT
    private var runningState: RunningState = RunningState.INIT

    private lateinit var circuit: CircuitObject
    private var currentSet: Int = 0
    private var sets: Int = 0
    private var timeRest: Int = 0
    private var timeWork: Int = 0
    private var criticalSeconds: Int = 0

    private var tone: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super<AppCompatActivity>.onCreate(savedInstanceState)
        setContentView(R.layout.activity_circuit_timer)
        bind = DataBindingUtil.setContentView(this, R.layout.activity_circuit_timer)

        createNotificationChannel()

        circuit = GsonBuilder().create()
            .fromJson(intent.getStringExtra("circuitObject"), CircuitObject::class.java)

        // Initialize stuff
        updateButtonUI()
        updateRestUI()
        loadAds()

        bind!!.startButton.setOnClickListener {
            loadTimer(circuit)
            timerState = TimerState.RUNNING
            getReady()
        }

        bind!!.pauseButton.setOnClickListener {
            countdown.cancel()
            timerState = TimerState.PAUSED
            updateButtonUI()
        }

        bind!!.resumeButton.setOnClickListener {
            startTimer(secondsLeft.toInt(), true)
            timerState = TimerState.RUNNING
            updateRestUI()
            updateButtonUI()
        }

        bind!!.stopButton.setOnClickListener {
            countdown.cancel()
            timerState = TimerState.INIT
            runningState = RunningState.INIT
            updateButtonUI()
            updateRestUI()
        }

        bind!!.closeButton.setOnClickListener {
            // Make sure that the timer is shut down
            if (timerState != TimerState.INIT) {
                countdown.cancel()
            }
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun startTimer(seconds: Int, wasPaused: Boolean) {
        var time = seconds.toFloat() * 1000 + 250
        if (wasPaused) {
            time = secondsLeft * 1000 + 250
        }
        countdown = object : CountDownTimer(time.toLong(), 250) {
            override fun onTick(p0: Long) {
                if ((p0.toFloat().roundToInt() / 1000.0f) != secondsLeft) {
                    secondsLeft = (p0.toFloat() / 1000.0f).roundToInt().toFloat()
                    updateTimerUI()
                    createNotification(secondsLeft)
                }
            }

            override fun onFinish() {
                if (currentSet != 0) {
                    when (runningState) {
                        RunningState.READY -> {
                            workout()
                        }
                        RunningState.WORK -> {
                            rest()
                        }
                        RunningState.REST -> {
                            workout()
                        }
                        else -> celebrate()
                    }
                } else {
                    celebrate()
                }
            }
        }.start()
    }

    private fun loadAds() {
        // Initialize and load up the ad for later
        MobileAds.initialize(this)

        mInterstitialAd = InterstitialAd(this)
        mInterstitialAd.adUnitId = mInterstitialAdUnitId
        mInterstitialAd.loadAd(AdRequest.Builder().build())
    }

    private fun celebrate() {
        // Load celebrate layout
        bind!!.mainLayout.visibility = View.GONE
        bind!!.celebrateLayout.visibility = View.VISIBLE

        // Wait 2.5 seconds before showing the finish prompt
        Handler(
            Looper.getMainLooper()
        ).postDelayed(
            {
                isDone()
            }, celebrateTimeout
        )
    }

    private fun isDone() {
        val builder = MaterialAlertDialogBuilder(this).create()
        val dialogView = layoutInflater.inflate(R.layout.dialog_alert, null)

        // Set Dialog Views
        dialogView.dialog_title.text = getString(R.string.circuit_complete)
        dialogView.subtitle.text = getString(R.string.circuit_complete_subtitle)
        dialogView.confirm.text = getString(R.string.circuit_complete_confirm)
        dialogView.cancel.text = getString(R.string.circuit_complete_cancel)

        dialogView.confirm.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))

        // User wants to return to dashboard
        dialogView.confirm.setOnClickListener {
            builder.dismiss()
            setResult(Activity.RESULT_OK)
            finish()

            // Show the ad
            if (mInterstitialAd.isLoaded) {
                mInterstitialAd.show()
            } else {
                Log.d("AD", "The interstitial wasn't loaded yet.")
            }
        }

        // If the user wants to run the circuit again
        dialogView.cancel.setOnClickListener {
            // Show the ad if it loaded
            if (mInterstitialAd.isLoaded) {
                mInterstitialAd.adListener = object : AdListener() {
                    override fun onAdClosed() {
                        // Reload the circuit
                        super.onAdClosed()
                        builder.dismiss()

                        bind!!.celebrateLayout.visibility = View.GONE
                        bind!!.mainLayout.visibility = View.VISIBLE

                        timerState = TimerState.INIT
                        runningState = RunningState.INIT
                        updateButtonUI()
                        updateRestUI()
                    }
                }
                mInterstitialAd.show()
            } else {
                // Ad didn't load but restart the circuit
                Log.d("AD", "The interstitial wasn't loaded yet.")
                // Reload the circuit
                builder.dismiss()

                bind!!.celebrateLayout.visibility = View.GONE
                bind!!.mainLayout.visibility = View.VISIBLE

                timerState = TimerState.INIT
                runningState = RunningState.INIT
                updateButtonUI()
                updateRestUI()
            }
        }

        // Display the Dialog
        builder.setView(dialogView)
        builder.show()
    }

    private fun getReady() {
        runningState = RunningState.READY
        bind!!.initButtonLayout.visibility = View.GONE
        updateRestUI()
        startTimer(5, false)
    }

    private fun workout() {
        tone.startTone(ToneGenerator.TONE_DTMF_D, 750)
        runningState = RunningState.WORK
        updateButtonUI()
        updateRestUI()
        startTimer(timeWork, false)
    }

    private fun rest() {
        tone.startTone(ToneGenerator.TONE_DTMF_2, 500)
        runningState = RunningState.REST
        updateRestUI()
        startTimer(timeRest, false)
        currentSet -= 1
    }

    // Update UI for every tick, possibly need to do more in the future
    fun updateTimerUI() {
        if (criticalSeconds != 0 && secondsLeft <= criticalSeconds && runningState == RunningState.WORK) {
            bind!!.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.stop_red))
            bind!!.countdown.setTextColor(ContextCompat.getColor(this, R.color.white))
            bind!!.currentSet.setTextColor(ContextCompat.getColor(this, R.color.white))
            bind!!.currentState.setTextColor(ContextCompat.getColor(this, R.color.white))
            bind!!.closeButton.setImageResource(R.drawable.ic_close_white)
        }
        if (timeRest > 5 && runningState == RunningState.REST && secondsLeft <= 5) {
            bind!!.currentState.text = getString(R.string.get_ready)
        }
        bind!!.countdown.text = (secondsLeft).toInt().toString()
    }

    private fun updateButtonUI() {
        when (timerState) {
            TimerState.INIT -> {
                bind!!.initButtonLayout.visibility = View.VISIBLE
                bind!!.runButtonLayout.visibility = View.GONE
                bind!!.pauseButtonLayout.visibility = View.GONE
                bind!!.countdown.text = "0"
            }
            TimerState.RUNNING -> {
                bind!!.initButtonLayout.visibility = View.GONE
                bind!!.runButtonLayout.visibility = View.VISIBLE
                bind!!.pauseButtonLayout.visibility = View.GONE
            }
            TimerState.PAUSED -> {
                bind!!.initButtonLayout.visibility = View.GONE
                bind!!.runButtonLayout.visibility = View.GONE
                bind!!.pauseButtonLayout.visibility = View.VISIBLE

                // Just for paused we put non-button UI stuff
                bind!!.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
                bind!!.countdown.setTextColor(ContextCompat.getColor(this, R.color.black))
                bind!!.currentSet.setTextColor(ContextCompat.getColor(this, R.color.dark_grey))
                bind!!.currentState.setTextColor(ContextCompat.getColor(this, R.color.dark_grey))
                bind!!.closeButton.setImageResource(R.drawable.ic_close_grey)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateRestUI() {
        when (runningState) {
            RunningState.READY -> {
                bind!!.currentState.text = getString(R.string.get_ready)
            }
            RunningState.INIT -> {
                bind!!.currentSet.text = getString(R.string.empty)
                bind!!.currentState.text = getString(R.string.lets_go)

                bind!!.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
                bind!!.countdown.setTextColor(ContextCompat.getColor(this, R.color.black))
                bind!!.currentSet.setTextColor(ContextCompat.getColor(this, R.color.dark_grey))
                bind!!.currentState.setTextColor(ContextCompat.getColor(this, R.color.dark_grey))
                bind!!.closeButton.setImageResource(R.drawable.ic_close_grey)
            }
            RunningState.WORK -> {
                bind!!.currentState.text = getString(R.string.workout)
                bind!!.currentSet.text = "Set " + (sets - currentSet.toString().toInt() + 1)

                bind!!.mainLayout.setBackgroundColor(
                    ContextCompat.getColor(
                        this,
                        R.color.beautiful_blue
                    )
                )
                bind!!.countdown.setTextColor(ContextCompat.getColor(this, R.color.white))
                bind!!.currentSet.setTextColor(ContextCompat.getColor(this, R.color.white))
                bind!!.currentState.setTextColor(ContextCompat.getColor(this, R.color.white))
                bind!!.closeButton.setImageResource(R.drawable.ic_close_white)
            }
            RunningState.REST -> {
                bind!!.currentState.text = getString(R.string.rest)

                bind!!.mainLayout.setBackgroundColor(
                    ContextCompat.getColor(
                        this,
                        R.color.rest_yellow
                    )
                )
                bind!!.countdown.setTextColor(ContextCompat.getColor(this, R.color.white))
                bind!!.currentSet.setTextColor(ContextCompat.getColor(this, R.color.white))
                bind!!.currentState.setTextColor(ContextCompat.getColor(this, R.color.white))
                bind!!.closeButton.setImageResource(R.drawable.ic_close_white)
            }
        }
    }

    private fun loadTimer(circuit: CircuitObject) {
        sets = circuit.sets!!
        currentSet = sets
        timeRest = circuit.rest!!
        timeWork = circuit.work!!

        criticalSeconds = if (timeWork > 5) {
            5
        } else {
            0
        }
    }

    private fun createNotification(time: Float) {
        val builder =
            NotificationCompat.Builder(this, getString(R.string.timer_notification_channel_id))
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle("Notification test")
                .setContentText(time.toString())
                .setPriority(NotificationCompat.PRIORITY_LOW)

        with(NotificationManagerCompat.from(this)) {
            //notificationId is a unique int for each notification that you must define
            notify(2, builder.build())
        }
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.timer_notification_channel_id)
            val descriptionText = "Timer notification"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(name, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}