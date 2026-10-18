package com.katiearose.sobriety.activities

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.katiearose.sobriety.AddictionCardAdapter
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.ActivityMainBinding
import com.katiearose.sobriety.databinding.DialogAddNoteAfterRelapseBinding
import com.katiearose.sobriety.databinding.DialogMiscBinding
import com.katiearose.sobriety.databinding.DialogRelapseTimeBinding
import com.katiearose.sobriety.databinding.DialogRenameAddictionBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.getAddNoteAfterRelapsePref
import com.katiearose.sobriety.utils.getDateFormatPattern
import com.katiearose.sobriety.utils.getSharedPref
import com.katiearose.sobriety.utils.isInputEmpty
import com.katiearose.sobriety.utils.showConfirmDialog
import com.katiearose.sobriety.utils.toast
import com.katiearose.sobriety.utils.write
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinLocalDate
import java.io.FileNotFoundException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class Main : AppCompatActivity() {

    companion object {
        const val EXTRA_NAMES = "com.katiearose.sobriety.EXTRA_NAMES"
        // Pass the position of the addiction so that it can be modified
        const val EXTRA_ADDICTION_POSITION = "com.katiearose.sobriety.EXTRA_ADDICTION_POSITION"
        val addictions = ArrayList<Addiction>()
    }

    private lateinit var adapterAddictions: AddictionCardAdapter
    private lateinit var cacheHandler: CacheHandler
    private lateinit var binding: ActivityMainBinding

    // One ticker for the whole list instead of one coroutine per card. Paused in onPause()
    // so no wakeups happen while the app is in the background.
    private val tickerHandler = Handler(Looper.getMainLooper())
    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (::adapterAddictions.isInitialized) {
                adapterAddictions.notifyItemRangeChanged(
                    0,
                    addictions.size,
                    AddictionCardAdapter.TICK
                )
            }
            tickerHandler.postDelayed(this, 1000)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private val addNewAddiction =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                val data = requireNotNull(requireNotNull(it.data) { "Something is wrong in the Create activity, go check the create() function" }.extras) { "Something is wrong in the Create activity, go check the create() function" }
                val name = data.getString("name")!!
                if (addictions.any { it.name.equals(name, ignoreCase = true) }) {
                    Snackbar.make(binding.root, R.string.error_duplicate_entry, BaseTransientBottomBar.LENGTH_SHORT).show()
                } else {
                    val instant = data.getLong("instant")
                    val priority =
                        Addiction.Priority.values()[data.getInt("priority")]
                    val addiction = Addiction.newInstance(name, instant, priority)
                    if (addiction.status == Addiction.Status.Future) {
                        // Update status at that point in time
                        addTimerForFutureAddiction(addiction)
                    }
                    addictions.add(addiction)
                    addictions.sortWith { a1, a2 -> a1.priority.compareTo(a2.priority) }
                    cacheHandler.write()
                    adapterAddictions.notifyDataSetChanged()
                }
            }
        }

    private val goToSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            if (it.data!!.extras!!.getBoolean("import")) {
                addictions.sortWith { a1, a2 -> a1.priority.compareTo(a2.priority) }
                cacheHandler.write()
            }
        }
        adapterAddictions.notifyDataSetChanged()
    }

    //needed for this activity to apply md3 theme after user backs away from settings
    private val materialYouSettingListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "material_you") recreate()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false)
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (preferences.getString("theme", "system") == "system" &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
        ) {
            preferences.edit { putString("theme", "light") }
        }
        preferences.registerOnSharedPreferenceChangeListener(materialYouSettingListener)
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addCardButton.setOnClickListener { newCardDialog() }
        cacheHandler = CacheHandler(this)
        cacheHandler.backupCacheIfMissing()
        if (addictions.isEmpty()) {
            try {
                this.openFileInput("Sobriety.cache").use {
                    val loaded = cacheHandler.readCache(it)
                    if (loaded.isEmpty()) {
                        // Cache may be corrupt/empty. Try the upgrade backup before giving up.
                        val restored = cacheHandler.readBackup()
                        if (restored != null) {
                            addictions.addAll(restored)
                        } else {
                        }
                    } else {
                        addictions.addAll(loaded)
                    }
                }
            } catch (e: FileNotFoundException) {
                // No cache yet; fall back to the backup if one exists (e.g. after a
                // partial migration or a user-cleared file).
                val restored = cacheHandler.readBackup()
                if (restored != null) addictions.addAll(restored)
            } catch (e: Exception) {
                // Any other failure: try the backup, never crash the launch.
                val restored = cacheHandler.readBackup()
                if (restored != null) addictions.addAll(restored)
            }
        }

        updatePromptVisibility()

        //Create adapter, and layout manager for recyclerview and attach them
        adapterAddictions = AddictionCardAdapter(this, deleteButtonAction =  {
            val action: () -> Unit = {
                adapterAddictions.notifyItemRemoved(addictions.indexOf(it))
                addictions.remove(it)
                updatePromptVisibility()
                cacheHandler.write()
            }
            showConfirmDialog(
                getString(R.string.delete),
                getString(R.string.delete_confirm, it.name),
                action
            )
        }, relapseButtonAction = {
            when (it.status) {
                Addiction.Status.Ongoing ->
                    showConfirmDialog(
                        getString(R.string.relapse),
                        getString(R.string.relapse_confirm, it.name)
                    ) { showRelapseTimeDialog(it, R.string.relapse_when, offerNote = true) }

                Addiction.Status.Stopped, Addiction.Status.Future ->
                    showConfirmDialog(
                        getString(R.string.track_now),
                        getString(R.string.start_tracking_now, it.name)
                    ) { showRelapseTimeDialog(it, R.string.track_when, offerNote = false) }
            }
        }, stopButtonAction = {
            when (it.status) {
                Addiction.Status.Ongoing ->
                    showConfirmDialog(
                        getString(R.string.stop),
                        getString(R.string.stop_confirm, it.name)
                    ) {
                        it.stopAbstaining()
                        adapterAddictions.notifyItemChanged(addictions.indexOf(it))
                        cacheHandler.write()
                        showAddNoteAfterRelapseDialogIfEnabled(it, it.history.values.last())
                    }

                Addiction.Status.Stopped ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.already_stopped, it.name),
                        BaseTransientBottomBar.LENGTH_SHORT
                    ).show()

                Addiction.Status.Future ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.not_tracked_yet, it.name),
                        BaseTransientBottomBar.LENGTH_SHORT
                    ).show()
            }
        }, timelineButtonAction = {
            when (it.status) {
                Addiction.Status.Ongoing, Addiction.Status.Stopped ->
                    startActivity(
                        Intent(this@Main, Timeline::class.java)
                            .putExtra(EXTRA_ADDICTION_POSITION, addictions.indexOf(it))
                    )

                Addiction.Status.Future ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.not_tracked_yet, it.name),
                        BaseTransientBottomBar.LENGTH_SHORT
                    ).show()
            }
        }, priorityTextViewAction = {
            var choice = it.priority.ordinal
            MaterialAlertDialogBuilder(this@Main)
                .setTitle(R.string.edit_priority)
                .setSingleChoiceItems(
                    R.array.priorities,
                    it.priority.ordinal
                ) { _, which -> choice = which }
                .setPositiveButton(R.string.edit) { _, _ ->
                    it.priority = Addiction.Priority.values()[choice]
                    addictions.sortWith { a1, a2 -> a1.priority.compareTo(a2.priority) }
                    cacheHandler.write()
                    adapterAddictions.notifyDataSetChanged()
                    Snackbar.make(
                        binding.root,
                        R.string.edit_priority_success,
                        BaseTransientBottomBar.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }, miscButtonAction = { a ->
            val dialogViewBinding = DialogMiscBinding.inflate(layoutInflater)
            val dialog = BottomSheetDialog(this@Main)
            dialogViewBinding.dailyNotes.setOnClickListener {
                startActivity(
                    Intent(this@Main, DailyNotes::class.java)
                        .putExtra(EXTRA_ADDICTION_POSITION, addictions.indexOf(a))
                )
                dialog.dismiss()
            }
            dialogViewBinding.savings.setOnClickListener {
                startActivity(
                    Intent(this@Main, Savings::class.java)
                        .putExtra(EXTRA_ADDICTION_POSITION, addictions.indexOf(a))
                )
                dialog.dismiss()
            }
            dialogViewBinding.milestones.setOnClickListener {
                startActivity(
                    Intent(this@Main, Milestones::class.java)
                        .putExtra(EXTRA_ADDICTION_POSITION, addictions.indexOf(a))
                )
                dialog.dismiss()
            }
            dialogViewBinding.renameAddiction.setOnClickListener {
                dialog.dismiss()
                showRenameDialog(a)
            }
            dialog.setContentView(dialogViewBinding.root)
            dialog.show()
        }, cardButtonAction = {
            when(it.status) {
                Addiction.Status.Ongoing, Addiction.Status.Stopped ->
                    startActivity(
                        Intent(this@Main, Summary::class.java)
                            .putExtra(EXTRA_ADDICTION_POSITION, addictions.indexOf(it))
                    )

                Addiction.Status.Future ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.not_tracked_yet, it.name),
                        BaseTransientBottomBar.LENGTH_SHORT
                    ).show()
            }

        })
        binding.recyclerAddictions.layoutManager = LinearLayoutManager(this)
        binding.recyclerAddictions.adapter = adapterAddictions

        // Add a timer to start an attempt on future addictions
        for (addiction in addictions) {
            if (addiction.status == Addiction.Status.Future)
                addTimerForFutureAddiction(addiction)
        }
    }

    private fun addTimerForFutureAddiction(addiction: Addiction) {
        // Also works for negative delays
        // See https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/os/Handler.java#693
        val delay = addiction.history.keys.last() - Instant.now().toEpochMilli()
        Handler(Looper.getMainLooper()).postDelayed({
            addiction.status = Addiction.Status.Ongoing
        }, delay)
    }

    private fun logRelapse(addiction: Addiction, timeMillis: Long, offerNote: Boolean = true) {
        addiction.relapse(timeMillis)
        adapterAddictions.notifyItemChanged(addictions.indexOf(addiction))
        cacheHandler.write()
        if (offerNote)
            showAddNoteAfterRelapseDialogIfEnabled(addiction, timeMillis)
    }

    private fun showRelapseTimeDialog(
        addiction: Addiction,
        @StringRes titleRes: Int,
        offerNote: Boolean
    ) {
        val dialogViewBinding = DialogRelapseTimeBinding.inflate(layoutInflater)
        dialogViewBinding.relapseWhenTitle.setText(titleRes)
        val dialog = BottomSheetDialog(this@Main)
        dialogViewBinding.relapseNow.setOnClickListener {
            dialog.dismiss()
            logRelapse(addiction, Clock.System.now().toEpochMilliseconds(), offerNote = offerNote)
        }
        dialogViewBinding.relapsePrevious.setOnClickListener {
            dialog.dismiss()
            pickRelapseDate(addiction, offerNote)
        }
        dialog.setContentView(dialogViewBinding.root)
        dialog.show()
    }

    private fun pickRelapseDate(addiction: Addiction, offerNote: Boolean) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pick_relapse_date)
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointBackward.now())
                    .build()
            )
            .build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Instant.ofEpochMilli(selection).atZone(ZoneId.of("UTC")).toLocalDate()
            pickRelapseTime(addiction, date, offerNote)
        }
        datePicker.show(supportFragmentManager, null)
    }

    /**
     * The earliest moment a new attempt may start.
     * Ongoing attempts may not be restarted at or before their own start; Stopped and
     * Future ones must start after the last attempt that actually ended. Null means
     * there is no lower bound.
     */
    private fun minRelapseMillis(addiction: Addiction): Long? = when (addiction.status) {
        Addiction.Status.Ongoing -> addiction.history.keys.last()

        Addiction.Status.Stopped -> addiction.history.values.last()

        Addiction.Status.Future -> {
            // The future attempt itself has no end, so look at the attempt before it
            val entries = addiction.history.entries.toList()
            if (entries.size >= 2) entries[entries.size - 2].value else null
        }
    }

    private fun pickRelapseTime(addiction: Addiction, date: LocalDate, offerNote: Boolean) {
        val now = ZonedDateTime.now()
        val timePicker = MaterialTimePicker.Builder()
            .setTitleText(R.string.pick_relapse_time)
            .setHour(now.hour)
            .setMinute(now.minute)
            .build()
        timePicker.addOnPositiveButtonClickListener {
            val relapseMillis = ZonedDateTime.of(
                date,
                LocalTime.of(timePicker.hour, timePicker.minute),
                ZoneId.systemDefault()
            ).toInstant().toEpochMilli()
            val minimum = minRelapseMillis(addiction)
            val tooEarlyError =
                if (addiction.status == Addiction.Status.Ongoing) R.string.error_relapse_before_start
                else R.string.error_track_before_end
            when {
                relapseMillis > Instant.now().toEpochMilli() ->
                    toast(R.string.error_future_relapse)

                minimum != null && relapseMillis <= minimum ->
                    toast(tooEarlyError)

                else -> logRelapse(addiction, relapseMillis, offerNote = offerNote)
            }
        }
        timePicker.show(supportFragmentManager, null)
    }

    private fun showAddNoteAfterRelapseDialogIfEnabled(addiction: Addiction, relapseMillis: Long) {
        val pref = getSharedPref()
        if (pref.getAddNoteAfterRelapsePref()) {
            var dialog: Dialog? = null
            val dialogViewBinding = DialogAddNoteAfterRelapseBinding.inflate(layoutInflater)
            val date = Instant.ofEpochMilli(relapseMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            val dateFormat = DateTimeFormatter.ofPattern(getSharedPref().getDateFormatPattern())
            with(dialogViewBinding) {
                noteDate.text = dateFormat.format(date)
                btnSave.setOnClickListener { _ ->
                    if (noteInput.isInputEmpty()) {
                        noteInputLayout.error = getString(R.string.error_empty_note)
                    } else {
                        addiction.dailyNotes.getOrPut(date.toKotlinLocalDate()) { mutableListOf() }
                            .add(noteInput.text.toString())
                        cacheHandler.write()
                        requireNotNull(dialog).dismiss()
                    }
                }
            }
            with(BottomSheetDialog(this)) {
                dialog = this
                setContentView(dialogViewBinding.root)
                setOnDismissListener {
                    if (dialogViewBinding.dontShowAgain.isChecked) {
                        pref.edit { putBoolean("add_note_after_relapse", false) }
                    }
                }
                show()
            }
        }
    }

    private fun showRenameDialog(addiction: Addiction) {
        val dialogViewBinding = DialogRenameAddictionBinding.inflate(layoutInflater)
        dialogViewBinding.renameInput.setText(addiction.name)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename)
            .setView(dialogViewBinding.root)
            .setPositiveButton(R.string.edit, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        // Override the positive button so the dialog stays open while the name is invalid
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val newName = dialogViewBinding.renameInput.text.toString().trim()
                val duplicate = addictions.any {
                    it !== addiction && it.name.equals(newName, ignoreCase = true)
                }
                when {
                    newName.isBlank() ->
                        dialogViewBinding.renameInputLayout.error = getString(R.string.error_empty_name)

                    duplicate ->
                        dialogViewBinding.renameInputLayout.error = getString(R.string.error_duplicate_entry)

                    else -> {
                        addiction.name = newName
                        cacheHandler.write()
                        adapterAddictions.notifyItemChanged(addictions.indexOf(addiction))
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun updatePromptVisibility() {
        binding.prompt.visibility = if (addictions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun newCardDialog() {
        //Pass current addiction names to create activity, to prevent creation of elements with identical keys
        val intent = Intent(this, Create::class.java)
            .putStringArrayListExtra(EXTRA_NAMES, addictions.mapTo(arrayListOf()) { it.name })
        addNewAddiction.launch(intent)
    }

    /**
     * This gets called once the Create Activity is closed (Necessary to hide the prompt in case
     * a first addiction was added to the list.
     */
    override fun onResume() {
        updatePromptVisibility()
        tickerHandler.removeCallbacks(tickerRunnable)
        tickerHandler.post(tickerRunnable)
        super.onResume()
    }

    override fun onPause() {
        tickerHandler.removeCallbacks(tickerRunnable)
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.go_to_settings) {
            val intent = Intent(this, Settings::class.java)
            goToSettings.launch(intent)
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
