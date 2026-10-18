package com.katiearose.sobriety.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.ActivityCreateBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.utils.applyThemes
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class Create : AppCompatActivity() {
    private var startDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    private var priority = Addiction.Priority.MEDIUM
    // Whether the user explicitly chose a date/time; if not, "now" is used on create
    private var timeChosen = false

    private lateinit var names: ArrayList<String>
    private lateinit var binding: ActivityCreateBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivityCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.clPickDate.setOnClickListener { pickDate() }
        binding.clPickTime.setOnClickListener { pickTime() }
        binding.clPickPriority.setOnClickListener { pickPriority() }
        binding.btnCreate.setOnClickListener { create() }

        @Suppress("DEPRECATION")
        savedInstanceState?.run {
            startDateTime = getSerializable("current_date_time") as ZonedDateTime
            priority = Addiction.Priority.values()[getInt("current_priority")]
            timeChosen = getBoolean("time_chosen")
        }
        checkFutureDateTime()

        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        binding.tvDate.text = startDateTime.toLocalDate().toString()
        binding.tvTime.text = startDateTime.toLocalTime().format(formatter).toString()
        binding.tvPriority.text = when (priority) {
            Addiction.Priority.HIGH -> getString(R.string.high)
            Addiction.Priority.MEDIUM -> getString(R.string.medium)
            Addiction.Priority.LOW -> getString(R.string.low)
        }

        names = intent.getStringArrayListExtra(Main.EXTRA_NAMES) as ArrayList<String>
    }

    private fun checkFutureDateTime() {
        binding.futureTimeNotice.visibility = if (startDateTime > ZonedDateTime.now()) View.VISIBLE else View.GONE
    }

    private fun pickPriority() {
        var choice = priority.ordinal
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.priority)
            .setSingleChoiceItems(R.array.priorities, priority.ordinal) { _, which -> choice = which }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                priority = Addiction.Priority.values()[choice]
                binding.tvPriority.text = when (priority) {
                    Addiction.Priority.HIGH -> getString(R.string.high)
                    Addiction.Priority.MEDIUM -> getString(R.string.medium)
                    Addiction.Priority.LOW -> getString(R.string.low)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED, Intent())
        finish()
    }

    private fun pickDate() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pick_starting_date)
            .build()
        datePicker.addOnPositiveButtonClickListener {
            startDateTime = ZonedDateTime.of(
                Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate(),
                startDateTime.toLocalTime(),
                ZoneId.systemDefault()
            )
            timeChosen = true
            binding.tvDate.text = startDateTime.toLocalDate().toString()
            checkFutureDateTime()
        }
        datePicker.show(supportFragmentManager, null)
    }

    private fun pickTime() {
        val timePicker = MaterialTimePicker.Builder()
            .setTitleText(R.string.pick_starting_time)
            .setHour(ZonedDateTime.now().hour)
            .setMinute(ZonedDateTime.now().minute)
            .build()
        timePicker.addOnPositiveButtonClickListener {
            startDateTime = ZonedDateTime.of(
                startDateTime.toLocalDate(),
                LocalTime.of(timePicker.hour, timePicker.minute),
                ZoneId.systemDefault()
            )
            timeChosen = true
            binding.tvTime.text = startDateTime.toLocalTime().toString()
            checkFutureDateTime()
        }
        timePicker.show(supportFragmentManager, null)
    }

    private fun create() {
        val name = binding.etTitle.text.toString().trim()
        val nameExists = names.any { it.equals(name, ignoreCase = true) }

        //Don't allow creating without a name, or with a duplicate name
        if (name.isBlank() || nameExists) {
            binding.til.error = if (name.isBlank()) getString(R.string.error_empty_name) else getString(
                R.string.error_duplicate_entry
            )
            return
        }

        val instant = if (timeChosen) startDateTime.toInstant().toEpochMilli() else Instant.now().toEpochMilli()
        val intent = Intent()
            .putExtra("instant", instant)
            .putExtra("name", name)
            .putExtra("priority", priority.ordinal)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.run {
            putSerializable("current_date_time", startDateTime)
            putInt("current_priority", priority.ordinal)
            putBoolean("time_chosen", timeChosen)
        }
    }
}