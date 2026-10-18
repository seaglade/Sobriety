package com.katiearose.sobriety

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.katiearose.sobriety.activities.Main
import com.katiearose.sobriety.databinding.CardAddictionBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.utils.convertRangeToString
import com.katiearose.sobriety.utils.convertSecondsToString
import com.katiearose.sobriety.utils.getDateFormatPattern
import com.katiearose.sobriety.utils.getAverageAttemptsWindow
import com.katiearose.sobriety.utils.getSharedPref
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AddictionCardAdapter(
    private val context: Context,
    private val deleteButtonAction: (Addiction) -> Unit,
    private val relapseButtonAction: (Addiction) -> Unit,
    private val stopButtonAction: (Addiction) -> Unit,
    private val timelineButtonAction: (Addiction) -> Unit,
    private val priorityTextViewAction: (Addiction) -> Unit,
    private val miscButtonAction: (Addiction) -> Unit,
    private val cardButtonAction: (Addiction) -> Unit
) :
    RecyclerView.Adapter<AddictionCardAdapter.AddictionCardViewHolder>() {

    companion object {
        // Payload used by the single centralized ticker in Main to re-render elapsed time only
        const val TICK = 1
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AddictionCardViewHolder {
        val binding =
            CardAddictionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AddictionCardViewHolder(binding,
            { deleteButtonAction(Main.addictions[it]) },
            { relapseButtonAction(Main.addictions[it]) },
            { stopButtonAction(Main.addictions[it]) },
            { timelineButtonAction(Main.addictions[it]) },
            { priorityTextViewAction(Main.addictions[it]) },
            { miscButtonAction(Main.addictions[it]) },
            { cardButtonAction(Main.addictions[it]) },
            { Main.addictions[it] })
    }

    override fun onBindViewHolder(
        holder: AddictionCardViewHolder,
        position: Int
    ) {
        val stringBuilder = StringBuilder(context.getString(R.string.priority)).append(": ")
        val addiction = Main.addictions[position]

        holder.textViewName.text = addiction.name
        holder.textViewPriority.text = when (addiction.priority) {
            Addiction.Priority.HIGH -> stringBuilder.append(context.getString(R.string.high))
            Addiction.Priority.MEDIUM -> stringBuilder.append(context.getString(R.string.medium))
            Addiction.Priority.LOW -> stringBuilder.append(context.getString(R.string.low))
        }
        // High-contrast container/on-container colours so the priority label stays legible
        // on any card background (pink default theme AND Material You dynamic colours).
        val (priorityBackground, priorityForeground) = when (addiction.priority) {
            Addiction.Priority.HIGH -> R.color.priority_high_bg to R.color.priority_high_fg
            Addiction.Priority.MEDIUM -> R.color.priority_medium_bg to R.color.priority_medium_fg
            Addiction.Priority.LOW -> R.color.priority_low_bg to R.color.priority_low_fg
        }
        holder.textViewPriority.chipBackgroundColor = ColorStateList.valueOf(
            ResourcesCompat.getColor(context.resources, priorityBackground, context.theme)
        )
        holder.textViewPriority.setTextColor(
            ResourcesCompat.getColor(context.resources, priorityForeground, context.theme)
        )
        val numAttempts = context.getSharedPref().getAverageAttemptsWindow()
        val averageRelapseDuration = addiction.calculateRecentAverage(numAttempts)
        if (averageRelapseDuration == null) {
            holder.textViewAverage.visibility = View.GONE
        } else {
            holder.textViewAverage.visibility = View.VISIBLE
            holder.textViewAverage.text =
                context.getString(
                    R.string.recent_avg,
                    context.convertSecondsToString(averageRelapseDuration / 1000L)
                )
        }

        holder.refresh()
    }

    override fun onBindViewHolder(
        holder: AddictionCardViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        // The centralized ticker sends TICK; rebind only the elapsed-time text.
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else if (payloads.contains(TICK)) {
            holder.refresh()
        }
    }

    override fun getItemCount() = Main.addictions.size

    class AddictionCardViewHolder(
        private val binding: CardAddictionBinding, deleteButtonAction: (Int) -> Unit,
        relapseButtonAction: (Int) -> Unit,
        stopButtonAction: (Int) -> Unit,
        timelineButtonAction: (Int) -> Unit,
        priorityTextViewAction: (Int) -> Unit,
        miscButtonAction: (Int) -> Unit,
        cardButtonAction: (Int) -> Unit,
        private val addictionSupplier: (Int) -> Addiction) : RecyclerView.ViewHolder(binding.root) {
        val textViewName: TextView = binding.textViewAddictionName
        val textViewPriority: Chip = binding.textViewPriority
        val textViewAverage: TextView = binding.textViewAverage
        private lateinit var addiction: Addiction

        fun refresh() {
            // A detached/recycled holder has no valid position; skip to avoid an index crash.
            if (adapterPosition == RecyclerView.NO_POSITION) return
            addiction = addictionSupplier(adapterPosition)
            displayInfo()
        }

        private fun getFormattedDate(milli: Long): String {
            val dateFormat = DateTimeFormatter.ofPattern(binding.root.context.getSharedPref().getDateFormatPattern())
            val date = Instant.ofEpochMilli(milli).atZone(ZoneId.systemDefault())
            return dateFormat.format(date)
        }

        private fun displayInfo() {
            val context = binding.root.context
            binding.textViewTime.text = when (addiction.status) {
                Addiction.Status.Ongoing -> context.convertRangeToString(addiction.history.keys.last())

                Addiction.Status.Stopped -> context.getString(
                    R.string.stop_notice,
                    getFormattedDate(addiction.history.values.last()),
                    context.convertRangeToString(addiction.history.keys.last(), addiction.history.values.last())
                )

                Addiction.Status.Future -> context.getString(
                    R.string.time_until_tracked,
                    context.convertRangeToString(Instant.now().toEpochMilli(), addiction.history.keys.last())
                )
            }
            // Only the live, ongoing streak is the "hero" metric. Stopped/Future use long
            // sentence strings, so they stay at the normal size to avoid awkward wrapping.
            val isHero = addiction.status == Addiction.Status.Ongoing
            binding.textViewTime.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                context.resources.getDimension(
                    if (isHero) R.dimen.text_size_hero else R.dimen.text_size_small
                )
            )
            binding.textViewTime.setTypeface(null, if (isHero) Typeface.BOLD else Typeface.NORMAL)
        }

        init {
            binding.imageDelete.setOnClickListener { deleteButtonAction(adapterPosition) }
            binding.imageReset.setOnClickListener { relapseButtonAction(adapterPosition) }
            binding.imageStop.setOnClickListener { stopButtonAction(adapterPosition) }
            binding.imageTimeline.setOnClickListener { timelineButtonAction(adapterPosition) }
            textViewPriority.setOnClickListener { priorityTextViewAction(adapterPosition) }
            binding.imageMisc.setOnClickListener { miscButtonAction(adapterPosition) }
            binding.cardViewAddiction.setOnClickListener { cardButtonAction(adapterPosition) }
        }
    }
}
