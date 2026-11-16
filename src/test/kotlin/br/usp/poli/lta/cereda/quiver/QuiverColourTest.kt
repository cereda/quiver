// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tests of the colours of the tool.
 *
 * The palette is the only thing that paints, and the policy is the only thing
 * that decides whether the palette paints at all; the tests exercise both,
 * and they check the rule that matters most: a session whose output is piped
 * is the same session, byte for byte.
 */
class QuiverColourTest {

    @Test
    fun `a plain line is not painted`() {
        val palette = Palette(true)
        assertEquals("hello", palette.paint(ReportLine("hello")))
    }

    @Test
    fun `a recognized line is painted green`() {
        val palette = Palette(true)
        val painted = palette.paint(ReportLine("true", LineKind.Recognized))
        assertContains(painted, "true")
        assertTrue(painted.startsWith("\u001b["), "The line was not painted")
        assertTrue(painted.endsWith("\u001b[0m"), "The style was not ended")
    }

    @Test
    fun `a rejected line is painted red`() {
        val palette = Palette(true)
        val painted = palette.paint(ReportLine("false", LineKind.Rejected))
        assertContains(painted, "false")
        assertTrue(painted.startsWith("\u001b[31m"), "The line was not painted red")
    }

    @Test
    fun `a failure is painted in bold red`() {
        val palette = Palette(true)
        val painted = palette.paint(ReportLine("error: nope", LineKind.Failure))
        assertContains(painted, "error: nope")
        assertTrue(painted.startsWith("\u001b[1;31m"), "The failure was not painted in bold red")
    }

    @Test
    fun `the banner is painted dim`() {
        val palette = Palette(true)
        val painted = palette.paint(ReportLine("Quiver REPL", LineKind.Banner))
        assertTrue(painted.startsWith("\u001b[2m"), "The banner was not painted dim")
    }

    @Test
    fun `a disabled palette paints nothing`() {
        val palette = Palette(false)
        assertEquals("true", palette.paint(ReportLine("true", LineKind.Recognized)))
        assertEquals("error: nope", palette.paint(ReportLine("error: nope", LineKind.Failure)))
    }

    @Test
    fun `the prompt is painted cyan`() {
        val palette = Palette(true)
        val painted = palette.paintPrompt("quiver> ")
        assertContains(painted, "quiver> ")
        assertTrue(painted.startsWith("\u001b[36m"), "The prompt was not painted cyan")
    }

    @Test
    fun `a disabled palette paints no prompt`() {
        val palette = Palette(false)
        assertEquals("quiver> ", palette.paintPrompt("quiver> "))
    }

    @Test
    fun `the terminal decides when the user makes no choice`() {
        val policy = ColourPolicy(null)
        assertTrue(policy.paints(true))
        assertFalse(policy.paints(false))
    }

    @Test
    fun `the user may force the colours on`() {
        val policy = ColourPolicy(true)
        assertTrue(policy.paints(false))
        assertTrue(policy.paints(true))
    }

    @Test
    fun `the user may force the colours off`() {
        val policy = ColourPolicy(false)
        assertFalse(policy.paints(true))
        assertFalse(policy.paints(false))
    }

    @Test
    fun `the environment variable turns the colours off`() {
        // The convention says that the variable forbids the colours when it
        // is present and not empty, whatever its value.
        val policy = ColourPolicy(null, forbidden = true)
        assertFalse(policy.paints(true))
        // The choice of the user overrides the environment.
        assertTrue(ColourPolicy(true, forbidden = true).paints(true))
        assertFalse(ColourPolicy(false, forbidden = false).paints(true))
    }

    @Test
    fun `the environment variable is empty when the environment does not forbid the colours`() {
        // The variable of the environment of the test suite decides, and the
        // policy reads it when it is built.
        val policy = ColourPolicy(null)
        val expected = System.getenv("NO_COLOR").isNullOrEmpty()
        assertEquals(expected, policy.paints(true))
    }

    @Test
    fun `the verdict of a recognition is a recognized line`() {
        assertEquals(LineKind.Recognized, verdict(true).kind)
        assertEquals("true", verdict(true).text)
    }

    @Test
    fun `the verdict of a rejection is a rejected line`() {
        assertEquals(LineKind.Rejected, verdict(false).kind)
        assertEquals("false", verdict(false).text)
    }

    @Test
    fun `the result of the debug mode follows the verdict`() {
        assertEquals(LineKind.Recognized, resultLine("run to completion: result=true", true).kind)
        assertEquals(LineKind.Rejected, resultLine("run to completion: result=false", false).kind)
    }
}
