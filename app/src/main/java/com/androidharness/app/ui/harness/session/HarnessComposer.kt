package com.androidharness.app.ui.harness.session

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidharness.app.agent.session.HarnessSessionState
import com.androidharness.app.agent.session.QueuedMessage
import com.androidharness.app.agent.session.SessionPermission
import com.androidharness.app.agent.session.SessionPhase
import com.androidharness.app.ui.harness.components.Pill
import com.androidharness.app.ui.harness.theme.HarnessTheme
import com.androidharness.app.ui.harness.theme.Space

/** Everything the composer can ask of the session, with no view-model types. */
data class ComposerActions(
    val send: (String) -> Unit,
    val stop: () -> Unit,
    val attachImage: (android.net.Uri) -> Unit,
    val attachments: Int,
    val models: List<ModelChoice>,
    val onSelectModel: (ModelChoice) -> Unit,
    val cancelQueued: (String) -> Unit,
    val sendQueuedNow: (String) -> Unit,
)

data class ModelChoice(val providerId: String, val providerName: String, val model: String)

private val slashCommands = listOf(
    "/compact" to "Compact the conversation",
    "/clear" to "Start a new session",
    "/cost" to "Show token and cost usage",
    "/init" to "Write CLAUDE.md for this project",
    "/doctor" to "Check every tool family",
    "/plan" to "Switch to Plan mode",
    "/btw" to "Ask a side question",
    "/skills" to "List installed skills",
)

@Composable
fun HarnessComposer(
    state: HarnessSessionState,
    session: com.androidharness.app.agent.session.AgentSession,
    actions: ComposerActions,
    initialDraft: String? = null,
    onOpenPermission: () -> Unit,
) {
    val palette = HarnessTheme.palette
    var text by rememberSaveable(state.sessionId) { mutableStateOf("") }
    var plusOpen by remember { mutableStateOf(false) }
    var modelsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) actions.attachImage(uri)
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) text = if (text.isBlank()) spoken else "$text $spoken"
        }
    }

    LaunchedEffect(initialDraft) {
        if (!initialDraft.isNullOrBlank() && text.isBlank()) text = initialDraft
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.paper)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        if (state.queue.isNotEmpty()) {
            Column(Modifier.padding(bottom = Space.sm)) {
                for (queued in state.queue) QueueRow(queued, actions, first = state.queue.first().id == queued.id)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(palette.surface, RoundedCornerShape(20.dp))
                .padding(horizontal = Space.md, vertical = Space.sm),
        ) {
            if (actions.attachments > 0) {
                Text(
                    "${actions.attachments} attachment" + if (actions.attachments == 1) "" else "s",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.faint,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = palette.text)),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp, max = 160.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            if (state.planMode) "Describe what to plan…" else "Reply, or ask for more…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.faint,
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.height(Space.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { plusOpen = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "Add attachment, command or voice", tint = palette.muted, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = plusOpen, onDismissRequest = { plusOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Photo") },
                            leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                            onClick = { plusOpen = false; imagePicker.launch("image/*") },
                        )
                        DropdownMenuItem(
                            text = { Text("Voice") },
                            leadingIcon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
                            onClick = {
                                plusOpen = false
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")
                                }
                                runCatching { speech.launch(intent) }.onFailure { text = text }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Slash commands") },
                            leadingIcon = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                            onClick = { plusOpen = false; text = "/" },
                        )
                        DropdownMenuItem(
                            text = { Text("Skills") },
                            leadingIcon = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                            onClick = { plusOpen = false; text = "/skills" },
                        )
                    }
                }

                // Model chip
                Box {
                    Pill(
                        label = state.modelLabel ?: "Model",
                        icon = Icons.Outlined.Terminal,
                        onClick = { modelsOpen = true },
                        contentDescription = "Choose model",
                    )
                    DropdownMenu(expanded = modelsOpen, onDismissRequest = { modelsOpen = false }) {
                        if (actions.models.isEmpty()) {
                            DropdownMenuItem(text = { Text("No provider configured") }, onClick = { modelsOpen = false })
                        }
                        for (choice in actions.models) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(choice.model, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(choice.providerName, style = MaterialTheme.typography.labelSmall, color = palette.muted)
                                    }
                                },
                                trailingIcon = {
                                    if (choice.model == state.modelLabel) Icon(Icons.Filled.Check, contentDescription = null, tint = palette.accent)
                                },
                                onClick = { modelsOpen = false; actions.onSelectModel(choice) },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(Space.sm))
                Pill(
                    label = state.permission.label,
                    selected = state.permission != SessionPermission.ASK,
                    onClick = onOpenPermission,
                    contentDescription = "Permission mode: ${state.permission.label}",
                )
                Spacer(Modifier.weight(1f))
                ContextRing(state.contextFraction, state.contextUsed, state.contextMax)
                Spacer(Modifier.width(Space.sm))
                if (state.busy) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(palette.text, CircleShape)
                            .clickable { actions.stop() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(11.dp).background(palette.paper, RoundedCornerShape(2.dp)))
                    }
                } else {
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(if (text.isBlank()) palette.borderStrong else palette.accent, CircleShape)
                            .clickable(enabled = text.isNotBlank()) {
                                val draft = text.trim()
                                if (draft.isNotEmpty()) {
                                    actions.send(draft)
                                    text = ""
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = "Send",
                            tint = if (text.isBlank()) palette.faint else palette.onAccent,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }

        if (text.startsWith("/")) {
            val matches = slashCommands.filter { it.first.startsWith(text.trim().split(" ").first()) }
            if (matches.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.sm)
                        .background(palette.surface, RoundedCornerShape(12.dp))
                        .padding(vertical = Space.xs),
                ) {
                    for (command in matches.take(6)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { text = command.first + " " }.padding(horizontal = Space.md, vertical = Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(command.first, style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.androidharness.app.ui.harness.theme.HarnessMono), color = palette.accentInk)
                            Spacer(Modifier.width(Space.md))
                            Text(command.second, style = MaterialTheme.typography.labelSmall, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (state.phase == SessionPhase.WAITING_APPROVAL) {
            Text(
                "Waiting for your approval above",
                style = MaterialTheme.typography.labelSmall,
                color = palette.faint,
                modifier = Modifier.padding(top = Space.xs, start = Space.sm),
            )
        }
    }
}

@Composable
private fun QueueRow(queued: QueuedMessage, actions: ComposerActions, first: Boolean) {
    val palette = HarnessTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .background(palette.sunken, RoundedCornerShape(10.dp))
            .padding(horizontal = Space.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("QUEUED", style = MaterialTheme.typography.labelSmall, color = palette.faint)
        Spacer(Modifier.width(Space.sm))
        Text(
            queued.text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (first) {
            Text(
                "Send now",
                style = MaterialTheme.typography.labelSmall,
                color = palette.accentInk,
                modifier = Modifier.clickable { actions.sendQueuedNow(queued.id) }.padding(horizontal = Space.sm),
            )
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Cancel queued message",
                tint = palette.faint,
                modifier = Modifier.size(14.dp).clickable { actions.cancelQueued(queued.id) },
            )
        }
    }
}

/** Context ring: used vs. window, amber near 75%, red near 90%. */
@Composable
fun ContextRing(fraction: Float, used: Int, max: Int) {
    val palette = HarnessTheme.palette
    val color = when {
        fraction >= 0.9f -> palette.danger
        fraction >= 0.75f -> palette.warning
        else -> palette.muted
    }
    Canvas(
        Modifier
            .size(26.dp)
            .clickable { }
            .padding(1.dp),
    ) {
        val stroke = size.minDimension * 0.12f
        drawArc(
            color = palette.border,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * fraction.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

fun contextLabel(used: Int, max: Int): String =
    if (max <= 0) "$used tokens" else "${(used * 100 / max)}% of context"
