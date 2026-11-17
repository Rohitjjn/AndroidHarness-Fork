package com.androidharness.app.ui.harness.session

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidharness.app.ui.harness.theme.HarnessMono
import com.androidharness.app.ui.harness.theme.HarnessTheme
import com.androidharness.app.ui.harness.theme.Space

/**
 * A deliberately small Markdown subset: headings, paragraphs, bullet and
 * numbered lists, quotes, rules and fenced code. Rich blocks (tables, charts,
 * diagrams) stay in the original renderer; this is the transcript view for the
 * new session screen, and it never blocks streaming.
 */
object HarnessMarkdown {

    sealed interface Block {
        data class Paragraph(val text: String) : Block
        data class Heading(val level: Int, val text: String) : Block
        data class Bullet(val text: String, val depth: Int) : Block
        data class Numbered(val number: String, val text: String, val depth: Int) : Block
        data class Quote(val text: String) : Block
        data class Code(val language: String?, val code: String) : Block
        data object Rule : Block
    }

    private val fence = Regex("^\\s*```\\s*([A-Za-z0-9_+#.-]*)\\s*$")
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val numbered = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")

    fun blocks(markdown: String): List<Block> {
        if (markdown.isBlank()) return emptyList()
        val out = ArrayList<Block>()
        val paragraph = StringBuilder()
        var code: StringBuilder? = null
        var codeLang: String? = null

        fun flushParagraph() {
            if (paragraph.isNotBlank()) out += Block.Paragraph(paragraph.toString().trim())
            paragraph.setLength(0)
        }

        for (raw in markdown.split('\n')) {
            val line = raw.trimEnd()
            val fenceMatch = fence.find(line)
            if (fenceMatch != null) {
                if (code == null) {
                    flushParagraph()
                    code = StringBuilder()
                    codeLang = fenceMatch.groupValues[1].ifBlank { null }
                } else {
                    out += Block.Code(codeLang, code.toString().trimEnd('\n'))
                    code = null
                    codeLang = null
                }
                continue
            }
            if (code != null) {
                code.append(line).append('\n')
                continue
            }
            when {
                line.isBlank() -> flushParagraph()
                line.trim() == "---" || line.trim() == "***" -> {
                    flushParagraph(); out += Block.Rule
                }
                heading.matches(line) -> {
                    flushParagraph()
                    val match = heading.find(line)!!
                    out += Block.Heading(match.groupValues[1].length, match.groupValues[2].trim())
                }
                bullet.matches(line) -> {
                    flushParagraph()
                    val match = bullet.find(line)!!
                    out += Block.Bullet(match.groupValues[2].trim(), match.groupValues[1].length / 2)
                }
                numbered.matches(line) -> {
                    flushParagraph()
                    val match = numbered.find(line)!!
                    out += Block.Numbered(match.groupValues[2], match.groupValues[3].trim(), match.groupValues[1].length / 2)
                }
                line.trimStart().startsWith(">") -> {
                    flushParagraph()
                    out += Block.Quote(line.trimStart().removePrefix(">").trim())
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
        }
        if (code != null) out += Block.Code(codeLang, code.toString().trimEnd('\n'))
        flushParagraph()
        return out
    }

    /** Inline spans: **bold**, *italic*, `code`, [link](url). */
    fun inline(
        text: String,
        codeColor: androidx.compose.ui.graphics.Color? = null,
        accent: androidx.compose.ui.graphics.Color? = null,
    ): AnnotatedString = buildAnnotatedString {
        var i = 0
        val plain = StringBuilder()

        fun flush() {
            if (plain.isNotEmpty()) {
                append(plain.toString())
                plain.setLength(0)
            }
        }

        while (i < text.length) {
            val rest = text.substring(i)
            when {
                rest.startsWith("**") && text.indexOf("**", i + 2) > i -> {
                    val end = text.indexOf("**", i + 2)
                    flush()
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
                rest.startsWith("`") && text.indexOf('`', i + 1) > i -> {
                    val end = text.indexOf('`', i + 1)
                    flush()
                    withStyle(SpanStyle(fontFamily = HarnessMono, color = codeColor ?: androidx.compose.ui.graphics.Color.Unspecified)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                }
                rest.startsWith("[") && text.indexOf("](", i) > i && text.indexOf(')', text.indexOf("](", i)) > i -> {
                    val mid = text.indexOf("](", i)
                    val end = text.indexOf(')', mid)
                    flush()
                    val label = text.substring(i + 1, mid)
                    withStyle(SpanStyle(color = accent ?: androidx.compose.ui.graphics.Color.Unspecified, fontWeight = FontWeight.Medium)) {
                        append(label)
                    }
                    i = end + 1
                }
                rest.startsWith("*") && text.indexOf('*', i + 1) > i + 1 -> {
                    val end = text.indexOf('*', i + 1)
                    flush()
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
                else -> {
                    plain.append(text[i])
                    i++
                }
            }
        }
        flush()
    }
}

/** Renders one markdown string in the transcript voice. */
@Composable
fun HarnessMarkdownText(text: String, modifier: Modifier = Modifier) {
    val palette = HarnessTheme.palette
    val blocks = androidx.compose.runtime.remember(text) { HarnessMarkdown.blocks(text) }
    Column(modifier.fillMaxWidth()) {
        for (block in blocks) {
            when (block) {
                is HarnessMarkdown.Block.Paragraph -> {
                    Text(
                        HarnessMarkdown.inline(block.text, palette.accentInk, palette.accentInk),
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.text,
                        modifier = Modifier.padding(bottom = Space.sm),
                    )
                }
                is HarnessMarkdown.Block.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        HarnessMarkdown.inline(block.text, palette.accentInk, palette.accentInk),
                        style = style.copy(fontFamily = com.androidharness.app.ui.harness.theme.HarnessSerif),
                        color = palette.text,
                        modifier = Modifier.padding(top = Space.sm, bottom = Space.xs),
                    )
                }
                is HarnessMarkdown.Block.Bullet -> {
                    Row(Modifier.padding(bottom = Space.xs, start = (block.depth * 12).dp)) {
                        Text("•", style = MaterialTheme.typography.bodyLarge, color = palette.muted, modifier = Modifier.width(16.dp))
                        Text(
                            HarnessMarkdown.inline(block.text, palette.accentInk, palette.accentInk),
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.text,
                        )
                    }
                }
                is HarnessMarkdown.Block.Numbered -> {
                    Row(Modifier.padding(bottom = Space.xs, start = (block.depth * 12).dp)) {
                        Text(
                            "${block.number}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.muted,
                            modifier = Modifier.width(22.dp),
                        )
                        Text(
                            HarnessMarkdown.inline(block.text, palette.accentInk, palette.accentInk),
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.text,
                        )
                    }
                }
                is HarnessMarkdown.Block.Quote -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.xs)
                            .background(palette.sunken, RoundedCornerShape(8.dp))
                            .padding(horizontal = Space.md, vertical = Space.sm),
                    ) {
                        Text(
                            HarnessMarkdown.inline(block.text, palette.accentInk, palette.accentInk),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted,
                        )
                    }
                }
                is HarnessMarkdown.Block.Code -> CodeBlock(block.language, block.code)
                HarnessMarkdown.Block.Rule -> {
                    Spacer(Modifier.height(Space.sm))
                    androidx.compose.material3.HorizontalDivider(color = palette.border)
                    Spacer(Modifier.height(Space.sm))
                }
            }
        }
    }
}

@Composable
fun CodeBlock(language: String?, code: String, modifier: Modifier = Modifier) {
    val palette = HarnessTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.codeBlock),
    ) {
        if (!language.isNullOrBlank()) {
            Text(
                language.lowercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = HarnessMono),
                color = palette.faint,
                modifier = Modifier.padding(start = Space.md, top = Space.sm),
            )
        }
        Text(
            code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = HarnessMono, lineHeight = 19.sp),
            color = palette.code,
            modifier = Modifier
                .padding(horizontal = Space.md, vertical = Space.sm)
                .horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
fun MonoLine(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color? = null) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = HarnessMono),
        color = color ?: HarnessTheme.palette.code,
    )
}

@Composable
fun ChipRow(items: List<String>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        for (item in items) {
            Text(
                item,
                style = MaterialTheme.typography.labelSmall,
                color = HarnessTheme.palette.muted,
                modifier = Modifier
                    .background(HarnessTheme.palette.sunken, RoundedCornerShape(999.dp))
                    .padding(horizontal = Space.sm, vertical = 3.dp),
            )
        }
    }
}
