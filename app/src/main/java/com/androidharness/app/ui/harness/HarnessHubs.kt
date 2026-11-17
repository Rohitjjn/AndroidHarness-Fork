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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.androidharness.app.data.ThemeMode
import com.androidharness.app.data.db.SessionEntity
import com.androidharness.app.ui.harness.components.EmptyState
import com.androidharness.app.ui.harness.components.GlyphTile
import com.androidharness.app.ui.harness.components.Hairline
import com.androidharness.app.ui.harness.components.HarnessCard
import com.androidharness.app.ui.harness.components.LiveDot
import com.androidharness.app.ui.harness.components.Pill
import com.androidharness.app.ui.harness.components.QuietButton
import com.androidharness.app.ui.harness.components.SectionLabel
import com.androidharness.app.ui.harness.theme.HarnessTheme
import com.androidharness.app.ui.harness.theme.Space
import kotlinx.coroutines.launch

@Composable
private fun HubScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onOpenDrawer: (() -> Unit)? = null,
    showMenuButton: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(HarnessTheme.palette.paper)) {
        HarnessHubHeader(
            title = title,
            onBack = if (showMenuButton) null else onBack,
            onOpenDrawer = if (showMenuButton) onOpenDrawer else null,
        )
        content(Modifier.weight(1f))
    }
}

/* ------------------------------------------------------------------ Projects */

@Composable
fun HarnessProjectsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    showMenuButton: Boolean,
    onOpenSession: (String?) -> Unit,
) {
    val projects by container.workspace.workProjects.collectAsStateWithLifecycle(initialValue = emptyList())
    val chats by container.sessions.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    var manage by remember { mutableStateOf(false) }
    var openProject by remember { mutableStateOf<String?>(null) }

    HubScaffold("Projects", onBack = onBack, onOpenDrawer = onOpenDrawer, showMenuButton = showMenuButton) { modifier ->
        if (manage) {
            Box(modifier) {
                com.androidharness.app.ui.projects.ProjectsScreen(
                    container = container,
                    onBack = { manage = false },
                    onOpenProject = { manage = false },
                )
            }
            return@HubScaffold
        }
        LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.gutter)) {
            item {
                Text(
                    "A project groups sessions, memory and a workspace. Worktree sessions add their own workspace automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HarnessTheme.palette.muted,
                )
                Spacer(Modifier.height(Space.lg))
            }
            if (projects.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Folder,
                        title = "No projects yet",
                        body = "Create a project to keep chats, memory and pending changes together.",
                    )
                }
            }
            items(projects, key = { it.id }) { project ->
                val count = chats.count { it.workProjectId == project.id }
                val expanded = openProject == project.id
                HarnessCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm),
                    onClick = { openProject = if (expanded) null else project.id },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphTile(Icons.Outlined.Folder)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.titleSmall, color = HarnessTheme.palette.text)
                            Text(
                                if (count == 1) "1 session" else "$count sessions",
                                style = MaterialTheme.typography.labelSmall,
                                color = HarnessTheme.palette.muted,
                            )
                        }
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = HarnessTheme.palette.faint, modifier = Modifier.size(16.dp))
                    }
                    if (expanded) {
                        Spacer(Modifier.height(Space.md))
                        val projectChats = chats.filter { it.workProjectId == project.id }.take(6)
                        if (projectChats.isEmpty()) {
                            Text("No sessions in this project yet.", style = MaterialTheme.typography.bodySmall, color = HarnessTheme.palette.muted)
                        }
                        for (chat in projectChats) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenSession(chat.id) }
                                    .padding(vertical = Space.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    chat.title.ifBlank { "Untitled session" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HarnessTheme.palette.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = HarnessTheme.palette.faint, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(Space.sm))
                QuietButton(
                    label = "Manage workspaces & projects",
                    onClick = { manage = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Description,
                )
            }
        }
    }
}

/* ----------------------------------------------------------------- Scheduled */

@Composable
fun HarnessScheduledScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    showMenuButton: Boolean,
    onOpenSession: (String?) -> Unit,
) {
    HubScaffold("Scheduled tasks", onBack = onBack, onOpenDrawer = onOpenDrawer, showMenuButton = showMenuButton) { modifier ->
        Box(modifier) {
            com.androidharness.app.ui.automation.AutomationScreen(
                container = container,
                onBack = onBack,
                onOpenSession = { sessionId -> onOpenSession(sessionId) },
            )
        }
    }
}

/* ---------------------------------------------------------- Parallel sessions */

@Composable
fun HarnessParallelScreen(
    container: AppContainer,
    sessions: List<SessionEntity>,
    running: Set<String>,
    onBack: () -> Unit,
    onOpenSession: (String?) -> Unit,
) {
    val runningRows = sessions.filter { it.id in running }
    val others = sessions.filter { it.id !in running }.take(8)
    Column(Modifier.fillMaxSize().background(HarnessTheme.palette.paper)) {
        HarnessHubHeader(title = "Parallel sessions", onBack = onBack)
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.gutter)) {
            item {
                Text(
                    "Runs keep going when you switch away. Open another session, answer a question, then come back — nothing is stopped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HarnessTheme.palette.muted,
                )
                Spacer(Modifier.height(Space.lg))
                SectionLabel("RUNNING")
                Spacer(Modifier.height(Space.sm))
            }
            if (runningRows.isEmpty()) {
                item {
                    Text("Nothing is running.", style = MaterialTheme.typography.bodySmall, color = HarnessTheme.palette.muted)
                    Spacer(Modifier.height(Space.lg))
                }
            }
            items(runningRows, key = { it.id }) { session ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Space.sm)
                        .background(HarnessTheme.palette.surface, RoundedCornerShape(14.dp))
                        .clickable { onOpenSession(session.id) }
                        .padding(Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveDot()
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(session.title.ifBlank { "Untitled session" }, style = MaterialTheme.typography.bodyMedium, color = HarnessTheme.palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Working…", style = MaterialTheme.typography.labelSmall, color = HarnessTheme.palette.muted)
                    }
                    Icon(Icons.Outlined.Splitscreen, contentDescription = null, tint = HarnessTheme.palette.faint, modifier = Modifier.size(16.dp))
                }
            }
            item {
                Spacer(Modifier.height(Space.md))
                SectionLabel("RECENT")
                Spacer(Modifier.height(Space.sm))
            }
            items(others, key = { it.id }) { session ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenSession(session.id) }.padding(vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        session.title.ifBlank { "Untitled session" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = HarnessTheme.palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = HarnessTheme.palette.faint, modifier = Modifier.size(14.dp))
                }
                Hairline()
            }
        }
    }
}

/* ------------------------------------------------------------------ Settings */

@Composable
fun HarnessSettingsScreen(
    container: AppContainer,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    showMenuButton: Boolean,
    onOpenClassic: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAllSettings: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenSkills: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    HubScaffold("Settings", onBack = onBack, onOpenDrawer = onOpenDrawer, showMenuButton = showMenuButton) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter)) {
            Spacer(Modifier.height(Space.sm))
            SectionLabel("APPEARANCE")
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                for (mode in listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.AMOLED to "Black")) {
                    Pill(
                        label = mode.second,
                        selected = settings.themeMode == mode.first,
                        onClick = { scope.launch { container.settings.setThemeMode(mode.first) } },
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
            Text(
                "Type: Newsreader, Inter and JetBrains Mono. Font size follows the system setting.",
                style = MaterialTheme.typography.bodySmall,
                color = HarnessTheme.palette.muted,
            )
            Spacer(Modifier.height(Space.xl))

            SectionLabel("AGENT")
            Spacer(Modifier.height(Space.sm))
            SettingsRow("All settings", "Search every setting, including advanced ones", Icons.Outlined.Description, onOpenAllSettings)
            SettingsRow("Providers & models", "API keys, endpoints, fallbacks", Icons.Outlined.Memory, onOpenProviders)
            SettingsRow("Skills", "Installed skills and their switches", Icons.Outlined.Schedule, onOpenSkills)
            SettingsRow("Permissions", "Permission mode, approvals and remembered grants", Icons.Outlined.Splitscreen, onOpenAllSettings)
            Spacer(Modifier.height(Space.xl))

            SectionLabel("MORE")
            Spacer(Modifier.height(Space.sm))
            SettingsRow("Labs & Device", "Local models, Telegram, FTP/SFTP, root & Shizuku and other extras", Icons.Outlined.Memory, onOpenLabs)
            SettingsRow("About Harness Code", "Version, licences and disclaimers", Icons.Outlined.Description, onOpenAbout)
            SettingsRow("Classic Harness UI", "Open the original AndroidHarness interface", Icons.Outlined.Folder, onOpenClassic)
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphTile(icon, size = 34.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = HarnessTheme.palette.text)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = HarnessTheme.palette.muted, maxLines = 2)
        }
        Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = HarnessTheme.palette.faint, modifier = Modifier.size(14.dp))
    }
    Hairline()
}

/* ------------------------------------------------------------ Labs & Device */

/** A row in Labs: an extra that Claude Code does not have, kept and labelled. */
private data class LabEntry(
    val id: String,
    val title: String,
    val body: String,
    val target: String? = null,
)

@Composable
fun HarnessLabsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenAllSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val entries = remember {
        listOf(
            LabEntry("local-models", "Local models", "Run GGUF models on the device, offline, with optional GPU acceleration.", "local-models"),
            LabEntry("9router", "9Router", "A local model router page served from the phone.", "9router"),
            LabEntry("telegram", "Telegram remote control", "Continue real chats from a paired private bot.", "telegram"),
            LabEntry("device", "Terminal & device", "Shizuku, Wireless ADB, root and the Linux toolchain.", "device"),
            LabEntry("transfer", "FTP / SFTP", "Sync workspaces over FTP and SFTP.", "transfer"),
            LabEntry("ssh", "SSH workspaces", "Files, terminal and Git on a remote host.", "ssh"),
            LabEntry("voice", "Voice & speech", "Speech to text and read-aloud engines.", "voice"),
            LabEntry("live", "Live Mode", "Hands-free conversation that can read files and plan."),
            LabEntry("chat-agents", "Chat agents", "Personal assistants with their own instructions and knowledge.", "chat-agents"),
            LabEntry("appearance", "Canvas & glass", "Background image, glass effects and motion blur.", "appearance"),
            LabEntry("rumble", "Rumble haptics", "Haptic pulses as a turn changes step.", "appearance"),
            LabEntry("providers", "Key rotation, proxy & fallbacks", "Rotate API keys, use a proxy, chain fallback models.", "providers"),
            LabEntry("caveman", "Caveman mode", "Short, plain answers from the same engine."),
            LabEntry("automation", "Automations", "Scheduled prompts, now under Scheduled tasks."),
        )
    }
    val visible = entries.filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) }
    Column(Modifier.fillMaxSize().background(HarnessTheme.palette.paper)) {
        HarnessHubHeader(title = "Labs & Device", onBack = onBack)
        Column(Modifier.fillMaxSize().padding(horizontal = Space.gutter)) {
            Text(
                "Everything here is extra: features AndroidHarness has that the Claude apps do not. Nothing was removed — it is grouped here and still searchable in All settings.",
                style = MaterialTheme.typography.bodySmall,
                color = HarnessTheme.palette.muted,
                modifier = Modifier.padding(bottom = Space.md),
            )
            DrawerSearch(query) { query = it }
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                items(visible, key = { it.id }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (entry.target != null) container.pendingSettingsScroll.value = entry.target
                                onOpenAllSettings()
                            }
                            .padding(vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlyphTile(Icons.Outlined.Memory, size = 34.dp)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.bodyMedium, color = HarnessTheme.palette.text)
                            Text(entry.body, style = MaterialTheme.typography.labelSmall, color = HarnessTheme.palette.muted, maxLines = 2)
                        }
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = HarnessTheme.palette.faint, modifier = Modifier.size(14.dp))
                    }
                    Hairline()
                }
            }
        }
    }
}

/* --------------------------------------------------------------------- About */

@Composable
fun HarnessAboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(HarnessTheme.palette.paper)) {
        HarnessHubHeader(title = "About", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter)) {
            Spacer(Modifier.height(Space.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HarnessMark(size = 44.dp)
                Spacer(Modifier.width(Space.lg))
                Column {
                    Text("Harness Code", style = MaterialTheme.typography.displaySmall, color = HarnessTheme.palette.text)
                    Text(
                        "Version ${com.androidharness.app.BuildConfig.VERSION_NAME} (${com.androidharness.app.BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = HarnessTheme.palette.muted,
                    )
                }
            }
            Spacer(Modifier.height(Space.xl))
            HarnessCard(Modifier.fillMaxWidth()) {
                Text(
                    "Harness Code is an unofficial personal project, not affiliated with Anthropic.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HarnessTheme.palette.text,
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    "This app borrows the shape of a terminal coding assistant — a session transcript, tool cards, diffs, permissions and plans — but it is an independent Android client built on the AndroidHarness engine. It contains no code, assets, icons or fonts from Anthropic's apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HarnessTheme.palette.muted,
                )
            }
            Spacer(Modifier.height(Space.md))
            HarnessCard(Modifier.fillMaxWidth()) {
                SectionLabel("ENGINE & LICENCE")
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Built on AndroidHarness by Sanuu7 and contributors, MIT licensed. The original licence and attribution are kept in the repository.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HarnessTheme.palette.muted,
                )
                Spacer(Modifier.height(Space.md))
                SectionLabel("TYPEFACES")
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Newsreader, Inter and JetBrains Mono, all under the SIL Open Font License 1.1. Licence texts ship in assets/licenses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HarnessTheme.palette.muted,
                )
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}
