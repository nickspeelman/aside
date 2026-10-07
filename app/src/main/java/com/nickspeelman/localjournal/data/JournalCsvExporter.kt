package com.nickspeelman.localjournal.data

import java.time.Instant

object JournalCsvExporter {
    private val header = listOf("timestamp", "mood_rating", "note", "hashtags")

    fun encode(entries: List<MoodEntry>): String = buildString {
        appendLine(header.joinToString(","))
        entries.sortedBy { it.timestamp }.forEach { entry ->
            val row = listOf(
                Instant.ofEpochMilli(entry.timestamp).toString(),
                entry.rating?.toString().orEmpty(),
                entry.note,
                entry.hashtags
            )
            appendLine(row.joinToString(",") { escape(it) })
        }
    }

    private fun escape(value: String): String {
        val safeValue = if (value.firstOrNull() in FORMULA_PREFIXES) "'$value" else value
        val escaped = safeValue.replace("\"", "\"\"")
        return if (
            safeValue.contains(',') ||
            safeValue.contains('"') ||
            safeValue.contains('\n') ||
            safeValue.contains('\r')
        ) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@')
}
