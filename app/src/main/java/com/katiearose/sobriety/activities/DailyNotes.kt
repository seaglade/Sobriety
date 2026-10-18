package com.katiearose.sobriety.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.katiearose.sobriety.NoteAdapter
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.ActivityDailyNotesBinding
import com.katiearose.sobriety.databinding.DialogAddNoteBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.checkValidIntentData
import com.katiearose.sobriety.utils.getDateFormatPattern
import com.katiearose.sobriety.utils.getSharedPref
import com.katiearose.sobriety.utils.isInputEmpty
import com.katiearose.sobriety.utils.showConfirmDialog
import com.katiearose.sobriety.utils.write
import kotlinx.datetime.*
import java.time.format.DateTimeFormatter

class DailyNotes : AppCompatActivity() {

    private lateinit var binding: ActivityDailyNotesBinding
    private lateinit var adapter: NoteAdapter
    private lateinit var dateFormat: DateTimeFormatter
    private lateinit var addiction: Addiction
    private lateinit var cacheHandler: CacheHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivityDailyNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dateFormat = DateTimeFormatter.ofPattern(getSharedPref().getDateFormatPattern())
        cacheHandler = CacheHandler(this)

        addiction = Main.addictions[checkValidIntentData()]
        adapter = NoteAdapter(addiction, this,
            { date, index -> showAddNoteDialog(true, date, index) },
            { date, index ->
                val action: () -> Unit = {
                    val notes = addiction.dailyNotes[date] ?: mutableListOf()
                    if (index in notes.indices) notes.removeAt(index)
                    if (notes.isEmpty()) addiction.dailyNotes.remove(date)
                    updateNotesList()
                }
                showConfirmDialog(
                    getString(R.string.delete),
                    getString(R.string.delete_note_confirm, dateFormat.format(date.toJavaLocalDate())),
                    action
                )
            })
        binding.notesList.layoutManager = LinearLayoutManager(this)
        binding.notesList.adapter = adapter

        binding.notesSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.setFilter(newText.orEmpty())
                return true
            }
        })

        binding.addNoteFab.setOnClickListener {
            showAddNoteDialog(false, localToday(), null)
        }
    }

    private fun localToday(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private fun showAddNoteDialog(isEdit: Boolean, date: LocalDate, index: Int?) {
        var pickedDate = date
        val dialogViewBinding = DialogAddNoteBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogViewBinding.root)
        if (isEdit) {
            dialogViewBinding.dateStr.visibility = View.GONE
            dialogViewBinding.noteDate.visibility = View.GONE
            dialogViewBinding.noteInput.setText(addiction.dailyNotes[date]?.getOrNull(index ?: -1))
        } else {
            dialogViewBinding.noteDate.text = dateFormat.format(pickedDate.toJavaLocalDate())
            dialogViewBinding.noteDate.setOnClickListener {
                with(MaterialDatePicker.Builder.datePicker().build()) {
                    addOnPositiveButtonClickListener {
                        pickedDate = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                        dialogViewBinding.noteDate.text = dateFormat.format(pickedDate.toJavaLocalDate())
                    }
                    show(supportFragmentManager, null)
                }
            }
        }
        dialogViewBinding.btnSave.setOnClickListener {
            if (dialogViewBinding.noteInput.isInputEmpty()) {
                dialogViewBinding.noteInputLayout.error = getString(R.string.error_empty_note)
            } else {
                val text = dialogViewBinding.noteInput.text.toString()
                if (isEdit)
                    addiction.dailyNotes[date]?.set(index ?: -1, text)
                else
                    addiction.dailyNotes.getOrPut(pickedDate) { mutableListOf() }.add(text)
                updateNotesList()
                if (!isEdit) adapter.expandDay(pickedDate)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun updateNotesList() {
        cacheHandler.write()
        adapter.update()
    }
}
