package com.greed.onlywrite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DocTextTest {

    private fun carve(seq: Int, pos: Int, s: String) = Rec(seq, "carve", pos, s, 0, 0)
    private fun stamp(seq: Int, pos: Int, s: String) = Rec(seq, "stamp", pos, s, 0, 0)

    @Test fun emptyDoc() = assertEquals("", DocText.render(emptyList()).text)

    @Test fun carveInOrder() =
        assertEquals("abcdef", DocText.render(listOf(carve(1, 0, "abc"), carve(2, 3, "def"))).text)

    @Test fun carveInMiddle() =
        assertEquals("abcd", DocText.render(listOf(carve(1, 0, "ad"), carve(2, 1, "bc"))).text)

    @Test fun negativePosDoesNotCrash() =
        assertEquals("xy", DocText.render(listOf(carve(1, -2, "x"), carve(2, 999, "y"))).text)

    /** 关键：时间块必须插在指定位置，而不是追加到末尾 */
    @Test fun stampInsertsAtPosition() {
        val r = DocText.render(listOf(carve(1, 0, "a"), stamp(2, 1, "T")))
        assertEquals("a\nT\n", r.text)
        assertEquals("T", r.text.substring(r.stamps[0].first, r.stamps[0].last + 1))
    }

    @Test fun stampInMiddleKeepsTail() {
        val r = DocText.render(listOf(
            carve(1, 0, "abXcd"),
            stamp(2, 2, "TT"),
            carve(3, 2 + 2 + 4, "cd")
        ))
        // 2 + len("TT")+2 = 6，尾部仍接在后面
        assertEquals("ab\nTT\ncd", r.text.replace("cd", "cd"))
    }

    @Test fun strikeRangeClampedAndKept() {
        val r = DocText.render(listOf(carve(1, 0, "hello"), Rec(2, "strike", 0, "", -5, 999)))
        assertEquals(1, r.strikes.size)
        assertEquals(0, r.strikes[0].first)
        assertEquals(4, r.strikes[0].last)
    }

    @Test fun degenerateStrikeIsDropped() {
        val recs = listOf(carve(1, 0, "hi"), Rec(2, "strike", 0, "", 1, 1))
        assertEquals(0, DocText.render(recs).strikes.size)
    }

    @Test fun countExcludesStampAndIndent() {
        val r = DocText.render(listOf(
            carve(1, 0, "ab"),
            stamp(2, 2, "T"),
            carve(3, 2 + 1 + 2, DocText.INDENT),
            carve(4, 2 + 1 + 4, "cd")
        ))
        assertEquals(4, DocText.countChars(r.text, r.stamps))
    }

    @Test fun floorFollowsLastStamp() {
        val r = DocText.render(listOf(
            carve(1, 0, "ab"),
            stamp(2, 2, "T"),
            carve(3, 2 + 1 + 2, DocText.INDENT)
        ))
        assertEquals(r.text.length, r.floor)
    }

    @Test fun safeRangeRejectsEmpty() {
        assertNull(DocText.safeRange(3, 3, 10))
        assertNull(DocText.safeRange(9, 2, 10))
        assertNotNull(DocText.safeRange(1, 4, 10))
    }
}
