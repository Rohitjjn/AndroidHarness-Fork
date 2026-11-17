package com.androidharness.app.github

import org.junit.Assert.assertTrue
import org.junit.Test

class FixFailingChecksPromptTest {

    private fun status(checks: List<PullRequestMonitor.Check>) = PullRequestMonitor.Status(
        number = 42,
        title = "Add worktree isolation",
        url = "https://github.com/o/r/pull/42",
        branch = "feature/worktrees",
        base = "main",
        sha = "abc1234",
        checks = checks,
    )

    @Test fun promptNamesThePullRequestAndEveryFailingCheck() {
        val prompt = fixFailingChecksPrompt(
            status(
                listOf(
                    PullRequestMonitor.Check("unit tests", PullRequestMonitor.CheckState.FAILED, "failure"),
                    PullRequestMonitor.Check("lint", PullRequestMonitor.CheckState.FAILED, "timed_out"),
                    PullRequestMonitor.Check("build", PullRequestMonitor.CheckState.PASSED, "success"),
                ),
            ),
        )
        assertTrue(prompt.contains("#42"))
        assertTrue(prompt.contains("feature/worktrees"))
        assertTrue(prompt.contains("unit tests"))
        assertTrue(prompt.contains("lint"))
        assertTrue(prompt.contains("timed_out"))
        assertTrue(!prompt.contains("build (success)"))
        assertTrue(prompt.contains("smallest fix"))
    }

    @Test fun summaryCountsFailuresBeforePending() {
        val failing = status(
            listOf(
                PullRequestMonitor.Check("a", PullRequestMonitor.CheckState.FAILED, "failure"),
                PullRequestMonitor.Check("b", PullRequestMonitor.CheckState.PENDING, "in_progress"),
            ),
        )
        assertTrue(failing.summary.contains("1 failing"))

        val pending = status(listOf(PullRequestMonitor.Check("b", PullRequestMonitor.CheckState.PENDING, "queued")))
        assertTrue(pending.summary.contains("running"))

        val green = status(listOf(PullRequestMonitor.Check("c", PullRequestMonitor.CheckState.PASSED, "success")))
        assertTrue(green.summary.contains("passed"))

        assertTrue(status(emptyList()).summary.contains("No checks"))
    }

    @Test fun repositoryIsParsedOnlyFromGitHubRemotes() {
        assertTrue(PullRequestMonitor.repositoryFromUrl("https://github.com/rohit/harness-code.git") == "rohit/harness-code")
        assertTrue(PullRequestMonitor.repositoryFromUrl("git@github.com:rohit/harness-code.git") == "rohit/harness-code")
        assertTrue(PullRequestMonitor.repositoryFromUrl("https://gitlab.com/rohit/harness-code.git") == null)
        assertTrue(PullRequestMonitor.repositoryFromUrl("") == null)
    }
}
