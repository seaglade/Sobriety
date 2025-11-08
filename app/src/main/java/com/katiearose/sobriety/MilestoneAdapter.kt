package com.katiearose.sobriety

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.katiearose.sobriety.databinding.ListItemMilestoneBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.SortMode
import com.katiearose.sobriety.utils.convertMilestoneToString
import com.katiearose.sobriety.utils.getDateFormatPattern
import com.katiearose.sobriety.utils.getHideCompletedMilestonesPref
import com.katiearose.sobriety.utils.getSharedPref
import com.katiearose.sobriety.utils.getSortMilestonesPref
import com.katiearose.sobriety.utils.textResource
import kotlinx.datetime.DateTimeUnit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MilestoneAdapter(
    private val addiction: Addiction, private val context: Context,
    private val deleteButtonAction: (Pair<Int, DateTimeUnit>) -> Unit
) :
    ListAdapter<Pair<Int, DateTimeUnit>, MilestoneAdapter.MilestoneViewHolder>(object :
        DiffUtil.ItemCallback<Pair<Int, DateTimeUnit>>() {
        override fun areItemsTheSame(
            oldItem: Pair<Int, DateTimeUnit>,
            newItem: Pair<Int, DateTimeUnit>
        ): Boolean = oldItem.first == newItem.first

        override fun areContentsTheSame(
            oldItem: Pair<Int, DateTimeUnit>,
            newItem: Pair<Int, DateTimeUnit>
        ): Boolean = oldItem.second == newItem.second
    }) {
    private val preferences = context.getSharedPref()

    init {
        update()
    }

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm ${preferences.getDateFormatPattern()}")

    fun update() {
        submitList(when (preferences.getSortMilestonesPref()) {
            "asc" -> addiction.getMilestonesList(SortMode.ASC, preferences.getHideCompletedMilestonesPref())
            "desc" -> addiction.getMilestonesList(SortMode.DESC, preferences.getHideCompletedMilestonesPref())
            else -> addiction.getMilestonesList(SortMode.NONE, preferences.getHideCompletedMilestonesPref())
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MilestoneViewHolder {
        val binding =
            ListItemMilestoneBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MilestoneViewHolder(binding) { deleteButtonAction(currentList[it]) }
    }

    override fun onBindViewHolder(holder: MilestoneViewHolder, position: Int) {
        val milestone = currentList[position]
        holder.milestone.text = context.convertMilestoneToString(milestone)
        val calculatedPair = addiction.calculateMilestoneProgressionPercentage(milestone)
        holder.milestoneProgressBar.progress = calculatedPair.second
        if (holder.milestoneProgressBar.progress == 100) {
            holder.milestoneTime.textResource = R.string.completed
        } else {
            holder.milestoneTime.text =
                dateTimeFormatter.format(Instant.ofEpochMilli(calculatedPair.first).atZone(ZoneId.systemDefault()))
        }
    }

    class MilestoneViewHolder(
        binding: ListItemMilestoneBinding,
        deleteButtonAction: (Int) -> Unit
    ) :
        ViewHolder(binding.root) {
        val milestone = binding.milestone
        val milestoneTime = binding.milestoneTime
        val milestoneProgressBar = binding.milestoneProgress

        init {
            binding.btnDeleteMilestone.setOnClickListener { deleteButtonAction(adapterPosition) }
        }
    }
}