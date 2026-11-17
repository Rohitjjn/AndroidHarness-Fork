package com.androidharness.app.workspace

import com.androidharness.app.data.env.ShellTierRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Git worktree isolation.
 *
 * A worktree is a second checkout of the same repository on its own branch, so
 * a session can work in isolation without cloning anything or touching the
 * user's checked-out files. Everything that can be reasoned about without a
 * device — commands, parsing and validation — is a pure function here and is
 * unit tested; only [create]/[remove]/[list] touch the shell.
 *
 * Storage: every worktree duplicates the repository's working tree (not its
 * history), so the manager reports the size after creation and refuses paths
 * outside the worktree root it manages.
 */
class WorktreeManager(
    private val router: ShellTierRouter,
    private val workspace: WorkspaceManager,
    /** Directory that owns every worktree this app creates. */
    private val root: File,
) {

    data class WorktreeInfo(val path: String, val branch: String?, val head: String?, val bare: Boolean, val detached: Boolean)

    data class Result(val ok: Boolean, val message: String, val path: String? = null, val projectId: String? = null, val projectPath: String? = null)

    companion object {
        private const val GIT = "git -c 'safe.directory=*' -c gc.auto=0 -c maintenance.auto=false"

        /** Stable, readable directory name for a branch (no slashes escaping the root). */
        fun folderName(branch: String): String =
            branch.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "worktree" }

        /** Where the worktree for [branch] lives, given the managed root. */
        fun worktreePath(rootPath: String, branch: String): String =
            File(rootPath, folderName(branch)).absolutePath

        /** Rejects names git itself would reject, with a plain explanation. */
        fun validateBranch(branch: String): String? {
            val name = branch.trim()
            return when {
                name.isEmpty() -> "A branch name is required."
                name.length > 120 -> "That branch name is too long."
                name.startsWith("-") -> "A branch name cannot start with a dash."
                name.contains(" ") -> "A branch name cannot contain spaces."
                name.contains("..") -> "A branch name cannot contain '..'."
                name.endsWith(".lock") -> "A branch name cannot end in .lock."
                name.endsWith("/") || name.startsWith("/") -> "A branch name cannot start or end with '/'."
                name.any { it in "~^:?*[\\\"" } -> "A branch name cannot contain ~ ^ : ? * [ \\ or a quote."
                else -> null
            }
        }

        fun addCommand(path: String, branch: String, createBranch: Boolean): String =
            if (createBranch) "$GIT worktree add -b ${q(branch)} ${q(path)}"
            else "$GIT worktree add ${q(path)} ${q(branch)}"

        fun removeCommand(path: String, force: Boolean): String =
            "$GIT worktree remove" + (if (force) " --force" else "") + " ${q(path)}"

        fun pruneCommand(): String = "$GIT worktree prune"

        fun listCommand(): String = "$GIT worktree list --porcelain"

        fun isRepoCommand(): String = "$GIT rev-parse --git-dir"

        fun duCommand(path: String): String = "du -sk ${q(path)} 2>/dev/null || true"

        /** Parses `git worktree list --porcelain` into records. */
        fun parseWorktrees(porcelain: String): List<WorktreeInfo> {
            val out = ArrayList<WorktreeInfo>()
            var path: String? = null
            var head: String? = null
            var branch: String? = null
            var bare = false
            var detached = false
            fun flush() {
                val p = path ?: return
                out += WorktreeInfo(
                    path = p,
                    branch = branch?.substringAfter("refs/heads/"),
                    head = head,
                    bare = bare,
                    detached = detached,
                )
                path = null; head = null; branch = null; bare = false; detached = false
            }
            for (line in porcelain.lineSequence()) {
                when {
                    line.isBlank() -> flush()
                    line.startsWith("worktree ") -> { flush(); path = line.removePrefix("worktree ").trim() }
                    line.startsWith("HEAD ") -> head = line.removePrefix("HEAD ").trim()
                    line.startsWith("branch ") -> branch = line.removePrefix("branch ").trim()
                    line == "bare" -> bare = true
                    line == "detached" -> detached = true
                }
            }
            flush()
            return out
        }

        /** True when [branch] is already checked out in a worktree other than [exceptPath]. */
        fun branchCheckedOutElsewhere(trees: List<WorktreeInfo>, branch: String, exceptPath: String?): String? =
            trees.firstOrNull { it.branch == branch && it.path != exceptPath }?.path

        fun sizeKb(duOutput: String): Long? =
            duOutput.trim().lineSequence().firstOrNull()?.split('\t')?.firstOrNull()?.trim()?.toLongOrNull()

        fun humanSize(kb: Long?): String = when {
            kb == null -> "unknown size"
            kb < 1024 -> "$kb KB"
            kb < 1024 * 1024 -> "${kb / 1024} MB"
            else -> "%.1f GB".format(kb / 1024.0 / 1024.0)
        }

        private fun q(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }

    /** Creates the worktree, attaches it as an editable workspace and reports storage. */
    suspend fun create(fs: WorkspaceFs, branch: String, sessionId: String?): Result = withContext(Dispatchers.IO) {
        validateBranch(branch)?.let { return@withContext Result(false, it) }
        val shellRoot = fs.shellRoot
            ?: return@withContext Result(false, "Worktrees need a real folder. This workspace is a document tree or a remote host, so Git cannot create a checkout inside it.")
        if (!shellRoot.isDirectory) return@withContext Result(false, "The workspace folder is not on this device any more.")

        val repo = run(fs, isRepoCommand())
        if (!repo.ok) {
            return@withContext Result(false, "This workspace is not a Git repository. Run git init first, or pick a repository workspace.")
        }
        val existing = parseWorktrees(run(fs, listCommand()).output)
        branchCheckedOutElsewhere(existing, branch, null)?.let { path ->
            return@withContext Result(false, "Branch '$branch' is already checked out at $path. Use a different branch name.")
        }

        val path = worktreePath(root.absolutePath, branch)
        root.mkdirs()
        val createBranch = existing.none { it.branch == branch } &&
            !branchExists(fs, branch)
        val add = run(fs, addCommand(path, branch, createBranch))
        if (!add.ok) {
            return@withContext Result(false, add.output.ifBlank { "git worktree add failed." })
        }

        val size = humanSize(sizeKb(run(fs, duCommand(path)).output))
        val project = runCatching { workspace.addShellProject(path, activate = false) }.getOrNull()
        if (project != null && sessionId != null) {
            runCatching { attachToSession(sessionId, project.id) }
        }
        Result(
            ok = true,
            message = buildString {
                append("Worktree ready on branch '$branch'.\n")
                append("Path: $path\n")
                append("Disk: $size (a worktree duplicates the working tree, not the history)\n")
                if (createBranch) append("Created branch '$branch'.\n")
                if (project != null) append("Added as a workspace: ${project.name}")
            }.trim(),
            path = path,
            projectId = project?.id,
            projectPath = path,
        )
    }

    /**
     * Deletes the worktree and forgets its workspace. Refuses paths outside the
     * managed root so a bad argument can never delete a user's folder.
     */
    suspend fun remove(fs: WorkspaceFs, path: String, force: Boolean = false): Result = withContext(Dispatchers.IO) {
        val target = File(path).canonicalFile
        val managed = runCatching { root.canonicalFile }.getOrNull()
        if (managed == null || !target.path.startsWith(managed.path + File.separator)) {
            return@withContext Result(false, "Only worktrees created by Harness Code can be removed from here.")
        }
        if (!target.exists()) {
            runCatching { run(fs, pruneCommand()) }
            return@withContext Result(false, "That worktree is already gone; the repository was pruned.")
        }
        val remove = run(fs, removeCommand(target.path, force))
        if (!remove.ok) {
            return@withContext Result(false, remove.output.ifBlank { "git worktree remove failed." })
        }
        trimProject(path)
        Result(true, "Worktree removed: ${target.path}\nThe branch itself was kept.")
    }

    /** Worktrees known to the repository, plus their size when it is cheap to measure. */
    suspend fun list(fs: WorkspaceFs): List<WorktreeInfo> = withContext(Dispatchers.IO) {
        parseWorktrees(run(fs, listCommand()).output)
    }

    private suspend fun branchExists(fs: WorkspaceFs, branch: String): Boolean =
        run(fs, "$GIT rev-parse --verify --quiet ${q2(branch)} >/dev/null && echo yes || true").output.contains("yes")

    private fun q2(value: String) = "'refs/heads/" + value.replace("'", "'\\''") + "'"

    /**
     * Attaches the new workspace to the session's selection so the running chat
     * can see it. Best effort: a failure here never fails the worktree itself.
     */
    private suspend fun attachToSession(sessionId: String, projectId: String) {
        val current = runCatching { workspace.selectionForChat(sessionId).first() }.getOrNull()
        val selection = if (current == null) ChatWorkspaceSelection.single(projectId) else {
            val grants = current.grants.filterNot { it.projectId == projectId } + ChatWorkspaceGrant(projectId, true)
            ChatWorkspaceSelection(current.primaryId.ifBlank { projectId }, grants)
        }
        workspace.assignSelection(sessionId, selection)
    }

    /**
     * Drops the workspace row that pointed at the deleted worktree so it does
     * not linger in the picker. Best effort: the git removal already succeeded.
     */
    private suspend fun trimProject(path: String) {
        val projects = runCatching { workspace.projects.first() }.getOrNull() ?: return
        val match = projects.firstOrNull { project ->
            project.uri == path || (project.name == File(path).name && project.kind == WorkspaceManager.KIND_SHELL)
        } ?: return
        runCatching { workspace.deleteProject(match) }
    }

    private suspend fun run(fs: WorkspaceFs, command: String): ShellOutput {
        val res = runCatching { router.runWorkspace(command, fs, timeoutMs = 120_000, maxOutput = 8_000) }
            .getOrElse { return ShellOutput(false, "Shell unavailable: ${it.message}") }
        val text = "${res.rawOutput}\n${res.rawStderr}".trim()
        return ShellOutput(res.exitCode == 0 && !res.timedOut, text)
    }

    data class ShellOutput(val ok: Boolean, val output: String)
}
