package com.nickspeelman.localjournal.data

import java.util.Locale

/**
 * Authoritative hashtag parsing and normalization.
 *
 * Hashtags are case-insensitive and consist only of Unicode letters/numbers plus underscore.
 * Punctuation, apostrophes, hyphens, emoji, and other special characters terminate the tag.
 */
object HashtagUtils {
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

    private val TAG_BODY_REGEX = Regex("^[\\p{L}\\p{N}_]+")
    private val HASHTAG_REGEX = Regex("(?<![\\p{L}\\p{N}_])#[\\p{L}\\p{N}_]+")
}
