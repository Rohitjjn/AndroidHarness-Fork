package com.androidharness.app.ui.harness.session

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.androidharness.app.agent.session.FileChange
import com.androidharness.app.agent.session.HarnessSessionState
import com.androidharness.app.agent.session.PendingQuestion
import com.androidharness.app.agent.session.SessionPermission
import com.androidharness.app.agent.session.SessionPhase
import com.androidharness.app.ui.chat.ChatViewModel
import com.androidharness.app.ui.harness.HarnessDrafts
import com.androidharness.app.ui.harness.components.HarnessCard
import com.androidharness.app.ui.harness.components.LiveDot
import com.androidharness.app.ui.harness.components.Pill
import com.androidharness.app.ui.harness.components.PrimaryButton
import com.androidharness.app.ui.harness.components.QuietButton
import com.androidharness.app.ui.harness.theme.HarnessTheme
import com.androidharness.app.ui.harness.theme.Space

/** Panels open as full-screen overlays on a phone and stay readable on a tablet. */
sealed interface SessionPanel {
    data class Diff(val change: FileChange) : SessionPanel
    data class ToolOutput(val activity: com.androidharness.app.agent.session.ToolActivity) : SessionPanel
    data class Rewind(val change: FileChange?) : SessionPanel
    data object Files : SessionPanel
    data object Terminal : SessionPanel
    data object BuildTest : SessionPanel
    data object Preview : SessionPanel
    data object Worktree : SessionPanel
    data object PullRequest : SessionPanel
    data object Hooks : SessionPanel
    data object Permissions : SessionPanel
}

@Composable
fun HarnessSessionRoute(
    viewModel: ChatViewModel,
    container: AppContainer,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
    onOpenSession: (String?) -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val session = rememberChatAgentSession(viewModel)
    val state by session.state.collectAsStateWithLifecycle()
    val chatState by viewModel.state.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf<SessionPanel?>(null) }
    var initialDraft by remember { mutableStateOf(HarnessDrafts.consume()) }
    val listState = rememberLazyListState()

    val actions = remember(state.items.size, state.approval, state.question, state.plan) {
        TranscriptActions(
            onOpenDiff = { change -> panel = SessionPanel.Diff(change) },
            onComment = { change -> panel = SessionPanel.Diff(change) },
            onRewind = { change -> panel = SessionPanel.Rewind(change) },
            onOpenToolOutput = { activity -> panel = SessionPanel.ToolOutput(activity) },
        )
    }

    // Follow the tail while a turn streams; the user's scroll wins otherwise.
    LaunchedEffect(state.items.size, state.busy) {
        if (state.items.isNotEmpty()) {
            val last = state.items.size
            runCatching { listState.animateScrollToItem(last) }
        }
    }

    BackHandler(enabled = panel != null) { panel = null }

    Box(Modifier.fillMaxSize().background(HarnessTheme.palette.paper)) {
        Column(Modifier.fillMaxSize()) {
            SessionTopBar(
                state = state,
                showMenuButton = showMenuButton,
                onOpenDrawer = onOpenDrawer,
                onBack = onBack,
                onOpenPanel = { panel = it },
            )
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "session-header") {
                        if (state.isEmpty) {
                            SessionEmptyState(state)
                        }
                    }
                    items(state.items, key = { it.id }) { item ->
                        TranscriptRow(item, actions)
                    }
                    item(key = "session-tail") {
                        Column {
                            if (state.plan != null) {
                                PlanCard(state.plan!!, onApprove = { session.approvePlan() }, onDiscard = { session.discardPlan() })
                            }
                            if (state.todos.isNotEmpty()) TasksCard(state)
                            if (!state.busy) VerificationCard(state.checks)
                        }
                    }
                }
                // Down-to-latest affordance.
                if (listState.firstVisibleItemIndex > 2) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = Space.md)
                            .size(34.dp)
                            .background(HarnessTheme.palette.surface, RoundedCornerShape(999.dp))
                            .clickable { },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to latest", tint = HarnessTheme.palette.muted, modifier = Modifier.size(20.dp))
                    }
                }
            }

            state.error?.let { error ->
                ErrorBanner(error, onDismiss = { session.dismissError() })
            }
            state.approval?.let { approval ->
                ApprovalCard(
                    approval = approval,
                    onApprove = { always -> session.approve(always) },
                    onReject = { session.reject() },
                    onComment = { comment ->
                        session.commentOnDiff(approval.toolName, comment)
                    },
                )
            }
            state.question?.let { question ->
                QuestionCard(question, onAnswer = { answer -> session.answer(answer) })
            }

            HarnessComposer(
                state = state,
                session = session,
                initialDraft = initialDraft,
                onOpenPermission = { panel = SessionPanel.Permissions },
                actions = ComposerActions(
                    send = { text -> initialDraft = null; session.send(text) },
                    stop = { session.stop() },
                    attachImage = { uri -> viewModel.attachImage(uri) },
                    attachments = chatState.attachments.size + chatState.fileAttachments.size,
                    models = chatState.providers.filter { it.enabled }.map { provider ->
                        ModelChoice(provider.id, provider.name, provider.model)
                    },
                    onSelectModel = { choice ->
                        viewModel.setActiveProvider(choice.providerId)
                        viewModel.selectModel(choice.providerId, choice.model)
                    },
                    cancelQueued = { id -> session.cancelQueued(id) },
                    sendQueuedNow = { id -> session.sendQueuedNow(id) },
                ),
            )
        }

        AnimatedVisibility(visible = panel != null) {
            val current = panel
            if (current != null) {
                SessionPanelHost(
                    panel = current,
                    container = container,
                    viewModel = viewModel,
                    session = session,
                    state = state,
                    onClose = { panel = null },
                    onSendToAgent = { text -> session.send(text); panel = null },
                    onOpenSession = onOpenSession,
                )
            }
        }
    }
}

@Composable
private fun SessionTopBar(
    state: HarnessSessionState,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
    onOpenPanel: (SessionPanel) -> Unit,
) {
    val palette = HarnessTheme.palette
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().statusBarsPadding().background(palette.paper)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = if (showMenuButton) onOpenDrawer else onBack) {
                Icon(
                    if (showMenuButton) Icons.Filled.Menu else Icons.Filled.ArrowBack,
                    contentDescription = if (showMenuButton) "Open menu" else "Back",
                    tint = palette.text,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.projectName != null) {
                        Text(
                            state.projectName,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(Space.xs))
                    }
                    if (state.branch != null) {
                        Text(
                            "· ${state.branch}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = com.androidharness.app.ui.harness.theme.HarnessMono),
                            color = palette.faint,
                            maxLines = 1,
                        )
                    }
                    if (state.busy) {
                        Spacer(Modifier.width(Space.sm))
                        LiveDot(size = 6.dp)
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Session menu", tint = palette.text)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuRow("Diff review", { menu = false; onOpenPanel(SessionPanel.Files) })
                    MenuRow("Files", { menu = false; onOpenPanel(SessionPanel.Files) })
                    MenuRow("Terminal", { menu = false; onOpenPanel(SessionPanel.Terminal) })
                    MenuRow("Build & Test", { menu = false; onOpenPanel(SessionPanel.BuildTest) })
                    MenuRow("Preview", { menu = false; onOpenPanel(SessionPanel.Preview) })
                    MenuRow("Rewind", { menu = false; onOpenPanel(SessionPanel.Rewind(null)) })
                    MenuRow("New worktree", { menu = false; onOpenPanel(SessionPanel.Worktree) })
                    MenuRow("Pull requests", { menu = false; onOpenPanel(SessionPanel.PullRequest) })
                    MenuRow("Hooks", { menu = false; onOpenPanel(SessionPanel.Hooks) })
                    MenuRow("Permissions", { menu = false; onOpenPanel(SessionPanel.Permissions) })
                }
            }
        }
        if (state.branch != null || state.modelLabel != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.md, end = Space.md, bottom = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.branch != null) {
                    Pill(label = state.branch, monospace = true, contentDescription = "Git branch ${state.branch}")
                }
                if (state.modelLabel != null) {
                    Pill(label = state.modelLabel, contentDescription = "Model ${state.modelLabel}")
                }
                Pill(
                    label = state.permission.label,
                    selected = state.permission != SessionPermission.ASK,
                    onClick = { onOpenPanel(SessionPanel.Permissions) },
                    contentDescription = "Permission mode ${state.permission.label}",
                )
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label, style = MaterialTheme.typography.bodyMedium) }, onClick = onClick)
}

@Composable
private fun SessionEmptyState(state: HarnessSessionState) {
    Column(Modifier.fillMaxWidth().padding(top = Space.xxl)) {
        Text(
            "New session",
            style = MaterialTheme.typography.displaySmall,
            color = HarnessTheme.palette.text,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "Ask for a change, a fix or an explanation. The agent works inside the project workspace and every step becomes a card you can inspect.",
            style = MaterialTheme.typography.bodyMedium,
            color = HarnessTheme.palette.muted,
        )
        Spacer(Modifier.height(Space.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Pill(label = "Plan mode", onClick = { }, contentDescription = "Plan mode is available from the composer")
        }
    }
}

@Composable
fun ApprovalCard(
    approval: com.androidharness.app.agent.session.PendingApproval,
    onApprove: (Boolean) -> Unit,
    onReject: () -> Unit,
    onComment: (String) -> Unit,
) {
    val palette = HarnessTheme.palette
    var comment by remember { mutableStateOf("") }
    var commenting by remember { mutableStateOf(false) }
    HarnessCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs),
        fill = palette.surface,
        border = palette.accent.copy(alpha = 0.5f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Approve ${approval.toolName.replace('_', ' ')}?",
                style = MaterialTheme.typography.titleSmall,
                color = palette.text,
                modifier = Modifier.weight(1f),
            )
            Pill(label = "Needs approval", selected = true)
        }
        if (approval.description.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(approval.description, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 3)
        }
        val diff = approval.diff
        if (!diff.isNullOrBlank()) {
            Spacer(Modifier.height(Space.sm))
            Box(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                DiffBody(diff)
            }
        }
        Spacer(Modifier.height(Space.md))
        if (commenting) {
            androidx.compose.material3.OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                placeholder = { Text("Tell the agent what to change instead", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PrimaryButton(
                    "Send comment",
                    onClick = {
                        if (comment.isNotBlank()) {
                            onComment(comment.trim())
                            comment = ""
                            commenting = false
                        }
                    },
                    enabled = comment.isNotBlank(),
                )
                QuietButton("Cancel", onClick = { commenting = false })
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PrimaryButton("Approve", onClick = { onApprove(false) })
                QuietButton("Always allow", onClick = { onApprove(true) })
                QuietButton("Reject", onClick = onReject, tint = palette.danger)
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                "Comment instead",
                style = MaterialTheme.typography.labelSmall,
                color = palette.accentInk,
                modifier = Modifier.clickable { commenting = true }.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
fun QuestionCard(question: PendingQuestion, onAnswer: (String) -> Unit) {
    val palette = HarnessTheme.palette
    var selected by remember(question.id) { mutableStateOf<Set<String>>(emptySet()) }
    HarnessCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs),
        border = palette.borderStrong,
    ) {
        Text(question.question, style = MaterialTheme.typography.bodyLarge, color = palette.text)
        Spacer(Modifier.height(Space.sm))
        for ((index, option) in question.options.withIndex()) {
            val isSelected = option in selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(if (isSelected) palette.accentSoft else palette.sunken, RoundedCornerShape(10.dp))
                    .clickable {
                        if (question.multiSelect) {
                            selected = if (isSelected) selected - option else selected + option
                        } else {
                            onAnswer(option)
                        }
                    }
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(option, style = MaterialTheme.typography.bodyMedium, color = palette.text, modifier = Modifier.weight(1f))
                if (index in question.recommended) {
                    Pill(label = "Suggested", selected = true)
                }
            }
        }
        if (question.multiSelect) {
            Spacer(Modifier.height(Space.sm))
            PrimaryButton(
                "Send selection",
                onClick = { if (selected.isNotEmpty()) onAnswer(selected.joinToString(", ")) },
                enabled = selected.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun PlanCard(plan: String, onApprove: () -> Unit, onDiscard: () -> Unit) {
    val palette = HarnessTheme.palette
    HarnessCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm),
        border = palette.accent.copy(alpha = 0.45f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Plan", style = MaterialTheme.typography.titleSmall, color = palette.text, modifier = Modifier.weight(1f))
            Pill(label = "Awaiting approval", selected = true)
        }
        Spacer(Modifier.height(Space.sm))
        HarnessMarkdownText(plan)
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            PrimaryButton("Approve plan", onClick = onApprove)
            QuietButton("Discard", onClick = onDiscard)
        }
    }
}

@Composable
private fun TasksCard(state: HarnessSessionState) {
    val palette = HarnessTheme.palette
    HarnessCard(modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm)) {
        Text("Tasks", style = MaterialTheme.typography.labelLarge, color = palette.muted)
        Spacer(Modifier.height(Space.xs))
        for (todo in state.todos) {
            val mark = when (todo.status) {
                "COMPLETED" -> "✓"
                "IN_PROGRESS" -> "▸"
                else -> "•"
            }
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mark,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (todo.status == "COMPLETED") palette.success else palette.accent,
                    modifier = Modifier.width(18.dp),
                )
                Text(
                    todo.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (todo.status == "COMPLETED") palette.faint else palette.text,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    val palette = HarnessTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.xs)
            .background(palette.danger.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(error, style = MaterialTheme.typography.bodySmall, color = palette.danger, modifier = Modifier.weight(1f))
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Dismiss error",
            tint = palette.danger,
            modifier = Modifier.size(16.dp).clickable { onDismiss() },
        )
    }
}
