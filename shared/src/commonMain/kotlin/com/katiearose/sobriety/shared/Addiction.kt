package com.katiearose.sobriety.shared

import kotlinx.datetime.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = AddictionSerializer::class)
@SerialName("addiction")
data class Addiction(
    val name: String,
    var status: Status,
    // Maps past, current, and future attempts start and end epoch milliseconds
    val history: LinkedHashMap<Long, Long>,
    var priority: Priority,
    val dailyNotes: LinkedHashMap<LocalDate, String>,
    var timeSaving: LocalTime,
    // What is being saved -> Pair(amount, unit)
    val savings: LinkedHashMap<String, Pair<Double, String>>,
    val milestones: LinkedHashSet<Pair<Int, DateTimeUnit>>,
) {

    val creationTimestamp: Long
        get() = history.keys.first()

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
        var totalDuration = 0L
        val list = history.toList()
        for (attemptIndex in whichAttempts) {
            totalDuration += (list[attemptIndex].second - list[attemptIndex].first)
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

    fun getNextMilestone(): Pair<Int, DateTimeUnit>? {
        val now = Clock.System.now().toEpochMilliseconds()
        return milestones
            .mapNotNull { inputPair ->
                val (quantity, unit) = inputPair
                val calculatedInstant = runCatching {
                    history.keys.last() + quantity * unit.toMillis()
                }.getOrNull()
                calculatedInstant?.let { Pair(it, inputPair) }
            }.filter { (calculatedTime, _) ->
                calculatedTime > now
            }
            .minByOrNull { (calculatedTime, _) -> calculatedTime }
            ?.second
    }

    /**
     * @return - the goal point in milliseconds
     * - the progress as an int in range 0..100
     */
    fun calculateMilestoneProgressionPercentage(milestone: Pair<Int, DateTimeUnit>): Pair<Long, Int> {
        val goal = history.keys.last() + milestone.first * milestone.second.toMillis()
        return when (status) {
            Status.Ongoing -> Pair(goal,
                (((Clock.System.now().toEpochMilliseconds() - history.keys.last()).toFloat() /
                    (goal - history.keys.last())) * 100).toInt())

            Status.Stopped, Status.Future -> Pair(goal, 0)
        }
    }

    fun stopAbstaining() {
        when (status) {
            // End the current attempt
            Status.Ongoing -> history.putLast(Clock.System.now().toEpochMilliseconds())

            // You can't stop twice
            Status.Stopped -> {}

            // If a Future addiction is stopped, remove the future attempt
            // If that was the only attempt, add in a 0-length attempt to reflect history
            Status.Future -> {
                history.remove(history.keys.last())
                if (history.isEmpty()) {
                    val currentTime = Clock.System.now().toEpochMilliseconds()
                    history[currentTime] = currentTime
                }
            }
        }
        status = Status.Stopped
    }

    fun relapse() {
        when (status) {
            // End the current attempt
            Status.Ongoing -> history.putLast(Clock.System.now().toEpochMilliseconds())

            Status.Stopped -> {}

            // Remove the future attempt (user is starting early)
            Status.Future -> history.remove(history.keys.last())
        }
        history[Clock.System.now().toEpochMilliseconds()] = 0
        status = Status.Ongoing
    }

    fun getDailyNotesList(sort: SortMode): List<Pair<LocalDate, String>> {
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






