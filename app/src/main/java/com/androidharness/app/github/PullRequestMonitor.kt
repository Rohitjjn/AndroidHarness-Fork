package com.androidharness.app.github

import com.androidharness.app.workspace.WorkspaceFs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Read-only pull request monitoring: the open PR for the current branch, its
 * commit, and the state of every check on that commit.
 *
 * It reuses the existing `GitHubApi` and the repository/branch resolution from
 * [GitHubWorkspaces], and it never writes to GitHub. The "fix failing checks"
 * action is a prompt handed to the agent, which then goes through the normal
 * approval path like any other work.
 */
class PullRequestMonitor(
    private val api: GitHubApi,
    private val github: GitHubWorkspaces,
    private val requests: GitHubPullRequests,
) {

    enum class CheckState { PASSED, FAILED, PENDING, SKIPPED, UNKNOWN }

    data class Check(val name: String, val state: CheckState, val detail: String)

    data class Status(
        val number: Int,
        val title: String,
        val url: String,
        val branch: String,
        val base: String,
        val sha: String,
        val checks: List<Check>,
    ) {
        val failing: List<Check> get() = checks.filter { it.state == CheckState.FAILED }
        val summary: String
            get() = when {
                checks.isEmpty() -> "No checks reported"
                failing.isNotEmpty() -> "${failing.size} failing"
                checks.any { it.state == CheckState.PENDING } -> "Checks running"
                else -> "All checks passed"
            }
    }

    /** Resolves the repository and branch of [fs], then looks for an open PR. */
    suspend fun forWorkspace(fs: WorkspaceFs, token: String): Status? {
        val state = runCatching { github.inspect(fs) }.getOrNull() ?: return null
        val repository = repositoryFromUrl(state.remoteUrl) ?: return null
        return forBranch(repository, state.branch, token)
    }

    suspend fun forBranch(repository: String, branch: String, token: String): Status? {
        val info = requests.findOpen(repository, branch, token) ?: return null
        val detail = api.get("/repos/$repository/pulls/${info.number}", token) as? JsonObject
        val sha = ((detail?.get("head") as? JsonObject)?.get("sha"))?.jsonPrimitive?.content
            ?: return null
        return Status(
            number = info.number,
            title = info.title,
            url = info.url,
            branch = info.head,
            base = info.base,
            sha = sha,
            checks = checks(repository, sha, token),
        )
    }

    private suspend fun checks(repository: String, sha: String, token: String): List<Check> {
        // Check runs first (GitHub Actions); the combined commit status covers
        // other CI providers. A permissions failure on either never throws.
        val runChecks = runCatching {
            val runs = api.get("/repos/$repository/commits/$sha/check-runs", token) as? JsonObject
            (runs?.get("check_runs") as? JsonArray).orEmpty().mapNotNull { entry ->
                val run = entry as? JsonObject ?: return@mapNotNull null
                val name = run.string("name").orEmpty().ifBlank { "check" }
                val status = run.string("status").orEmpty()
                val conclusion = run.string("conclusion").orEmpty()
                Check(name, state(status, conclusion), conclusion.ifBlank { status })
            }
        }.getOrElse { emptyList() }

        val contexts = runCatching {
            val statuses = api.get("/repos/$repository/commits/$sha/status", token) as? JsonObject
            (statuses?.get("statuses") as? JsonArray).orEmpty().mapNotNull { entry ->
                val ctx = entry as? JsonObject ?: return@mapNotNull null
                val name = ctx.string("context").orEmpty().ifBlank { "status" }
                Check(name, state("completed", ctx.string("state").orEmpty()), ctx.string("state").orEmpty())
            }
        }.getOrElse { emptyList() }

        return (runChecks + contexts).distinctBy { it.name }
    }

    private fun state(status: String, conclusion: String): CheckState {
        val value = (if (status == "completed") conclusion else status).lowercase()
        return when (value) {
            "success", "passed", "pass" -> CheckState.PASSED
            "failure", "failed", "fail", "cancelled", "timed_out", "action_required", "error", "startup_failure" -> CheckState.FAILED
            "pending", "queued", "in_progress", "waiting", "requested" -> CheckState.PENDING
            "neutral", "skipped", "stale" -> CheckState.SKIPPED
            else -> CheckState.UNKNOWN
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content

    companion object {
        /** owner/repository from a GitHub remote URL, or null for anything else. */
        fun repositoryFromUrl(url: String): String? = runCatching {
            gitHubRepositoryUrl(url).removePrefix("https://github.com/").removeSuffix(".git")
        }.getOrNull()
    }
}

/**
 * Prompt for the "fix failing checks" action. Names the PR, its branch and the
 * failing checks so the agent starts from facts instead of guessing, and asks
 * for a minimal fix with the verification step included.
 */
fun fixFailingChecksPrompt(status: PullRequestMonitor.Status): String = buildString {
    append("Pull request #${status.number} (\"${status.title}\") on branch ${status.branch} ")
    append("has failing checks:\n")
    for (check in status.failing) append("- ${check.name} (${check.detail})\n")
    append("\nFind the cause, make the smallest fix, and run the checks again. ")
    append("If a check is failing for a reason outside this repository, say so instead of editing unrelated files.")
}
