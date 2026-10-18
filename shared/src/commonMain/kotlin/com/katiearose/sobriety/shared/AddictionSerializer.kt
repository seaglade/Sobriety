package com.katiearose.sobriety.shared

import kotlinx.datetime.*
import kotlinx.datetime.serializers.DateTimeUnitSerializer
import kotlinx.datetime.serializers.InstantComponentSerializer
import kotlinx.datetime.serializers.LocalTimeIso8601Serializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object AddictionSerializer : KSerializer<Addiction> {
    private val historySerializer = MapSerializer(Long.serializer(), Long.serializer())
    private val savingsSerializer = MapSerializer(String.serializer(), PairSerializer(Double.serializer(), String.serializer()))
    private val milestonesSerializer = SetSerializer(PairSerializer(Int.serializer(), DateTimeUnitSerializer))

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Addiction =
        decoder.decodeStructure(descriptor) {
            var name = ""
            var status = Addiction.Status.Ongoing
            val history = LinkedHashMap<Long, Long>()
            var priority = Addiction.Priority.MEDIUM
            var dailyNotes = LinkedHashMap<LocalDate, MutableList<String>>()
            var timeSaving = LocalTime(0, 0)
            var savings = LinkedHashMap<String, Pair<Double, String>>()
            var milestones = LinkedHashSet<Pair<Int, DateTimeUnit>>()
            while (true) {
                when (val i = decodeElementIndex(descriptor)) {
                    0 -> name = decodeStringElement(descriptor, i)
                    1 -> {
                        val statusStr = decodeStringElement(descriptor, i)
                        status = runCatching { Addiction.Status.valueOf(statusStr) }.getOrDefault(Addiction.Status.Ongoing)
                    }
                    // putAll rather than reassign for last_relapse legacy key
                    2 -> history.putAll(LinkedHashMap(decodeSerializableElement(descriptor, i, historySerializer)))
                    3 -> {
                        val priorityIdx = decodeIntElement(descriptor, i)
                        val priorities = Addiction.Priority.values()
                        priority = if (priorityIdx in priorities.indices) priorities[priorityIdx] else Addiction.Priority.MEDIUM
                    }
                    4 -> dailyNotes = dailyNotesFromJson(decodeSerializableElement(descriptor, i, JsonElement.serializer()))
                    5 -> timeSaving = decodeSerializableElement(descriptor, i, LocalTimeIso8601Serializer)
                    6 -> savings = LinkedHashMap(decodeSerializableElement(descriptor, i, savingsSerializer))
                    7 -> milestones = LinkedHashSet(decodeSerializableElement(descriptor, i, milestonesSerializer))

                    // Legacy keys
                    // last_relapse
                    8 -> {
                        val lastRelapse = decodeSerializableElement(descriptor, i, InstantComponentSerializer)
                        if (lastRelapse > Clock.System.now()) {
                            status = Addiction.Status.Future
                            history[lastRelapse.toEpochMilliseconds()] = 0
                        } else if (history.isEmpty()) {
                            history[lastRelapse.toEpochMilliseconds()] = 0
                        }
                    }
                    // is_stopped
                    9 -> if (decodeBooleanElement(descriptor, i)) status = Addiction.Status.Stopped

                    CompositeDecoder.DECODE_DONE -> break
                }
            }

            Addiction(name, status, history, priority, dailyNotes, timeSaving, savings, milestones)
        }

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("addiction") {
            element<String>("name")
            element<String>("status")
            element<LinkedHashMap<Long, Long>>("history")
            element<Int>("priority")
            element<JsonElement>("daily_notes")
            element<LocalTime>("time_saving")
            element<LinkedHashMap<String, Pair<Double, String>>>("savings")
            element<LinkedHashSet<Pair<Int, DateTimeUnit>>>("milestones")

            // Legacy keys
            element<Instant>("last_relapse")
            element<Boolean>("is_stopped")
        }

    override fun serialize(encoder: Encoder, value: Addiction) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.name)
            encodeStringElement(descriptor, 1, value.status.name)
            encodeSerializableElement(descriptor, 2, historySerializer, value.history)
            encodeIntElement(descriptor, 3, value.priority.ordinal)
            encodeSerializableElement(descriptor, 4, JsonElement.serializer(), dailyNotesToJson(value.dailyNotes))
            encodeSerializableElement(descriptor, 5, LocalTimeIso8601Serializer, value.timeSaving)
            encodeSerializableElement(descriptor, 6, savingsSerializer, value.savings)
            encodeSerializableElement(descriptor, 7, milestonesSerializer, value.milestones)
        }
    }
}

/**
 * Decodes the daily_notes map, accepting both the legacy format (each day maps to a
 * single string) and the current format (each day maps to a list of strings).
 */
private fun dailyNotesFromJson(element: JsonElement): LinkedHashMap<LocalDate, MutableList<String>> {
    val result = LinkedHashMap<LocalDate, MutableList<String>>()
    val obj = element as? JsonObject ?: return result
    for ((key, value) in obj) {
        val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
        val notes = mutableListOf<String>()
        when (value) {
            is JsonPrimitive -> notes.add(value.content)
            is JsonArray -> value.forEach { if (it is JsonPrimitive) notes.add(it.content) }
            else -> {}
        }
        result[date] = notes
    }
    return result
}

private fun dailyNotesToJson(map: LinkedHashMap<LocalDate, MutableList<String>>): JsonElement =
    JsonObject(map.entries.associate { (date, notes) ->
        date.toString() to JsonArray(notes.map { JsonPrimitive(it) })
    })