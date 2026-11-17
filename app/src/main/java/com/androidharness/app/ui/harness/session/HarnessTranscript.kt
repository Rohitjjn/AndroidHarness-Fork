package com.androidharness.app.ui.harness.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androidharness.app.agent.session.AssistantTurn
import com.androidharness.app.agent.session.FileChange
import com.androidharness.app.agent.session.Notice
import com.androidharness.app.agent.session.ThinkingBlock
import com.androidharness.app.agent.session.ToolActivity
import com.androidharness.app.agent.session.ToolRunStatus
import com.androidharness.app.agent.session.TranscriptItem
import com.androidharness.app.agent.session.UserTurn
import com.androidharness.app.ui.harness.components.StreamingCaret
import com.androidharness.app.ui.harness.theme.HarnessMono
import com.androidharness.app.ui.harness.theme.HarnessTheme
import com.androidharness.app.ui.harness.theme.Space

/** Callbacks the transcript rows need; keeps rows free of view-model types. */
data class TranscriptActions(
    val onOpenDiff: (FileChange) -> Unit,
    val onComment: (FileChange) -> Unit,
    val onRewind: (FileChange) -> Unit,
    val onOpenToolOutput: (ToolActivity) -> Unit,
)

@Composable
fun TranscriptRow(item: TranscriptItem, actions: TranscriptActions, modifier: Modifier = Modifier) {
    when (item) {
        is UserTurn -> UserBubble(item, modifier)
        is AssistantTurn -> AssistantText(item, modifier)
        is ThinkingBlock -> ThinkingCard(item, modifier)
        is ToolActivity -> ToolCard(item, modifier, actions)
        is FileChange -> ChangeCard(item, modifier, actions)
        is Notice -> Text(
            item.text,
            style = MaterialTheme.typography.labelSmall,
            color = HarnessTheme.palette.faint,
            modifier = modifier.fillMaxWidth().padding(vertical = Space.sm),
        )
    }
}

@Composable
private fun UserBubble(turn: UserTurn, modifier: Modifier = Modifier) {
    val palette = HarnessTheme.palette
    Row(modifier.fillMaxWidth().padding(vertical = Space.sm), horizontalArrangement = Arrangement.End) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.sunken)
                .padding(horizontal = Space.md, vertical = Space.md),
        ) {
            Text(
                turn.text,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.text,
            )
            if (turn.attachments > 0) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    "${turn.attachments} attachment" + if (turn.attachments == 1) "" else "s",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.faint,
                )
            }
        }
    }
}

@Composable
private fun AssistantText(turn: AssistantTurn, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.sm)) {
        HarnessMarkdownText(turn.text)
        if (turn.streaming) StreamingCaret()
    }
}

@Composable
private fun ThinkingCard(block: ThinkingBlock, modifier: Modifier = Modifier) {
    val palette = HarnessTheme.palette
    var open by remember(block.id) { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Psychology, contentDescription = null, tint = palette.faint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(Space.sm))
            Text(
                when {
                    block.streaming -> "Thinking…"
                    block.durationMs > 0 -> "Thought for ${(block.durationMs / 1000).coerceAtLeast(1)}s"
                    else -> "Thought process"
                },
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "Collapse thinking" else "Expand thinking",
                tint = palette.faint,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = open) {
            Text(
                block.text,
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
                modifier = Modifier.padding(top = Space.sm),
            )
        }
    }
}

@Composable
private fun ToolCard(activity: ToolActivity, modifier: Modifier = Modifier, actions: TranscriptActions) {
    val palette = HarnessTheme.palette
    var open by remember(activity.id) { mutableStateOf(false) }
    val statusColor = when (activity.status) {
        ToolRunStatus.RUNNING -> palette.accent
        ToolRunStatus.DONE -> palette.success
        ToolRunStatus.FAILED -> palette.danger
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (activity.name.startsWith("shell")) Icons.Outlined.Terminal else Icons.Outlined.Description,
                contentDescription = null,
                tint = palette.muted,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                activity.title,
                style = MaterialTheme.typography.labelLarge,
                color = palette.text,
            )
            if (activity.summary.isNotBlank()) {
                Spacer(Modifier.width(Space.sm))
                Text(
                    activity.summary,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = HarnessMono),
                    color = palette.muted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (activity.additions > 0 || activity.removals > 0) {
                Text("+${activity.additions}", style = MaterialTheme.typography.labelSmall, color = palette.diffAdd)
                Spacer(Modifier.width(2.dp))
                Text("−${activity.removals}", style = MaterialTheme.typography.labelSmall, color = palette.diffRemove)
                Spacer(Modifier.width(Space.sm))
            }
            when (activity.status) {
                ToolRunStatus.RUNNING -> com.androidharness.app.ui.harness.components.LiveDot(color = statusColor, size = 6.dp)
                ToolRunStatus.DONE -> Icon(Icons.Filled.Check, contentDescription = "Done", tint = statusColor, modifier = Modifier.size(14.dp))
                ToolRunStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, contentDescription = "Failed", tint = statusColor, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(Space.xs))
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "Hide details" else "Show details",
                tint = palette.faint,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(top = Space.sm)) {
                if (activity.input.isNotBlank()) {
                    Text("INPUT", style = MaterialTheme.typography.labelSmall, color = palette.faint)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.codeBlock)
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(Space.sm),
                    ) {
                        Text(
                            activity.input,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = HarnessMono),
                            color = palette.code,
                        )
                    }
                }
                if (activity.output.isNotBlank()) {
                    Spacer(Modifier.height(Space.sm))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("OUTPUT", style = MaterialTheme.typography.labelSmall, color = palette.faint)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Open",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accentInk,
                            modifier = Modifier.clickable { actions.onOpenToolOutput(activity) }.padding(2.dp),
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.codeBlock)
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(Space.sm),
                    ) {
                        Text(
                            activity.output,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = HarnessMono),
                            color = if (activity.status == ToolRunStatus.FAILED) palette.danger else palette.code,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeCard(change: FileChange, modifier: Modifier = Modifier, actions: TranscriptActions) {
    val palette = HarnessTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .clickable { actions.onOpenDiff(change) }
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = palette.muted, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(Space.sm))
            Text(
                change.path,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = HarnessMono),
                color = palette.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text("+${change.additions}", style = MaterialTheme.typography.labelSmall, color = palette.diffAdd)
            Spacer(Modifier.width(2.dp))
            Text("−${change.removals}", style = MaterialTheme.typography.labelSmall, color = palette.diffRemove)
        }
        Spacer(Modifier.height(Space.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(
                "Review",
                style = MaterialTheme.typography.labelSmall,
                color = palette.accentInk,
                modifier = Modifier.clickable { actions.onOpenDiff(change) }.padding(vertical = 2.dp),
            )
            Text(
                "Comment",
                style = MaterialTheme.typography.labelSmall,
                color = palette.accentInk,
                modifier = Modifier.clickable { actions.onComment(change) }.padding(vertical = 2.dp),
            )
            if (change.turnId != null) {
                Text(
                    "Rewind",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    modifier = Modifier.clickable { actions.onRewind(change) }.padding(vertical = 2.dp),
                )
            }
        }
    }
}

/** Inline unified-diff renderer with +/− tinting. */
@Composable
fun DiffBody(diff: String, modifier: Modifier = Modifier) {
    val palette = HarnessTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.codeBlock)
            .padding(Space.sm),
    ) {
        for (line in diff.lineSequence()) {
            val color = when {
                line.startsWith("+++") || line.startsWith("---") -> palette.muted
                line.startsWith("+") -> palette.diffAdd
                line.startsWith("-") -> palette.diffRemove
                line.startsWith("@@") -> palette.accentInk
                else -> palette.code
            }
            val tint = when {
                line.startsWith("+++") || line.startsWith("---") -> Color.Transparent
                line.startsWith("+") -> palette.diffAdd.copy(alpha = 0.10f)
                line.startsWith("-") -> palette.diffRemove.copy(alpha = 0.10f)
                else -> Color.Transparent
            }
            Text(
                line.ifEmpty { " " },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = HarnessMono, fontWeight = FontWeight.Normal),
                color = color,
                modifier = Modifier.fillMaxWidth().background(tint).padding(horizontal = 2.dp),
            )
        }
    }
}

/** Verification card: every build/test/lint this session ran. */
@Composable
fun VerificationCard(checks: List<com.androidharness.app.agent.session.CheckResult>, modifier: Modifier = Modifier) {
    if (checks.isEmpty()) return
    val palette = HarnessTheme.palette
    val failures = checks.count { it.passed == false }
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .padding(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (failures == 0) Icons.Filled.Check else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = if (failures == 0) palette.success else palette.danger,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                if (failures == 0) "Checks passed" else "$failures check" + if (failures == 1) "" else "s" + " failed",
                style = MaterialTheme.typography.labelLarge,
                color = palette.text,
            )
        }
        Spacer(Modifier.height(Space.sm))
        for (check in checks) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (check.passed == false) "✕" else "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (check.passed == false) palette.danger else palette.success,
                    modifier = Modifier.width(18.dp),
                )
                Text(
                    check.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = HarnessMono),
                    color = palette.muted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
