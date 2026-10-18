package com.katiearose.sobriety

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.katiearose.sobriety.databinding.ListItemNoteBinding
import com.katiearose.sobriety.databinding.ListItemNoteDayBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.SortMode
import com.katiearose.sobriety.utils.getDateFormatPattern
import com.katiearose.sobriety.utils.getSharedPref
import com.katiearose.sobriety.utils.getSortNotesPref
import com.katiearose.sobriety.utils.toggleVisibility
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

/**
 * A flattened row in the daily notes list: either a collapsible day header or one of its
 * notes (identified by its index within that day so edit/delete target the right note).
 */
sealed class DailyNoteListItem {
    data class Day(val date: LocalDate, val noteCount: Int, val expanded: Boolean) : DailyNoteListItem()
    data class Note(val date: LocalDate, val index: Int, val text: String) : DailyNoteListItem()
}

class NoteAdapter(
    private val addiction: Addiction, context: Context,
    private val editButtonAction: (LocalDate, Int) -> Unit,
    private val deleteButtonAction: (LocalDate, Int) -> Unit
) : ListAdapter<DailyNoteListItem, NoteAdapter.AdapterViewHolder>(diffCallback) {

    private val preferences = context.getSharedPref()
    private val dateFormat = DateTimeFormatter.ofPattern(preferences.getDateFormatPattern())
    private val expandedDays = mutableSetOf<LocalDate>()
    private var expansionInitialized = false
    private var filter: String = ""

    init { update() }

    /**
     * Filters the displayed notes by a case-insensitive substring match on the note text.
     * The underlying [Addiction.dailyNotes] map is never modified.
     */
    fun setFilter(query: String) {
        if (query == filter) return
        filter = query
        update()
    }

    /** Expands (shows the notes of) the given day, e.g. after a note is added to it. */
    fun expandDay(date: LocalDate) {
        expandedDays.add(date)
        update()
    }

    private fun toggleDay(date: LocalDate) {
        if (!expandedDays.add(date)) expandedDays.remove(date)
        update()
    }

    fun update() {
        val grouped = when (preferences.getSortNotesPref()) {
            "asc" -> addiction.getDailyNotesGrouped(SortMode.ASC)
            "desc" -> addiction.getDailyNotesGrouped(SortMode.DESC)
            else -> addiction.getDailyNotesGrouped(SortMode.NONE)
        }
        val searching = filter.isNotBlank()
        if (!expansionInitialized) {
            grouped.maxByOrNull { it.first }?.let { expandedDays.add(it.first) }
            expansionInitialized = true
        }
        val items = mutableListOf<DailyNoteListItem>()
        for ((date, notes) in grouped) {
            val visible = notes.withIndex().filter { !searching || it.value.contains(filter, ignoreCase = true) }
            if (searching && visible.isEmpty()) continue
            val expanded = searching || date in expandedDays
            items.add(DailyNoteListItem.Day(date, notes.size, expanded))
            if (expanded)
                visible.forEach { (index, text) -> items.add(DailyNoteListItem.Note(date, index, text)) }
        }
        submitList(items)
    }

    override fun getItemViewType(position: Int): Int =
        if (currentList[position] is DailyNoteListItem.Day) TYPE_DAY else TYPE_NOTE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdapterViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_DAY)
            DayViewHolder(ListItemNoteDayBinding.inflate(inflater, parent, false))
        else
            NoteViewHolder(ListItemNoteBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: AdapterViewHolder, position: Int) {
        when (val item = currentList[position]) {
            is DailyNoteListItem.Day -> (holder as DayViewHolder).bind(item)
            is DailyNoteListItem.Note -> (holder as NoteViewHolder).bind(item)
        }
    }

    abstract class AdapterViewHolder(root: View) : ViewHolder(root)

    inner class DayViewHolder(private val binding: ListItemNoteDayBinding) : AdapterViewHolder(binding.root) {
        fun bind(item: DailyNoteListItem.Day) {
            binding.dayDate.text = dateFormat.format(item.date.toJavaLocalDate())
            binding.dayCount.text = binding.root.context.resources
                .getQuantityString(R.plurals.notes_count, item.noteCount, item.noteCount)
            binding.dayExpandCollapse.setImageResource(
                if (item.expanded) R.drawable.expand_less_24px else R.drawable.expand_more_24px
            )
            binding.root.setOnClickListener { toggleDay(item.date) }
        }
    }

    inner class NoteViewHolder(private val binding: ListItemNoteBinding) : AdapterViewHolder(binding.root) {
        fun bind(item: DailyNoteListItem.Note) {
            binding.date.visibility = View.GONE
            binding.note.text = item.text
            binding.btnEdit.setOnClickListener { editButtonAction(item.date, item.index) }
            binding.btnDelete.setOnClickListener { deleteButtonAction(item.date, item.index) }
            binding.btnExpandCollapse.setOnClickListener {
                binding.noteCard.toggleVisibility()
                binding.btnExpandCollapse.setImageResource(
                    if (binding.noteCard.visibility == View.VISIBLE) R.drawable.expand_less_24px else R.drawable.expand_more_24px
                )
                binding.btnExpandCollapse.contentDescription =
                    if (binding.noteCard.visibility == View.VISIBLE) it.context.getString(R.string.collapse) else it.context.getString(R.string.expand)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    binding.btnExpandCollapse.tooltipText =
                        if (binding.noteCard.visibility == View.VISIBLE) it.context.getString(R.string.collapse) else it.context.getString(R.string.expand)
            }
        }
    }

    companion object {
        private const val TYPE_DAY = 0
        private const val TYPE_NOTE = 1

        private val diffCallback = object : DiffUtil.ItemCallback<DailyNoteListItem>() {
            override fun areItemsTheSame(oldItem: DailyNoteListItem, newItem: DailyNoteListItem): Boolean =
                when {
                    oldItem is DailyNoteListItem.Day && newItem is DailyNoteListItem.Day ->
                        oldItem.date == newItem.date
                    oldItem is DailyNoteListItem.Note && newItem is DailyNoteListItem.Note ->
                        oldItem.date == newItem.date && oldItem.index == newItem.index
                    else -> false
                }

            override fun areContentsTheSame(oldItem: DailyNoteListItem, newItem: DailyNoteListItem): Boolean =
                oldItem == newItem
        }
    }
}
