package com.androidharness.app.ui.harness.session

import com.androidharness.app.agent.AgentMode
import com.androidharness.app.agent.PermissionMode
import com.androidharness.app.agent.session.AssistantTurn
import com.androidharness.app.agent.session.CheckResult
import com.androidharness.app.agent.session.FileChange
import com.androidharness.app.agent.session.HarnessSessionState
import com.androidharness.app.agent.session.Notice
import com.androidharness.app.agent.session.PendingApproval
import com.androidharness.app.agent.session.PendingQuestion
import com.androidharness.app.agent.session.QueuedMessage
import com.androidharness.app.agent.session.SessionPermission
import com.androidharness.app.agent.session.SessionPhase
import com.androidharness.app.agent.session.ThinkingBlock
import com.androidharness.app.agent.session.TodoRow
import com.androidharness.app.agent.session.ToolActivity
import com.androidharness.app.agent.session.ToolRunStatus
import com.androidharness.app.agent.session.TranscriptItem
import com.androidharness.app.agent.session.UserTurn
import com.androidharness.app.core.ChatMessage
import com.androidharness.app.core.Role
import com.androidharness.app.core.ToolCallData
import com.androidharness.app.ui.chat.ChatUiState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure ChatUiState → HarnessSessionState mapping.
 *
 * Keeping this a plain function (no Android, no engine, no flows) is what makes
 * the new transcript testable: HarnessSessionMapperTest feeds it hand-built
 * ChatUiState values and asserts the exact rows the UI will draw.
 */
object HarnessSessionMapper {

    /** Everything the session screen needs that does not live in ChatUiState. */
    data class Extras(
        val projectName: String? = null,
        val branch: String? = null,
        val workspacePath: String? = null,
        val canRewind: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun map(source: ChatUiState, extras: Extras = Extras()): HarnessSessionState {
        val argsByCall = HashMap<String, ToolCallData>()
        source.messages.forEach { message ->
            if (message.role == Role.ASSISTANT) message.toolCalls.forEach { argsByCall[it.id] = it }
        }
        source.runningCalls.forEach { argsByCall[it.id] = it }
        val resultsByCall = HashMap<String, ChatMessage>()
        source.messages.forEach { message ->
            if (message.role == Role.TOOL && message.toolCallId != null) resultsByCall[message.toolCallId] = message
        }

        val items = ArrayList<TranscriptItem>(source.messages.size + 4)
        val seenToolIds = HashSet<String>()
        for (message in source.messages) {
            when (message.role) {
                Role.USER -> items += UserTurn(
                    id = message.id ?: "user-${items.size}",
                    text = message.text,
                    attachments = message.images.size + if (message.artifact != null) 1 else 0,
                )

                Role.ASSISTANT -> {
                    val base = message.id ?: "assistant-${items.size}"
                    if (message.thinking.isNotBlank()) {
                        items += ThinkingBlock(
                            id = "$base-thinking",
                            text = message.thinking,
                            durationMs = message.thinkingMs,
                        )
                    }
                    if (message.text.isNotBlank()) {
                        items += AssistantTurn(id = base, text = message.text)
                    }
                    message.toolCalls.forEach { call ->
                        seenToolIds += call.id
                        items += toolActivity(call, resultsByCall[call.id])
                        items += fileChanges(call, message.turnId)
                    }
                }

                Role.TOOL -> {
                    val callId = message.toolCallId
                    // Results whose call is not in the loaded window still deserve a row.
                    if (callId == null || callId !in seenToolIds) {
                        val call = callId?.let { argsByCall[it] }
                        if (call != null) {
                            seenToolIds += call.id
                            items += toolActivity(call, message)
                            items += fileChanges(call, message.turnId)
                        } else {
                            items += ToolActivity(
                                id = callId ?: "tool-${items.size}",
                                name = message.toolName ?: "tool",
                                title = describeTool(message.toolName ?: "tool", null).first,
                                summary = describeTool(message.toolName ?: "tool", null).second,
                                status = if (message.isError) ToolRunStatus.FAILED else ToolRunStatus.DONE,
                                output = message.text,
                            )
                        }
                    }
                }

                Role.SYSTEM -> Unit
            }
        }

        source.compactionNote?.takeIf { it.isNotBlank() }?.let {
            items += Notice(id = "notice-compaction", text = it)
        }

        // Live rows: the engine holds them outside the message list until commit.
        source.streamingThinking?.takeIf { it.isNotBlank() }?.let {
            items += ThinkingBlock(id = "live-thinking", text = it, durationMs = source.thinkingTiming.durationMs(com.androidharness.app.agent.ThinkingTiming.now()), streaming = true)
        }
        source.streamingText?.takeIf { it.isNotBlank() }?.let {
            items += AssistantTurn(id = source.streamingMessageId ?: "live-text", text = it, streaming = true)
        }
        source.runningCalls.forEach { call ->
            if (call.id !in seenToolIds) {
                seenToolIds += call.id
                items += toolActivity(call, null).copy(status = ToolRunStatus.RUNNING)
            }
        }

        return HarnessSessionState(
            sessionId = source.sessionId,
            title = source.sessionTitle.ifBlank { "New session" },
            projectName = extras.projectName ?: source.workspaceName.takeIf { it.isNotBlank() },
            branch = extras.branch,
            modelLabel = source.effectiveModel ?: source.activeProvider?.name,
            phase = phase(source),
            items = items,
            checks = checks(source, argsByCall),
            todos = source.todos.map { TodoRow(it.content, it.status.name) },
            queue = source.taskControl.queue.map { QueuedMessage(it.id, it.text) },
            approval = source.pendingApproval?.let { request ->
                PendingApproval(
                    toolName = request.call.name,
                    description = request.toolDescription,
                    grantKey = request.grantKey,
                    diff = request.diffPreview,
                    input = request.call.argumentsJson,
                )
            },
            question = source.pendingQuestion?.let { request ->
                PendingQuestion(
                    id = request.callId,
                    question = request.question,
                    options = request.options,
                    multiSelect = request.multiSelect,
                    recommended = request.recommended,
                )
            },
            plan = source.pendingPlan,
            planMode = source.mode == AgentMode.PLAN,
            permission = permission(source),
            error = source.error,
            contextUsed = source.contextUsed,
            contextMax = source.maxContextTokens,
            busy = source.busy,
            loading = source.isLoadingMessages,
            canRewind = extras.canRewind && source.turnsWithCheckpoints.isNotEmpty(),
            workspacePath = extras.workspacePath,
        )
    }

    private fun phase(source: ChatUiState): SessionPhase = when {
        source.error != null -> SessionPhase.ERROR
        source.pendingApproval != null -> SessionPhase.WAITING_APPROVAL
        source.pendingQuestion != null -> SessionPhase.WAITING_ANSWER
        source.streamingThinking != null -> SessionPhase.THINKING
        source.runningCalls.isNotEmpty() -> SessionPhase.TOOL
        source.streamingText != null -> SessionPhase.RESPONDING
        source.busy -> SessionPhase.RESPONDING
        source.messages.isEmpty() -> SessionPhase.EMPTY
        else -> SessionPhase.IDLE
    }

    private fun permission(source: ChatUiState): SessionPermission = when {
        source.mode == AgentMode.PLAN -> SessionPermission.PLAN
        source.permissionMode == PermissionMode.FULL_ACCESS -> SessionPermission.FULL_ACCESS
        source.permissionMode == PermissionMode.FULL_AUTO -> SessionPermission.AUTO_EDIT
        else -> SessionPermission.ASK
    }

    private fun toolActivity(call: ToolCallData, result: ChatMessage?): ToolActivity {
        val args = parseArgs(call.argumentsJson)
        val (title, summary) = describeTool(call.name, args)
        val status = when {
            result == null -> ToolRunStatus.RUNNING
            result.isError -> ToolRunStatus.FAILED
            else -> ToolRunStatus.DONE
        }
        return ToolActivity(
            id = call.id,
            name = call.name,
            title = title,
            summary = summary,
            status = status,
            input = prettyArgs(call.argumentsJson),
            output = result?.text.orEmpty(),
            additions = call.codeChanges.sumOf { it.added },
            removals = call.codeChanges.sumOf { it.removed },
            workspace = call.workspaceName,
        )
    }

    private fun fileChanges(call: ToolCallData, turnId: String?): List<TranscriptItem> =
        call.codeChanges
            .filter { it.path.isNotBlank() && (it.added > 0 || it.removed > 0) }
            .map { change ->
                FileChange(
                    id = "${call.id}:${change.path}",
                    path = change.path,
                    additions = change.added,
                    removals = change.removed,
                    turnId = turnId,
                )
            }

    /**
     * Build/test/lint commands this session ran, in order. A later failed run
     * of the same command wins, so the card reflects the current truth.
     */
    private fun checks(source: ChatUiState, argsByCall: Map<String, ToolCallData>): List<CheckResult> {
        val order = ArrayList<String>()
        val byCommand = LinkedHashMap<String, CheckResult>()
        source.messages.forEach { message ->
            if (message.role != Role.TOOL) return@forEach
            val call = message.toolCallId?.let { argsByCall[it] } ?: return@forEach
            if (call.name != "shell" && call.name != "shell_background") return@forEach
            val command = parseArgs(call.argumentsJson)["command"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (!isCheckCommand(command)) return@forEach
            val label = checkLabel(command)
            if (label !in byCommand) order += label
            byCommand[label] = CheckResult(
                id = call.id,
                label = label,
                command = command,
                passed = if (message.isError) false else true,
            )
        }
        return order.mapNotNull { byCommand[it] }
    }

    private val checkPattern = Regex(
        "(?i)\\b(gradle|gradlew|npm|pnpm|yarn|pytest|jest|vitest|mvn|cargo|go test|make|ctest|eslint|ktlint|detekt|tsc|flutter test)\\b.*" +
            "(test|build|check|lint|assemble|compile|verify|run)\\b|\\btest(s)?\\b"
    )

    fun isCheckCommand(command: String): Boolean = command.isNotBlank() && checkPattern.containsMatchIn(command)

    /** Short label for a verification row: the tool plus its first target. */
    fun checkLabel(command: String): String {
        val firstLine = command.lineSequence().firstOrNull()?.trim().orEmpty()
        val cleaned = firstLine.removePrefix("./").replace(Regex("\\s+"), " ")
        return if (cleaned.length <= 48) cleaned else cleaned.take(45) + "…"
    }

    /** Human title + one-line summary per tool, independent of the tool layer. */
    fun describeTool(name: String, args: Map<String, kotlinx.serialization.json.JsonElement>?): Pair<String, String> {
        fun arg(key: String): String? =
            args?.get(key)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }?.takeIf { it.isNotBlank() }
        val path = arg("path") ?: arg("file_path") ?: arg("file")
        return when (name) {
            "write_file" -> "Write" to (path ?: "")
            "edit_file", "multi_edit", "apply_patch" -> "Edit" to (path ?: "")
            "read_file" -> "Read" to (path ?: "")
            "list_dir" -> "List" to (path ?: "workspace")
            "grep" -> "Search" to (arg("pattern")?.let { "“$it”" } ?: "")
            "search_files", "glob" -> "Find" to (arg("pattern") ?: arg("query") ?: "")
            "shell", "shell_background" -> "Run" to (arg("command")?.lineSequence()?.firstOrNull()?.take(72) ?: "")
            "git_status" -> "Git status" to ""
            "git_diff" -> "Git diff" to (path ?: "")
            "git_commit" -> "Commit" to (arg("message")?.lineSequence()?.firstOrNull()?.take(64) ?: "")
            "git_log" -> "Git log" to ""
            "task" -> "Subagent" to (arg("description") ?: arg("prompt")?.take(64) ?: "")
            "web_search" -> "Search the web" to (arg("query") ?: "")
            "web_fetch" -> "Read page" to (arg("url") ?: "")
            "todo_write" -> "Update tasks" to ""
            "ask_user", "ask" -> "Ask you" to (arg("question") ?: "")
            "worktree_create" -> "New worktree" to (arg("branch") ?: "")
            "worktree_remove" -> "Remove worktree" to (arg("path") ?: arg("branch") ?: "")
            "create_pull_request" -> "Open PR" to (arg("title") ?: "")
            else -> name.replace('_', ' ').replaceFirstChar { it.uppercase() } to
                (path ?: arg("command")?.take(64) ?: "")
        }
    }

    private fun parseArgs(raw: String): Map<String, kotlinx.serialization.json.JsonElement> =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()?.toMap() ?: emptyMap()

    /** Pretty-print tool input for the expanded card without throwing on junk. */
    fun prettyArgs(raw: String): String {
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            ?: return raw
        return runCatching {
            Json { prettyPrint = true; ignoreUnknownKeys = true }.encodeToString(
                kotlinx.serialization.json.JsonElement.serializer(),
                element,
            )
        }.getOrDefault(raw)
    }
}
