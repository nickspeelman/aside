package com.nickspeelman.localjournal.data

import java.util.Locale

/**
 * Authoritative hashtag parsing and normalization.
 *
 * Hashtags are case-insensitive and consist only of Unicode letters/numbers plus underscore.
 * Punctuation, apostrophes, hyphens, emoji, and other special characters terminate the tag.
 */
object HashtagUtils {
    data class ActiveHashtag(val start: Int, val end: Int, val prefix: String)

    fun normalize(tag: String): String {
        val body = tag.trim().removePrefix("#")
        if (body.isBlank()) return ""
        val validBody = TAG_BODY_REGEX.find(body)?.value.orEmpty()
        if (validBody.isBlank()) return ""
        return "#${validBody.lowercase(Locale.ROOT)}"
    }

    fun parse(raw: String): List<String> = raw.split(',')
        .map(::normalize)
        .filter { it.isNotBlank() }
        .distinct()

    fun extractFromContent(content: String): List<String> = HASHTAG_REGEX.findAll(content)
        .map { normalize(it.value) }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

    fun contains(raw: String, tag: String): Boolean {
        val target = normalize(tag)
        if (target.isBlank()) return false
        return parse(raw).any { it == target }
    }

    fun activeHashtag(content: String, cursor: Int): ActiveHashtag? {
        if (cursor !in 0..content.length) return null

        var start = cursor
        while (start > 0 && content[start - 1].isHashtagBodyCharacter()) start--
        if (start == 0 || content[start - 1] != '#') return null
        start--
        if (start > 0 && content[start - 1].isHashtagBodyCharacter()) return null

        var end = cursor
        while (end < content.length && content[end].isHashtagBodyCharacter()) end++
        return ActiveHashtag(
            start = start,
            end = end,
            prefix = content.substring(start, cursor).lowercase(Locale.ROOT)
        )
    }

    fun suggestions(
        knownHashtags: List<String>,
        activeHashtag: ActiveHashtag?,
        limit: Int = 4
    ): List<String> {
        if (activeHashtag == null || limit <= 0) return emptyList()
        return knownHashtags.asSequence()
            .map(::normalize)
            .filter { it.isNotBlank() && it.startsWith(activeHashtag.prefix) }
            .distinct()
            .take(limit)
            .toList()
    }

    private fun Char.isHashtagBodyCharacter(): Boolean = isLetterOrDigit() || this == '_'

    private val TAG_BODY_REGEX = Regex("^[\\p{L}\\p{N}_]+")
    private val HASHTAG_REGEX = Regex("(?<![\\p{L}\\p{N}_])#[\\p{L}\\p{N}_]+")
}
