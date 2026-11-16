// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

/**
 * The kinds of line the tool reports.
 *
 * The kind is the only thing that decides the colour of a line, so the
 * meaning of a line and the way it is painted never drift apart.
 */
internal enum class LineKind {

    /** An ordinary line, with no colour of its own. */
    Plain,

    /** A line of the banner that opens the session. */
    Banner,

    /** The line reports a recognized input. */
    Recognized,

    /** The line reports a rejected input. */
    Rejected,

    /** The line reports a failure. */
    Failure,
}

/**
 * A line the tool reports.
 *
 * @property text the text of the line.
 * @property kind the kind of the line, which decides its colour.
 */
internal data class ReportLine(val text: String, val kind: LineKind = LineKind.Plain)

/**
 * The palette of the tool: the colours of the lines it reports.
 *
 * The palette paints nothing when the terminal does not paint, so a session
 * whose output is piped is the same session, byte for byte.
 *
 * @property enabled whether the lines are painted at all.
 */
internal class Palette(private val enabled: Boolean) {

    /**
     * Paints a line.
     *
     * @param line the line to paint.
     * @return the painted line.
     */
    fun paint(line: ReportLine): String {
        if (!enabled) return line.text
        val style = styleOf(line.kind)
        return if (style.isEmpty()) line.text else style + line.text + RESET
    }

    /**
     * Paints the prompt of the session.
     *
     * @param prompt the prompt to paint.
     * @return the painted prompt.
     */
    fun paintPrompt(prompt: String): String = if (!enabled) prompt else CYAN + prompt + RESET

    /**
     * The style of a kind of line.
     *
     * @param kind the kind of the line.
     * @return the escape sequence of the style, or the empty text when the
     *   kind has no colour of its own.
     */
    private fun styleOf(kind: LineKind): String = when (kind) {
        LineKind.Plain -> ""
        LineKind.Banner -> DIM
        LineKind.Recognized -> GREEN
        LineKind.Rejected -> RED
        LineKind.Failure -> BOLD_RED
    }

    /** The escape sequences of the palette. */
    companion object {
        /** The sequence that ends a style. */
        private const val RESET = "\u001b[0m"

        /** The style of the banner. */
        private const val DIM = "\u001b[2m"

        /** The style of a recognized input. */
        private const val GREEN = "\u001b[32m"

        /** The style of a rejected input. */
        private const val RED = "\u001b[31m"

        /** The style of a failure. */
        private const val BOLD_RED = "\u001b[1;31m"

        /** The style of the prompt. */
        private const val CYAN = "\u001b[36m"
    }
}

/**
 * The policy of the colours.
 *
 * The policy is the same everywhere: the user may force the colours on or
 * off, and otherwise the terminal decides. The environment variable
 * `NO_COLOR` turns the colours off when the user does not ask for them: the
 * convention says that the variable forbids the colours when it is present
 * and not empty, whatever its value.
 *
 * @property forced the explicit choice of the user: true forces the colours
 *   on, false forces them off, and null leaves the choice to the terminal.
 * @property forbidden whether the environment forbids the colours.
 */
internal class ColourPolicy(
    private val forced: Boolean?,
    private val forbidden: Boolean = !System.getenv("NO_COLOR").isNullOrEmpty(),
) {

    /**
     * True when the lines are painted.
     *
     * @param terminalPaints whether the terminal paints anything.
     * @return true when the colours are on.
     */
    fun paints(terminalPaints: Boolean): Boolean = when (forced) {
        true -> true
        false -> false
        null -> terminalPaints && !forbidden
    }
}

/**
 * The options of the colours, for the commands that paint their own output.
 *
 * The two flags are the explicit choice of the user about the colours, and
 * they contradict each other: a command that takes both is a mistake of the
 * command line, and the usage summary says so.
 */
internal class ColourOptions : OptionGroup() {

    /** Whether the user asks for the colours, whatever the terminal is. */
    val color: Boolean by option(
        "--color",
        help = "Paint the output, even when it does not go to a terminal.",
    ).flag()

    /** Whether the user forbids the colours. */
    val noColor: Boolean by option(
        "--no-color",
        help = "Never paint the output.",
    ).flag()

    /**
     * The choice of the user about the colours.
     *
     * @param context the context of the command, which the usage summary of a
     *   mistake names.
     * @return true when the user asks for the colours, false when the user
     *   forbids them, and null when the user makes no choice.
     * @throws com.github.ajalt.clikt.core.UsageError when the user asks for
     *   the colours and forbids them at the same time.
     */
    fun choice(context: Context): Boolean? = when {
        color && noColor ->
            throw usageError("--color and --no-color cannot be used together", context)

        color -> true

        noColor -> false

        else -> null
    }
}

/**
 * True when the standard output of the process paints.
 *
 * The standard output paints when the process has a console, that is, when
 * both the standard input and the standard output are terminals, and when the
 * terminal is not a dumb one.
 *
 * @return true when the colours are welcome on the standard output.
 */
internal fun standardOutputPaints(): Boolean = System.console() != null && System.getenv("TERM") != "dumb"
