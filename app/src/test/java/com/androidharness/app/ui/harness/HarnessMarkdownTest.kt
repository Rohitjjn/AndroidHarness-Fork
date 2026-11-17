package com.androidharness.app.ui.harness

import com.androidharness.app.ui.harness.session.HarnessMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessMarkdownTest {

    @Test fun fencedCodeKeepsEveryLineAndItsLanguage() {
        val blocks = HarnessMarkdown.blocks("Before\n```kotlin\nval x = 1\nprintln(x)\n```\nAfter")
        val code = blocks.filterIsInstance<HarnessMarkdown.Block.Code>().single()
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1\nprintln(x)", code.code)
        assertEquals(2, blocks.filterIsInstance<HarnessMarkdown.Block.Paragraph>().size)
    }

    @Test fun anUnclosedFenceStillRendersCode() {
        val blocks = HarnessMarkdown.blocks("```sh\nls -la")
        val code = blocks.filterIsInstance<HarnessMarkdown.Block.Code>().single()
        assertEquals("sh", code.language)
        assertEquals("ls -la", code.code)
    }

    @Test fun headingsCarryTheirLevel() {
        val blocks = HarnessMarkdown.blocks("# One\n## Two\n### Three")
        assertEquals(listOf(1, 2, 3), blocks.filterIsInstance<HarnessMarkdown.Block.Heading>().map { it.level })
        assertEquals("Two", blocks.filterIsInstance<HarnessMarkdown.Block.Heading>()[1].text)
    }

    @Test fun bulletsAndNumbersBecomeListRows() {
        val blocks = HarnessMarkdown.blocks("- first\n  - nested\n1. one\n12. twelve")
        val bullets = blocks.filterIsInstance<HarnessMarkdown.Block.Bullet>()
        assertEquals(listOf("first", "nested"), bullets.map { it.text })
        assertEquals(1, bullets[1].depth)
        val numbers = blocks.filterIsInstance<HarnessMarkdown.Block.Numbered>()
        assertEquals(listOf("1", "12"), numbers.map { it.number })
    }

    @Test fun quotesAndRulesAreSeparateBlocks() {
        val blocks = HarnessMarkdown.blocks("> quoted\n---")
        assertEquals("quoted", blocks.filterIsInstance<HarnessMarkdown.Block.Quote>().single().text)
        assertTrue(blocks.contains(HarnessMarkdown.Block.Rule))
    }

    @Test fun consecutiveLinesJoinIntoOneParagraph() {
        val blocks = HarnessMarkdown.blocks("first line\nsecond line\n\nnew paragraph")
        val paragraphs = blocks.filterIsInstance<HarnessMarkdown.Block.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertEquals("first line second line", paragraphs[0].text)
    }

    @Test fun codeInsideAFenceIsNeverParsedAsMarkdown() {
        val blocks = HarnessMarkdown.blocks("```\n# not a heading\n- not a bullet\n```")
        val code = blocks.filterIsInstance<HarnessMarkdown.Block.Code>().single()
        assertEquals("# not a heading\n- not a bullet", code.code)
        assertTrue(blocks.none { it is HarnessMarkdown.Block.Heading })
    }

    @Test fun blankInputProducesNoBlocks() {
        assertTrue(HarnessMarkdown.blocks("   \n\n ").isEmpty())
    }
}
