// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.int
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import java.io.File
import java.io.IOException

/**
 * The name of the tool, which the usage summaries and the terminal carry.
 */
private const val TOOL_NAME = "quiver"

/**
 * The version of the interpreter.
 *
 * The version is the one the manifest of the jar states, and a development
 * build has none.
 */
private val VERSION: String = QuiverCommand::class.java.`package`.implementationVersion ?: "DEVELOPMENT BUILD"

/**
 * The root command of the interpreter.
 *
 * The command holds the subcommands of the tool: the one that runs a program
 * over an input, the one that starts the interactive session, and the one
 * that summarizes a program. A bare invocation prints the help summary and
 * ends successfully.
 *
 * @property terminal the factory of the terminal of the interactive session.
 */
class QuiverCommand(private val terminal: () -> Terminal = { TerminalBuilder.builder().name(TOOL_NAME).build() }) :
    CliktCommand(name = TOOL_NAME) {

    init {
        versionOption(
            version = VERSION,
            names = setOf("-V", "--version"),
            message = { "$TOOL_NAME $it" },
        )
        completionOption()
        subcommands(RunCommand(), ReplCommand(terminal), InspectCommand())
    }

    /**
     * The one-line help of the command.
     *
     * @param context the context of the command.
     * @return the help of the command.
     */
    override fun help(context: Context): String = "Quiver: a term rewriting language for adaptive automata"

    /**
     * Runs the command.
     *
     * The command has no work of its own: a bare invocation teaches the
     * surface and ends, and Clikt prints the help summary of the command
     * when it gets no arguments at all.
     */
    override fun run() {
        // (nothing to do: the subcommands do the work, and the summary of
        //  the surface is the help of the command)
    }
}

/**
 * The command that runs a program over an input.
 *
 * The command prints `true` when the input is recognized, and `false`
 * otherwise.
 */
class RunCommand : CliktCommand(name = "run") {

    /** The Quiver source file to run. */
    private val inputFile: String by option(
        "-i",
        "--input",
        metavar = "FILE",
        help = "The Quiver source file to run.",
    ).required()

    /** The symbols of the input, one per argument. */
    private val symbols: List<String> by argument(
        name = "SYMBOL",
        help = "One symbol of the input per argument.",
    ).multiple()

    /** Whether the rule-firing trace is printed. */
    private val trace: Boolean by option(
        "--trace",
        help = "Print the rule-firing trace to the standard error stream.",
    ).flag()

    /** Whether the recognition is non-deterministic. */
    private val nondet: Boolean by option(
        "--nondet",
        help = "Non-deterministic recognition (NFA-style, any path accepts).",
    ).flag()

    /** The bound of the number of steps of the run. */
    private val maxSteps: Int by option(
        "--max-steps",
        metavar = "N",
        help = "The maximum number of steps before the run aborts.",
    ).int().default(DEFAULT_MAX_STEPS)

    /**
     * The one-line help of the command.
     *
     * @param context the context of the command.
     * @return the help of the command.
     */
    override fun help(context: Context): String = "Run a Quiver program over an input."

    /**
     * Runs the command.
     *
     * The symbols of the command line override the input the program declares
     * in its header; when neither gives one, the run fails.
     */
    override fun run() {
        val palette = Palette(ColourPolicy(null).paints(standardOutputPaints()))
        val input = inputOf(symbols, currentContext)
        val sourceFile = File(inputFile)
        // The `include` directives are resolved relative to the directory of
        // the source file, not to the working directory of the process.
        val baseDir = sourceFile.absoluteFile.parent ?: "."
        val result =
            try {
                val source = sourceFile.readText()
                runQuiver(
                    source,
                    input,
                    trace,
                    maxSteps,
                    baseDir,
                    sourceFile.absolutePath,
                    nondet,
                )
            } catch (e: QuiverException) {
                reportError(e, palette)
            } catch (e: NoInputException) {
                reportError(e, palette)
            } catch (e: FuelExhaustedException) {
                reportError(e, palette)
            } catch (e: IOException) {
                reportError(e, palette)
            }
        echo(palette.paint(verdict(result)))
    }

    /**
     * Reports a run failure to the standard error stream.
     *
     * The failure is not a mistake of the command line, so the usage summary
     * is not printed: the run ends with a failure of its own.
     *
     * @param e the failure of the run.
     * @param palette the palette of the output.
     */
    private fun reportError(e: Exception, palette: Palette): Nothing = throw PrintMessage(
        palette.paint(ReportLine("error: ${e.message}", LineKind.Failure)),
        statusCode = 1,
        printError = true,
    )
}

/**
 * The command that starts the interactive session.
 *
 * @property terminal the factory of the terminal of the session.
 */
class ReplCommand(private val terminal: () -> Terminal = { TerminalBuilder.builder().name("quiver").build() }) :
    CliktCommand(name = "repl") {

    /** Whether the session keeps no history on disk. */
    private val noHistory: Boolean by option(
        "--no-history",
        help = "Keep no history of the session on disk.",
    ).flag()

    /** The file the history of the session is kept in. */
    private val historyFile: String? by option(
        "--history",
        metavar = "PATH",
        help = "The file the history of the session is kept in.",
    )

    /** The program to load before the session starts. */
    private val load: String? by option(
        "--load",
        metavar = "FILE",
        help = "The Quiver source file to load before the session starts.",
    )

    /** Whether the session opens with no banner. */
    private val noBanner: Boolean by option(
        "--no-banner",
        help = "Open the session with no banner.",
    ).flag()

    /** The options of the colours of the output. */
    private val colours by ColourOptions()

    /**
     * The one-line help of the command.
     *
     * @param context the context of the command.
     * @return the help of the command.
     */
    override fun help(context: Context): String = "Start the interactive session."

    /**
     * Runs the command.
     */
    override fun run() {
        // The choice of the colours is read before the terminal is built: a
        // command line that contradicts itself is a mistake of the user, and
        // it is reported before the session opens a terminal.
        val colour = colours.choice(currentContext)
        val session = QuiverSession()
        val repl = QuiverRepl(session)
        val terminal =
            try {
                terminal()
            } catch (expected: IOException) {
                // (no terminal to read the commands from: the session cannot
                //  start, and the failure is the outcome of the command)
                throw PrintMessage(
                    "error: no terminal: ${expected.message}",
                    statusCode = 1,
                    printError = true,
                )
            }
        // The report of the program the session loads is part of the output
        // of the session, and not of the command: it is printed by the loop,
        // with the rest of the lines, and in the order the user sees them.
        val program = load
        val opening = if (program == null) emptyList() else session.load(program)
        runReplSession(
            repl,
            ReplOptions(
                noHistory = noHistory,
                historyPath = historyFile,
                stateHome = System.getenv("XDG_STATE_HOME"),
                home = System.getProperty("user.home"),
                colour = colour,
                showBanner = !noBanner,
                opening = opening,
            ),
            terminal,
        )
    }
}

/**
 * The command that summarizes a program.
 *
 * The command reports the facts of the program: the header, the strategies
 * and the rules the run consults. It runs nothing.
 */
class InspectCommand : CliktCommand(name = "inspect") {

    /** The Quiver source file to summarize. */
    private val inputFile: String by option(
        "-i",
        "--input",
        metavar = "FILE",
        help = "The Quiver source file to summarize.",
    ).required()

    /**
     * The one-line help of the command.
     *
     * @param context the context of the command.
     * @return the help of the command.
     */
    override fun help(context: Context): String = "Summarize a Quiver program."

    /**
     * Runs the command.
     */
    override fun run() {
        val palette = Palette(ColourPolicy(null).paints(standardOutputPaints()))
        when (val result = loadProgram(inputFile)) {
            is LoadResult.Failed ->
                throw PrintMessage(
                    palette.paint(ReportLine("error: ${result.detail}", LineKind.Failure)),
                    statusCode = 1,
                    printError = true,
                )

            is LoadResult.Loaded -> inspectReport(result.summary).forEach { echo(palette.paint(it)) }
        }
    }
}

/**
 * A usage error that names the command that was misused.
 *
 * The error of a command that is run, and not parsed, carries no context of
 * its own, and the usage summary it prints would name the command that
 * started the run. The context of the command that was misused is therefore
 * given to the error explicitly.
 *
 * @param message the message of the error.
 * @param context the context of the command that was misused.
 * @return the error.
 */
internal fun usageError(message: String, context: Context): UsageError =
    UsageError(message).also { it.context = context }

/**
 * Reads the input symbols off the command line.
 *
 * Every argument that is not an option is one symbol of the input, and the
 * name of the symbol is the text of the argument. The empty argument is the
 * exception: it denotes the empty input, and it cannot be combined with
 * other symbols.
 *
 * @param arguments the positional arguments of the command line.
 * @param context the context of the command, which the usage summary of a
 *   mistake names.
 * @return the symbols of the input, or null when the command line gives none.
 * @throws UsageError when the arguments name no valid input.
 */
internal fun inputOf(arguments: List<String>, context: Context): List<String>? {
    if (arguments.isEmpty()) return null
    val empty = arguments.count { it.isEmpty() }
    return when {
        empty == 0 -> arguments

        empty == 1 && arguments.size == 1 -> emptyList()

        else -> throw usageError(
            "the empty argument denotes the empty input and cannot be combined with other symbols",
            context,
        )
    }
}
