package com.katiearose.sobriety.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun DateTimeUnit.toMillis(): Long {
    return when (this) {
        is DateTimeUnit.TimeBased -> nanoseconds / 1_000_000
        is DateTimeUnit.DayBased -> days.toLong() * 24 * 60 * 60 * 1000
        is DateTimeUnit.MonthBased -> (months * 30.4375 * 24 * 60 * 60 * 1000).toLong()
    }
}

/**
 * Puts the specified value to the last key in this map.
 */
fun <K, V> LinkedHashMap<K, V>.putLast(value: V) {
    put(keys.last(), value)
}