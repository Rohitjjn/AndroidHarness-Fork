package com.androidharness.app.ui.harness.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.androidharness.app.agent.AgentMode
import com.androidharness.app.agent.PermissionMode
import com.androidharness.app.agent.session.AgentSession
import com.androidharness.app.agent.session.HarnessSessionState
import com.androidharness.app.agent.session.RewindFile
import com.androidharness.app.agent.session.RewindInfo
import com.androidharness.app.agent.session.SessionPermission
import com.androidharness.app.ui.chat.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The only class in the new UI that knows ChatViewModel exists.
 *
 * Every method is a one-line delegation; nothing here changes engine behaviour.
 * The permission ladder is mapped onto the existing engine primitives:
 *
 *   Ask          → PermissionMode.CONFIRM_RISKY, AgentMode.ACT
 *   Auto-edit    → PermissionMode.FULL_AUTO,     AgentMode.ACT
 *   Plan         → AgentMode.PLAN (approvals unchanged)
 *   Full access  → PermissionMode.FULL_ACCESS,   AgentMode.ACT
 *
 * CONFIRM_ALL has no Claude Code equivalent and maps back to "Ask"; switching
 * to Ask therefore uses CONFIRM_RISKY, which keeps unsafe commands gated.
 */
class ChatAgentSession(
    private val viewModel: ChatViewModel,
    private val extras: StateFlow<HarnessSessionMapper.Extras>,
    scope: CoroutineScope,
) : AgentSession {

    override val state: StateFlow<HarnessSessionState> =
        combine(viewModel.state, extras) { chat, extra -> HarnessSessionMapper.map(chat, extra) }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(5_000),
                HarnessSessionMapper.map(viewModel.state.value),
            )

    override fun send(text: String): Boolean = viewModel.send(text)

    override fun stop() = viewModel.stop()

    override fun approve(always: Boolean) = viewModel.approve(always)

    override fun reject() = viewModel.deny()

    override fun answer(text: String) = viewModel.answerQuestion(text)

    override fun setPermission(level: SessionPermission) {
        when (level) {
            SessionPermission.ASK -> {
                if (viewModel.state.value.mode == AgentMode.PLAN) viewModel.setMode(AgentMode.ACT)
                viewModel.setPermissionMode(PermissionMode.CONFIRM_RISKY)
            }
            SessionPermission.AUTO_EDIT -> {
                if (viewModel.state.value.mode == AgentMode.PLAN) viewModel.setMode(AgentMode.ACT)
                viewModel.setPermissionMode(PermissionMode.FULL_AUTO)
            }
            SessionPermission.PLAN -> {
                viewModel.setMode(AgentMode.PLAN)
            }
            SessionPermission.FULL_ACCESS -> {
                if (viewModel.state.value.mode == AgentMode.PLAN) viewModel.setMode(AgentMode.ACT)
                viewModel.setPermissionMode(PermissionMode.FULL_ACCESS)
            }
        }
    }

    override fun setPlanMode(enabled: Boolean) {
        viewModel.setMode(if (enabled) AgentMode.PLAN else AgentMode.ACT)
    }

    override fun approvePlan() = viewModel.executePendingPlan()

    override fun discardPlan() = viewModel.discardPendingPlan()

    /**
     * The engine only exposes "cancel the queue head" today, so a non-head id is
     * forwarded anyway; the UI only offers cancel on the first row and labels it
     * that way rather than pretending per-row cancellation exists.
     */
    override fun cancelQueued(id: String) = viewModel.cancelQueuedMessage()

    override fun sendQueuedNow(id: String) = viewModel.sendQueuedNow(id)

    override fun dismissError() = viewModel.dismissError()

    override suspend fun diffFor(turnId: String, path: String): String =
        runCatching { viewModel.fileDiff(turnId, path) }.getOrElse { it.message ?: "Diff unavailable" }

    override suspend fun rewindPreview(turnId: String): RewindInfo? {
        val preview = runCatching { viewModel.rewindPreview(turnId) }.getOrNull() ?: return null
        return RewindInfo(
            files = preview.files.map {
                RewindFile(path = it.relPath, added = it.added, removed = it.removed, willDelete = it.willBeDeleted)
            },
            messages = preview.messagesDeleted,
            turns = preview.turns,
        )
    }

    override suspend fun rewind(turnId: String): String = viewModel.performRewind(turnId)
}

/**
 * Builds an [AgentSession] for a ChatViewModel and keeps the extras (project
 * name, git branch, workspace path) in sync with the open conversation.
 */
@Composable
fun rememberChatAgentSession(viewModel: ChatViewModel): AgentSession {
    val scope = rememberCoroutineScope()
    val extras = remember(viewModel) { MutableStateFlow(HarnessSessionMapper.Extras()) }
    val sessionId = viewModel.state.value.sessionId
    LaunchedEffect(viewModel, sessionId) {
        extras.value = HarnessSessionMapper.Extras()
        if (sessionId == null) return@LaunchedEffect
        extras.value = workspaceExtras(viewModel, sessionId)
    }
    val session = remember(viewModel) { ChatAgentSession(viewModel, extras, scope) }
    return session
}

/**
 * Branch name for the header chip. Reads .git/HEAD directly (a worktree's .git
 * is a file pointing at the real gitdir), so it costs nothing and never runs a
 * shell command. Returns null outside a git checkout.
 */
internal fun readGitBranch(displayPath: String): String? = runCatching {
    var gitDir = File(displayPath, ".git")
    if (gitDir.isFile) {
        val pointer = gitDir.readText().lineSequence()
            .firstOrNull { it.startsWith("gitdir:") }
            ?.removePrefix("gitdir:")?.trim()
            ?: return null
        gitDir = if (File(pointer).isAbsolute) File(pointer) else File(displayPath, pointer)
    }
    val head = File(gitDir, "HEAD")
    if (!head.isFile) return null
    val line = head.readText().trim()
    when {
        line.startsWith("ref: refs/heads/") -> line.removePrefix("ref: refs/heads/").ifBlank { null }
        line.startsWith("ref: ") -> line.removePrefix("ref: ").substringAfterLast('/')
        line.length >= 7 -> line.take(7) // detached HEAD
        else -> null
    }
}.getOrNull()

/** Bridges the suspending workspace lookups for callers outside composition. */
internal suspend fun workspaceExtras(viewModel: ChatViewModel, sessionId: String): HarnessSessionMapper.Extras =
    withContext(Dispatchers.IO) {
        val manager = viewModel.container.workspace
        val project = runCatching { manager.projectForSession(sessionId) }.getOrNull()
        val displayPath = runCatching { manager.forSession(sessionId).displayPath }.getOrNull()
        HarnessSessionMapper.Extras(
            projectName = project?.name,
            branch = displayPath?.let { readGitBranch(it) },
            workspacePath = displayPath,
            canRewind = true,
        )
    }
