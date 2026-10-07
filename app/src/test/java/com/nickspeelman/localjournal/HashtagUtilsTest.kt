package com.nickspeelman.localjournal

import com.nickspeelman.localjournal.data.HashtagUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HashtagUtilsTest {
    @Test
    fun contains_matchesExactTagCaseInsensitively() {
        assertTrue(HashtagUtils.contains("#Home,#Friends", "#home"))
        assertTrue(HashtagUtils.contains("home,friends", "HOME"))
        assertFalse(HashtagUtils.contains("#homework,#friends", "#home"))
    }
    @Test
    fun extract_usesLettersNumbersAndUnderscoreAndStopsAtSpecialCharacters() {
        val tags = HashtagUtils.extractFromContent(
            "#Work, #café #work_2026 #mental-health #don't #happy😊"
        )
        assertTrue(tags.contains("#work"))
        assertTrue(tags.contains("#café"))
        assertTrue(tags.contains("#work_2026"))
        assertTrue(tags.contains("#mental"))
        assertTrue(tags.contains("#don"))
        assertTrue(tags.contains("#happy"))
        assertFalse(tags.contains("#mental-health"))
    }

}
