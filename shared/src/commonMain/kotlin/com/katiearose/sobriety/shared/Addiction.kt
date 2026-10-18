package com.katiearose.sobriety.shared

import kotlinx.datetime.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = AddictionSerializer::class)
@SerialName("addiction")
data class Addiction(
    var name: String,
    var status: Status,
    // Maps past, current, and future attempts start and end epoch milliseconds
    val history: LinkedHashMap<Long, Long>,
    var priority: Priority,
    val dailyNotes: LinkedHashMap<LocalDate, MutableList<String>>,
    var timeSaving: LocalTime,
    // What is being saved -> Pair(amount, unit)
    val savings: LinkedHashMap<String, Pair<Double, String>>,
    val milestones: LinkedHashSet<Pair<Int, DateTimeUnit>>,
) {

    enum class Priority {
        HIGH, MEDIUM, LOW
    }

    enum class Status {
        Ongoing, Stopped, Future
    }

    /**
     * @param whichAttempts list of map indices
     */
    fun calculateAvgRelapseDuration(whichAttempts: List<Int>): Long {
        if (whichAttempts.isEmpty()) return 0L
        var totalDuration = 0L
        val list = history.toList()
        for (attemptIndex in whichAttempts) {
            if (attemptIndex in list.indices) {
                totalDuration += (list[attemptIndex].second - list[attemptIndex].first).coerceAtLeast(0L)
            }
        }
        return totalDuration / whichAttempts.size
    }

    /**
     * @param numAttempts number of attempts to calculate
     * @return average duration in milliseconds or null if no history
     */
    fun calculateRecentAverage(numAttempts: Int): Long? {
        val maxExclusive = when (status) {
            // Do not include unfinished attempt
            Status.Ongoing, Status.Future -> history.size - 1
            // Stopped addictions have the final full attempt at the end
            Status.Stopped -> history.size
        }
        val minimum = (maxExclusive - numAttempts).coerceAtLeast(0)
        val range = (minimum until maxExclusive).toList()
        return if (range.isNotEmpty())
            calculateAvgRelapseDuration(range)
        else
            null
    }

    /**
     * @return the longest single attempt duration in milliseconds. The ongoing attempt
     * is counted up to now. Zero-length and not-yet-started attempts count as 0.
     */
    fun longestAttemptMillis(): Long = history.entries.maxOfOrNull { (start, end) ->
        attemptDuration(start, end)
    } ?: 0L

    /**
     * @return the total time spent abstaining across all attempts in milliseconds.
     */
    fun totalAttemptMillis(): Long = history.entries.sumOf { (start, end) ->
        attemptDuration(start, end)
    }

    private fun attemptDuration(start: Long, end: Long): Long {
        // An end of 0 marks the ongoing attempt, which is measured up to now
        val effectiveEnd = if (end == 0L) Clock.System.now().toEpochMilliseconds() else end
        return (effectiveEnd - start).coerceAtLeast(0L)
    }

    /**
     * @return - the goal point in milliseconds
     * - the progress as an int in range 0..100
     */
    fun calculateMilestoneProgressionPercentage(milestone: Pair<Int, DateTimeUnit>): Pair<Long, Int> {
        val lastKey = history.keys.lastOrNull() ?: return Pair(0L, 0)
        val goal = lastKey + milestone.first * milestone.second.toMillis()
        return when (status) {
            Status.Ongoing -> {
                val totalSpan = goal - lastKey
                val progress = if (totalSpan <= 0L) 100 else {
                    val elapsed = Clock.System.now().toEpochMilliseconds() - lastKey
                    (((elapsed.toFloat() / totalSpan) * 100).toInt()).coerceIn(0, 100)
                }
                Pair(goal, progress)
            }

            Status.Stopped, Status.Future -> Pair(goal, 0)
        }
    }

    fun stopAbstaining() {
        val now = Clock.System.now().toEpochMilliseconds()
        when (status) {
            // End the current attempt
            Status.Ongoing -> history.putLast(now)

            // You can't stop twice
            Status.Stopped -> {}

            // If a Future addiction is stopped, remove the future attempt
            // If that was the only attempt, add in a 0-length attempt to reflect history
            Status.Future -> {
                if (history.isNotEmpty()) history.remove(history.keys.last())
                if (history.isEmpty()) {
                    history[now] = now
                } else if (history.values.last() == 0L) {
                    history.putLast(now)
                }
            }
        }
        status = Status.Stopped
    }

    /**
     * @param atMillis moment the relapse happened, defaults to now
     */
    fun relapse(atMillis: Long = Clock.System.now().toEpochMilliseconds()) {
        val lastStart = history.keys.lastOrNull() ?: 0L
        val validAt = if (atMillis <= lastStart && history.isNotEmpty()) lastStart + 1L else atMillis
        when (status) {
            // End the current attempt
            Status.Ongoing -> history.putLast(validAt)

            Status.Stopped -> {}

            // Remove the future attempt (user is starting early)
            Status.Future -> if (history.isNotEmpty()) history.remove(history.keys.last())
        }
        history[validAt] = 0
        status = Status.Ongoing
    }

    /**
     * @return each day mapped to its ordered list of notes
     */
    fun getDailyNotesGrouped(sort: SortMode): List<Pair<LocalDate, MutableList<String>>> {
        return when (sort) {
            SortMode.ASC -> dailyNotes.toList().sortedWith { n1, n2 -> n1.first.compareTo(n2.first) }
            SortMode.DESC -> dailyNotes.toList().sortedWith { n1, n2 -> n2.first.compareTo(n1.first) }
            SortMode.NONE -> dailyNotes.toList()
        }
    }

    fun getMilestonesList(sort: SortMode, hideComplete: Boolean): List<Pair<Int, DateTimeUnit>> {
        var list = when (sort) {
            SortMode.ASC -> milestones.toList().sortedWith { m1, m2 ->
                (m1.first * m1.second.toMillis()).compareTo(m2.first * m2.second.toMillis())
            }
            SortMode.DESC -> milestones.toList().sortedWith { m1, m2 ->
                (m2.first * m2.second.toMillis()).compareTo(m1.first * m1.second.toMillis())
            }
            SortMode.NONE -> milestones.toList()
        }
        if (hideComplete)
            list = list.filter {
                Clock.System.now().toEpochMilliseconds() < history.keys.last() + it.first * it.second.toMillis()
            }
        return list
    }

    companion object {
        fun newInstance(name: String, millis: Long, priority: Priority): Addiction {
            val history = LinkedHashMap<Long, Long>()
            history[millis] = 0
            val status =
                if (millis > Clock.System.now().toEpochMilliseconds()) Status.Future
                else Status.Ongoing
            return  Addiction(
                name,
                status,
                history,
                priority,
                LinkedHashMap(),
                LocalTime(0, 0),
                LinkedHashMap(),
                LinkedHashSet()
            )
        }
    }
}






