package com.androidharness.app.tools

import com.androidharness.app.workspace.WorktreeManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Worktree isolation for a session.
 *
 * `worktree_create` adds a second checkout on its own branch and attaches it as
 * a workspace; `worktree_remove` deletes that checkout again. Both refuse the
 * cases that would surprise a user: a workspace that is not a Git repository,
 * a branch that is already checked out somewhere else, and any path outside the
 * folder this app manages.
 */
class WorktreeCreateTool(private val worktrees: WorktreeManager) : Tool {
    override val name = "worktree_create"
    override val description =
        "Create an isolated Git worktree for this session: a second checkout of the same repository " +
        "on its own branch, added as an editable workspace. Use it when you should not disturb the " +
        "files the user has open. Refuses workspaces that are not Git repositories and branches that " +
        "are already checked out. Reports the disk space the checkout uses."
    override val parametersSchema = Schema.obj(
        mapOf(
            "branch" to Schema.string("Branch to check out. A new branch is created when it does not exist yet."),
            "purpose" to Schema.string("Optional short note about what this worktree is for."),
        ),
        required = listOf("branch"),
    )
    override val isReadOnly = false

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val branch = args["branch"]?.jsonPrimitive?.content
            ?: throw ToolFailure("Missing required argument: branch")
        val result = worktrees.create(ctx.workspace, branch, ctx.sessionId)
        return ToolResult(result.ok, result.message)
    }
}

class WorktreeRemoveTool(private val worktrees: WorktreeManager) : Tool {
    override val name = "worktree_remove"
    override val description =
        "Remove a Git worktree created by worktree_create and forget its workspace. The branch and " +
        "its commits are kept. Refuses paths outside the worktrees this app manages."
    override val parametersSchema = Schema.obj(
        mapOf(
            "path" to Schema.string("Absolute path of the worktree to remove."),
            "force" to Schema.string("Pass \"true\" to remove a worktree with uncommitted changes."),
        ),
        required = listOf("path"),
    )
    override val isReadOnly = false

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val path = args["path"]?.jsonPrimitive?.content
            ?: throw ToolFailure("Missing required argument: path")
        val force = args["force"]?.jsonPrimitive?.content == "true"
        val result = worktrees.remove(ctx.workspace, path, force)
        return ToolResult(result.ok, result.message)
    }
}

class WorktreeListTool(private val worktrees: WorktreeManager) : Tool {
    override val name = "worktree_list"
    override val description =
        "List the Git worktrees of this repository with their branch and path. Read-only."
    override val parametersSchema = Schema.obj(emptyMap())
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val trees = worktrees.list(ctx.workspace)
        if (trees.isEmpty()) return ToolResult(true, "No worktrees (not a Git repository, or no checkouts yet).")
        val text = trees.joinToString("\n") { tree ->
            val branch = tree.branch ?: if (tree.detached) "(detached)" else "(unknown)"
            "$branch  ${tree.path}"
        }
        return ToolResult(true, text)
    }
}
