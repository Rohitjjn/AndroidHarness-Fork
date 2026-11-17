package com.androidharness.app.ui.harness

import com.androidharness.app.agent.AgentMode
import com.androidharness.app.agent.PermissionMode
import com.androidharness.app.agent.session.AssistantTurn
import com.androidharness.app.agent.session.FileChange
import com.androidharness.app.agent.session.SessionPermission
import com.androidharness.app.agent.session.SessionPhase
import com.androidharness.app.agent.session.ThinkingBlock
import com.androidharness.app.agent.session.ToolActivity
import com.androidharness.app.agent.session.ToolRunStatus
import com.androidharness.app.agent.session.UserTurn
import com.androidharness.app.core.ChatMessage
import com.androidharness.app.core.Role
import com.androidharness.app.core.ToolCallData
import com.androidharness.app.core.ToolCodeChange
import com.androidharness.app.ui.chat.ChatUiState
import com.androidharness.app.ui.harness.session.HarnessSessionMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessSessionMapperTest {

    private fun assistant(
        text: String = "",
        thinking: String = "",
        calls: List<ToolCallData> = emptyList(),
        turnId: String? = "turn-1",
        id: String = "a1",
    ) = ChatMessage(
        role = Role.ASSISTANT,
        text = text,
        thinking = thinking,
        toolCalls = calls,
        turnId = turnId,
        id = id,
    )

    private fun toolResult(callId: String, output: String, error: Boolean = false) = ChatMessage(
        role = Role.TOOL,
        text = output,
        toolCallId = callId,
        toolName = "shell",
        isError = error,
        id = "r-$callId",
    )

    private fun writeCall(id: String = "c1", path: String = "src/Main.kt", added: Int = 12, removed: Int = 3, turnId: String? = null) =
        ToolCallData(
            id = id,
            name = "write_file",
            argumentsJson = """{"path":"$path","content":"x"}""",
            codeChanges = listOf(ToolCodeChange(path, added, removed)),
        )

    @Test fun assistantThinkingAndTextBecomeSeparateRows() {
        val state = ChatUiState(
            messages = listOf(
                ChatMessage(role = Role.USER, text = "Fix the build", id = "u1"),
                assistant(text = "Done.", thinking = "Look at the log first."),
            ),
        )
        val mapped = HarnessSessionMapper.map(state)
        assertEquals(3, mapped.items.size)
        assertTrue(mapped.items[0] is UserTurn)
        assertTrue(mapped.items[1] is ThinkingBlock)
        assertTrue(mapped.items[2] is AssistantTurn)
        assertEquals("Done.", (mapped.items[2] as AssistantTurn).text)
    }

    @Test fun blankThinkingAndBlankTextProduceNoRows() {
        val state = ChatUiState(messages = listOf(assistant(text = "  ", thinking = "")))
        assertTrue(HarnessSessionMapper.map(state).items.isEmpty())
    }

    @Test fun toolResultIsMergedIntoItsCall() {
        val state = ChatUiState(
            messages = listOf(
                assistant(calls = listOf(writeCall())),
                toolResult("c1", "Wrote src/Main.kt"),
            ),
        )
        val mapped = HarnessSessionMapper.map(state)
        val tool = mapped.items.filterIsInstance<ToolActivity>().single()
        assertEquals(ToolRunStatus.DONE, tool.status)
        assertEquals("Write", tool.title)
        assertEquals("src/Main.kt", tool.summary)
        assertEquals("Wrote src/Main.kt", tool.output)
        // A result row must never create a second card for the same call.
        assertEquals(1, mapped.items.count { it is ToolActivity })
    }

    @Test fun failedToolResultIsMarkedFailed() {
        val state = ChatUiState(
            messages = listOf(assistant(calls = listOf(writeCall())), toolResult("c1", "denied", error = true)),
        )
        assertEquals(ToolRunStatus.FAILED, HarnessSessionMapper.map(state).items.filterIsInstance<ToolActivity>().single().status)
    }

    @Test fun pendingCallWithoutResultIsRunning() {
        val state = ChatUiState(messages = listOf(assistant(calls = listOf(writeCall()))))
        assertEquals(ToolRunStatus.RUNNING, HarnessSessionMapper.map(state).items.filterIsInstance<ToolActivity>().single().status)
    }

    @Test fun measuredCodeChangesBecomeFileChangeRows() {
        val state = ChatUiState(messages = listOf(assistant(calls = listOf(writeCall(added = 7, removed = 2)))))
        val change = HarnessSessionMapper.map(state).items.filterIsInstance<FileChange>().single()
        assertEquals("src/Main.kt", change.path)
        assertEquals(7, change.additions)
        assertEquals(2, change.removals)
        assertEquals("turn-1", change.turnId)
    }

    @Test fun zeroSizedChangesAreNotRows() {
        val call = ToolCallData(
            id = "c9",
            name = "read_file",
            argumentsJson = """{"path":"README.md"}""",
            codeChanges = listOf(ToolCodeChange("README.md", 0, 0)),
        )
        val state = ChatUiState(messages = listOf(assistant(calls = listOf(call))))
        assertTrue(HarnessSessionMapper.map(state).items.none { it is FileChange })
    }

    @Test fun liveStreamingRowsAreAppendedWithStreamingFlags() {
        val state = ChatUiState(
            messages = listOf(ChatMessage(role = Role.USER, text = "Go", id = "u1")),
            streamingThinking = "Considering…",
            streamingText = "Writing…",
            streamingMessageId = "live-1",
            busy = true,
        )
        val mapped = HarnessSessionMapper.map(state)
        val thinking = mapped.items.filterIsInstance<ThinkingBlock>().single()
        val text = mapped.items.filterIsInstance<AssistantTurn>().single()
        assertTrue(thinking.streaming)
        assertTrue(text.streaming)
        assertEquals("live-1", text.id)
        assertEquals(SessionPhase.THINKING, mapped.phase)
    }

    @Test fun pendingApprovalWinsThePhase() {
        val state = ChatUiState(
            streamingText = "partial",
            busy = true,
            pendingApproval = com.androidharness.app.agent.ApprovalRequest(
                call = writeCall(),
                toolDescription = "Write src/Main.kt",
                diffPreview = "+1",
            ),
        )
        val mapped = HarnessSessionMapper.map(state)
        assertEquals(SessionPhase.WAITING_APPROVAL, mapped.phase)
        assertEquals("write_file", mapped.approval?.toolName)
        assertEquals("+1", mapped.approval?.diff)
    }

    @Test fun permissionLadderMapsBothWaysThroughTheEngineEnums() {
        assertEquals(SessionPermission.ASK, HarnessSessionMapper.map(ChatUiState(permissionMode = PermissionMode.CONFIRM_RISKY)).permission)
        assertEquals(SessionPermission.ASK, HarnessSessionMapper.map(ChatUiState(permissionMode = PermissionMode.CONFIRM_ALL)).permission)
        assertEquals(SessionPermission.AUTO_EDIT, HarnessSessionMapper.map(ChatUiState(permissionMode = PermissionMode.FULL_AUTO)).permission)
        assertEquals(SessionPermission.FULL_ACCESS, HarnessSessionMapper.map(ChatUiState(permissionMode = PermissionMode.FULL_ACCESS)).permission)
        assertEquals(
            SessionPermission.PLAN,
            HarnessSessionMapper.map(ChatUiState(mode = AgentMode.PLAN, permissionMode = PermissionMode.CONFIRM_ALL)).permission,
        )
    }

    @Test fun planModeIsReportedWithThePendingPlan() {
        val state = ChatUiState(mode = AgentMode.PLAN, pendingPlan = "1. Read\n2. Change")
        val mapped = HarnessSessionMapper.map(state)
        assertTrue(mapped.planMode)
        assertEquals("1. Read\n2. Change", mapped.plan)
    }

    @Test fun checksOnlyCountBuildTestAndLintCommands() {
        val build = ToolCallData("c1", "shell", """{"command":"gradle testDebugUnitTest"}""")
        val ls = ToolCallData("c2", "shell", """{"command":"ls -la"}""")
        val state = ChatUiState(
            messages = listOf(
                assistant(id = "a1", calls = listOf(build, ls)),
                toolResult("c1", "BUILD SUCCESSFUL"),
                toolResult("c2", "file list"),
            ),
        )
        val checks = HarnessSessionMapper.map(state).checks
        assertEquals(1, checks.size)
        assertEquals(true, checks.single().passed)
        assertTrue(checks.single().command.contains("gradle"))
    }

    @Test fun aLaterFailureReplacesTheEarlierPassForTheSameCommand() {
        val call = ToolCallData("c1", "shell", """{"command":"npm test"}""")
        val state = ChatUiState(
            messages = listOf(
                assistant(id = "a1", calls = listOf(call)),
                toolResult("c1", "ok"),
                assistant(id = "a2", calls = listOf(call.copy(id = "c2"))),
                toolResult("c2", "1 failing", error = true),
            ),
        )
        val checks = HarnessSessionMapper.map(state).checks
        assertEquals(1, checks.size)
        assertEquals(false, checks.single().passed)
    }

    @Test fun todosAndQueueAreExposed() {
        val state = ChatUiState(
            todos = listOf(com.androidharness.app.agent.TodoItem("Write tests", com.androidharness.app.agent.TodoItem.Status.IN_PROGRESS)),
            taskControl = com.androidharness.app.agent.TaskRecord(
                queue = listOf(com.androidharness.app.agent.QueuedPrompt(id = "q1", text = "then run tests")),
            ),
        )
        val mapped = HarnessSessionMapper.map(state)
        assertEquals("Write tests", mapped.todos.single().text)
        assertEquals("IN_PROGRESS", mapped.todos.single().status)
        assertEquals("then run tests", mapped.queue.single().text)
    }

    @Test fun extrasFillProjectBranchAndRewind() {
        val state = ChatUiState(sessionId = "s1", turnsWithCheckpoints = setOf("t1"))
        val mapped = HarnessSessionMapper.map(
            state,
            HarnessSessionMapper.Extras(projectName = "Harness", branch = "main", workspacePath = "/tmp/x", canRewind = true),
        )
        assertEquals("Harness", mapped.projectName)
        assertEquals("main", mapped.branch)
        assertTrue(mapped.canRewind)
    }

    @Test fun rewindNeedsACheckpoint() {
        val mapped = HarnessSessionMapper.map(ChatUiState(), HarnessSessionMapper.Extras(canRewind = true))
        assertTrue(!mapped.canRewind)
        assertNull(mapped.sessionId)
    }

    @Test fun emptyConversationIsEmptyPhase() {
        val mapped = HarnessSessionMapper.map(ChatUiState())
        assertEquals(SessionPhase.EMPTY, mapped.phase)
        assertTrue(mapped.isEmpty)
    }

    @Test fun contextFractionIsClamped() {
        val mapped = HarnessSessionMapper.map(ChatUiState(maxContextTokens = 1000, usage = com.androidharness.app.ui.chat.UsageStats(lastInput = 2000L)))
        assertEquals(1f, mapped.contextFraction, 0.001f)
    }

    @Test fun toolDescriptionsCoverTheCommonTools() {
        val (title, summary) = HarnessSessionMapper.describeTool(
            "shell",
            mapOf("command" to kotlinx.serialization.json.JsonPrimitive("gradle assembleDebug")),
        )
        assertEquals("Run", title)
        assertEquals("gradle assembleDebug", summary)
    }

    @Test fun prettyArgsNeverThrowsOnJunk() {
        assertEquals("not json", HarnessSessionMapper.prettyArgs("not json"))
    }

    @Test fun checkLabelsAreTrimmed() {
        val long = "./gradlew :app:assembleDebug --stacktrace --info and-a-very-long-extra-flag-here"
        assertTrue(HarnessSessionMapper.checkLabel(long).length <= 48)
        assertEquals("gradlew test", HarnessSessionMapper.checkLabel("./gradlew   test"))
    }
}
