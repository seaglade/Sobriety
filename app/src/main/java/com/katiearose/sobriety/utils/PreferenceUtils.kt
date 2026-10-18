package com.katiearose.sobriety.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

fun Context.getSharedPref(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

fun SharedPreferences.getDateFormatPattern(): String = getString("date_format", "MMMM dd uuuu")!!

fun SharedPreferences.getAddNoteAfterRelapsePref(): Boolean =
    getBoolean("add_note_after_relapse", true)

fun SharedPreferences.getAverageAttemptsWindow(): Int =
    getString("average_attempts_window", "3")?.toIntOrNull()?.coerceAtLeast(0) ?: 3

fun SharedPreferences.getAltTimelinePref(): Boolean = getBoolean("alt_timeline_view", false)

fun SharedPreferences.getSortNotesPref(): String = getString("sort_notes", "asc")!!

fun SharedPreferences.getSortMilestonesPref(): String = getString("sort_milestones", "asc")!!

fun SharedPreferences.getHideCompletedMilestonesPref(): Boolean =
    getBoolean("hide_completed_milestones", false)
