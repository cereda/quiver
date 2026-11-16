// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.Terminal
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths

/** The type of the terminal that cannot paint anything. */
private const val DUMB_TERMINAL = "dumb"

/** The number of entries the history of a session keeps in memory. */
private const val HISTORY_SIZE = 5_000

/** The number of entries the history file of a session keeps. */
private const val HISTORY_FILE_SIZE = 10_000

/**
 * The outcome of a command of the interactive REPL.
 */
internal sealed interface ReplResult {

    /** The lines the command printed. */
    val lines: List<ReportLine>

    /** The command printed the lines, and the session continues. */
    data class Printed(override val lines: List<ReportLine>) : ReplResult

    /** The command printed the lines, and the session ends. */
    data class Finished(override val lines: List<ReportLine>) : ReplResult
}

/**
 * The completion the argument of a command accepts.
 */
internal sealed interface ReplArgument {

    /** The command takes no argument. */
    data object None : ReplArgument

    /**
     * The command takes one of a fixed set of words.
     *
     * @property words the words the argument accepts.
     */
    data class Words(val words: List<String>) : ReplArgument

    /** The command takes the name of a file. */
    data object File : ReplArgument

    /** The command takes an input, whose symbols are the ones of the program. */
    data object Input : ReplArgument
}

/**
 * A command of the interactive REPL.
 *
 * @property name the name of the command, without the leading colon.
 * @property usage the usage of the command, as the help shows it.
 * @property description the one-line help of the command.
 * @property argument the completion the argument of the command accepts.
 * @property aliases the other names the command answers to.
 * @property handler the handler of the command.
 */
internal data class ReplCommandSpec(
    val name: String,
    val usage: String,
    val description: String,
    val argument: ReplArgument = ReplArgument.None,
    val aliases: List<String> = emptyList(),
    val handler: (String, QuiverSession) -> ReplResult,
) {

    /**
     * The names the command answers to: its own name and its aliases.
     *
     * The names are the ones the lookup matches and the ones the completion
     * offers, so a command is known by a single list of names.
     */
    val names: List<String>
        get() = listOf(name) + aliases
}

/**
 * The commands of the interactive REPL.
 *
 * The order of the list is the order the help shows them in.
 */
internal val REPL_COMMANDS: List<ReplCommandSpec> = listOf(
    ReplCommandSpec(
        name = "load",
        usage = ":load <file.quiver>",
        description = "load a Quiver source file (rules + FSM header)",
        argument = ReplArgument.File,
    ) { args, session -> ReplResult.Printed(session.load(args)) },
    ReplCommandSpec(
        name = "rules",
        usage = ":rules",
        description = "list the currently loaded rules",
    ) { _, session -> ReplResult.Printed(session.rulesReport()) },
    ReplCommandSpec(
        name = "strategy",
        usage = ":strategy <kind>",
        description = "set rewriting strategy (first / all / repeat [max N])",
        argument = ReplArgument.Words(listOf("first", "all", "repeat")),
    ) { args, session -> ReplResult.Printed(session.setStrategy(args)) },
    ReplCommandSpec(
        name = "trace",
        usage = ":trace on|off",
        description = "enable/disable step tracing",
        argument = ReplArgument.Words(listOf("on", "off")),
    ) { args, session -> ReplResult.Printed(session.setTrace(args == "on")) },
    ReplCommandSpec(
        name = "nondet",
        usage = ":nondet on|off",
        description = "enable/disable non-deterministic recognition",
        argument = ReplArgument.Words(listOf("on", "off")),
    ) { args, session -> ReplResult.Printed(session.setNondet(args == "on")) },
    ReplCommandSpec(
        name = "step",
        usage = ":step <input>",
        description = "recognize input and print trace to stderr",
        argument = ReplArgument.Input,
    ) { args, session -> ReplResult.Printed(session.step(args)) },
    ReplCommandSpec(
        name = "recognize",
        usage = ":recognize [<input>]",
        description = "recognize input (print true/false)",
        argument = ReplArgument.Input,
    ) { args, session -> ReplResult.Printed(session.recognize(args.ifEmpty { null })) },
    ReplCommandSpec(
        name = "debug",
        usage = ":debug <input>",
        description = "enter interactive step-by-step debug mode for input",
        argument = ReplArgument.Input,
    ) { args, session -> ReplResult.Printed(session.startDebug(args)) },
    ReplCommandSpec(
        name = "reset",
        usage = ":reset",
        description = "clear all rules and the automaton",
    ) { _, session -> ReplResult.Printed(session.reset()) },
    ReplCommandSpec(
        name = "quit",
        usage = ":quit / :exit",
        description = "exit the REPL",
        aliases = listOf("exit"),
    ) { _, _ -> ReplResult.Finished(listOf(ReportLine("goodbye"))) },
    ReplCommandSpec(
        name = "help",
        usage = ":help",
        description = "show this help",
    ) { _, _ -> ReplResult.Printed(replHelp()) },
)

/**
 * The commands of the interactive debug mode.
 *
 * The order of the list is the order the help shows them in.
 */
internal val DEBUG_COMMANDS: List<ReplCommandSpec> = listOf(
    ReplCommandSpec(
        name = "step",
        usage = ":step",
        description = "step one input symbol",
    ) { _, session -> ReplResult.Printed(session.debugStep()) },
    ReplCommandSpec(
        name = "inspect",
        usage = ":inspect",
        description = "show current state, position, and automaton",
    ) { _, session -> ReplResult.Printed(session.debugInspect()) },
    ReplCommandSpec(
        name = "continue",
        usage = ":continue / :c",
        description = "run to completion",
        aliases = listOf("c"),
    ) { _, session -> ReplResult.Printed(session.debugContinue()) },
    ReplCommandSpec(
        name = "quit",
        usage = ":quit / :exit",
        description = "exit debug mode (return to REPL)",
        aliases = listOf("exit"),
    ) { _, session -> ReplResult.Printed(session.leaveDebug()) },
    ReplCommandSpec(
        name = "help",
        usage = ":help",
        description = "show this help",
    ) { _, _ -> ReplResult.Printed(debugHelp()) },
)

/**
 * The help of the interactive REPL.
 *
 * @return the lines the help holds.
 */
internal fun replHelp(): List<ReportLine> = helpText("Commands", REPL_COMMANDS) +
    listOf(
        ReportLine(""),
        ReportLine("Any other line (outside debug mode) is treated as input to recognize"),
        ReportLine("against the loaded automaton. An input is a list of symbols: a bare"),
        ReportLine("token is one symbol, a quoted string is one symbol, and commas separate"),
        ReportLine("them."),
    )

/**
 * The help of the interactive debug mode.
 *
 * @return the lines the help holds.
 */
internal fun debugHelp(): List<ReportLine> = helpText("Debug commands", DEBUG_COMMANDS)

/**
 * The help of a set of commands.
 *
 * @param title the title of the help.
 * @param commands the commands the help lists.
 * @return the lines the help holds.
 */
internal fun helpText(title: String, commands: List<ReplCommandSpec>): List<ReportLine> {
    val width = commands.maxOf { it.usage.length }
    val lines = mutableListOf<ReportLine>(ReportLine("$title:"))
    for (command in commands) {
        lines += ReportLine("  ${command.usage.padEnd(width)}   ${command.description}")
    }
    return lines
}

/**
 * The interactive REPL of Quiver, without a terminal.
 *
 * The REPL owns the session and the registry of the commands, and answers
 * every line with the lines to print. The terminal loop is a thin layer on
 * top of it.
 *
 * @property session the session the commands act on.
 */
internal class QuiverRepl(val session: QuiverSession = QuiverSession()) {

    /** The banner the session opens with. */
    val banner: List<ReportLine> = listOf(
        ReportLine("Quiver REPL: a term rewriting language for adaptive automata", LineKind.Banner),
        ReportLine("Type :help for commands, :quit to exit.", LineKind.Banner),
        ReportLine("", LineKind.Banner),
    )

    /** True while the session is in the interactive debug mode. */
    val inDebugMode: Boolean
        get() = session.debugging

    /** The prompt the session shows. */
    val prompt: String
        get() = if (inDebugMode) "quiver-debug> " else "quiver> "

    /**
     * Executes one line typed at the session.
     *
     * @param line the line to execute.
     * @return the outcome of the line: the lines to print, and whether the
     *   session goes on.
     */
    fun execute(line: String): ReplResult {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return ReplResult.Printed(emptyList())
        return if (inDebugMode) debugCommand(trimmed) else mainCommand(trimmed)
    }

    /**
     * Executes one line typed in the main mode.
     *
     * A line that does not start with a colon is an input to recognize, and a
     * line that names no command is reported as unknown.
     *
     * @param trimmed the line, without its surrounding blanks.
     * @return the outcome of the line.
     */
    private fun mainCommand(trimmed: String): ReplResult {
        if (!trimmed.startsWith(":")) return ReplResult.Printed(session.recognize(trimmed))
        val command = findCommand(REPL_COMMANDS, trimmed)
            ?: return ReplResult.Printed(listOf(ReportLine("unknown command: $trimmed", LineKind.Failure)))
        return command.handler(argumentOf(trimmed), session)
    }

    /**
     * Executes one line typed in the debug mode.
     *
     * @param trimmed the line, without its surrounding blanks.
     * @return the outcome of the line.
     */
    private fun debugCommand(trimmed: String): ReplResult {
        val command = findCommand(DEBUG_COMMANDS, trimmed)
            ?: return ReplResult.Printed(
                listOf(
                    ReportLine(
                        "unknown debug command; type :help for debug commands",
                        LineKind.Failure,
                    ),
                ),
            )
        return command.handler(argumentOf(trimmed), session)
    }

    /**
     * The command a line names.
     *
     * @param commands the commands to look among.
     * @param line the line, without its surrounding blanks.
     * @return the command, or null when the line names none.
     */
    private fun findCommand(commands: List<ReplCommandSpec>, line: String): ReplCommandSpec? {
        val name = commandNameOf(line).removePrefix(":")
        return commands.firstOrNull { name in it.names }
    }

    /**
     * The argument of a command line.
     *
     * @param line the line, without its surrounding blanks.
     * @return the text of the argument, without its surrounding blanks.
     */
    private fun argumentOf(line: String): String = commandArgumentOf(line)
}

/**
 * The name of a command line.
 *
 * The name is the text that precedes the first blank of the line, and the
 * blank is any whitespace: a command may be separated from its argument by
 * a space or by a tab.
 *
 * @param line the line, without its surrounding blanks.
 * @return the name of the command.
 */
internal fun commandNameOf(line: String): String = line.takeWhile { !it.isWhitespace() }

/**
 * The argument of a command line.
 *
 * The argument is the text that follows the name of the command, without its
 * surrounding blanks.
 *
 * @param line the line, without its surrounding blanks.
 * @return the text of the argument.
 */
internal fun commandArgumentOf(line: String): String = line.drop(commandNameOf(line).length).trim()

/**
 * The options of an interactive session.
 *
 * @property noHistory whether the session keeps no history on disk.
 * @property historyPath the file the history is kept in, if the user names one.
 * @property stateHome the value of `XDG_STATE_HOME`, if the environment names one.
 * @property home the home directory of the user.
 * @property colour the choice of the user about the colours: true forces
 *   them on, false forces them off, and null leaves the choice to the
 *   terminal.
 * @property showBanner whether the session opens with the banner.
 * @property opening the lines the session prints after the banner, namely
 *   the report of the program it loads.
 */
internal data class ReplOptions(
    val noHistory: Boolean = false,
    val historyPath: String? = null,
    val stateHome: String? = null,
    val home: String? = null,
    val colour: Boolean? = null,
    val showBanner: Boolean = true,
    val opening: List<ReportLine> = emptyList(),
)

/**
 * The path of the history file of the session.
 *
 * The state root of the XDG specification is honoured; with it unset, the
 * XDG default derivation (`$home/.local/state`) makes
 * `~/.local/state/quiver/repl-history` the root, and the chain ends at null
 * when there is no home at all. The specification says that a root that is
 * not an absolute path is ignored, and so it is.
 *
 * @param stateHome the value of `XDG_STATE_HOME`, if the environment names one.
 * @param home the home directory of the user.
 * @return the path of the history file, or null when there is no home to put it in.
 */
internal fun historyFilePath(stateHome: String?, home: String?): String? {
    if (stateHome?.isNotBlank() == true && Paths.get(stateHome).isAbsolute) {
        return Paths.get(stateHome).resolve("quiver").resolve("repl-history").toString()
    }
    return home?.takeIf {
        it.isNotBlank()
    }?.let { Paths.get(it, ".local", "state", "quiver", "repl-history").toString() }
}

/**
 * Whether the session paints.
 *
 * The decision is the one of the colour policy: the user may force the
 * colours on or off, and otherwise the terminal decides. The palette, the
 * prompt and the highlighter of the line being edited all read the same
 * decision, so they cannot disagree about it.
 *
 * @param options the options of the session.
 * @param terminal the terminal of the session.
 * @return true when the session paints.
 */
internal fun sessionPaints(options: ReplOptions, terminal: Terminal): Boolean =
    ColourPolicy(options.colour).paints(terminal.type != DUMB_TERMINAL)

/**
 * Reads one line of the session.
 *
 * The terminal reports two events that are not lines: the interruption of
 * the line being typed, which kills the line and leaves the session alone,
 * and the end of the input, which ends the session.
 *
 * @param read the reading of the line.
 * @return the line, the empty line when the line was interrupted, and null
 *   when the input ended.
 */
internal fun readLine(read: () -> String): String? = try {
    read()
} catch (_: UserInterruptException) {
    // (Ctrl-C: the line dies, and the prompt is fresh)
    ""
} catch (_: EndOfFileException) {
    // (Ctrl-D: the session ends)
    null
}

/**
 * Runs the interactive session over the given terminal.
 *
 * The terminal decides whether the session paints: a terminal that cannot
 * paint (a pipe, or a terminal of the `dumb` kind) gets the plain lines, so
 * the transcript of a session that is piped is the transcript of the article,
 * byte for byte. The choice of the user overrides the terminal, and it
 * applies to every painted thing: the lines, the prompt and the line being
 * edited.
 *
 * @param repl the REPL that answers the lines.
 * @param options the options of the session.
 * @param terminal the terminal of the session.
 */
internal fun runReplSession(repl: QuiverRepl, options: ReplOptions, terminal: Terminal) {
    val palette = Palette(sessionPaints(options, terminal))
    val reader = buildReader(terminal, options, repl)
    val out = terminal.writer()
    try {
        if (options.showBanner) repl.banner.forEach { out.println(palette.paint(it)) }
        options.opening.forEach { out.println(palette.paint(it)) }
        out.flush()
        loop(repl, reader, palette, out)
    } finally {
        // The history and the terminal are released even when the session
        // fails: a session that dies must not leave the terminal open.
        saveHistory(reader)
        terminal.close()
    }
}

/**
 * The loop of the session: it reads the lines and answers them, until the
 * session ends or the input does.
 *
 * @param repl the REPL that answers the lines.
 * @param reader the reader of the session.
 * @param palette the palette of the output.
 * @param out the stream the lines are printed to.
 */
private fun loop(repl: QuiverRepl, reader: LineReader, palette: Palette, out: PrintWriter) {
    var running = true
    while (running) {
        val line = readLine { reader.readLine(palette.paintPrompt(repl.prompt)) }
        if (line == null) {
            running = false
        } else {
            val result = repl.execute(line)
            result.lines.forEach { out.println(palette.paint(it)) }
            out.flush()
            if (result is ReplResult.Finished) running = false
        }
    }
}

/**
 * Saves the history of the session.
 *
 * JLine keeps the history of the session in memory, and writes it to the
 * file only when it is told to: the save is therefore explicit, so that the
 * history of a session that ends with `:quit` is on the disk when the next
 * one starts.
 *
 * @param reader the reader of the session.
 */
private fun saveHistory(reader: LineReader) {
    try {
        reader.getHistory().save()
    } catch (_: IOException) {
        // (an unwritable history file: the history of the session stays in
        //  memory, and the next run faces the same world)
    }
}

/**
 * The reader of the session: JLine owns the terminal, Quiver owns the policy.
 *
 * @param terminal the terminal of the session.
 * @param options the options of the session.
 * @param repl the REPL the completion and the highlighting consult.
 * @return the reader of the session.
 */
internal fun buildReader(terminal: Terminal, options: ReplOptions, repl: QuiverRepl): LineReader {
    val paints = sessionPaints(options, terminal)
    val builder =
        LineReaderBuilder
            .builder()
            .terminal(terminal)
            .completer(QuiverCompleter { repl.session })
            .highlighter(QuiverHighlighter { paints })
    if (!options.noHistory) {
        val path = options.historyPath ?: historyFilePath(options.stateHome, options.home)
        if (path != null) {
            createHistoryDirectory(path)
            builder.variable(LineReader.HISTORY_FILE, path)
            builder.variable(LineReader.HISTORY_SIZE, HISTORY_SIZE)
            builder.variable(LineReader.HISTORY_FILE_SIZE, HISTORY_FILE_SIZE)
        }
    }
    return builder.build().also { reader ->
        // (the policy of the history: no duplicates, no blanks, and no
        //  leading spaces)
        reader.option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
        reader.option(LineReader.Option.HISTORY_REDUCE_BLANKS, true)
        reader.option(LineReader.Option.HISTORY_IGNORE_SPACE, true)
        // JLine attaches the history to the reader when the reader reads its
        // first line, and not before: the history is attached here, so that
        // the history of the previous session is there from the start, and
        // so that a session that reads no line at all still saves it.
        reader.getHistory().attach(reader)
    }
}

/**
 * Creates the directory of the history file, if the file has one.
 *
 * The directory is created ahead of JLine's attach-time load, since the
 * loader does not create it, and a path with no directory at all needs no
 * directory.
 *
 * @param path the path of the history file.
 */
private fun createHistoryDirectory(path: String) {
    try {
        Paths.get(path).parent?.let { Files.createDirectories(it) }
    } catch (_: IOException) {
        // (an unwritable state root: the history of the session stays in
        //  memory, and the next run faces the same world)
    }
}
