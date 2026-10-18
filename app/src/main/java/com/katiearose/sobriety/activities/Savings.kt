package com.katiearose.sobriety.activities

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat.CLOCK_24H
import com.katiearose.sobriety.R
import com.katiearose.sobriety.SavingsAdapter
import com.katiearose.sobriety.databinding.ActivitySavingsBinding
import com.katiearose.sobriety.databinding.DialogAddSavingBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.utils.applyThemes
import com.katiearose.sobriety.utils.checkValidIntentData
import com.katiearose.sobriety.utils.isInputEmpty
import com.katiearose.sobriety.utils.showConfirmDialog
import com.katiearose.sobriety.utils.toggleVisibility
import com.katiearose.sobriety.utils.write
import kotlinx.datetime.LocalTime

class Savings : AppCompatActivity() {

    private lateinit var binding: ActivitySavingsBinding
    private lateinit var addiction: Addiction
    private lateinit var cacheHandler: CacheHandler
    private lateinit var adapter: SavingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivitySavingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cacheHandler = CacheHandler(this)

        addiction = Main.addictions[checkValidIntentData()]
        updateSavedTimeDisplay()

        binding.btnEditTime.setOnClickListener {
            with(MaterialTimePicker.Builder()
                .setTitleText(R.string.select_time_saved)
                .setTimeFormat(CLOCK_24H)
                .build()) {
                addOnPositiveButtonClickListener {
                    addiction.timeSaving = LocalTime(hour, minute)
                    cacheHandler.write()
                    updateSavedTimeDisplay()
                }
                show(supportFragmentManager, null)
            }
        }
        binding.btnExpandCollapseTime.setOnClickListener {
            binding.timeSavedCard.toggleVisibility()
            binding.btnExpandCollapseTime.apply {
                setImageResource(if (binding.timeSavedCard.visibility == View.VISIBLE) R.drawable.expand_less_24px else R.drawable.expand_more_24px)
                contentDescription =
                    if (binding.timeSavedCard.visibility == View.VISIBLE) getString(R.string.collapse) else getString(
                        R.string.expand
                    )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    tooltipText = if (binding.timeSavedCard.visibility == View.VISIBLE) getString(R.string.collapse) else getString(
                            R.string.expand
                        )
            }
        }

        binding.btnAddOther.setOnClickListener { showAddSavingDialog(null) }
        binding.btnExpandCollapseOther.setOnClickListener {
            binding.otherSavingsList.toggleVisibility()
            binding.btnExpandCollapseOther.apply {
                setImageResource(if (binding.otherSavingsList.visibility == View.VISIBLE) R.drawable.expand_less_24px else R.drawable.expand_more_24px)
                contentDescription = if (binding.otherSavingsList.visibility == View.VISIBLE) getString(R.string.collapse) else getString(
                        R.string.expand
                    )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    tooltipText = if (binding.otherSavingsList.visibility == View.VISIBLE) getString(R.string.collapse) else getString(
                            R.string.expand
                        )
            }
        }

        adapter = SavingsAdapter(addiction, this, { showAddSavingDialog(it) }, {
            val action: () -> Unit = {
                addiction.savings.remove(it.first)
                update()
            }
            showConfirmDialog(getString(R.string.delete), getString(R.string.delete_saving_confirm, it.first), action)
        })
        binding.otherSavingsList.layoutManager = LinearLayoutManager(this)
        binding.otherSavingsList.adapter = adapter
    }

    private fun showAddSavingDialog(existingSaving: Pair<String, Pair<Double, String>>?) {
        val dialogViewBinding = DialogAddSavingBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogViewBinding.root)
        if (existingSaving != null) {
            dialogViewBinding.nameStr.visibility = View.GONE
            dialogViewBinding.savingsNameInputLayout.visibility = View.GONE
            dialogViewBinding.savingsAmountInput.setText(existingSaving.second.first.toString())
            dialogViewBinding.unitInput.setText(existingSaving.second.second)
        }
        dialogViewBinding.btnSaveSaving.setOnClickListener {
            val inputFields = listOf(dialogViewBinding.savingsAmountInputLayout, dialogViewBinding.unitInputLayout, dialogViewBinding.savingsNameInputLayout)
            val savingsAmount = dialogViewBinding.savingsAmountInput.text.toString().toDoubleOrNull()
            when {
                savingsAmount == null -> {
                    dialogViewBinding.savingsAmountInputLayout.error = getString(R.string.error_empty_amount)
                    inputFields.forEach { if (it !== dialogViewBinding.savingsAmountInputLayout) it.error = null }
                }
                dialogViewBinding.unitInput.isInputEmpty() -> {
                    dialogViewBinding.unitInputLayout.error = getString(R.string.error_empty_unit)
                    inputFields.forEach { if (it !== dialogViewBinding.unitInputLayout) it.error = null }
                }
                else -> {
                    if (existingSaving != null) {
                        addiction.savings[existingSaving.first] = Pair(
                            savingsAmount,
                            dialogViewBinding.unitInput.text.toString())
                        update()
                        dialog.dismiss()
                    } else {
                        if (dialogViewBinding.savingsNameInput.isInputEmpty()) {
                            dialogViewBinding.savingsNameInputLayout.error = getString(R.string.error_empty_name)
                            inputFields.forEach { if (it !== dialogViewBinding.savingsNameInputLayout) it.error = null }
                        } else {
                            addiction.savings[dialogViewBinding.savingsNameInput.text.toString()] =
                                Pair(
                                    savingsAmount,
                                    dialogViewBinding.unitInput.text.toString())
                            update()
                            dialog.dismiss()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun update() {
        cacheHandler.write()
        adapter.update()
    }

    private fun updateSavedTimeDisplay() {
        binding.timeSaved.text =
            if (addiction.timeSaving.hour == 0 && addiction.timeSaving.minute == 0) getString(R.string.no_set)
            else StringBuilder(resources.getQuantityString(R.plurals.hours, addiction.timeSaving.hour, addiction.timeSaving.hour))
                .append(" ")
                .append(resources.getQuantityString(R.plurals.minutes, addiction.timeSaving.minute, addiction.timeSaving.minute))
                .apply { deleteAt(length - 1) } //delete the comma at the end
    }
}