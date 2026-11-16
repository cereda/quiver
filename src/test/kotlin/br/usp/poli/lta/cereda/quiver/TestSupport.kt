// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.parse
import org.jline.terminal.impl.DumbTerminal
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

// The helpers the test suites share.
//
// The interpreter writes to the standard streams and reads the standard input,
// so the tests that exercise those paths capture the streams and replace the
// input while they run.

/**
 * Loads a resource of the test suite.
 *
 * @param name the name of the resource, relative to the root of the test
 *   resources (for instance `simple.quiver`).
 * @return the contents of the resource.
 */
internal fun loadResource(name: String): String =
    requireNotNull(QuiverTestSupport::class.java.getResource("/$name")) { "missing test resource: $name" }.readText()

/**
 * Runs [block] with the standard output captured.
 *
 * @param block the code to run.
 * @return everything the block printed on the standard output.
 */
internal fun captureStdout(block: () -> Unit): String {
    val buffer = ByteArrayOutputStream()
    val previous = System.out
    System.setOut(java.io.PrintStream(buffer, true, Charsets.UTF_8))
    try {
        block()
    } finally {
        System.setOut(previous)
    }
    return buffer.toString(Charsets.UTF_8)
}

/**
 * Runs [block] with the standard error captured.
 *
 * @param block the code to run.
 * @return everything the block printed on the standard error stream.
 */
internal fun captureStderr(block: () -> Unit): String {
    val buffer = ByteArrayOutputStream()
    val previous = System.err
    System.setErr(java.io.PrintStream(buffer, true, Charsets.UTF_8))
    try {
        block()
    } finally {
        System.setErr(previous)
    }
    return buffer.toString(Charsets.UTF_8)
}

/**
 * The output a run wrote to the standard streams.
 *
 * @property stdout everything the run printed on the standard output.
 * @property stderr everything the run printed on the standard error stream.
 */
internal data class Streams(val stdout: String, val stderr: String)

/**
 * Runs [block] with both standard streams captured.
 *
 * @param block the code to run.
 * @return what the block wrote to the two streams.
 */
internal fun captureStreams(block: () -> Unit): Streams {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val previousOut = System.out
    val previousErr = System.err
    System.setOut(java.io.PrintStream(out, true, Charsets.UTF_8))
    System.setErr(java.io.PrintStream(err, true, Charsets.UTF_8))
    try {
        block()
    } finally {
        System.setOut(previousOut)
        System.setErr(previousErr)
    }
    return Streams(out.toString(Charsets.UTF_8), err.toString(Charsets.UTF_8))
}

/**
 * Runs [block] with the standard input replaced by [input].
 *
 * @param input the text the standard input delivers.
 * @param block the code to run.
 */
internal fun withStandardInput(input: String, block: () -> Unit) {
    val previous: InputStream = System.`in`
    System.setIn(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))
    try {
        block()
    } finally {
        System.setIn(previous)
    }
}

/**
 * The outcome of a command-line run.
 *
 * @property status the exit status of the run.
 * @property stdout what the run printed on the standard output.
 * @property stderr what the run printed on the standard error stream.
 */
internal data class CliRun(val status: Int, val stdout: String, val stderr: String)

/**
 * Runs the command line with the given arguments.
 *
 * The helper does what the entry point does, and it never terminates the
 * process: the outcome of the run is reported, so that every path of the
 * command line is testable.
 *
 * @param args the arguments of the run.
 * @return the outcome of the run.
 */
internal fun cli(vararg args: String): CliRun {
    var status = 0
    val command = QuiverCommand()
    val streams = captureStreams {
        try {
            command.parse(args.toList())
        } catch (e: CliktError) {
            command.echoFormattedHelp(e)
            status = e.statusCode
        }
    }
    return CliRun(status, streams.stdout, streams.stderr)
}

/**
 * Runs the interactive session over a pipe, and returns everything it
 * printed.
 *
 * The session reads the pipe through the terminal of JLine, so the helper
 * exercises the loop a user goes through, and the output is the output the
 * user sees.
 *
 * @param script the commands to type.
 * @return everything the session printed.
 */
internal fun runRepl(script: String): String {
    val input = ByteArrayInputStream(script.toByteArray(Charsets.UTF_8))
    val output = ByteArrayOutputStream()
    runReplSession(QuiverRepl(), ReplOptions(noHistory = true), DumbTerminal(input, output))
    return output.toString(Charsets.UTF_8)
}

/**
 * Runs the interactive session over a pipe, with the arguments of the
 * command line, and returns the outcome of the run.
 *
 * The session keeps no history on disk, unless the arguments name a file
 * for it.
 *
 * @param script the commands to type.
 * @param args the arguments of the session, after the name of the command.
 * @return the outcome of the run.
 */
internal fun cliRepl(script: String, vararg args: String): CliRun =
    cliReplSession(script, listOf("--no-history") + args)

/**
 * Runs the interactive session over a pipe, with a history file, and returns
 * the outcome of the run.
 *
 * @param script the commands to type.
 * @param history the file the history of the session is kept in.
 * @param args the arguments of the session, after the name of the command.
 * @return the outcome of the run.
 */
internal fun cliReplWithHistory(script: String, history: String, vararg args: String): CliRun =
    cliReplSession(script, listOf("--history", history) + args)

/**
 * Runs the interactive session over a pipe, with a terminal that cannot be
 * built, and returns the outcome of the run.
 *
 * @return the outcome of the run.
 */
internal fun cliReplWithoutTerminal(): CliRun {
    var status = 0
    val streams = captureStreams {
        val command = QuiverCommand { throw IOException("no terminal here") }
        try {
            command.parse(listOf("repl", "--no-history"))
        } catch (e: CliktError) {
            command.echoFormattedHelp(e)
            status = e.statusCode
        }
    }
    return CliRun(status, streams.stdout, streams.stderr)
}

/**
 * Runs the interactive session over a pipe, with the arguments of the
 * command line, and returns the outcome of the run.
 *
 * The helper drives the root command, and not the session alone, so that the
 * session is reached the way a user reaches it: the usage summaries and the
 * errors name the command that was misused, and not the one that started the
 * run.
 *
 * @param script the commands to type.
 * @param args the arguments of the session, after the name of the command.
 * @return the outcome of the run.
 */
private fun cliReplSession(script: String, args: List<String>): CliRun {
    var status = 0
    val input = ByteArrayInputStream(script.toByteArray(Charsets.UTF_8))
    val streams = captureStreams {
        val command = QuiverCommand { DumbTerminal(input, System.out) }
        try {
            command.parse(listOf("repl") + args)
        } catch (e: CliktError) {
            command.echoFormattedHelp(e)
            status = e.statusCode
        }
    }
    return CliRun(status, streams.stdout, streams.stderr)
}

/** The anchor of the test helpers, used to reach the test resources. */
private object QuiverTestSupport
