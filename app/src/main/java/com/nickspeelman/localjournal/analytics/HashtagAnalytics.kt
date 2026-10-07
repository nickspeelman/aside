package com.nickspeelman.localjournal.analytics

import com.nickspeelman.localjournal.data.HashtagUtils
import com.nickspeelman.localjournal.data.MoodEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class HashtagDateRange(val start: LocalDate, val end: LocalDate)

enum class HashtagSort(val label: String) {
    MOST_USED("Most used"),
    RECENTLY_USED("Recently used"),
    HIGHEST_AVERAGE("Highest average"),
    LOWEST_AVERAGE("Lowest average"),
    ALPHABETICAL("A–Z")
}

data class HashtagStat(
    val tag: String,
    val entryCount: Int,
    val ratedEntryCount: Int,
    val averageRating: Double?,
    val lastUsedTimestamp: Long
)

data class HashtagSnapshot(
    val stats: List<HashtagStat>,
    val taggedEntryCount: Int
)

/** Shared, deterministic hashtag statistics derived from journal entries. */
object HashtagAnalytics {
    fun snapshot(
        entries: List<MoodEntry>,
        range: HashtagDateRange? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): HashtagSnapshot {
        data class Accumulator(
            var entryCount: Int = 0,
            var ratedEntryCount: Int = 0,
            var ratingTotal: Int = 0,
            var lastUsedTimestamp: Long = Long.MIN_VALUE
        )

        val byTag = linkedMapOf<String, Accumulator>()
        var taggedEntries = 0

        entries.forEach { entry ->
            if (range != null) {
                val date = Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).toLocalDate()
                if (date.isBefore(range.start) || date.isAfter(range.end)) return@forEach
            }

            val tags = HashtagUtils.parse(entry.hashtags).distinct()
            if (tags.isEmpty()) return@forEach
            taggedEntries += 1

            tags.forEach { tag ->
                val accumulator = byTag.getOrPut(tag) { Accumulator() }
                accumulator.entryCount += 1
                if (entry.rating != null) {
                    accumulator.ratedEntryCount += 1
                    accumulator.ratingTotal += entry.rating
                }
                accumulator.lastUsedTimestamp = maxOf(accumulator.lastUsedTimestamp, entry.timestamp)
            }
        }

        return HashtagSnapshot(
            stats = byTag.map { (tag, value) ->
                HashtagStat(
                    tag = tag,
                    entryCount = value.entryCount,
                    ratedEntryCount = value.ratedEntryCount,
                    averageRating = if (value.ratedEntryCount == 0) null
                    else value.ratingTotal.toDouble() / value.ratedEntryCount,
                    lastUsedTimestamp = value.lastUsedTimestamp
                )
            },
            taggedEntryCount = taggedEntries
        )
    }

    fun filterAndSort(
        stats: List<HashtagStat>,
        query: String,
        sort: HashtagSort
    ): List<HashtagStat> {
        val normalizedQuery = query.trim()
            .removePrefix("#")
            .lowercase(Locale.ROOT)

        val filtered = if (normalizedQuery.isBlank()) stats else stats.filter { stat ->
            stat.tag.removePrefix("#").contains(normalizedQuery, ignoreCase = true)
        }

        return when (sort) {
            HashtagSort.MOST_USED -> filtered.sortedWith(
                compareByDescending<HashtagStat> { it.entryCount }
                    .thenByDescending { it.lastUsedTimestamp }
                    .thenBy { it.tag }
            )
            HashtagSort.RECENTLY_USED -> filtered.sortedWith(
                compareByDescending<HashtagStat> { it.lastUsedTimestamp }
                    .thenByDescending { it.entryCount }
                    .thenBy { it.tag }
            )
            HashtagSort.HIGHEST_AVERAGE -> filtered.sortedWith(
                compareBy<HashtagStat> { it.averageRating == null }
                    .thenByDescending { it.averageRating ?: Double.NEGATIVE_INFINITY }
                    .thenByDescending { it.ratedEntryCount }
                    .thenByDescending { it.entryCount }
                    .thenBy { it.tag }
            )
            HashtagSort.LOWEST_AVERAGE -> filtered.sortedWith(
                compareBy<HashtagStat> { it.averageRating == null }
                    .thenBy { it.averageRating ?: Double.POSITIVE_INFINITY }
                    .thenByDescending { it.ratedEntryCount }
                    .thenByDescending { it.entryCount }
                    .thenBy { it.tag }
            )
            HashtagSort.ALPHABETICAL -> filtered.sortedBy { it.tag }
        }
    }
}
