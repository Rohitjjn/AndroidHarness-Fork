package com.androidharness.app.ui.harness

import com.androidharness.app.data.db.SessionEntity

/** One drawer section: a label and the sessions under it. */
data class SessionSection(val label: String, val sessions: List<SessionEntity>)

/**
 * Pure grouping for the session list: pinned first, then Today / Yesterday /
 * Earlier. Archived sessions are hidden unless the archive view is open.
 *
 * Kept free of Android and Compose so SessionGroupingTest can pin the rules
 * (day boundaries, pin order, search, archived filtering) without a device.
 */
object SessionGrouping {

    fun classify(
        updatedAt: Long,
        now: Long,
        zoneOffsetMillis: Long = 0L,
    ): String {
        val day = 86_400_000L
        val today = (now + zoneOffsetMillis) / day
        val thatDay = (updatedAt + zoneOffsetMillis) / day
        return when (today - thatDay) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> "Earlier"
        }
    }

    fun sections(
        sessions: List<SessionEntity>,
        pinned: Set<String>,
        query: String = "",
        now: Long = System.currentTimeMillis(),
        zoneOffsetMillis: Long = 0L,
    ): List<SessionSection> {
        val needle = query.trim().lowercase()
        val visible = sessions.filter { needle.isEmpty() || it.title.lowercase().contains(needle) }
        val pinnedRows = visible.filter { it.id in pinned }.sortedByDescending { it.updatedAt }
        val rest = visible.filter { it.id !in pinned }.sortedByDescending { it.updatedAt }
        val out = ArrayList<SessionSection>(4)
        if (pinnedRows.isNotEmpty()) out += SessionSection("Pinned", pinnedRows)
        for (label in listOf("Today", "Yesterday", "Earlier")) {
            val rows = rest.filter { classify(it.updatedAt, now, zoneOffsetMillis) == label }
            if (rows.isNotEmpty()) out += SessionSection(label, rows)
        }
        return out
    }

    fun archived(sessions: List<SessionEntity>, archived: Set<String>): List<SessionEntity> =
        sessions.filter { it.id in archived }.sortedByDescending { it.updatedAt }
}
