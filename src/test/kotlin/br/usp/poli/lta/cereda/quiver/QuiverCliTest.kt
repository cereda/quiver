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
        val run = cli("run", "-i", SIMPLE, "a", "b")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `a rejected input is reported as false`() {
        val run = cli("run", "-i", SIMPLE, "a")
        assertEquals(0, run.status)
        assertContains(run.stdout, "false")
    }

    @Test
    fun `the trace option reports the steps on the error stream`() {
        val run = cli("run", "-i", SIMPLE, "a", "b", "--trace")
        assertEquals(0, run.status)
        assertContains(run.stderr, "trace step")
    }

    @Test
    fun `the max steps option bounds the run`() {
        val run = cli("run", "-i", SIMPLE, "a", "b", "--max-steps", "1")
        assertEquals(1, run.status)
        assertContains(run.stderr, "Step budget exhausted")
    }

    @Test
    fun `the help option prints the usage summary of the run`() {
        val run = cli("run", "--help")
        assertEquals(0, run.status)
        assertContains(run.stdout, "Usage: quiver run")
        assertContains(run.stdout, "--max-steps")
    }

    @Test
    fun `a bare invocation prints the help summary`() {
        val run = cli()
        assertEquals(0, run.status)
        assertContains(run.stdout, "Usage: quiver")
        assertContains(run.stdout, "run")
        assertContains(run.stdout, "repl")
    }

    @Test
    fun `a missing file argument is a usage error`() {
        val run = cli("run")
        assertEquals(1, run.status)
        assertContains(run.stderr, "missing option --input")
        assertContains(run.stderr, "Usage: quiver run")
    }

    @Test
    fun `an invalid max steps argument is a usage error`() {
        val run = cli("run", "-i", SIMPLE, "a", "b", "--max-steps", "many")
        assertEquals(1, run.status)
        assertContains(run.stderr, "invalid value for --max-steps")
    }

    @Test
    fun `every trailing argument is one symbol of the input`() {
        // The arguments are the symbols of the input, one by one: the input
        // of `simple.quiver` is the two symbols `a` and `b`.
        val run = cli("run", "-i", SIMPLE, "a", "b")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `a program that declares no input fails`() {
        // The program declares no input, and the command line gives none:
        // the run fails, and the usage summary is not printed.
        val run = cli("run", "-i", SIMPLE)
        assertEquals(1, run.status)
        assertContains(run.stderr, "error: the program declares no input; give one on the command line")
        assertTrue(!run.stderr.contains("Usage:"), "The usage summary was printed on a failed run")
    }

    @Test
    fun `a missing source file is reported`() {
        val run = cli("run", "-i", "src/test/resources/does-not-exist.quiver", "a", "b")
        assertEquals(1, run.status)
        assertContains(run.stderr, "error:")
    }

    @Test
    fun `a malformed program is reported`() {
        val run = cli("run", "-i", "src/test/resources/error-bad-mapping-left.quiver", "a")
        assertEquals(1, run.status)
        assertContains(run.stderr, "Invalid FSM mapping")
    }

    @Test
    fun `the includes of the source file are resolved relative to it`() {
        val run = cli("run", "-i", "src/test/resources/include-main.quiver", "a", "b")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `the usage summary is not printed on a successful run`() {
        val run = cli("run", "-i", SIMPLE, "a", "b")
        assertTrue(!run.stdout.contains("Usage:"), "The usage summary was printed on a successful run")
    }

    @Test
    fun `the nondet flag enables non-deterministic recognition`() {
        // nondet.quiver has two (q0,a) transitions; only --nondet accepts 'a'
        // because the second path reaches the accepting state.
        val run = cli("run", "-i", NONDET, "a", "--nondet")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `the nondet flag rejects when all paths die`() {
        val run = cli("run", "-i", NONDET, "a", "b", "--nondet")
        assertEquals(0, run.status)
        assertContains(run.stdout, "false")
    }

    @Test
    fun `without nondet flag behavior is deterministic`() {
        // Without --nondet, only the first (q0,a) transition is used.
        val run = cli("run", "-i", NONDET, "a")
        assertEquals(0, run.status)
        assertContains(run.stdout, "false")
    }

    @Test
    fun `the nondet flag is accepted before the input symbols`() {
        val run = cli("run", "-i", NONDET, "--nondet", "a")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `the trace and the nondet flags work together`() {
        val run = cli("run", "-i", NONDET, "a", "--nondet", "--trace")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
        assertContains(run.stderr, "trace step")
    }

    @Test
    fun `the usage summary names every option`() {
        val run = cli("run", "--help")
        assertContains(run.stdout, "-i, --input")
        assertContains(run.stdout, "<symbol>")
        assertContains(run.stdout, "--trace")
        assertContains(run.stdout, "--nondet")
        assertContains(run.stdout, "--max-steps")
        assertContains(run.stdout, "-h, --help")
    }

    @Test
    fun `the max steps option is accepted before the input symbols`() {
        val run = cli("run", "-i", SIMPLE, "--max-steps", "10", "a", "b")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `the end of options marker ends the options`() {
        // Everything after the marker is a symbol of the input, and the
        // options that follow it are symbols as well.
        val run = cli("run", "-i", SIMPLE, "--", "a", "b")
        assertEquals(0, run.status)
        assertContains(run.stdout, "true")
    }

    @Test
    fun `an option after the end of options marker is a symbol`() {
        val run = cli("run", "-i", SIMPLE, "--", "--trace")
        assertEquals(0, run.status)
        assertContains(run.stdout, "false")
        assertTrue(!run.stderr.contains("trace step"), "The trace was turned on after the marker")
    }

    @Test
    fun `the empty argument is the empty input`() {
        // The empty argument is the empty input: the run reads nothing, and
        // the start state of the recognizer does not accept.
        val run = cli("run", "-i", SIMPLE, "")
        assertEquals(0, run.status)
        assertContains(run.stdout, "false")
    }

    @Test
    fun `the empty argument cannot be combined with other symbols`() {
        val run = cli("run", "-i", SIMPLE, "", "a")
        assertEquals(1, run.status)
        assertContains(run.stderr, "the empty argument denotes the empty input")
        // The usage summary names the command that was misused, and not the
        // command that started the run.
        assertContains(run.stderr, "Usage: quiver run")
    }

    @Test
    fun `the version option reports the version`() {
        val run = cli("--version")
        assertEquals(0, run.status)
        assertContains(run.stdout, "quiver")
    }

    @Test
    fun `the completion option generates the completion script`() {
        val run = cli("--generate-completion", "bash")
        assertEquals(0, run.status)
        assertContains(run.stdout, "quiver")
    }

    @Test
    fun `an unknown subcommand is a usage error`() {
        val run = cli("dance")
        assertEquals(1, run.status)
        assertContains(run.stderr, "Usage: quiver")
    }

    @Test
    fun `the entry point runs the command line`() {
        val output = captureStdout { main(arrayOf("run", "-i", SIMPLE, "a", "b")) }
        assertContains(output, "true")
    }

    @Test
    fun `the inspect command reports the program`() {
        val run = cli("inspect", "-i", SIMPLE)
        assertEquals(0, run.status)
        assertContains(run.stdout, "Program: $SIMPLE")
        assertContains(run.stdout, "FSM: start=q0, accepting=q2")
        assertContains(run.stdout, "alphabet: a, b")
    }

    @Test
    fun `the inspect command reports the strategies of the program`() {
        val run = cli("inspect", "-i", STRATEGY)
        assertEquals(0, run.status)
        assertContains(run.stdout, "active strategy: repeatTest (REPEAT, 1 rules)")
        assertContains(run.stdout, "repeatTest (REPEAT, 1 rules)")
    }

    @Test
    fun `the inspect command reports a missing file`() {
        val run = cli("inspect", "-i", "src/test/resources/does-not-exist.quiver")
        assertEquals(1, run.status)
        assertContains(run.stderr, "error:")
        assertTrue(!run.stderr.contains("Usage:"), "The usage summary was printed on a failed run")
    }

    @Test
    fun `the inspect command reports a malformed program`() {
        val run = cli("inspect", "-i", "src/test/resources/error-bad-mapping-left.quiver")
        assertEquals(1, run.status)
        assertContains(run.stderr, "Invalid FSM mapping")
    }

    @Test
    fun `the inspect command needs a file`() {
        val run = cli("inspect")
        assertEquals(1, run.status)
        assertContains(run.stderr, "missing option --input")
        assertContains(run.stderr, "Usage: quiver inspect")
    }

    @Test
    fun `the help option prints the usage summary of the inspection`() {
        val run = cli("inspect", "--help")
        assertEquals(0, run.status)
        assertContains(run.stdout, "Usage: quiver inspect")
        assertContains(run.stdout, "-i, --input")
    }

    @Test
    fun `the help summary names the inspection`() {
        val run = cli()
        assertContains(run.stdout, "inspect")
    }

    @Test
    fun `the output of the run is not painted when it is piped`() {
        // The colours are the privilege of a terminal: a run whose output is
        // piped is the run of the article, byte for byte.
        val run = cli("run", "-i", SIMPLE, "a", "b")
        assertEquals("true\n", run.stdout)
    }

    @Test
    fun `the output of the inspection is not painted when it is piped`() {
        val run = cli("inspect", "-i", SIMPLE)
        assertTrue(!run.stdout.contains("\u001b["), "The report was painted")
    }

    @Test
    fun `the session without a terminal fails`() {
        // The session reads the commands from a terminal, and a process
        // that has none cannot start it.
        val run = cliReplWithoutTerminal()
        assertEquals(1, run.status)
        assertContains(run.stderr, "error: no terminal")
    }

    @Test
    fun `the run of an unreadable file fails`() {
        // A file that cannot be read is a failure of the run, and not a
        // mistake of the command line.
        val run = cli("run", "-i", "src/test/resources/does-not-exist.quiver", "a")
        assertEquals(1, run.status)
        assertContains(run.stderr, "error:")
        assertTrue(!run.stderr.contains("Usage:"), "The usage summary was printed on a failed run")
    }

    @Test
    fun `the run of a directory fails`() {
        // A path that names a directory is not a program, and the run says
        // so instead of falling over it.
        val run = cli("run", "-i", "src/test/resources", "a")
        assertEquals(1, run.status)
        assertContains(run.stderr, "error:")
    }

    @Test
    fun `the run of the root of the filesystem fails`() {
        // The root of the filesystem has no directory of its own, and the
        // command must not fall over it.
        val run = cli("run", "-i", "/", "a")
        assertEquals(1, run.status)
        assertContains(run.stderr, "error:")
    }

    /** The path of the simple recognizer, relative to the project directory. */
    private companion object {
        const val SIMPLE = "src/test/resources/simple.quiver"
        const val NONDET = "src/test/resources/nondet.quiver"
        const val STRATEGY = "src/test/resources/strategy.quiver"
    }
}
