package com.nickspeelman.localjournal

import com.nickspeelman.localjournal.notifications.ParserUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodParserTest {

    @Test
    fun `parse full entry correctly`() {
        val input = "8 feeling great #happy #sun"
        val entry = ParserUtils.parseMoodInput(input)
        
        assertEquals(8, entry.rating)
        assertEquals("feeling great", entry.note)
        assertEquals("#happy,#sun", entry.hashtags)
    }

    @Test
    fun `parse rating only`() {
        val input = "5"
        val entry = ParserUtils.parseMoodInput(input)
        
        assertEquals(5, entry.rating)
        assertEquals("", entry.note)
        assertEquals("", entry.hashtags)
    }

    @Test
    fun `parse rating and hashtags only`() {
        val input = "10 #amazing #blessed"
        val entry = ParserUtils.parseMoodInput(input)
        
        assertEquals(10, entry.rating)
        assertEquals("", entry.note)
        assertEquals("#amazing,#blessed", entry.hashtags)
    }

    @Test
    fun `handle invalid rating`() {
        val input = "abc note #tag"
        val entry = ParserUtils.parseMoodInput(input)
        
        assertEquals(5, entry.rating) // Default
        assertEquals("note", entry.note)
        assertEquals("#tag", entry.hashtags)
    }

    @Test
    fun `clamp rating value`() {
        assertEquals(10, ParserUtils.parseMoodInput("15").rating)
        assertEquals(1, ParserUtils.parseMoodInput("0").rating)
        assertEquals(1, ParserUtils.parseMoodInput("-5").rating)
    }
}
