package com.androidharness.app.ui.harness

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.data.AppSettings
import com.androidharness.app.data.db.SessionEntity
import com.androidharness.app.ui.harness.components.LiveDot
import com.androidharness.app.ui.harness.components.Pill
import com.androidharness.app.ui.harness.components.SectionLabel
import com.androidharness.app.ui.harness.theme.HarnessTheme
import com.androidharness.app.ui.harness.theme.Space
import kotlinx.coroutines.launch

/**
 * The Harness Code drawer / sidebar: wordmark, new session, session list grouped
 * Today / Yesterday / Earlier with pin and archive, then Projects, Scheduled,
 * parallel sessions, Labs & Device and Settings.
 */
@Composable
fun HarnessDrawer(
    container: AppContainer,
    settings: AppSettings,
    sessions: List<SessionEntity>,
    runningIds: Set<String>,
    activeSessionId: String?,
    current: HarnessRoute,
    compact: Boolean,
    onClose: () -> Unit,
    onNewSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    onNavigate: (HarnessRoute) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SessionEntity?>(null) }
    val sections = remember(sessions, settings.pinnedSessions, query) {
        SessionGrouping.sections(sessions, settings.pinnedSessions, query)
    }
    val archived = remember(sessions, settings.archivedSessions) {
        SessionGrouping.archived(sessions, settings.archivedSessions)
    }

    Column(Modifier.fillMaxHeight().padding(horizontal = Space.sm)) {
        HarnessWordmark(
            subtitle = if (compact) null else "an unofficial coding agent",
            onClose = if (compact) null else onClose,
        )
        Box(Modifier.padding(horizontal = Space.xs)) { DrawerSearch(query) { query = it } }
        Spacer(Modifier.height(Space.sm))
        DrawerDestination(
            label = "New session",
            icon = HarnessIcons.New,
            selected = false,
            onClick = onNewSession,
        )

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (showArchived) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("Archive")
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Back",
                            style = MaterialTheme.typography.labelMedium,
                            color = HarnessTheme.palette.accentInk,
                            modifier = Modifier.clickable { showArchived = false }.padding(4.dp),
                        )
                    }
                }
                items(archived, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        running = session.id in runningIds,
                        active = session.id == activeSessionId,
                        pinned = false,
                        archived = true,
                        onOpen = { onOpenSession(session.id) },
                        onTogglePin = {},
                        onToggleArchive = {
                            scope.launch { container.settings.setArchived(session.id, false) }
                        },
                        onDelete = { pendingDelete = session },
                    )
                }
                if (archived.isEmpty()) {
                    item {
                        Text(
                            "Nothing archived",
                            style = MaterialTheme.typography.bodySmall,
                            color = HarnessTheme.palette.faint,
                            modifier = Modifier.padding(Space.md),
                        )
                    }
                }
            } else {
                for (section in sections) {
                    item(key = "header-${section.label}") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = Space.md, end = Space.md, top = Space.md, bottom = Space.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionLabel(section.label.uppercase())
                        }
                    }
                    items(section.sessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            running = session.id in runningIds,
                            active = session.id == activeSessionId,
                            pinned = session.id in settings.pinnedSessions,
                            archived = false,
                            onOpen = { onOpenSession(session.id) },
                            onTogglePin = {
                                scope.launch { container.settings.setPinned(session.id, session.id !in settings.pinnedSessions) }
                            },
                            onToggleArchive = {
                                scope.launch {
                                    container.settings.setPinned(session.id, false)
                                    container.settings.setArchived(session.id, true)
                                }
                            },
                            onDelete = { pendingDelete = session },
                        )
                    }
                }
                if (sections.isEmpty()) {
                    item {
                        Text(
                            if (query.isBlank()) "No sessions yet" else "No session matches “$query”",
                            style = MaterialTheme.typography.bodySmall,
                            color = HarnessTheme.palette.faint,
                            modifier = Modifier.padding(Space.md),
                        )
                    }
                }
                if (archived.isNotEmpty()) {
                    item(key = "archive-link") {
                        Column(Modifier.padding(top = Space.sm)) {
                            SectionLabel("ARCHIVE", Modifier.padding(horizontal = Space.md))
                            DrawerDestination(
                                label = "Archived sessions",
                                icon = Icons.Outlined.Archive,
                                selected = false,
                                badge = archived.size.toString(),
                                onClick = { showArchived = true },
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(HarnessTheme.palette.border))
        Column(Modifier.padding(vertical = Space.xs)) {
            DrawerDestination(
                label = "Projects",
                icon = HarnessIcons.Projects,
                selected = current == HarnessRoute.Projects,
                onClick = { onNavigate(HarnessRoute.Projects) },
            )
            DrawerDestination(
                label = "Scheduled",
                icon = HarnessIcons.Scheduled,
                selected = current == HarnessRoute.Scheduled,
                onClick = { onNavigate(HarnessRoute.Scheduled) },
            )
            DrawerDestination(
                label = "Parallel sessions",
                icon = HarnessIcons.Parallel,
                selected = current == HarnessRoute.Parallel,
                badge = runningIds.size.takeIf { it > 0 }?.toString(),
                onClick = { onNavigate(HarnessRoute.Parallel) },
            )
            DrawerDestination(
                label = "Labs & Device",
                icon = HarnessIcons.Labs,
                selected = current == HarnessRoute.Labs,
                onClick = { onNavigate(HarnessRoute.Labs) },
            )
            DrawerDestination(
                label = "Settings",
                icon = HarnessIcons.Settings,
                selected = current == HarnessRoute.Settings,
                onClick = { onNavigate(HarnessRoute.Settings) },
            )
        }
        DrawerFooter(onNewSession = onNewSession, runningCount = runningIds.size)
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete session?") },
            text = { Text("“${session.title}” and its message history will be deleted. Files in the workspace stay untouched.") },
            confirmButton = {
                Text(
                    "Delete",
                    color = HarnessTheme.palette.danger,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable {
                            pendingDelete = null
                            scope.launch {
                                container.runManager.stopAndJoin(session.id)
                                container.sessions.deleteSession(session)
                            }
                        }
                        .padding(Space.md),
                )
            },
            dismissButton = {
                Text(
                    "Keep",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { pendingDelete = null }.padding(Space.md),
                )
            },
        )
    }
}

@Composable
private fun SessionRow(
    session: SessionEntity,
    running: Boolean,
    active: Boolean,
    pinned: Boolean,
    archived: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = HarnessTheme.palette
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = Space.xs, vertical = 1.dp)
            .background(if (active) palette.accentSoft else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(start = Space.md, end = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            LiveDot()
            Spacer(Modifier.width(Space.sm))
        }
        Text(
            session.title.ifBlank { "Untitled session" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) palette.accentInk else palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (pinned) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = "Pinned",
                tint = palette.faint,
                modifier = Modifier.size(13.dp),
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Session actions", tint = palette.faint, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (archived) {
                    DropdownMenuItem(
                        text = { Text("Restore") },
                        leadingIcon = { Icon(Icons.Outlined.Unarchive, contentDescription = null) },
                        onClick = { menu = false; onToggleArchive() },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(if (pinned) "Unpin" else "Pin") },
                        leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                        onClick = { menu = false; onTogglePin() },
                    )
                    DropdownMenuItem(
                        text = { Text("Archive") },
                        leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                        onClick = { menu = false; onToggleArchive() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = HarnessTheme.palette.danger) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = HarnessTheme.palette.danger) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}
