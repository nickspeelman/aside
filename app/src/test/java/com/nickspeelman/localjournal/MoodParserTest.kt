package com.nickspeelman.localjournal

import com.nickspeelman.localjournal.data.MoodInputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoodParserTest {

    @Test
    fun `parse full entry correctly`() {
        val entry = MoodInputParser.parse("4 feeling great #happy #sun")

        assertEquals(4, entry.rating)
        assertEquals("feeling great #happy #sun", entry.note)
        assertEquals("#happy,#sun", entry.hashtags)
    }

    @Test
    fun `parse rating only`() {
        val entry = MoodInputParser.parse("5")

        assertEquals(5, entry.rating)
        assertEquals("", entry.note)
        assertEquals("", entry.hashtags)
    }

    @Test
    fun `parse rating and hashtags only`() {
        val entry = MoodInputParser.parse("5 #amazing #blessed")

        assertEquals(5, entry.rating)
        assertEquals("#amazing #blessed", entry.note)
        assertEquals("#amazing,#blessed", entry.hashtags)
    }

    @Test
    fun `text without rating becomes unrated and preserves the note`() {
        val entry = MoodInputParser.parse("rough day at work #work")

        assertNull(entry.rating)
        assertEquals("rough day at work #work", entry.note)
        assertEquals("#work", entry.hashtags)
    }

    @Test
    fun `hashtag only response becomes unrated and preserves hashtag`() {
        val entry = MoodInputParser.parse("#home")

        assertNull(entry.rating)
        assertEquals("#home", entry.note)
        assertEquals("#home", entry.hashtags)
    }

    @Test
    fun `clamp fully numeric rating value for backwards compatibility`() {
        assertEquals(5, MoodInputParser.parse("15").rating)
        assertEquals(1, MoodInputParser.parse("0").rating)
        assertEquals(1, MoodInputParser.parse("-5").rating)
    }

    @Test
    fun `parse compact notification choice followed by note`() {
        val entry = MoodInputParser.parse("4feeling good #home")

        assertEquals(4, entry.rating)
        assertEquals("feeling good #home", entry.note)
        assertEquals("#home", entry.hashtags)
    }

    @Test
    fun `parse compact notification choice followed immediately by hashtag`() {
        val entry = MoodInputParser.parse("2#work")

        assertEquals(2, entry.rating)
        assertEquals("#work", entry.note)
        assertEquals("#work", entry.hashtags)
    }

    @Test
    fun separateRating_keepsRatingOutOfContentField() {
        val entry = MoodInputParser.parseContent(4, "pretty good #home #friends")

        assertEquals(4, entry.rating)
        assertEquals("pretty good #home #friends", entry.note)
        assertEquals("#home,#friends", entry.hashtags)
    }

    @Test
    fun separateRating_allowsNoteOnlyEntry() {
        val entry = MoodInputParser.parseContent(null, "rough morning #work")

        assertNull(entry.rating)
        assertEquals("rough morning #work", entry.note)
        assertEquals("#work", entry.hashtags)
    }

    @Test
    fun separateNotificationNote_doesNotInterpretLeadingNumberAsRating() {
        val entry = MoodInputParser.parseContent(null, "4 hours of sleep #tired")

        assertNull(entry.rating)
        assertEquals("4 hours of sleep #tired", entry.note)
        assertEquals("#tired", entry.hashtags)
    }

}
