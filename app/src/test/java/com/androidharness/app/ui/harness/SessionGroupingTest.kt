package com.androidharness.app.ui.harness

import com.androidharness.app.data.db.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGroupingTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L

    private fun session(id: String, updatedAt: Long, title: String = id) =
        SessionEntity(id = id, title = title, createdAt = updatedAt, updatedAt = updatedAt)

    @Test fun todayYesterdayAndEarlierSplitAtDayBoundaries() {
        val rows = listOf(
            session("today", now - 60_000),
            session("yesterday", now - day),
            session("older", now - 5 * day),
        )
        val sections = SessionGrouping.sections(rows, pinned = emptySet(), now = now)
        assertEquals(listOf("Today", "Yesterday", "Earlier"), sections.map { it.label })
        assertEquals(listOf("today"), sections[0].sessions.map { it.id })
        assertEquals(listOf("yesterday"), sections[1].sessions.map { it.id })
        assertEquals(listOf("older"), sections[2].sessions.map { it.id })
    }

    @Test fun pinnedSessionsComeFirstAndAreNotRepeated() {
        val rows = listOf(
            session("a", now - 60_000),
            session("b", now - 2 * day),
            session("c", now - 60_000),
        )
        val sections = SessionGrouping.sections(rows, pinned = setOf("b"), now = now)
        assertEquals("Pinned", sections.first().label)
        assertEquals(listOf("b"), sections.first().sessions.map { it.id })
        val rest = sections.drop(1).flatMap { it.sessions }.map { it.id }
        assertEquals(listOf("a", "c"), rest)
    }

    @Test fun pinsKeepNewestFirst() {
        val rows = listOf(
            session("older-pin", now - 3 * day),
            session("newer-pin", now - day),
        )
        val pinned = SessionGrouping.sections(rows, pinned = setOf("older-pin", "newer-pin"), now = now)
        assertEquals(listOf("newer-pin", "older-pin"), pinned.first().sessions.map { it.id })
    }

    @Test fun searchFiltersCaseInsensitively() {
        val rows = listOf(
            session("a", now, title = "Refactor the parser"),
            session("b", now, title = "Fix gradle build"),
        )
        val sections = SessionGrouping.sections(rows, pinned = emptySet(), query = "PARSER", now = now)
        assertEquals(listOf("a"), sections.flatMap { it.sessions }.map { it.id })
    }

    @Test fun emptySearchResultProducesNoSections() {
        val rows = listOf(session("a", now, title = "Refactor"))
        assertTrue(SessionGrouping.sections(rows, pinned = emptySet(), query = "nothing-matches", now = now).isEmpty())
    }

    @Test fun archivedSessionsAreKeptOutOfTheMainListByTheCaller() {
        val rows = listOf(session("archived", now - day), session("live", now))
        assertEquals(listOf("archived"), SessionGrouping.archived(rows, setOf("archived")).map { it.id })
    }

    @Test fun classifyUsesTheProvidedTimeZoneOffset() {
        // 2023-11-14T23:30Z is already the 15th in UTC+2.
        val elevenPm = 1_699_999_800_000L
        val offset = 2 * 60 * 60 * 1000L
        assertEquals("Today", SessionGrouping.classify(elevenPm, elevenPm + 60_000, offset))
        assertEquals("Today", SessionGrouping.classify(elevenPm, elevenPm + 60_000, 0))
    }
}
