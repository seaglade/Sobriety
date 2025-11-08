package com.katiearose.sobriety.utils

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatEditText
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.katiearose.sobriety.R
import com.katiearose.sobriety.activities.Main
import com.katiearose.sobriety.shared.CacheHandler
import kotlinx.datetime.DateTimeUnit
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.util.*

private const val MINUTE = 60
private const val HOUR = MINUTE * 60
private const val DAY = HOUR * 24
private const val WEEK = DAY * 7
private const val YEAR = (DAY * 365.25).toInt()
private const val MONTH = YEAR / 12


fun Context.convertSecondsToString(given: Long): String {
    if (given == -1L) return ""
    var time = given
    val s = time % MINUTE
    time -= s
    val m = (time % HOUR) / MINUTE
    time -= m * MINUTE
    val h = (time % DAY) / HOUR
    time -= h * HOUR
    val d = (time % WEEK) / DAY
    time -= d * DAY
    val w = (time % MONTH) / WEEK
    time -= w * WEEK
    val mo = (time % YEAR) / MONTH
    time -= mo * MONTH
    val y = time / YEAR
    val stringBuilder = StringBuilder()
    if (y != 0L) stringBuilder.append(resources.getQuantityString(R.plurals.years, y.toInt(), y)).append(" ")
    if (mo != 0L) stringBuilder.append(resources.getQuantityString(R.plurals.months, mo.toInt(), mo)).append(" ")
    if (w != 0L) stringBuilder.append(resources.getQuantityString(R.plurals.weeks, w.toInt(), w)).append(" ")
    if (d != 0L) stringBuilder.append(resources.getQuantityString(R.plurals.days, d.toInt(), d)).append(" ")
    if (h != 0L) stringBuilder.append(resources.getQuantityString(R.plurals.hours, h.toInt(), h)).append(" ")
    if (m != 0L) stringBuilder.append(resources.getQuantityString(R.plurals.minutes, m.toInt(), m)).append(" ")
    if (!(y == 0L && mo == 0L && w == 0L && d == 0L && h == 0L && m == 0L)) stringBuilder.append(
        getString(R.string.and)
    ).append(" ")
    stringBuilder.append(resources.getQuantityString(R.plurals.seconds, s.toInt(), s))
    return stringBuilder.toString()
}

// Expects start and end timestamps in epoch milliseconds
fun Context.convertRangeToString(start: Long, end: Long = Instant.now().toEpochMilli(), secondPrecision: Boolean = true): String {
    if (start == -1L) return ""
    val startDate: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(start),
        TimeZone.getDefault().toZoneId())
    val endDate: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(end),
        TimeZone.getDefault().toZoneId())
    // Duration calculated time based days rather than "conceptual" days
    // Used directly for hours, minutes, seconds
    val duration: Duration = Duration.between(startDate, endDate)
    // Period for years, months, weeks, days
    val period: Period = Period.between(
        // The reason why startDate is not used is to prevent a conceptual calculation of days
        // i.e. 2023-01-30T23:30 to 2023-01-31TT01:30 is 1 conceptual day but only 2 literal hours
        endDate.minusDays(duration.toDaysPart()).toLocalDate(),
        endDate.toLocalDate()
    )

    val y = period.years
    val mo = period.months
    val d = period.days % 7
    val w = period.days / 7
    val h = duration.toHoursPart()
    val m = duration.toMinutesPart()
    val s = duration.toSecondsPart()

    val stringBuilder = StringBuilder()
    if (y != 0) stringBuilder.append(resources.getQuantityString(R.plurals.years, y, y)).append(" ")
    if (mo != 0) stringBuilder.append(resources.getQuantityString(R.plurals.months, mo, mo)).append(" ")
    if (w != 0) stringBuilder.append(resources.getQuantityString(R.plurals.weeks, w, w)).append(" ")
    if (d != 0) stringBuilder.append(resources.getQuantityString(R.plurals.days, d, d)).append(" ")
    if (h != 0) stringBuilder.append(resources.getQuantityString(R.plurals.hours, h, h)).append(" ")
    if (m != 0 && secondPrecision) stringBuilder.append(resources.getQuantityString(R.plurals.minutes, m, m)).append(" ")
    if(secondPrecision){
        if (!(y == 0 && mo == 0 && w == 0 && d == 0 && h == 0 && m == 0)) stringBuilder.append(
            getString(R.string.and)
        ).append(" ")
        stringBuilder.append(resources.getQuantityString(R.plurals.seconds, s, s))
    }
    else if(stringBuilder.length >2) {
        //Remove previously inserted comma and space at end
        stringBuilder.deleteCharAt(stringBuilder.length-2)
    }
    else {
        stringBuilder.append(getString(R.string.sub_1hour))
    }

    return stringBuilder.toString()
}

// Each triple contains the raw number (e.g. 12), the percentage in per mille (500), and the unit (hours)
fun convertRangeToPercentList(start: Long, end: Long = Instant.now().toEpochMilli())
        : List<Triple<Int, Int, DateTimeUnit>> {
    val startDate: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(start),
        TimeZone.getDefault().toZoneId())
    val endDate: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(end),
        TimeZone.getDefault().toZoneId())
    // Duration calculated time based days rather than "conceptual" days
    // Used directly for hours, minutes, seconds
    val duration: Duration = Duration.between(startDate, endDate)
    // Period for years, months, weeks, days
    val period: Period = Period.between(
        // The reason why startDate is not used is to prevent a conceptual calculation of days
        // i.e. 2023-01-30T23:30 to 2023-01-31TT01:30 is 1 conceptual day but only 2 literal hours
        endDate.minusDays(duration.toDaysPart()).toLocalDate(),
        endDate.toLocalDate()
    )

    // Calculate the number of days until the month period ends
    val nextMonth = startDate.plus(Period.of(period.years, period.months + 1, 0))
    val durationLeft = Duration.between(endDate, nextMonth)
    val daysLeft = (duration.plus(durationLeft).toDaysPart() - duration.toDaysPart()).toInt()

    // Initially store denominator in second part of triple
    return listOf(
        // Years progress bar should never complete, but crosses 50% at 2 years
        Triple(period.years, period.years+2, DateTimeUnit.YEAR),
        Triple(period.months, 12, DateTimeUnit.MONTH),
        // Weeks make the visualization non-whole so they are omitted
        Triple(period.days, period.days + daysLeft, DateTimeUnit.DAY),
        Triple(duration.toHoursPart(), 24, DateTimeUnit.HOUR),
        Triple(duration.toMinutesPart(), 60, DateTimeUnit.MINUTE),
        Triple(duration.toSecondsPart(), 60, DateTimeUnit.SECOND)
    ).map {
        Triple(
            it.first,
            (1000 * it.first.toDouble().div(it.second)).toInt(),
            it.third
        )
    }
}

fun Context.convertMilestoneToString(milestone: Pair<Int, DateTimeUnit>): String {
    return StringBuilder(milestone.first.toString()).append(" ").append(
        when (milestone.second) {
            DateTimeUnit.HOUR -> resources.getString(R.string.unit_hour)
            DateTimeUnit.DAY -> resources.getString(R.string.unit_day)
            DateTimeUnit.WEEK -> resources.getString(R.string.unit_week)
            DateTimeUnit.MONTH -> resources.getString(R.string.unit_month)
            DateTimeUnit.YEAR -> resources.getString(R.string.unit_year)
            else -> "Unsupported"
        }
    ).toString()
}

fun Context.showConfirmDialog(title: String, message: String, action: () -> Unit) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok) { _, _ -> action() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

fun Activity.applyThemes() {
    val preferences = PreferenceManager.getDefaultSharedPreferences(this)
    when (preferences.getString("theme", "system")) {
        "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
    if (preferences.getBoolean("material_you", false)) {
        setTheme(R.style.Theme_Sobriety_Material3)
    } else setTheme(R.style.Theme_Sobriety)
}

fun Activity.checkValidIntentData() : Int {
    val index = intent.getIntExtra(Main.EXTRA_ADDICTION_POSITION, -1)
    require(index != -1) { "Invalid intent data received" }
    return index
}

fun AppCompatEditText.isInputEmpty(): Boolean = text == null || text.toString().isBlank()

fun View.toggleVisibility() {
    visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
}

fun Context.toast(@StringRes textRes: Int) = Toast.makeText(this, textRes, Toast.LENGTH_SHORT).show()

inline var TextView.textResource: Int
    set(@StringRes value) = setText(value)
    @Deprecated("This property is set-only, don't bother.", level = DeprecationLevel.HIDDEN)
    get() = error("don't")

fun CacheHandler.write() = writeCache(Main.addictions)