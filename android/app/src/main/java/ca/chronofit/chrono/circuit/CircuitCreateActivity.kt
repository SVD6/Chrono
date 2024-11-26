package ca.chronofit.chrono.circuit

import `in`.goodiebag.carouselpicker.CarouselPicker
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.viewpager.widget.ViewPager
import ca.chronofit.chrono.MainActivity
import ca.chronofit.chrono.R
import ca.chronofit.chrono.databinding.ActivityCircuitCreateBinding
import ca.chronofit.chrono.databinding.DialogSelectIconBinding
import ca.chronofit.chrono.util.BaseActivity
import ca.chronofit.chrono.util.constants.Constants
import ca.chronofit.chrono.util.constants.Events
import ca.chronofit.chrono.util.listeners.RepeatListener
import ca.chronofit.chrono.util.objects.CircuitObject
import ca.chronofit.chrono.util.objects.CircuitsObject
import ca.chronofit.chrono.util.objects.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.analytics.FirebaseAnalytics

class CircuitCreateActivity : BaseActivity() {
    private lateinit var bind: ActivityCircuitCreateBinding
    private var selectedIcon: Int = 0
    private var isEdit: Boolean = false
    private var editPosition: Int = -1
    private lateinit var iconNames: TypedArray

    private var showMinSetValueMsg = true
    private var showMinWorkValueMsg = true
    private var showMinRestValueMsg = true
    private var showMaxSetValueMsg = true
    private var showMaxWorkValueMsg = true
    private var showMaxRestValueMsg = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceManager.with(this)
        bind = DataBindingUtil.setContentView(this, R.layout.activity_circuit_create)

        iconNames = resources.obtainTypedArray(R.array.icon_files)
        handleIntent(intent)

        bind.circuitName.addTextChangedListener {
            if (bind.circuitName.length() >= MAX_CHARACTERS) {
                bind.circuitNameWarning.visibility = View.VISIBLE
                bind.circuitName.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.stop_red))
            } else {
                bind.circuitNameWarning.visibility = View.GONE
                bind.circuitName.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorAccent))
            }
        }

        // Button Listeners
        bind.discardButton.setOnClickListener {
            navBack(Activity.RESULT_CANCELED)
        }

        bind.saveButton.setOnClickListener {
            if (validateInputs()) {
                FirebaseAnalytics.getInstance(this).logEvent(Events.CREATE_COMPLETE, Bundle())
                saveCircuit()
                navBack(Activity.RESULT_OK)
            }
        }

        bind.iconLayout.setOnClickListener {
            hideKeyboard(currentFocus ?: View(this))
            selectIconDialog()
        }

        bind.circuitIcon.setOnClickListener {
            hideKeyboard(currentFocus ?: View(this))
            selectIconDialog()
        }

        bind.addSet.setOnTouchListener(RepeatListener {
            hideKeyboard(bind.addSet)
            hideCursors()
            addSet()
        })

        bind.minusSet.setOnTouchListener(RepeatListener {
            hideKeyboard(currentFocus ?: View(this))
            hideCursors()
            minusSet()
        })

        bind.addWork.setOnTouchListener(RepeatListener {
            hideKeyboard(currentFocus ?: View(this))
            hideCursors()
            addWork()
        })

        bind.minusWork.setOnTouchListener(RepeatListener {
            hideKeyboard(currentFocus ?: View(this))
            hideCursors()
            minusWork()
        })

        bind.addRest.setOnTouchListener(RepeatListener {
            hideKeyboard(currentFocus ?: View(this))
            hideCursors()
            addRest()
        })

        bind.minusRest.setOnTouchListener(RepeatListener {
            hideKeyboard(currentFocus ?: View(this))
            hideCursors()
            minusRest()
        })

        bind.setNum.setOnClickListener { bind.setNum.isCursorVisible = true }
        bind.setWork.setOnClickListener { bind.setWork.isCursorVisible = true }
        bind.setRest.setOnClickListener { bind.setRest.isCursorVisible = true }

        bind.mainLayout.setOnTouchListener { _, _ ->
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            if (imm.isAcceptingText) imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
            return@setOnTouchListener true
        }
    }

    override fun onBackPressed() {
        if (isTaskRoot) {
            navBack(Activity.RESULT_CANCELED)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun saveCircuit() {
        val circuit = CircuitObject()
        circuit.name = bind.circuitName.text.toString()
        circuit.sets = bind.setNum.text.toString().toInt()
        circuit.work = bind.setWork.text.toString().toInt()
        circuit.rest = bind.setRest.text.toString().toInt()
        circuit.iconId = selectedIcon

        // Save circuit in Shared Preferences
        val circuits: CircuitsObject? = PreferenceManager.get<CircuitsObject>(Constants.CIRCUITS)

        // Check if the user is editing a circuit
        if (isEdit) {
            if (editPosition != -1) {
                circuits!!.circuits!![editPosition] = circuit
            } else {
                setResult(Activity.RESULT_CANCELED)
                Toast.makeText(this, "Error editing and saving circuit.", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            circuits!!.circuits!!.add(circuit)
        }
        PreferenceManager.put(circuits, Constants.CIRCUITS)
    }

    private fun loadCircuitInfo(position: Int) {
        if (position != -1) {
            val circuit =
                (PreferenceManager.get<CircuitsObject>(Constants.CIRCUITS))!!.circuits!![position]
            bind.circuitName.setText(circuit.name)
            bind.setNum.setText(circuit.sets.toString())
            bind.setWork.setText(circuit.work.toString())
            bind.setRest.setText(circuit.rest.toString())

            // Set Circuit Icon
            val icons = resources.obtainTypedArray(R.array.icon_files)
            selectedIcon = circuit.iconId!!
            bind.circuitIcon.setImageResource(
                resources.getIdentifier(
                    icons.getString(selectedIcon),
                    "drawable",
                    packageName
                )
            )
            icons.recycle()
        } else {
            Toast.makeText(
                this,
                "Error loading selected circuit, please try again.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun validateInputs(): Boolean {
        if (bind.circuitName.text.toString() == "") {
            Toast.makeText(this, "Please enter a circuit name", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!(bind.setNum.text.toString().matches(("^[1-9]\\d*\$").toRegex()))) {
            Toast.makeText(this, "Invalid Number of Sets.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!(bind.setWork.text.toString().matches(("^[1-9]\\d*\$").toRegex()))) {
            Toast.makeText(this, "Invalid Work Time.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!(bind.setRest.text.toString().matches(("^[1-9]\\d*\$").toRegex()))) {
            Toast.makeText(this, "Invalid Rest Time", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun selectIconDialog() {
        val builder =
            MaterialAlertDialogBuilder(this, R.style.CustomMaterialDialog).create()
        val dialogBinding = DialogSelectIconBinding.inflate(LayoutInflater.from(this))

        // Setting Carousel Items
        val imageItems: ArrayList<CarouselPicker.PickerItem> = ArrayList()
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_default_icon))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_arm))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_heart))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_star))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_home))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_smartphone))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_smartphone_2))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_smartwatch))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_laptop))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_workout))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_dumbbell))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_sets))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_kettlebell))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_plates))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_abs))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_jump_rope))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_shoes))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_whistle))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_gymnast))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_mat))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_resistance))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_grippers))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_trampoline))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_outdoor))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_gym_bag))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_gym))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_bike_machine))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_bench_press))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_treadmill))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_machine))
        imageItems.add(CarouselPicker.DrawableItem(R.drawable.ic_bottle))

        val imageAdapter: CarouselPicker.CarouselViewAdapter =
            CarouselPicker.CarouselViewAdapter(this, imageItems, 0)
        dialogBinding.carousel.adapter = imageAdapter

        dialogBinding.carousel.currentItem = selectedIcon

        // Carousel Logic
        dialogBinding.carousel.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
            }

            override fun onPageSelected(position: Int) {
                selectedIcon = position
            }

            override fun onPageScrollStateChanged(state: Int) {
            }
        })

        // Button Logic
        dialogBinding.dismiss.setOnClickListener {
            builder.dismiss()
        }

        dialogBinding.save.setOnClickListener {
            bind.circuitIcon.setImageResource(
                resources.getIdentifier(
                    iconNames.getString(selectedIcon),
                    "drawable",
                    packageName
                )
            )
            builder.dismiss()
        }
        // Display the Dialog
        builder.setView(dialogBinding.root)
        builder.show()
    }


    // Floor to a multiple of timeChangeVal
    private fun floorVal(valToFloor: Int): Int {
        return (valToFloor / TIME_CHANGE_VALUE) * (TIME_CHANGE_VALUE)
    }

    //When decrementing set time, subtract to the closest multiple of timeChangeVal
    private fun roundTimeDown(valToRound: Int): Int {
        return if (valToRound % TIME_CHANGE_VALUE == 0) {
            valToRound - TIME_CHANGE_VALUE
        } else {
            floorVal(valToRound)
        }
    }

    private fun hideKeyboard(view: View) {
        val inputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun addSet() {
        val currentText = bind.setNum.text.toString()
        showMinSetValueMsg = true
        if (currentText == "") {
            bind.setNum.setText("1")
        } else {
            if (currentText.toInt() == MAX_SETS) {
                if (showMaxSetValueMsg) {
                    Toast.makeText(
                        this,
                        "Can't have more than 99 sets, take it easy!",
                        Toast.LENGTH_SHORT
                    ).show()
                    showMaxSetValueMsg = false
                }
            } else {
                bind.setNum.setText((currentText.toInt() + 1).toString())
            }
        }
    }

    private fun minusSet() {
        val currentText = bind.setNum.text.toString()
        showMaxSetValueMsg = true
        if (currentText == "") {
            if (showMinSetValueMsg) {
                Toast.makeText(
                    this,
                    "Can't have no sets! " + ("\uD83E\uDD14"),
                    Toast.LENGTH_SHORT
                ).show()
                showMinSetValueMsg = false
            }
        } else if (currentText == "1") {
            bind.setNum.setText("")
        } else {
            bind.setNum.setText(((currentText.toInt() - 1)).toString())
        }
    }

    private fun addWork() {
        val currentText = bind.setWork.text.toString()
        showMinWorkValueMsg = true
        if (currentText == "") {
            bind.setWork.setText(TIME_CHANGE_VALUE.toString())
        } else {
            if (currentText.toInt() >= MAX_WORK) {
                if (showMaxWorkValueMsg) {
                    Toast.makeText(
                        this,
                        "Can't have more than 999 seconds of workout!",
                        Toast.LENGTH_SHORT
                    ).show()
                    showMaxWorkValueMsg = false
                }
            } else {
                bind.setWork.setText(floorVal(currentText.toInt() + TIME_CHANGE_VALUE).toString())
            }
        }
    }

    private fun minusWork() {
        val currentText = bind.setWork.text.toString()
        showMaxWorkValueMsg = true
        if (currentText == "") {
            if (showMinWorkValueMsg) {
                Toast.makeText(
                    this,
                    "Can't have no work time! " + ("\uD83D\uDE2C"),
                    Toast.LENGTH_SHORT
                ).show()
                showMinWorkValueMsg = false
            }
        } else if (currentText.toInt() <= 5) {
            bind.setWork.setText("")
        } else {
            bind.setWork.setText(roundTimeDown(currentText.toInt()).toString())
        }
    }

    private fun addRest() {
        val currentText = bind.setRest.text.toString()
        showMinRestValueMsg = true
        if (currentText == "") {
            bind.setRest.setText(TIME_CHANGE_VALUE.toString())
        } else {
            if (currentText.toInt() >= MAX_REST) {
                if (showMaxRestValueMsg) {
                    Toast.makeText(
                        this,
                        "Can't have more than 999 seconds of rest!",
                        Toast.LENGTH_SHORT
                    ).show()
                    showMaxRestValueMsg = false
                }
            } else {
                bind.setRest.setText(floorVal(currentText.toInt() + TIME_CHANGE_VALUE).toString())
            }
        }
    }

    private fun minusRest() {
        val currentText = bind.setRest.text.toString()
        showMaxRestValueMsg = true
        if (currentText == "") {
            if (showMinRestValueMsg) {
                Toast.makeText(
                    this,
                    "Can't have no rest! " + ("\uD83D\uDE2C"),
                    Toast.LENGTH_SHORT
                ).show()
                showMinRestValueMsg = false
            }
        } else if (currentText.toInt() <= 5) {
            bind.setRest.setText("")
        } else {
            bind.setRest.setText(roundTimeDown(currentText.toInt()).toString())
        }
    }

    private fun navBack(result: Int?) {
        if (!isTaskRoot) {
            setResult(result!!)
            finish()
        } else {
            val navToMainActivity = Intent(this, MainActivity::class.java)
            navToMainActivity.putExtra("deeplinkResult", result)
            startActivity(navToMainActivity)
            finish()
        }
    }

    private fun hideCursors() {
        bind.circuitName.isCursorVisible = false
        bind.setNum.isCursorVisible = false
        bind.setRest.isCursorVisible = false
        bind.setWork.isCursorVisible = false
    }

    private fun handleIntent(intent: Intent) {
        val intentAction = intent.action
        val intentData = intent.data
        if (Intent.ACTION_VIEW == intentAction) {
            val uri = Uri.parse(intentData.toString())
            val name = uri!!.getQueryParameter("name")
            val sets = uri.getQueryParameter("sets")
            val work = uri.getQueryParameter("work")
            val rest = uri.getQueryParameter("rest")

            bind.circuitName.setText(name)
            bind.setNum.setText(sets)
            bind.setWork.setText(work)
            bind.setRest.setText(rest)
        } else if (intent.getBooleanExtra("isEdit", false)) {
            isEdit = true
            editPosition = intent.getIntExtra("circuitPosition", -1)
            loadCircuitInfo(editPosition)
        }
    }

    companion object {
        private const val MAX_SETS: Int = 99
        private const val MAX_WORK: Int = 995 // Actually 999
        private const val MAX_REST: Int = 995 // Actually 999
        private const val TIME_CHANGE_VALUE: Int = 5
        private const val MAX_CHARACTERS: Int = 50
    }
}
