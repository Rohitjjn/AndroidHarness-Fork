package com.androidharness.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorktreeCommandTest {

    @Test fun folderNameStripsPathSeparators() {
        assertEquals("feature-parser-fix", WorktreeManager.folderName("feature/parser-fix"))
        assertEquals("worktree", WorktreeManager.folderName("   "))
        assertEquals("hotfix-1.2", WorktreeManager.folderName("hotfix-1.2"))
    }

    @Test fun worktreePathStaysInsideTheManagedRoot() {
        val path = WorktreeManager.worktreePath("/data/app/worktrees", "feature/nested/branch")
        assertEquals("/data/app/worktrees/feature-nested-branch", path)
        assertFalse(path.contains(".."))
    }

    @Test fun branchValidationRejectsWhatGitRejects() {
        assertNull(WorktreeManager.validateBranch("feature/parser-fix"))
        assertNull(WorktreeManager.validateBranch("hotfix-1"))
        assertEquals("A branch name is required.", WorktreeManager.validateBranch("  "))
        assertTrue(WorktreeManager.validateBranch("has space")!!.contains("spaces"))
        assertTrue(WorktreeManager.validateBranch("-leading")!!.contains("dash"))
        assertTrue(WorktreeManager.validateBranch("a..b")!!.contains(".."))
        assertTrue(WorktreeManager.validateBranch("a~b")!!.contains("~"))
    }

    @Test fun addCommandCreatesABranchWhenAsked() {
        assertEquals(
            "git -c 'safe.directory=*' -c gc.auto=0 -c maintenance.auto=false worktree add -b 'f/x' '/w/f-x'",
            WorktreeManager.addCommand("/w/f-x", "f/x", createBranch = true),
        )
        assertEquals(
            "git -c 'safe.directory=*' -c gc.auto=0 -c maintenance.auto=false worktree add '/w/f-x' 'f/x'",
            WorktreeManager.addCommand("/w/f-x", "f/x", createBranch = false),
        )
    }

    @Test fun removeCommandQuotesAndForces() {
        assertTrue(WorktreeManager.removeCommand("/w/a b", force = true).contains("--force"))
        assertTrue(WorktreeManager.removeCommand("/w/a b", force = false).contains("'/w/a b'"))
    }

    @Test fun parsePorcelainReadsEveryWorktree() {
        val porcelain = """
            worktree /repo
            HEAD 1111111111111111111111111111111111111111
            branch refs/heads/main

            worktree /worktrees/feature
            HEAD 2222222222222222222222222222222222222222
            branch refs/heads/feature/x

            worktree /worktrees/detached
            HEAD 3333333333333333333333333333333333333333
            detached
        """.trimIndent()
        val trees = WorktreeManager.parseWorktrees(porcelain)
        assertEquals(3, trees.size)
        assertEquals("main", trees[0].branch)
        assertEquals("/repo", trees[0].path)
        assertEquals("feature/x", trees[1].branch)
        assertTrue(trees[2].detached)
        assertNull(trees[2].branch)
    }

    @Test fun branchCheckedOutElsewhereFindsTheOtherCheckout() {
        val trees = listOf(
            WorktreeManager.WorktreeInfo("/repo", "main", null, false, false),
            WorktreeManager.WorktreeInfo("/worktrees/feature", "feature/x", null, false, false),
        )
        assertEquals("/worktrees/feature", WorktreeManager.branchCheckedOutElsewhere(trees, "feature/x", "/repo"))
        assertNull(WorktreeManager.branchCheckedOutElsewhere(trees, "feature/x", "/worktrees/feature"))
        assertNull(WorktreeManager.branchCheckedOutElsewhere(trees, "feature/new", "/repo"))
    }

    @Test fun duOutputBecomesAHumanSize() {
        assertEquals(2048L, WorktreeManager.sizeKb("2048\t/worktrees/feature"))
        assertEquals("2 MB", WorktreeManager.humanSize(2048))
        assertEquals("512 KB", WorktreeManager.humanSize(512))
        assertEquals("1.5 GB", WorktreeManager.humanSize(1024L * 1024L * 3 / 2))
        assertEquals("unknown size", WorktreeManager.humanSize(null))
    }
}
