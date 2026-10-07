package com.nickspeelman.localjournal.data

/** Shared creation path for every Aside capture surface. */
class JournalEntryWriter(private val repository: MoodRepository) {
    suspend fun create(rating: Int?, content: String): Long? {
        if (rating == null && content.isBlank()) return null
        return repository.insert(MoodInputParser.parseContent(rating, content))
    }

    suspend fun createCompact(input: String): Long? {
        if (input.isBlank()) return null
        return repository.insert(MoodInputParser.parse(input))
    }

    suspend fun attachContent(entryId: Int, content: String): Boolean {
        if (content.isBlank()) return true
        val existing = repository.getEntryById(entryId) ?: return false
        val parsed = MoodInputParser.parseContent(null, content)
        repository.update(existing.copy(note = parsed.note, hashtags = parsed.hashtags))
        return true
    }
}
