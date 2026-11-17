package com.androidharness.app.agent.session

import kotlinx.coroutines.flow.StateFlow

/**
 * The UI ⇄ engine boundary.
 *
 * The Harness Code UI never touches [com.androidharness.app.agent.RunManager],
 * the provider layer or the tool registry. It reads one [HarnessSessionState]
 * stream and calls the handful of intents on [AgentSession]. Everything that
 * crosses this line is engine-neutral, so the transcript renderer can be
 * previewed and unit-tested without an engine, and the engine can keep
 * evolving without the screens noticing.
 *
 * See docs/harness-code/ARCHITECTURE.md for the full layering rules.
 */
interface AgentSession {
    /** The single state stream the session UI renders. */
    val state: StateFlow<HarnessSessionState>

    /** Queues or starts a user turn. Returns false when the draft cannot be sent. */
    fun send(text: String): Boolean

    /** Stops the running turn; the engine keeps the partial transcript. */
    fun stop()

    /** Answers a pending tool approval. */
    fun approve(always: Boolean)
    fun reject()

    /** Answers a pending agent question. */
    fun answer(text: String)

    fun setPermission(level: SessionPermission)
    fun setPlanMode(enabled: Boolean)

    /** Approves or discards the plan the agent proposed in Plan mode. */
    fun approvePlan()
    fun discardPlan()

    fun cancelQueued(id: String)
    fun sendQueuedNow(id: String)
    fun dismissError()

    /** Sends an inline diff review comment back to the agent as a user turn. */
    fun commentOnDiff(path: String, comment: String) {
        send("Review comment on `$path`:\n$comment")
    }

    /** Read-only diff of one file at the end of [turnId]. */
    suspend fun diffFor(turnId: String, path: String): String

    /** What Rewind would change for [turnId], or null when it is unavailable. */
    suspend fun rewindPreview(turnId: String): RewindInfo?

    /** Performs the rewind and returns a user-facing summary. */
    suspend fun rewind(turnId: String): String
}

/** Roll-up of a rewind, engine-free. */
data class RewindInfo(
    val files: List<RewindFile>,
    val messages: Int,
    val turns: Int,
)

data class RewindFile(
    val path: String,
    val added: Long,
    val removed: Long,
    val willDelete: Boolean,
)

/** Permission ladder shown by the composer chip. */
enum class SessionPermission(val label: String, val sessionLabel: String) {
    ASK("Ask", "Ask before edits and commands"),
    AUTO_EDIT("Auto-edit", "Edit files freely, ask before commands"),
    PLAN("Plan", "Read only, propose a plan"),
    FULL_ACCESS("Full access", "No approvals, sandbox off for this chat"),
}

enum class SessionPhase { EMPTY, IDLE, THINKING, RESPONDING, TOOL, WAITING_APPROVAL, WAITING_ANSWER, ERROR }

enum class ToolRunStatus { RUNNING, DONE, FAILED }

/**
 * One row in the transcript. Items are produced by a pure mapper from the
 * engine state, so ordering and grouping are testable.
 */
sealed interface TranscriptItem {
    val id: String
}

data class UserTurn(
    override val id: String,
    val text: String,
    val attachments: Int = 0,
) : TranscriptItem

data class AssistantTurn(
    override val id: String,
    val text: String,
    val streaming: Boolean = false,
) : TranscriptItem

data class ThinkingBlock(
    override val id: String,
    val text: String,
    val durationMs: Long = 0,
    val streaming: Boolean = false,
) : TranscriptItem

data class ToolActivity(
    override val id: String,
    val name: String,
    val title: String,
    val summary: String,
    val status: ToolRunStatus,
    val input: String = "",
    val output: String = "",
    val additions: Int = 0,
    val removals: Int = 0,
    val workspace: String? = null,
) : TranscriptItem

/** A file the agent changed in a turn, expandable to an inline diff. */
data class FileChange(
    override val id: String,
    val path: String,
    val additions: Int,
    val removals: Int,
    val turnId: String?,
) : TranscriptItem

/** A short engine notice rendered as a quiet line (compaction, model switch…). */
data class Notice(
    override val id: String,
    val text: String,
) : TranscriptItem

data class PendingApproval(
    val toolName: String,
    val description: String,
    val grantKey: String,
    val diff: String?,
    val input: String,
)

data class PendingQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val multiSelect: Boolean,
    val recommended: List<Int>,
)

data class QueuedMessage(val id: String, val text: String)

/** A build/test/lint command seen in this session and how it ended. */
data class CheckResult(
    val id: String,
    val label: String,
    val command: String,
    val passed: Boolean?,
)

data class TodoRow(val text: String, val status: String)

data class HarnessSessionState(
    val sessionId: String? = null,
    val title: String = "New session",
    val projectName: String? = null,
    val branch: String? = null,
    val modelLabel: String? = null,
    val phase: SessionPhase = SessionPhase.EMPTY,
    val items: List<TranscriptItem> = emptyList(),
    val checks: List<CheckResult> = emptyList(),
    val todos: List<TodoRow> = emptyList(),
    val queue: List<QueuedMessage> = emptyList(),
    val approval: PendingApproval? = null,
    val question: PendingQuestion? = null,
    val plan: String? = null,
    val planMode: Boolean = false,
    val permission: SessionPermission = SessionPermission.ASK,
    val error: String? = null,
    val contextUsed: Int = 0,
    val contextMax: Int = 1_000_000,
    val busy: Boolean = false,
    val loading: Boolean = false,
    val canRewind: Boolean = false,
    val workspacePath: String? = null,
) {
    /** 0..1 fill of the context ring. */
    val contextFraction: Float
        get() = if (contextMax <= 0) 0f else (contextUsed.toFloat() / contextMax.toFloat()).coerceIn(0f, 1f)

    val isEmpty: Boolean get() = items.isEmpty() && !loading
}
