package com.greed.onlywrite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {

    @Test fun noStrikesReturnsBody() {
        assertEquals("abc", Export.marked("abc", ""))
    }

    @Test fun singleRange() {
        assertEquals("a\u27e61\u27e7bc\u27e61\u27e7", Export.marked("abc", "1:3"))
    }

    @Test fun twoRangesAreNumbered() {
        val out = Export.marked("abcdef", "0:1;3:4")
        assertTrue(out.startsWith("\u27e61\u27e7a\u27e61\u27e7"))
        assertTrue(out.contains("\u27e62\u27e7d\u27e62\u27e7"))
    }

    @Test fun unorderedRangesGetSorted() {
        val out = Export.marked("abcdef", "3:4;0:1")
        assertTrue(out.indexOf("\u27e61\u27e7") < out.indexOf("\u27e62\u27e7"))
    }

    @Test fun outOfBoundsRangeIsClamped() {
        val out = Export.marked("abc", "1:999")
        assertEquals("a\u27e61\u27e7bc\u27e61\u27e7", out)
    }

    @Test fun degenerateRangeIgnored() {
        assertEquals("abc", Export.marked("abc", "2:2"))
    }

    @Test fun duplicateRangesCollapseToOneTag() {
        val out = Export.marked("abc", "1:3;1:3")
        assertEquals("a\u27e61\u27e7bc\u27e61\u27e7", out)
    }

    @Test fun duplicateRangesNotDuplicatedInJson() {
        val j = Export.strikesJson("abc", "1:3;1:3")
        assertEquals(1, j.split("\"s\":").size - 1)
    }

    @Test fun badEncodingIgnored() {
        assertEquals("abc", Export.marked("abc", "x:y;1"))
    }

    @Test fun jsonEscapesQuotesAndNewlines() {
        val s = Export.strikesJson("a\"b\nc", "0:4")
        assertTrue(s.contains("\\\""))
        assertTrue(s.contains("\\n"))
        assertTrue(s.startsWith("["))
    }

    @Test fun emptyStrikesJson() {
        assertEquals("[]", Export.strikesJson("abc", ""))
    }

    @Test fun chainJsonContainsAllRecords() {
        val revs = listOf(
            Rev(1, "carve", "0", "h1", 0, "ab", 0, 0, 1000L),
            Rev(2, "strike", "h1", "h2", 0, "", 0, 1, 2000L)
        )
        val j = Export.chainJson(revs, "ab", "0:1", "doc-1", 3000L)
        assertTrue(j.contains("\"docId\":\"doc-1\""))
        assertTrue(j.contains("\"chainHead\":\"h2\""))
        assertTrue(j.contains("\"seq\":1"))
        assertTrue(j.contains("\"seq\":2"))
        assertTrue(j.contains("\"archivedAt\":3000"))
    }

    @Test fun notesTxtEmptyWhenNoNotes() {
        assertEquals("", Export.notesTxt("abc", emptyList()))
    }

    @Test fun notesTxtContainsAnchorAndBody() {
        val n = Note(2, 4, "这里写得虚", 1700000000000L)
        val s = Export.notesTxt("abcdef", listOf(n))
        assertTrue(s.contains("这里写得虚"))
        assertTrue(s.contains("定位：cd"))
        assertTrue(s.contains("[1]"))
    }

    @Test fun notesJsonIsArray() {
        val n = Note(0, 2, "x", 1L)
        val j = Export.notesJson("abc", listOf(n))
        assertTrue(j.startsWith("["))
        assertTrue(j.contains("\"anchor\":\"ab\""))
        assertTrue(j.contains("\"body\":\"x\""))
    }

    @Test fun fullTxtHasTitleBodyAndNotes() {
        val n = Note(1, 2, "注", 1L)
        val s = Export.fullTxt("T", "abc", "", listOf(n))
        assertTrue(s.startsWith("T\n\nabc"))
        assertTrue(s.contains("注记"))
        assertTrue(s.contains("注"))
    }

    @Test fun recordHashIsStable() {
        val a = Export.recordHash("d", 1, "p", "carve", "x", 0, 0, 0, 1L)
        val b = Export.recordHash("d", 1, "p", "carve", "x", 0, 0, 0, 1L)
        assertEquals(a, b)
        assertEquals(64, a.length)
    }
}
