package com.katiearose.sobriety.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.katiearose.sobriety.MilestoneAdapter
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.ActivityMilestonesBinding
import com.katiearose.sobriety.databinding.DialogAddMilestoneBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.checkValidIntentData
import com.katiearose.sobriety.utils.isInputEmpty
import com.katiearose.sobriety.utils.showConfirmDialog
import com.katiearose.sobriety.utils.write
import kotlinx.datetime.DateTimeUnit

class Milestones : AppCompatActivity() {

    private lateinit var binding: ActivityMilestonesBinding
    private lateinit var addiction: Addiction
    private lateinit var cacheHandler: CacheHandler
    private lateinit var adapter: MilestoneAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivityMilestonesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cacheHandler = CacheHandler(this)

        addiction = Main.addictions[checkValidIntentData()]
        adapter = MilestoneAdapter(addiction, this) {
            val action: () -> Unit = {
                addiction.milestones.remove(it)
                cacheHandler.write()
                update()
            }
            showConfirmDialog(getString(R.string.delete), getString(R.string.delete_milestone_confirm), action)
        }
        binding.milestoneList.layoutManager = LinearLayoutManager(this)
        binding.milestoneList.setHasFixedSize(true)
        binding.milestoneList.adapter = adapter

        binding.addMilestoneFab.setOnClickListener {
            val dialogViewBinding = DialogAddMilestoneBinding.inflate(layoutInflater)
            val dialog = BottomSheetDialog(this)
            dialog.setContentView(dialogViewBinding.root)
            val units = resources.getStringArray(R.array.time_units)
            dialogViewBinding.btnSaveMilestone.setOnClickListener {
                val inputFields = listOf(dialogViewBinding.milestoneNumberInputLayout, dialogViewBinding.milestoneTimeUnitInputLayout)
                val milestoneNumber = dialogViewBinding.milestoneNumberInput.text.toString().toIntOrNull()
                when {
                    milestoneNumber == null || milestoneNumber == 0 -> {
                        dialogViewBinding.milestoneNumberInputLayout.error = getString(R.string.error_empty_amount)
                        inputFields.forEach { if (it !== dialogViewBinding.milestoneNumberInputLayout) it.error = null }
                    }
                    dialogViewBinding.unitInput.text.toString().isEmpty() -> {
                        dialogViewBinding.milestoneTimeUnitInputLayout.error = getString(R.string.error_empty_unit)
                        inputFields.forEach { if (it !== dialogViewBinding.milestoneTimeUnitInputLayout) it.error = null }
                    }
                    else -> {
                        val num = milestoneNumber
                        when (dialogViewBinding.unitInput.text.toString()) {
                            units[0] -> addiction.milestones.add(Pair(num, DateTimeUnit.HOUR))
                            units[1] -> addiction.milestones.add(Pair(num, DateTimeUnit.DAY))
                            units[2] -> addiction.milestones.add(Pair(num, DateTimeUnit.WEEK))
                            units[3] -> addiction.milestones.add(Pair(num, DateTimeUnit.MONTH))
                            units[4] -> addiction.milestones.add(Pair(num, DateTimeUnit.YEAR))
                        }
                        cacheHandler.write()
                        adapter.update()
                        dialog.dismiss()
                    }
                }
            }
            dialog.show()
        }
    }

    private fun update() {
        cacheHandler.write()
        adapter.update()
    }
}