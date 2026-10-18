package com.katiearose.sobriety.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.katiearose.sobriety.ProgressBarAdapter
import com.katiearose.sobriety.R
import com.katiearose.sobriety.databinding.ActivitySummaryBinding
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.utils.*

class Summary : AppCompatActivity() {

    private lateinit var binding: ActivitySummaryBinding
    private lateinit var addiction: Addiction
    private lateinit var progressAdapter: ProgressBarAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        binding = ActivitySummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        addiction = Main.addictions[checkValidIntentData()]
        title = addiction.name

        binding.longestStreakLabel.text = getString(R.string.longest_streak)
        binding.longestStreak.text =
            convertSecondsToString(addiction.longestAttemptMillis() / 1000)

        binding.totalCleanLabel.text = getString(R.string.total_clean)
        binding.totalClean.text =
            convertSecondsToString(addiction.totalAttemptMillis() / 1000)

        progressAdapter = ProgressBarAdapter(addiction, this)
        binding.progressBarList.layoutManager = LinearLayoutManager(this)
        binding.progressBarList.setHasFixedSize(true)
        binding.progressBarList.adapter = progressAdapter
        (binding.progressBarList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    }

    override fun onResume() {
        super.onResume()
        progressAdapter.start()
    }

    override fun onPause() {
        progressAdapter.stop()
        super.onPause()
    }
}