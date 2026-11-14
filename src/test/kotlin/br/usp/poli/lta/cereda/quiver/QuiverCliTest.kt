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
        val run = cli("-i", SIMPLE, "ab")
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
        val run = cli("-i", SIMPLE, "ab", "--trace")
        assertEquals(0, run.status)
        assertContains(run.stderr, "trace step")
    }

    @Test
    fun `the max steps option bounds the run`() {
        val run = cli("-i", SIMPLE, "ab", "--max-steps", "1")
        assertEquals(1, run.status)
        assertContains(run.stderr, "Step budget exhausted")
    }

    @Test
    fun `the help option prints the usage summary`() {
        val run = cli("--help")
        assertEquals(0, run.status)
        assertContains(run.stdout, "Usage: quiver -i <file.quiver> <input-string>")
        assertContains(run.stdout, "--max-steps N")
    }

    @Test
    fun `the short help option prints the usage summary`() {
        val run = cli("-h")
        assertEquals(0, run.status)
        assertContains(run.stdout, "Usage: quiver -i <file.quiver> <input-string>")
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
        val run = cli("-i", SIMPLE, "ab", "--max-steps")
        assertEquals(1, run.status)
        assertContains(run.stderr, "error: missing argument to --max-steps")
    }

    @Test
    fun `an invalid max steps argument is a usage error`() {
        val run = cli("-i", SIMPLE, "ab", "--max-steps", "many")
        assertEquals(1, run.status)
        assertContains(run.stderr, "error: invalid argument to --max-steps: many")
    }

    @Test
    fun `an unexpected argument is a usage error`() {
        val run = cli("-i", SIMPLE, "ab", "cd")
        assertEquals(1, run.status)
        assertContains(run.stderr, "error: unexpected argument: cd")
    }

    @Test
    fun `a missing input file is a usage error`() {
        val run = cli("ab")
        assertEquals(1, run.status)
        assertContains(run.stderr, "both -i <file> and an input string are required")
    }

    @Test
    fun `a missing input string is a usage error`() {
        val run = cli("-i", SIMPLE)
        assertEquals(1, run.status)
        assertContains(run.stderr, "both -i <file> and an input string are required")
    }

    @Test
    fun `a missing source file is reported`() {
        val run = cli("-i", "src/test/resources/does-not-exist.quiver", "ab")
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
        val run = cli("-i", "src/test/resources/include-main.quiver", "ab")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `the usage summary is not printed on a successful run`() {
        val run = cli("-i", SIMPLE, "ab")
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
        val output = captureStdout { runEntryPoint(arrayOf("-i", SIMPLE, "ab")) }
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
        val run = cli("-i", NONDET, "ab", "--nondet")
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
    fun `the nondet flag is accepted before the input string`() {
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
        val output = captureStdout { main(arrayOf("-i", SIMPLE, "ab")) }
        assertContains(output, "true")
    }

    @Test
    fun `the usage summary names every option`() {
        val run = cli("--help")
        assertContains(run.stdout, "-i <file.quiver>")
        assertContains(run.stdout, "<input-string>")
        assertContains(run.stdout, "--trace")
        assertContains(run.stdout, "--nondet")
        assertContains(run.stdout, "--max-steps N")
        assertContains(run.stdout, "-h, --help")
    }

    @Test
    fun `the max steps option is accepted before the input string`() {
        val run = cli("-i", SIMPLE, "--max-steps", "10", "ab")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
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
