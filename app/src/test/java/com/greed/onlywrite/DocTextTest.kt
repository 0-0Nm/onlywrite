package com.greed.onlywrite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DocTextTest {

    private fun carve(seq: Int, pos: Int, s: String) = Rec(seq, "carve", pos, s, 0, 0)
    private fun stamp(seq: Int, s: String) = Rec(seq, "stamp", 0, s, 0, 0)

    @Test
    fun emptyDoc() {
        assertEquals("", DocText.render(emptyList()).text)
    }

    @Test
    fun carveInOrder() {
        val r = DocText.render(listOf(carve(1, 0, "abc"), carve(2, 3, "def")))
        assertEquals("abcdef", r.text)
    }

    @Test
    fun carveInMiddle() {
        val r = DocText.render(listOf(carve(1, 0, "ad"), carve(2, 1, "bc")))
        assertEquals("abcd", r.text)
    }

    @Test
    fun negativePosDoesNotCrash() {
        val r = DocText.render(listOf(carve(1, -2, "x"), carve(2, 999, "y")))
        assertEquals("xy", r.text)
    }

    @Test
    fun stampRangeIsRecorded() {
        val r = DocText.render(listOf(carve(1, 0, "a"), stamp(2, "T")))
        assertEquals("a\nT\n", r.text)
        assertEquals(1, r.stamps.size)
        assertEquals("T", r.text.substring(r.stamps[0].first, r.stamps[0].last))
    }

    @Test
    fun strikeRangeClampedAndKept() {
        val recs = listOf(carve(1, 0, "hello"), Rec(2, "strike", 0, "", -5, 999))
        val r = DocText.render(recs)
        assertEquals(1, r.strikes.size)
        assertEquals(0, r.strikes[0].first)
        assertEquals(4, r.strikes[0].last)
    }

    @Test
    fun degenerateStrikeIsDropped() {
        val recs = listOf(carve(1, 0, "hi"), Rec(2, "strike", 0, "", 1, 1))
        assertEquals(0, DocText.render(recs).strikes.size)
    }

    @Test
    fun countExcludesStampAndIndent() {
        val r = DocText.render(listOf(carve(1, 0, "ab"), stamp(2, "TT"), carve(3, 6, "\u3000\u3000cd")))
        assertEquals(4, DocText.countChars(r.text, r.stamps))
    }

    @Test
    fun safeRangeRejectsEmpty() {
        assertNull(DocText.safeRange(3, 3, 10))
        assertNull(DocText.safeRange(9, 2, 10))
        assertNotNull(DocText.safeRange(1, 4, 10))
    }
}
