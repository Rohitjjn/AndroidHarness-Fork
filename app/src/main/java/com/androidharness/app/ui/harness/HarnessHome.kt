package com.androidharness.app.ui.harness

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.data.AppSettings
import com.androidharness.app.data.db.SessionEntity
import com.androidharness.app.ui.harness.components.GlyphTile
import com.androidharness.app.ui.harness.components.Hairline
import com.androidharness.app.ui.harness.components.LiveDot
import com.androidharness.app.ui.harness.components.Pill
import com.androidharness.app.ui.harness.components.SectionLabel
import com.androidharness.app.ui.harness.theme.HarnessTheme
import com.androidharness.app.ui.harness.theme.Space

/**
 * The welcome state: greeting, project picker and suggestion chips. Used when
 * no conversation is open, and shown again whenever the drawer's New session is
 * tapped.
 */
@Composable
fun HarnessHomeScreen(
    container: AppContainer,
    settings: AppSettings,
    sessions: List<SessionEntity>,
    projects: List<Pair<String, String>>,
    onOpenDrawer: () -> Unit,
    showMenuButton: Boolean,
    onNewSession: (String?) -> Unit,
    onOpenSession: (String) -> Unit,
    onNavigate: (HarnessRoute) -> Unit,
) {
    val palette = HarnessTheme.palette
    val running by container.runManager.runningSessionIds.collectAsStateWithLifecycle()
    var selectedProject by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(palette.paper)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showMenuButton) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = palette.text)
                }
            } else {
                Spacer(Modifier.width(Space.sm))
            }
            HarnessMark(size = 22.dp)
            Spacer(Modifier.width(Space.sm))
            Text("Harness Code", style = MaterialTheme.typography.titleMedium, color = palette.text)
            Spacer(Modifier.weight(1f))
            if (running.isNotEmpty()) {
                Row(
                    Modifier
                        .clickable { onNavigate(HarnessRoute.Parallel) }
                        .padding(horizontal = Space.sm, vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveDot()
                    Spacer(Modifier.width(Space.xs))
                    Text("${running.size} running", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                }
            }
            IconButton(onClick = { onNewSession(selectedProject) }) {
                Icon(Icons.Filled.Add, contentDescription = "New session", tint = palette.text)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
        ) {
            Spacer(Modifier.height(Space.xxl))
            Text(
                greeting(),
                style = MaterialTheme.typography.displayMedium,
                color = palette.text,
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                "What should we work on?",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.muted,
            )
            Spacer(Modifier.height(Space.xl))

            SectionLabel("PROJECT")
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                Pill(
                    label = "No project",
                    selected = selectedProject == null,
                    onClick = { selectedProject = null },
                )
                if (projects.isNotEmpty()) {
                    Pill(
                        label = projects.first().second,
                        selected = selectedProject == projects.first().first,
                        onClick = { selectedProject = projects.first().first },
                    )
                }
                if (projects.size > 2) {
                    Pill(
                        label = "+${projects.size - 2}",
                        onClick = { onNavigate(HarnessRoute.Projects) },
                    )
                }
            }
            Spacer(Modifier.height(Space.xl))

            SectionLabel("START WITH")
            Spacer(Modifier.height(Space.sm))
            val suggestions = listOf(
                "Explain this codebase" to "Give me a tour of the project: entry points, modules and how they fit together.",
                "Fix the failing tests" to "Run the test suite, find the failures and fix them one by one.",
                "Review my changes" to "Review the pending git changes for correctness, safety and style.",
                "Plan a feature" to "Help me plan a feature. Ask what you need, then write a plan.",
            )
            for (row in suggestions.chunked(2)) {
                Row(Modifier.fillMaxWidth().padding(bottom = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    for (suggestion in row) {
                        SuggestionChip(suggestion.first, Modifier.weight(1f)) {
                            HarnessDrafts.pending = suggestion.second
                            onNewSession(selectedProject)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Space.xl))

            if (running.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("RUNNING NOW")
                    Spacer(Modifier.weight(1f))
                    Text(
                        "See all",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accentInk,
                        modifier = Modifier.clickable { onNavigate(HarnessRoute.Parallel) },
                    )
                }
                Spacer(Modifier.height(Space.sm))
                val runningRows = sessions.filter { it.id in running }.take(3)
                for (session in runningRows) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(palette.surface, RoundedCornerShape(12.dp))
                            .clickable { onOpenSession(session.id) }
                            .padding(Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiveDot()
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                session.title.ifBlank { "Untitled session" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("Working…", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                        }
                        Icon(
                            Icons.Outlined.Splitscreen,
                            contentDescription = null,
                            tint = palette.faint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(Space.xl))
            }

            val recent = sessions.filter { it.id !in settings.archivedSessions }.take(5)
            if (recent.isNotEmpty()) {
                SectionLabel("RECENT")
                Spacer(Modifier.height(Space.xs))
                for (session in recent) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSession(session.id) }
                            .padding(vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlyphTile(
                            icon = HarnessIcons.Today,
                            size = 30.dp,
                        )
                        Spacer(Modifier.width(Space.md))
                        Text(
                            session.title.ifBlank { "Untitled session" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            relativeDay(session.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.faint,
                        )
                    }
                    Hairline(Modifier.padding(start = 42.dp))
                }
            }

            Spacer(Modifier.height(Space.xxl))
            Text(
                "Harness Code is an unofficial personal project, not affiliated with Anthropic.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.faint,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/** One-tap starters on the welcome screen. */
@Composable
private fun SuggestionChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = HarnessTheme.palette
    Column(
        modifier
            .height(64.dp)
            .background(palette.surface, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(Space.md),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.text, maxLines = 2)
    }
}

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "Still up?"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun relativeDay(updatedAt: Long): String {
    val label = SessionGrouping.classify(updatedAt, System.currentTimeMillis())
    return when (label) {
        "Today" -> "today"
        "Yesterday" -> "yesterday"
        else -> {
            val days = ((System.currentTimeMillis() - updatedAt) / 86_400_000L).coerceAtLeast(2)
            "${days}d ago"
        }
    }
}

/**
 * A tiny hand-off slot for suggestion-chip drafts. The shell reads and clears
 * it when the new session mounts, so no ViewModel plumbing is needed.
 */
object HarnessDrafts {
    @Volatile
    var pending: String? = null

    fun consume(): String? {
        val value = pending
        pending = null
        return value
    }
}
