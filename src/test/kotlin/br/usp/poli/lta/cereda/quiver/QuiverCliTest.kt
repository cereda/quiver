// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tests of the command-line interface.
 *
 * The interface reports its outcome through an exit status, so the tests can
 * drive every path of it, the successful ones and the failing ones, without
 * terminating the test run.
 */
class QuiverCliTest {

    @Test
    fun `a recognized input is reported as true`() {
        val run = cli("-i", SIMPLE, "a", "b")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `a rejected input is reported as false`() {
        val run = cli("-i", SIMPLE, "a")
        assertEquals(0, run.status)
        assertContains(run.stdout, "false")
    }

    @Test
    fun `the trace option reports the steps on the error stream`() {
        val run = cli("-i", SIMPLE, "a", "b", "--trace")
        assertEquals(0, run.status)
        assertContains(run.stderr, "trace step")
    }

    @Test
    fun `the max steps option bounds the run`() {
        val run = cli("-i", SIMPLE, "a", "b", "--max-steps", "1")
        assertEquals(1, run.status)
        assertContains(run.stderr, "Step budget exhausted")
    }

    @Test
    fun `the help option prints the usage summary`() {
        val run = cli("--help")
        assertEquals(0, run.status)
        assertContains(run.stdout, "Usage: quiver -i <file.quiver> [<symbol>...]")
        assertContains(run.stdout, "--max-steps N")
    }

    @Test
    fun `the short help option prints the usage summary`() {
        val run = cli("-h")
        assertEquals(0, run.status)
        assertContains(run.stdout, "Usage: quiver -i <file.quiver> [<symbol>...]")
    }

    @Test
    fun `a missing file argument is a usage error`() {
        val run = cli("-i")
        assertEquals(1, run.status)
        assertContains(run.stderr, "error: missing argument to -i")
        assertContains(run.stderr, "Usage: quiver")
    }

    @Test
    fun `a missing max steps argument is a usage error`() {
        val run = cli("-i", SIMPLE, "a", "b", "--max-steps")
        assertEquals(1, run.status)
        assertContains(run.stderr, "error: missing argument to --max-steps")
    }

    @Test
    fun `an invalid max steps argument is a usage error`() {
        val run = cli("-i", SIMPLE, "a", "b", "--max-steps", "many")
        assertEquals(1, run.status)
        assertContains(run.stderr, "error: invalid argument to --max-steps: many")
    }

    @Test
    fun `every trailing argument is one symbol of the input`() {
        // The arguments are the symbols of the input, one by one: the input
        // of `simple.quiver` is the two symbols `a` and `b`.
        val run = cli("-i", SIMPLE, "a", "b")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `a missing input file is a usage error`() {
        val run = cli("ab")
        assertEquals(1, run.status)
        assertContains(run.stderr, "a source file is required (-i <file.quiver>)")
    }

    @Test
    fun `a program that declares no input fails`() {
        // The program declares no input, and the command line gives none:
        // the run fails, and the usage summary is not printed.
        val run = cli("-i", SIMPLE)
        assertEquals(1, run.status)
        assertContains(run.stderr, "error: the program declares no input; give one on the command line")
        assertTrue(!run.stderr.contains("Usage:"), "The usage summary was printed on a failed run")
    }

    @Test
    fun `a missing source file is reported`() {
        val run = cli("-i", "src/test/resources/does-not-exist.quiver", "a", "b")
        assertEquals(1, run.status)
        assertContains(run.stderr, "error:")
    }

    @Test
    fun `a malformed program is reported`() {
        val run = cli("-i", "src/test/resources/error-bad-mapping-left.quiver", "a")
        assertEquals(1, run.status)
        assertContains(run.stderr, "Invalid FSM mapping")
    }

    @Test
    fun `the includes of the source file are resolved relative to it`() {
        val run = cli("-i", "src/test/resources/include-main.quiver", "a", "b")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `the usage summary is not printed on a successful run`() {
        val run = cli("-i", SIMPLE, "a", "b")
        assertTrue(!run.stdout.contains("Usage:"), "The usage summary was printed on a successful run")
    }

    @Test
    fun `the entry point without arguments starts the repl`() {
        val output = captureStdout {
            withStandardInput(":quit\n") { runEntryPoint(emptyArray()) }
        }
        assertContains(output, "Quiver REPL: a term rewriting language for adaptive automata")
    }

    @Test
    fun `the entry point with arguments runs the command line`() {
        val output = captureStdout { runEntryPoint(arrayOf("-i", SIMPLE, "a", "b")) }
        assertContains(output, "true")
    }

    @Test
    fun `the nondet flag enables non-deterministic recognition`() {
        // nondet.quiver has two (q0,a) transitions; only --nondet accepts 'a'
        // because the second path reaches the accepting state.
        val run = cli("-i", NONDET, "a", "--nondet")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `the nondet flag rejects when all paths die`() {
        val run = cli("-i", NONDET, "a", "b", "--nondet")
        assertEquals(0, run.status)
        assertContains(run.stdout, "false")
    }

    @Test
    fun `without nondet flag behavior is deterministic`() {
        // Without --nondet, only the first (q0,a) transition is used.
        val run = cli("-i", NONDET, "a")
        assertEquals(0, run.status)
        assertContains(run.stdout, "false")
    }

    @Test
    fun `the nondet flag is accepted before the input symbols`() {
        val run = cli("-i", NONDET, "--nondet", "a")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `the trace and the nondet flags work together`() {
        val run = cli("-i", NONDET, "a", "--nondet", "--trace")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
        assertContains(run.stderr, "trace step")
    }

    @Test
    fun `the main entry point runs the command line`() {
        val output = captureStdout { main(arrayOf("-i", SIMPLE, "a", "b")) }
        assertContains(output, "true")
    }

    @Test
    fun `the usage summary names every option`() {
        val run = cli("--help")
        assertContains(run.stdout, "-i <file.quiver>")
        assertContains(run.stdout, "<symbol>...")
        assertContains(run.stdout, "--trace")
        assertContains(run.stdout, "--nondet")
        assertContains(run.stdout, "--max-steps N")
        assertContains(run.stdout, "-h, --help")
    }

    @Test
    fun `the max steps option is accepted before the input symbols`() {
        val run = cli("-i", SIMPLE, "--max-steps", "10", "a", "b")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `the end of options marker ends the options`() {
        // Everything after the marker is a symbol of the input, and the
        // options that follow it are symbols as well.
        val run = cli("-i", SIMPLE, "--", "a", "b")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `an option after the end of options marker is a symbol`() {
        val run = cli("-i", SIMPLE, "--", "--trace")
        assertEquals(0, run.status)
        assertContains(run.stdout, "false")
        assertTrue(!run.stderr.contains("trace step"), "The trace was turned on after the marker")
    }

    @Test
    fun `the empty argument is the empty input`() {
        // The empty argument is the empty input: the run reads nothing, and
        // the start state of the recognizer does not accept.
        val run = cli("-i", SIMPLE, "")
        assertEquals(0, run.status)
        assertContains(run.stdout, "false")
    }

    @Test
    fun `the empty argument cannot be combined with other symbols`() {
        val run = cli("-i", SIMPLE, "", "a")
        assertEquals(1, run.status)
        assertContains(run.stderr, "the empty argument denotes the empty input")
    }

    /**
     * The outcome of a command-line run.
     *
     * @property status the exit status of the run.
     * @property stdout what the run printed on the standard output.
     * @property stderr what the run printed on the standard error stream.
     */
    private data class CliRun(val status: Int, val stdout: String, val stderr: String)

    /**
     * Runs the command-line interface with the given arguments.
     *
     * @param args the arguments of the run.
     * @return the outcome of the run.
     */
    private fun cli(vararg args: String): CliRun {
        var status = 0
        val streams = captureStreams { status = runCli(arrayOf(*args)) }
        return CliRun(status, streams.stdout, streams.stderr)
    }

    /** The path of the simple recognizer, relative to the project directory. */
    private companion object {
        const val SIMPLE = "src/test/resources/simple.quiver"
        const val NONDET = "src/test/resources/nondet.quiver"
    }
}
