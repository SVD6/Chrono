package com.example.chrono.main.stopwatch

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.example.chrono.R
import com.example.chrono.databinding.FragmentStopwatchBinding
import com.example.chrono.util.components.MyChronometer
import com.google.android.material.button.MaterialButton
import kotlinx.android.synthetic.main.fragment_stopwatch.*
import kotlinx.android.synthetic.main.reset_lap_button.*


class StopwatchFrag : Fragment() {
    var bind: FragmentStopwatchBinding? = null
    var isPlaying: Boolean = false
    var init_stopwatch = 0 // Clock has not been initialized

    var chronometer: MyChronometer? = null
    var startstopbutton: MaterialButton? = null
    var resetlapbutton: MaterialButton? = null
    var offset: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        bind = DataBindingUtil.inflate(inflater, R.layout.fragment_stopwatch, container, false)

        chronometer = bind!!.chronometer
        isPlaying = false

        startstopbutton = bind!!.startstopbutton

        startstopbutton?.setOnClickListener {
            if (!isPlaying) {
                playingUI()
            } else {
                pausedUI()
            }
        }

//        bind!!.resetbutton.setOnClickListener { reset() }

        chronometer!!.setOnClickListener {
            if (!isPlaying) {
                playingUI()
            } else {
                pausedUI()
                isPlaying = false
            }
        }

        chronometer!!.setOnLongClickListener {
            reset()
            return@setOnLongClickListener true
        }

        return bind!!.root
    }

    private fun playingUI() {
        if (init_stopwatch == 0) { //The stopwatch has not been initialized and this is the first instance of it starting.
            // Then we add a reset/lap button
            val resetLapBtn = resetlapbutton
            buttons.addView(resetLapBtn)
            init_stopwatch = 1 //Init is done
        }

        chronometer!!.base = SystemClock.elapsedRealtime() - offset
        chronometer!!.start()
        isPlaying = true

        startstopbutton?.setText(R.string.stop)
        startstopbutton?.setBackgroundColor(
            ContextCompat.getColor(
                requireContext(), R.color.stop_red
            )
        )
    }

    private fun pausedUI() {
        chronometer!!.stop()
        offset = (SystemClock.elapsedRealtime() - chronometer!!.base).toInt()
        isPlaying = false

        startstopbutton?.setText(R.string.resume)
        startstopbutton?.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.resume_green)
        )
    }

    private fun reset() {
        chronometer!!.stop()
        chronometer!!.base = SystemClock.elapsedRealtime()
        offset = 0
        isPlaying = false

        startstopbutton?.setText(R.string.start)
        startstopbutton?.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.resume_green)
        )
    }
}