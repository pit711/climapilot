package com.climapilot.free

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests for [UpdateChecker.plainText], which turns a GitHub release body (Markdown) into the plain
 * text the update dialog shows. The dialog has no Markdown renderer, so anything left behind is
 * literal noise in front of the user — these cases are the markup the project's own release notes use.
 */
class ReleaseNotesTest {

    @Test
    fun stripsHeadingMarkers() {
        assertEquals("🇬🇧 New", UpdateChecker.plainText("### 🇬🇧 New"))
        assertEquals("Fixed", UpdateChecker.plainText("# Fixed"))
    }

    @Test
    fun unwrapsBoldAndItalic() {
        assertEquals(
            "Indoor temperature calibration (Options tab)",
            UpdateChecker.plainText("**Indoor temperature calibration** (*Options tab*)"),
        )
    }

    @Test
    fun keepsOnlyTheLinkLabel() {
        assertEquals(
            "Suggested on r/MideaPortaSplit: many units read off.",
            UpdateChecker.plainText(
                "Suggested on [r/MideaPortaSplit](https://www.reddit.com/r/MideaPortaSplit/): many units read off.",
            ),
        )
    }

    @Test
    fun turnsListDashesIntoBullets() {
        assertEquals("•  first\n•  second", UpdateChecker.plainText("- first\n- second"))
    }

    @Test
    fun dropsHorizontalRulesAndCollapsesBlankLines() {
        assertEquals("above\n\nbelow", UpdateChecker.plainText("above\n\n---\n\nbelow"))
    }

    @Test
    fun leavesPlainProseUntouched() {
        val prose = "Version 0.6.6 is available — you're on 0.6.5. Nothing to unwrap here."
        assertEquals(prose, UpdateChecker.plainText(prose))
    }

    @Test
    fun keepsMultiplicationAndUnitsIntact() {
        // EN: A lone asterisk or underscore inside prose must survive — release notes mention widget
        //     sizes like 1×1 and identifiers with underscores.
        val text = "Widget in 1×1 / 2×2 · net_ac_E6DE · 50 % limit"
        assertEquals(text, UpdateChecker.plainText(text))
    }

    @Test
    fun handlesTheRealReleaseBody() {
        val body = """
            Suggested on [r/MideaPortaSplit](https://www.reddit.com/r/MideaPortaSplit/): many units read a degree off.

            ### 🇬🇧 New
            - **Indoor temperature calibration** (*Options tab*) — dial in **±5 K in 0.5 K steps**.

            ---
            Install: download `climapilot-0.6.7.apk` below.
        """.trimIndent()
        val out = UpdateChecker.plainText(body)
        assertFalse("no heading markers left", out.contains("###"))
        assertFalse("no bold markers left", out.contains("**"))
        assertFalse("no link syntax left", out.contains(']') || out.contains("](" ))
        assertFalse("no backticks left", out.contains('`'))
        assertEquals(
            """
            Suggested on r/MideaPortaSplit: many units read a degree off.

            🇬🇧 New
            •  Indoor temperature calibration (Options tab) — dial in ±5 K in 0.5 K steps.

            Install: download climapilot-0.6.7.apk below.
            """.trimIndent(),
            out,
        )
    }
}
