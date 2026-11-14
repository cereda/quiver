// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

/** The anchor of the test helpers, used to reach the test resources. */
private object QuiverTestSupport
