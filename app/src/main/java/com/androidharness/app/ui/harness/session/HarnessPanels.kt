package com.androidharness.app.ui.harness.session

import android.webkit.WebView
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.agent.session.AgentSession
import com.androidharness.app.agent.session.FileChange
import com.androidharness.app.agent.session.HarnessSessionState
import com.androidharness.app.agent.session.SessionPermission
import com.androidharness.app.agent.session.ToolRunStatus
import com.androidharness.app.ui.chat.ChatViewModel
import com.androidharness.app.ui.harness.components.EmptyState
import com.androidharness.app.ui.harness.components.HarnessCard
import com.androidharness.app.ui.harness.components.ListRow
import com.androidharness.app.ui.harness.components.Pill
import com.androidharness.app.ui.harness.components.PrimaryButton
import com.androidharness.app.ui.harness.components.QuietButton
import com.androidharness.app.ui.harness.components.SectionLabel
import com.androidharness.app.ui.harness.theme.HarnessTheme
import com.androidharness.app.ui.harness.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Side panels: full-screen sheets with a header, used for diff review, files,
 * terminal, preview, build & test, rewind, worktrees, pull requests, hooks and
 * permissions. Each one is a small screen with its own back affordance.
 */
@Composable
fun SessionPanelHost(
    panel: SessionPanel,
    container: AppContainer,
    viewModel: ChatViewModel,
    session: AgentSession,
    state: HarnessSessionState,
    onClose: () -> Unit,
    onSendToAgent: (String) -> Unit,
    onOpenSession: (String?) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(HarnessTheme.palette.paper).statusBarsPadding().navigationBarsPadding()) {
        when (panel) {
            is SessionPanel.Diff -> DiffPanel(panel.change, session, onClose, onSendToAgent)
            is SessionPanel.ToolOutput -> ToolOutputPanel(panel.activity, onClose)
            is SessionPanel.Rewind -> RewindPanel(panel.change, state, session, onClose)
            SessionPanel.Files -> FilesPanel(container, state, onClose)
            SessionPanel.Terminal -> TerminalPanel(container, state, onClose)
            SessionPanel.BuildTest -> BuildTestPanel(container, state, onClose, onSendToAgent)
            SessionPanel.Preview -> PreviewPanel(container, state, onClose)
            SessionPanel.Worktree -> WorktreePanel(container, state, onClose, onSendToAgent)
            SessionPanel.PullRequest -> PullRequestPanel(container, state, onClose, onSendToAgent)
            SessionPanel.Hooks -> HooksPanel(container, onClose)
            SessionPanel.Permissions -> PermissionsPanel(state, session, onClose)
        }
    }
}

@Composable
private fun PanelHeader(title: String, onClose: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = "Close panel", tint = HarnessTheme.palette.text)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = HarnessTheme.palette.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
    HorizontalDivider(color = HarnessTheme.palette.border)
}

/* ------------------------------------------------------------- Diff review */

@Composable
private fun DiffPanel(
    change: FileChange,
    session: AgentSession,
    onClose: () -> Unit,
    onSendToAgent: (String) -> Unit,
) {
    val diff by produceState(initialValue = "Loading…", change.id) {
        value = if (change.turnId == null) "This change is no longer attached to a checkpoint, so no diff is available."
        else session.diffFor(change.turnId, change.path)
    }
    var comment by remember(change.id) { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Review · ${change.path}", onClose)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.md),
        ) {
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+${change.additions}", style = MaterialTheme.typography.labelMedium, color = HarnessTheme.palette.diffAdd)
                Spacer(Modifier.width(Space.xs))
                Text("−${change.removals}", style = MaterialTheme.typography.labelMedium, color = HarnessTheme.palette.diffRemove)
            }
            Spacer(Modifier.height(Space.sm))
            DiffBody(diff)
            Spacer(Modifier.height(Space.lg))
            SectionLabel("COMMENT FOR THE AGENT")
            Spacer(Modifier.height(Space.xs))
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                placeholder = { Text("What should change here?", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(Space.sm))
            PrimaryButton(
                "Send review comment",
                enabled = comment.isNotBlank(),
                onClick = {
                    onSendToAgent("Review comment on `${change.path}`:\n${comment.trim()}")
                    comment = ""
                    onClose()
                },
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/* ------------------------------------------------------------ Tool output */

@Composable
private fun ToolOutputPanel(activity: com.androidharness.app.agent.session.ToolActivity, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader(activity.title, onClose)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.md)) {
            SectionLabel("INPUT")
            Spacer(Modifier.height(Space.xs))
            CodeSurface(activity.input.ifBlank { "(none)" })
            Spacer(Modifier.height(Space.md))
            SectionLabel(if (activity.status == ToolRunStatus.FAILED) "OUTPUT · FAILED" else "OUTPUT")
            Spacer(Modifier.height(Space.xs))
            CodeSurface(activity.output.ifBlank { "(no output)" })
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun CodeSurface(text: String, color: androidx.compose.ui.graphics.Color? = null) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = com.androidharness.app.ui.harness.theme.HarnessMono),
        color = color ?: HarnessTheme.palette.code,
        modifier = Modifier
            .fillMaxWidth()
            .background(HarnessTheme.palette.codeBlock, RoundedCornerShape(10.dp))
            .padding(Space.sm),
    )
}

/* ---------------------------------------------------------------- Rewind */

@Composable
private fun RewindPanel(
    change: FileChange?,
    state: HarnessSessionState,
    session: AgentSession,
    onClose: () -> Unit,
) {
    val turnId = change?.turnId
    val preview by produceState<com.androidharness.app.agent.session.RewindInfo?>(initialValue = null, turnId) {
        value = turnId?.let { session.rewindPreview(it) }
    }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Rewind", onClose)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.md)) {
            Spacer(Modifier.height(Space.md))
            if (turnId == null) {
                EmptyState(
                    icon = Icons.Outlined.Memory,
                    title = "Nothing to rewind from here",
                    body = "Open Rewind from a file card in the transcript, or pick a checkpoint below.",
                )
                val checkpoints = state.items.filterIsInstance<FileChange>().mapNotNull { it.turnId }.distinct().takeLast(6)
                for (checkpoint in checkpoints) {
                    ListRow(
                        title = "Turn ${checkpoint.take(8)}",
                        subtitle = "Preview and undo this turn",
                        onClick = { },
                        trailing = { Pill(label = "Turn", monospace = true) },
                    )
                }
                Spacer(Modifier.height(Space.xxl))
                return@Column
            }
            val info = preview
            if (info == null) {
                Text("Loading the checkpoint…", style = MaterialTheme.typography.bodyMedium, color = HarnessTheme.palette.muted)
            } else {
                Text(
                    "Rewinding takes files and messages back to the start of this turn. Checkpoints are kept, so nothing is lost for good.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HarnessTheme.palette.muted,
                )
                Spacer(Modifier.height(Space.md))
                HarnessCard(Modifier.fillMaxWidth()) {
                    Text("${info.files.size} file(s) · ${info.messages} message(s) · ${info.turns} turn(s)", style = MaterialTheme.typography.titleSmall, color = HarnessTheme.palette.text)
                    Spacer(Modifier.height(Space.sm))
                    for (file in info.files) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                file.path,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.androidharness.app.ui.harness.theme.HarnessMono),
                                color = HarnessTheme.palette.text,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            if (file.willDelete) {
                                Pill(label = "deleted", monospace = true)
                            } else {
                                Text("+${file.added}", style = MaterialTheme.typography.labelSmall, color = HarnessTheme.palette.diffAdd)
                                Spacer(Modifier.width(2.dp))
                                Text("−${file.removed}", style = MaterialTheme.typography.labelSmall, color = HarnessTheme.palette.diffRemove)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.lg))
                result?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = HarnessTheme.palette.text)
                    Spacer(Modifier.height(Space.md))
                }
                PrimaryButton(
                    if (busy) "Rewinding…" else "Rewind this turn",
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            result = session.rewind(turnId)
                            busy = false
                        }
                    },
                )
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}


/* ----------------------------------------------------------------- Files */

private data class FileRowUi(val path: String, val size: Long)

@Composable
private fun FilesPanel(container: AppContainer, state: HarnessSessionState, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val files by produceState(initialValue = emptyList<FileRowUi>(), state.sessionId) {
        value = withContext(Dispatchers.IO) {
            val sessionId = state.sessionId ?: return@withContext emptyList()
            runCatching {
                val fs = container.workspace.forSession(sessionId)
                fs.walk("").filter { it.isFile }
                    .take(3000)
                    .map { FileRowUi(it.relPath, it.length) }
                    .toList()
            }.getOrElse { emptyList() }
        }
    }
    var open by remember { mutableStateOf<Pair<String, String>?>(null) }
    Column(Modifier.fillMaxSize()) {
        PanelHeader(open?.first ?: "Files", onClose = { if (open != null) open = null else onClose() })
        val current = open
        if (current != null) {
            Text(
                current.second,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = com.androidharness.app.ui.harness.theme.HarnessMono),
                color = HarnessTheme.palette.code,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.md),
            )
            return@Column
        }
        Column(Modifier.padding(horizontal = Space.md)) {
            com.androidharness.app.ui.harness.components.DrawerSearch(query) { query = it }
        }
        val visible = files.filter { query.isBlank() || it.path.contains(query, true) }
        if (visible.isEmpty()) {
            EmptyState(Icons.Outlined.Folder, "No files", "This session has no workspace files yet.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(visible.take(500), key = { it.path }) { file ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            open = file.path to runCatching {
                                container.workspace.forSession(state.sessionId!!).resolve(file.path).readText().take(200_000)
                            }.getOrElse { "This file could not be read." }
                        }
                        .padding(horizontal = Space.md, vertical = Space.sm),
                ) {
                    Text(
                        file.path,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = com.androidharness.app.ui.harness.theme.HarnessMono),
                        color = HarnessTheme.palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (file.size < 1024) "${file.size} B" else "${file.size / 1024} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = HarnessTheme.palette.faint,
                    )
                }
                HorizontalDivider(color = HarnessTheme.palette.border)
            }
        }
    }
}

/* -------------------------------------------------------------- Terminal */

@Composable
private fun TerminalPanel(container: AppContainer, state: HarnessSessionState, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Terminal", onClose)
        com.androidharness.app.ui.terminal.TerminalScreen(
            container = container,
            onBack = onClose,
            sessionId = state.sessionId,
        )
    }
}

/* ----------------------------------------------------------- Build & Test */

@Composable
private fun BuildTestPanel(
    container: AppContainer,
    state: HarnessSessionState,
    onClose: () -> Unit,
    onSendToAgent: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Build & Test", onClose)
        com.androidharness.app.ui.buildtest.BuildTestScreen(
            container = container,
            onBack = onClose,
            onOpenFile = { _, _ -> },
            onOpenTerminal = onClose,
            onFixWithAgent = { prompt -> onSendToAgent(prompt) },
            sessionId = state.sessionId,
        )
    }
}

/* --------------------------------------------------------------- Preview */

@Composable
private fun PreviewPanel(container: AppContainer, state: HarnessSessionState, onClose: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("http://localhost:3000") }
    var loaded by remember { mutableStateOf<String?>(null) }
    var htmlFiles by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(state.sessionId) {
        if (state.sessionId != null) {
            htmlFiles = withContext(Dispatchers.IO) {
                runCatching {
                    container.workspace.forSession(state.sessionId).walk("")
                        .filter { it.isFile && (it.name.endsWith(".html") || it.name.endsWith(".htm")) }
                        .map { it.relPath }.take(20).toList()
                }.getOrElse { emptyList() }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Preview", onClose)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text("Dev server or file URL", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            )
            QuietButton("Open", onClick = { loaded = url })
        }
        if (htmlFiles.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                for (file in htmlFiles.take(2)) {
                    Pill(label = file.substringAfterLast('/'), onClick = {
                        val path = withContextPath(container, state, file)
                        if (path != null) { url = path; loaded = path }
                    })
                }
            }
        }
        val target = loaded
        if (target == null) {
            EmptyState(
                icon = Icons.Outlined.Description,
                title = "Nothing loaded",
                body = "Start a dev server in Terminal, or open one of the workspace's HTML files. Localhost URLs are served by this device.",
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { web -> if (web.url != target) web.loadUrl(target) },
            )
        }
    }
}

/** file:// address for a workspace file, or null for SAF/SSH workspaces. */
private fun withContextPath(container: AppContainer, state: HarnessSessionState, relPath: String): String? {
    val root = runCatching { container.workspace.forSession(state.sessionId!!).shellRoot }.getOrNull() ?: return null
    val file = java.io.File(root, relPath)
    return "file://" + file.absolutePath
}

/* -------------------------------------------------------------- Worktree */

@Composable
private fun WorktreePanel(
    container: AppContainer,
    state: HarnessSessionState,
    onClose: () -> Unit,
    onSendToAgent: (String) -> Unit,
) {
    var branch by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val existing by produceState(initialValue = emptyList<com.androidharness.app.workspace.WorktreeManager.WorktreeInfo>(), state.sessionId) {
        if (state.sessionId != null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val fs = container.workspace.forSession(state.sessionId)
                    container.worktrees.list(fs)
                }.getOrElse { emptyList() }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Worktrees", onClose)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.md)) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "A worktree is a second checkout of this repository on its own branch. It is added to this session as an editable workspace, so the agent can work there without touching your open files.",
                style = MaterialTheme.typography.bodyMedium,
                color = HarnessTheme.palette.muted,
            )
            Spacer(Modifier.height(Space.md))
            OutlinedTextField(
                value = branch,
                onValueChange = { branch = it },
                singleLine = true,
                label = { Text("Branch", style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text("feature/refactor-parser") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(Space.sm))
            PrimaryButton(
                if (busy) "Creating…" else "Create worktree",
                enabled = branch.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        val fs = runCatching { container.workspace.forSession(state.sessionId!!) }.getOrNull()
                        val result = if (fs == null) null else container.worktrees.create(fs, branch.trim(), state.sessionId)
                        message = result?.message ?: "No workspace is attached to this session."
                        busy = false
                    }
                },
            )
            message?.let {
                Spacer(Modifier.height(Space.md))
                HarnessCard(Modifier.fillMaxWidth()) {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = HarnessTheme.palette.text)
                }
            }
            Spacer(Modifier.height(Space.lg))
            SectionLabel("EXISTING WORKTREES")
            Spacer(Modifier.height(Space.xs))
            if (existing.isEmpty()) {
                Text("None yet.", style = MaterialTheme.typography.bodySmall, color = HarnessTheme.palette.muted)
            }
            for (tree in existing) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tree.branch ?: "(detached)",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = com.androidharness.app.ui.harness.theme.HarnessMono),
                            color = HarnessTheme.palette.text,
                        )
                        Text(
                            tree.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = HarnessTheme.palette.faint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "Remove",
                        style = MaterialTheme.typography.labelSmall,
                        color = HarnessTheme.palette.danger,
                        modifier = Modifier
                            .clickable {
                                busy = true
                                scope.launch {
                                    val fs = runCatching { container.workspace.forSession(state.sessionId!!) }.getOrNull()
                                    message = if (fs == null) "No workspace." else container.worktrees.remove(fs, tree.path).message
                                    busy = false
                                }
                            }
                            .padding(4.dp),
                    )
                }
                HorizontalDivider(color = HarnessTheme.palette.border)
            }
            Spacer(Modifier.height(Space.md))
            QuietButton(
                "Ask the agent to use a worktree",
                onClick = {
                    onSendToAgent(
                        "Create a git worktree on a new branch for the next task with worktree_create, " +
                            "then make the change there and tell me the worktree path.",
                    )
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/* ---------------------------------------------------------- Pull requests */

@Composable
private fun PullRequestPanel(
    container: AppContainer,
    state: HarnessSessionState,
    onClose: () -> Unit,
    onSendToAgent: (String) -> Unit,
) {
    var status by remember { mutableStateOf<com.androidharness.app.github.PullRequestMonitor.Status?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.sessionId) {
        if (state.sessionId == null) return@LaunchedEffect
        busy = true
        error = null
        status = withContext(Dispatchers.IO) {
            runCatching {
                val token = container.github.accessToken() ?: error("Connect GitHub in Settings first.")
                val fs = container.workspace.forSession(state.sessionId)
                container.pullRequestMonitor.forWorkspace(fs, token)
            }.getOrElse {
                error = it.message ?: "Could not reach GitHub."
                null
            }
        }
        busy = false
    }
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Pull request", onClose, trailing = {
            Text(
                "Refresh",
                style = MaterialTheme.typography.labelMedium,
                color = HarnessTheme.palette.accentInk,
                modifier = Modifier.clickable { reload++; busy = true }.padding(Space.md),
            )
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.md)) {
            Spacer(Modifier.height(Space.md))
            val current = status
            when {
                busy -> Text("Checking GitHub…", style = MaterialTheme.typography.bodyMedium, color = HarnessTheme.palette.muted)
                error != null -> {
                    Text(error!!, style = MaterialTheme.typography.bodyMedium, color = HarnessTheme.palette.danger)
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "Connect GitHub from Settings, then refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HarnessTheme.palette.muted,
                    )
                }
                current == null -> EmptyState(
                    icon = Icons.Outlined.Description,
                    title = "No open pull request",
                    body = "This branch has no open PR on GitHub. Ask the agent to open one, or push the branch first.",
                    action = {
                        PrimaryButton("Ask the agent to open a PR", onClick = {
                            onSendToAgent("Open a pull request for the current branch, using the pending changes as the summary.")
                            onClose()
                        })
                    },
                )
                else -> {
                    HarnessCard(Modifier.fillMaxWidth()) {
                        Text("#${current.number} ${current.title}", style = MaterialTheme.typography.titleSmall, color = HarnessTheme.palette.text)
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "${current.branch} → ${current.base}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = com.androidharness.app.ui.harness.theme.HarnessMono),
                            color = HarnessTheme.palette.muted,
                        )
                        Spacer(Modifier.height(Space.sm))
                        Pill(
                            label = current.summary,
                            selected = current.failing.isNotEmpty(),
                        )
                        Spacer(Modifier.height(Space.md))
                        for (check in current.checks) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(
                                    when (check.state) {
                                        com.androidharness.app.github.PullRequestMonitor.CheckState.PASSED -> "✓"
                                        com.androidharness.app.github.PullRequestMonitor.CheckState.FAILED -> "✕"
                                        com.androidharness.app.github.PullRequestMonitor.CheckState.PENDING -> "•"
                                        else -> "–"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (check.state) {
                                        com.androidharness.app.github.PullRequestMonitor.CheckState.PASSED -> HarnessTheme.palette.success
                                        com.androidharness.app.github.PullRequestMonitor.CheckState.FAILED -> HarnessTheme.palette.danger
                                        else -> HarnessTheme.palette.muted
                                    },
                                    modifier = Modifier.width(18.dp),
                                )
                                Text(
                                    check.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HarnessTheme.palette.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (current.failing.isNotEmpty()) {
                        Spacer(Modifier.height(Space.md))
                        PrimaryButton("Fix failing checks", onClick = {
                            onSendToAgent(com.androidharness.app.github.fixFailingChecksPrompt(current))
                            onClose()
                        })
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "The agent proposes a fix first; approvals still apply.",
                            style = MaterialTheme.typography.labelSmall,
                            color = HarnessTheme.palette.faint,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/* ----------------------------------------------------------------- Hooks */

@Composable
private fun HooksPanel(container: AppContainer, onClose: () -> Unit) {
    val revision by container.pluginManager.revision.collectAsStateWithLifecycle()
    val installed = remember(revision) { runCatching { container.pluginManager.store.list() }.getOrElse { emptyList() } }
    // Hooks are declared in the manifest on disk, so read them from there.
    val hooks = remember(revision) {
        installed.filter { it.enabled }.flatMap { plugin ->
            val root = container.pluginManager.store.directory(plugin)
            val contents = runCatching { com.androidharness.app.plugins.PluginManifest.inspect(root) }.getOrNull()
            val names = (contents?.hooks.orEmpty() + contents?.commandHooks.orEmpty()).distinct()
            names.map { name -> plugin.name to name }
        }
    }
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Hooks", onClose)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.md)) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "Hooks come from installed plugins and run around tool calls. This list is read-only: turn a plugin off in Settings to stop its hooks.",
                style = MaterialTheme.typography.bodyMedium,
                color = HarnessTheme.palette.muted,
            )
            Spacer(Modifier.height(Space.md))
            if (hooks.isEmpty()) {
                EmptyState(
                    Icons.Outlined.Memory,
                    "No hooks active",
                    if (installed.isEmpty()) "Install a plugin that declares hooks to see them here."
                    else "The installed plugins do not declare any hooks.",
                )
            }
            for ((pluginName, hook) in hooks) {
                HarnessCard(Modifier.fillMaxWidth().padding(bottom = Space.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(hook, style = MaterialTheme.typography.titleSmall.copy(fontFamily = com.androidharness.app.ui.harness.theme.HarnessMono), color = HarnessTheme.palette.text, modifier = Modifier.weight(1f))
                        Pill(label = pluginName, selected = true)
                    }
                }
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/* ----------------------------------------------------------- Permissions */

@Composable
private fun PermissionsPanel(state: HarnessSessionState, session: AgentSession, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Permissions", onClose)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.md)) {
            Spacer(Modifier.height(Space.sm))
            for (level in SessionPermission.entries) {
                val selected = state.permission == level
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(
                            if (selected) HarnessTheme.palette.accentSoft else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { session.setPermission(level); onClose() }
                        .padding(Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(level.label, style = MaterialTheme.typography.titleSmall, color = HarnessTheme.palette.text)
                        Text(level.sessionLabel, style = MaterialTheme.typography.bodySmall, color = HarnessTheme.palette.muted)
                    }
                    if (selected) Pill(label = "current", selected = true)
                }
            }
            Spacer(Modifier.height(Space.lg))
            Text(
                "Plan mode is the third option: the agent may read and search, then hands you a plan to approve.",
                style = MaterialTheme.typography.labelSmall,
                color = HarnessTheme.palette.faint,
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}
