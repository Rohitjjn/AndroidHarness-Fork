package com.androidharness.app.ui.harness.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.androidharness.app.ui.harness.theme.HarnessMono
import com.androidharness.app.ui.harness.theme.HarnessTheme
import com.androidharness.app.ui.harness.theme.Space

/**
 * The visual primitives of Harness Code: soft cards with hairline borders
 * instead of shadows, pill chips, and 48dp touch targets throughout.
 */

/** Flat card: surface fill, hairline border, generous radius, no shadow. */
@Composable
fun HarnessCard(
    modifier: Modifier = Modifier,
    fill: Color = HarnessTheme.palette.surface,
    border: Color = HarnessTheme.palette.border,
    radius: Dp = 16.dp,
    padding: Dp = Space.lg,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(role = Role.Button) { onClick() } else Modifier),
        color = fill,
        border = if (border.alpha > 0f) BorderStroke(1.dp, border) else null,
        shape = RoundedCornerShape(radius),
    ) {
        Column(Modifier.padding(padding)) { content() }
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val palette = HarnessTheme.palette
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) palette.accent else palette.borderStrong,
        contentColor = if (enabled) palette.onAccent else palette.muted,
        modifier = modifier
            .height(48.dp)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button) { onClick() },
    ) {
        Row(
            Modifier.padding(horizontal = Space.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
fun QuietButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tint: Color = HarnessTheme.palette.text,
) {
    val palette = HarnessTheme.palette
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, palette.border),
        contentColor = if (enabled) tint else palette.faint,
        modifier = modifier
            .height(48.dp)
            .clickable(enabled = enabled, role = Role.Button) { onClick() },
    ) {
        Row(
            Modifier.padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Compact pill used for chips: branch, model, permission mode, context. */
@Composable
fun Pill(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    monospace: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    contentDescription: String? = null,
) {
    val palette = HarnessTheme.palette
    val bg = if (selected) palette.accentSoft else palette.sunken
    val fg = if (selected) palette.accentInk else palette.muted
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        contentColor = fg,
        modifier = modifier
            .height(34.dp)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button) { onClick() } else Modifier),
    ) {
        Row(
            Modifier.padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                label,
                style = if (monospace) MaterialTheme.typography.labelMedium.copy(fontFamily = HarnessMono)
                else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke()
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = HarnessTheme.palette.border) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = HarnessTheme.palette.faint,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Small square glyph tile used in lists and headers. */
@Composable
fun GlyphTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = HarnessTheme.palette.accentInk,
    background: Color = HarnessTheme.palette.accentSoft,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size / 2))
    }
}

/** A soft dot that marks live activity. */
@Composable
fun LiveDot(color: Color = HarnessTheme.palette.success, size: Dp = 7.dp) {
    val transition = rememberInfiniteTransition(label = "live-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "live-dot-alpha",
    )
    Box(Modifier.size(size).alpha(alpha).clip(CircleShape).background(color))
}

/** Throbbing caret appended to a streaming assistant message. */
@Composable
fun StreamingCaret(color: Color = HarnessTheme.palette.accent) {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "caret-alpha",
    )
    Box(Modifier.padding(start = 3.dp).size(width = 2.dp, height = 15.dp).alpha(alpha).background(color))
}

@Composable
fun Monospace(text: String, color: Color = HarnessTheme.palette.code, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall.copy(fontFamily = HarnessMono), color = color)
}

/** Two-line row used everywhere in the drawer, hubs and lists. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val palette = HarnessTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 44.dp else 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) palette.accentSoft else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button) { onClick() } else Modifier)
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) palette.accentInk else palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val palette = HarnessTheme.palette
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(palette.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(Space.lg))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = palette.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.sm))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(Space.lg))
            action()
        }
    }
}

/** Label + value line used inside sheets and cards. */
@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = HarnessTheme.palette
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.muted)
        Text(value, style = MaterialTheme.typography.bodySmall, color = palette.text, maxLines = 2, textAlign = TextAlign.End)
    }
}

/** Full-bleed overlay panel used instead of a modal sheet (phones and tablets). */
@Composable
fun PanelSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(HarnessTheme.palette.paper),
    ) {
        CompositionLocalProvider(LocalContentColor provides HarnessTheme.palette.text) { content() }
    }
}
