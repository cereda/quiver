// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import java.io.File
import java.io.IOException
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * Entry point of the interpreter.
 *
 * Without arguments the interpreter starts the interactive REPL, so that the
 * executable jar is a complete tool on its own. With arguments it runs the
 * command-line interface.
 *
 * Usage:
 *   quiver -i <file.quiver> <input-string> [--trace] [--nondet] [--max-steps N]
 *
 * Prints "true" if the input string is recognized, "false" otherwise.
 */
fun main(args: Array<String>) {
    runEntryPoint(args)
}

/**
 * Runs the interpreter's entry point: the interactive REPL without arguments,
 * the command-line interface with them.
 *
 * @param args the arguments of the run.
 */
internal fun runEntryPoint(args: Array<String>) {
    if (args.isEmpty()) {
        startRepl(emptyArray())
    } else {
        val code = runCli(args)
        if (code != 0) exitProcess(code)
    }
}

/**
 * Runs the command-line interface.
 *
 * @param args the command-line arguments.
 * @return the exit status of the run: 0 on success, 1 on failure.
 */
internal fun runCli(args: Array<String>): Int {
    val parsed = parseArgs(args)
    return when (parsed) {
        is CliResult.Parsed -> runProgram(parsed.options)
        CliResult.Help -> 0
        is CliResult.UsageError -> 1
    }
}

/**
 * Runs the interpreter over the source file named by the options.
 *
 * @param options the options of the run.
 * @return the exit status of the run: 0 on success, 1 on failure.
 */
private fun runProgram(options: Options): Int {
    val sourceFile = File(options.inputFile)
    // The `include` directives are resolved relative to the directory of the
    // source file, not to the working directory of the process.
    val baseDir = sourceFile.absoluteFile.parent ?: "."
    return try {
        val source = sourceFile.readText()
        val result =
            runQuiver(
                source,
                options.inputString,
                options.trace,
                options.maxSteps,
                baseDir,
                sourceFile.absolutePath,
                options.nondet,
            )
        println(if (result) "true" else "false")
        0
    } catch (e: QuiverException) {
        reportError(e)
    } catch (e: FuelExhaustedException) {
        reportError(e)
    } catch (e: IOException) {
        reportError(e)
    }
}

/** The parsed command-line options of a Quiver run. */
private data class Options(
    val inputFile: String,
    val inputString: String,
    val trace: Boolean,
    val maxSteps: Int,
    val nondet: Boolean,
)

/**
 * The outcome of parsing the command-line arguments.
 *
 * The parser never terminates the process: it reports what it found, and the
 * caller decides the exit status. A usage error is therefore testable, which a
 * call to `System.exit` inside the parser would not be.
 */
private sealed interface CliResult {
    /** The arguments were parsed into the options of the run. */
    data class Parsed(val options: Options) : CliResult

    /** The usage summary was requested; the run ends successfully. */
    data object Help : CliResult

    /**
     * The arguments are invalid; the run ends with a failure.
     *
     * @property message the message naming the problem.
     */
    data class UsageError(val message: String) : CliResult
}

/**
 * Parses the command-line arguments.
 *
 * @return the outcome of the parsing: the options, the help request, or the
 *   usage error; the last two come with their message already reported.
 */
private fun parseArgs(args: Array<String>): CliResult {
    val scanned = scanArgs(args)
    when (scanned) {
        CliResult.Help -> printUsage(System.out)

        is CliResult.UsageError -> {
            System.err.println("error: ${scanned.message}")
            printUsage(System.err)
        }

        is CliResult.Parsed -> Unit
    }
    return scanned
}

/**
 * Holds the mutable state accumulated while scanning command-line arguments.
 */
private class ParseState {
    var inputFile: String? = null
    var inputString: String? = null
    var trace = false
    var maxSteps = DEFAULT_MAX_STEPS
    var nondet = false
    var help = false
    var error: String? = null
}

/**
 * Scans the command-line arguments.
 *
 * The scan stops at the first problem it finds, so the reported message names
 * the first invalid argument of the command line.
 *
 * @param args the command-line arguments.
 * @return the outcome of the scan: the options of the run, the help request,
 *   or the usage error with its message.
 */
private fun scanArgs(args: Array<String>): CliResult {
    val state = ParseState()
    var i = 0
    while (i < args.size && !state.help && state.error == null) {
        val result = processArg(args[i], args, i, state)
        i = result.first
        state.error = result.second
        i++
    }

    val error = state.error
    val inputFile = state.inputFile
    val inputString = state.inputString
    return when {
        state.help -> CliResult.Help

        error != null -> CliResult.UsageError(error)

        inputFile == null || inputString == null ->
            CliResult.UsageError("both -i <file> and an input string are required")

        else -> CliResult.Parsed(
            Options(inputFile, inputString, state.trace, state.maxSteps, state.nondet),
        )
    }
}

/**
 * Processes one command-line argument, updating [state] in place.
 *
 * @return a pair of (next index, error message or null). The next index is
 *   the position of the argument's value (already advanced past the option
 *   name when the option takes a value), so the caller advances past it.
 */
private fun processArg(arg: String, args: Array<String>, i: Int, state: ParseState): Pair<Int, String?> = when (arg) {
    "-i" -> {
        val next = i + 1
        state.inputFile = if (next < args.size) args[next] else null
        if (state.inputFile == null) Pair(next, "missing argument to -i") else Pair(next, null)
    }

    "--trace" -> {
        state.trace = true
        Pair(i, null)
    }

    "--nondet" -> {
        state.nondet = true
        Pair(i, null)
    }

    "--max-steps" -> {
        val next = i + 1
        val parsed = parseMaxStepsArg(args, next)
        if (parsed == null) {
            val value = args.getOrNull(next)
            Pair(
                next,
                if (value == null) {
                    "missing argument to --max-steps"
                } else {
                    "invalid argument to --max-steps: $value"
                },
            )
        } else {
            state.maxSteps = parsed
            Pair(next, null)
        }
    }

    "--help", "-h" -> {
        state.help = true
        Pair(i, null)
    }

    else -> {
        if (state.inputString == null) {
            state.inputString = arg
            Pair(i, null)
        } else {
            Pair(i, "unexpected argument: $arg")
        }
    }
}

/**
 * Parses the integer argument following `--max-steps`.
 *
 * @param args the command-line arguments.
 * @param pos the position of the value argument (already advanced past `--max-steps`).
 * @return the parsed integer, or null when the argument is missing or not an integer.
 */
private fun parseMaxStepsArg(args: Array<String>, pos: Int): Int? = if (pos < args.size) {
    args[pos].toIntOrNull()
} else {
    null
}

/**
 * Reports a run failure to stderr.
 *
 * @return the exit status of the run: 1.
 */
private fun reportError(e: Exception): Int {
    System.err.println("error: ${e.message}")
    return 1
}

/**
 * Prints the command-line usage summary.
 *
 * @param out the stream the summary is printed to.
 */
private fun printUsage(out: PrintStream) {
    out.println("Quiver - a term rewriting language for adaptive automata")
    out.println()
    out.println("Usage: quiver -i <file.quiver> <input-string> [--trace] [--nondet] [--max-steps N]")
    out.println()
    out.println("Options:")
    out.println("  -i <file.quiver>   Quiver source file to execute")
    out.println("  <input-string>  input string to recognize")
    out.println("  --trace         print rule-firing trace to stderr")
    out.println("  --nondet        non-deterministic recognition (NFA-style, any path accepts)")
    out.println("  --max-steps N   maximum number of steps before aborting (default: 10000)")
    out.println("  -h, --help      show this help")
    out.println()
    out.println("Run the interpreter without arguments to start the interactive REPL.")
}
