// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The tests of the summary of a program.
 *
 * The summary is the seam the surfaces of the tool share: the REPL reports it
 * when a program is loaded, and the `inspect` command reports it as well.
 * The tests exercise the summary on its own, and the reports it yields.
 */
class QuiverProgramTest {

    @Test
    fun `a program is loaded and summarized`() {
        val result = loadProgram(SIMPLE)
        val loaded = assertIs<LoadResult.Loaded>(result)
        assertEquals(SIMPLE, loaded.summary.path)
        assertEquals(0, loaded.summary.rules.size)
        assertTrue(loaded.summary.header.present)
    }

    @Test
    fun `a program with no header has no facts`() {
        val result = loadProgram(NO_FSM)
        val loaded = assertIs<LoadResult.Loaded>(result)
        assertTrue(!loaded.summary.header.present)
        assertEquals(0, loaded.summary.header.transitions)
    }

    @Test
    fun `a missing file fails the load`() {
        val result = loadProgram("src/test/resources/does-not-exist.quiver")
        val failed = assertIs<LoadResult.Failed>(result)
        assertContains(failed.detail, "does-not-exist.quiver")
    }

    @Test
    fun `the root of the filesystem fails the load`() {
        // The root of the filesystem has no directory of its own, and the
        // load must not fall over it.
        val result = loadProgram("/")
        assertIs<LoadResult.Failed>(result)
    }

    @Test
    fun `a malformed program fails the load`() {
        val result = loadProgram("src/test/resources/error-bad-mapping-left.quiver")
        val failed = assertIs<LoadResult.Failed>(result)
        assertContains(failed.detail, "Invalid FSM mapping")
    }

    @Test
    fun `the facts of the header are the ones of the automaton`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(SIMPLE))
        val header = loaded.summary.header
        assertEquals("q0", header.start)
        assertEquals(listOf("q2"), header.accepting)
        assertEquals(listOf("q0", "q1", "q2"), header.states)
        assertEquals(listOf("a", "b"), header.alphabet)
        assertEquals(2, header.transitions)
        assertEquals(null, header.input)
    }

    @Test
    fun `the facts of the header carry the input of the program`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(INPUT))
        assertEquals(listOf("a", "b"), loaded.summary.header.input)
    }

    @Test
    fun `the symbols of an automaton are the ones it reacts to`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(SIMPLE))
        assertEquals(listOf("a", "b"), symbolsOf(loaded.summary.automaton))
    }

    @Test
    fun `the report of a load names the program`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(SIMPLE))
        val report = loadReport(loaded.summary)
        assertEquals(
            "Loaded $SIMPLE: 0 rule(s), 0 strategy(ies)",
            report.first().text,
        )
        assertEquals("  FSM: start=q0, accepting=q2", report[1].text)
        assertEquals("  mapping: 2 transition(s)", report[2].text)
        assertEquals("  input: (none declared; give one on the command line)", report[3].text)
    }

    @Test
    fun `the report of a load names the input of the program`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(INPUT))
        val report = loadReport(loaded.summary)
        assertEquals("  input: 2 symbol(s): a, b", report.last().text)
    }

    @Test
    fun `the report of a load of a program with no header says so`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(NO_FSM))
        val report = loadReport(loaded.summary)
        assertEquals(
            "  (no FSM header; recognition requires :load a file with @fsm)",
            report.last().text,
        )
    }

    @Test
    fun `the report of the rules names the active strategy`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(STRATEGY))
        val report = rulesReport(loaded.summary)
        assertEquals("Loaded rules (1):", report.first().text)
        assertEquals("Active strategy: REPEAT (1 rules)", report.last().text)
    }

    @Test
    fun `the report of the rules separates the rules the strategy ignores`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(INDEX_STRATEGY))
        val report = rulesReport(loaded.summary).map { it.text }
        assertContains(report, "Top-level rules (1), not consulted by the active strategy:")
        assertContains(report, "Active strategy: FIRST (1 rules)")
    }

    @Test
    fun `the report of the rules of an empty session says so`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(NO_FSM))
        // (the program has a top-level rule, so the report lists it)
        assertEquals("Loaded rules (1):", rulesReport(loaded.summary).first().text)
    }

    @Test
    fun `the report of the inspection names the program and its facts`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(SIMPLE))
        val report = inspectReport(loaded.summary).map { it.text }
        assertEquals("Program: $SIMPLE", report[0])
        assertEquals("  top-level rules: 0", report[1])
        assertEquals("  strategies: 0", report[2])
        assertEquals("  FSM: start=q0, accepting=q2", report[3])
        assertEquals("    states: q0, q1, q2", report[4])
        assertEquals("    alphabet: a, b", report[5])
        assertEquals("    transitions: 2", report[6])
        assertEquals("  input: (none declared; give one on the command line)", report[7])
        assertEquals("  active strategy: default (FIRST, 0 rules)", report[8])
    }

    @Test
    fun `the report of the inspection names the strategies of the program`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(STRATEGY))
        val report = inspectReport(loaded.summary).map { it.text }
        assertContains(report, "  strategies:")
        assertContains(report, "    repeatTest (REPEAT, 1 rules)")
        assertContains(report, "  active strategy: repeatTest (REPEAT, 1 rules)")
        assertContains(report, "  rules:")
    }

    @Test
    fun `the report of the inspection separates the rules the strategy ignores`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(INDEX_STRATEGY))
        val report = inspectReport(loaded.summary).map { it.text }
        assertContains(report, "  top-level rules (1), not consulted by the active strategy:")
    }

    @Test
    fun `the report of the inspection of a program with no header says so`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(NO_FSM))
        val report = inspectReport(loaded.summary).map { it.text }
        assertContains(report, "  (no FSM header; recognition requires a file with @fsm)")
    }

    @Test
    fun `the line of a rule names its pattern and its clauses`() {
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(STRATEGY))
        val rule = loaded.summary.activeStrategy.rules.first()
        assertContains(ruleLine(0, rule), "[0] ")
        assertContains(ruleLine(0, rule), "clause(s)")
    }

    @Test
    fun `a label that two strategies share names the last of them`() {
        // The command line, the session and the inspection all read the same
        // body of the program, so they cannot disagree about the strategy a
        // label names.
        val loaded = assertIs<LoadResult.Loaded>(loadProgram(TWIN))
        assertEquals(2, loaded.summary.strategies.size)
        assertEquals("twin", loaded.summary.activeStrategy.label)
        assertEquals(
            loaded.summary.activeStrategy,
            loadBody(File(TWIN).readText(), ".", File(TWIN).absolutePath).activeStrategy,
        )
    }

    /** The paths of the programs of the test suite. */
    private companion object {
        const val SIMPLE = "src/test/resources/simple.quiver"
        const val NO_FSM = "src/test/resources/no-fsm.quiver"
        const val INPUT = "src/test/resources/input.quiver"
        const val STRATEGY = "src/test/resources/strategy.quiver"
        const val INDEX_STRATEGY = "src/test/resources/index-strategy.quiver"
        const val TWIN = "src/test/resources/twin-strategy.quiver"
    }
}
