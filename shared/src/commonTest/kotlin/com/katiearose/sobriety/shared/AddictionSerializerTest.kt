package com.katiearose.sobriety.shared

import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AddictionSerializerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(AddictionSerializer)

    private fun decode(raw: String): Addiction =
        json.decodeFromString(serializer, raw).single()

    private fun encode(addiction: Addiction): String =
        json.encodeToString(serializer, listOf(addiction))

    private val date = LocalDate(2026, 10, 18)

    @Test
    fun legacySingleStringNoteBecomesSingleItemList() {
        val legacy = """[{"name":"x","status":"Ongoing","history":{"1000":0},"priority":1,""" +
            """"daily_notes":{"2026-10-18":"2211"},"time_saving":"00:00","savings":{},"milestones":[]}]"""
        val addiction = decode(legacy)
        assertEquals(listOf("2211"), addiction.dailyNotes.getValue(date))
    }

    @Test
    fun currentArrayNotesArePreserved() {
        val current = """[{"name":"x","status":"Ongoing","history":{"1000":0},"priority":1,""" +
            """"daily_notes":{"2026-10-18":["abc","123"]},"time_saving":"00:00","savings":{},"milestones":[]}]"""
        val addiction = decode(current)
        assertEquals(listOf("abc", "123"), addiction.dailyNotes.getValue(date))
    }

    @Test
    fun roundTripPreservesMultipleNotesPerDay() {
        val current = """[{"name":"x","status":"Ongoing","history":{"1000":0},"priority":1,""" +
            """"daily_notes":{"2026-10-18":["abc","123"]},"time_saving":"00:00","savings":{},"milestones":[]}]"""
        val original = decode(current)
        val decoded = decode(encode(original))
        assertEquals(original.dailyNotes, decoded.dailyNotes)
        assertEquals(listOf("abc", "123"), decoded.dailyNotes.getValue(date))
    }

    @Test
    fun legacyNoteRoundTripsIntoNewFormat() {
        val legacy = """[{"name":"x","status":"Ongoing","history":{"1000":0},"priority":1,""" +
            """"daily_notes":{"2026-10-18":"2211"},"time_saving":"00:00","savings":{},"milestones":[]}]"""
        val decoded = decode(encode(decode(legacy)))
        assertEquals(listOf("2211"), decoded.dailyNotes.getValue(date))
    }

    @Test
    fun legacyLastRelapseInPastSeedsOngoingAttempt() {
        val legacy = """[{"name":"x","status":"Ongoing","history":{},"priority":1,""" +
            """"daily_notes":{},"time_saving":"00:00","savings":{},"milestones":[],"last_relapse":{"epochSeconds":1600000000,"nanosecondsOfSecond":0}}]"""
        val decoded = decode(legacy)
        assertEquals(1, decoded.history.size)
        assertEquals(0L, decoded.history[1600000000000L])
        assertEquals(Addiction.Status.Ongoing, decoded.status)
    }

    @Test
    fun unknownEnumValuesFallbackGracefully() {
        val jsonStr = """[{"name":"x","status":"UnknownFutureStatus","history":{"1000":0},"priority":999,""" +
            """"daily_notes":{},"time_saving":"00:00","savings":{},"milestones":[]}]"""
        val decoded = decode(jsonStr)
        assertEquals(Addiction.Status.Ongoing, decoded.status)
        assertEquals(Addiction.Priority.MEDIUM, decoded.priority)
    }

    @Test
    fun calculateAvgRelapseDurationEmptyListReturnsZero() {
        val addiction = Addiction.newInstance("test", 1000L, Addiction.Priority.LOW)
        assertEquals(0L, addiction.calculateAvgRelapseDuration(emptyList()))
    }

    @Test
    fun calculateMilestoneProgressionPercentageClampedTo100() {
        // Created far in past: 1000L
        val addiction = Addiction.newInstance("test", 1000L, Addiction.Priority.LOW)
        val milestone = Pair(1, kotlinx.datetime.DateTimeUnit.HOUR) // goal was 1000 + 3600_000
        val result = addiction.calculateMilestoneProgressionPercentage(milestone)
        assertEquals(100, result.second)
    }
}
