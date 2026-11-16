// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.jline.reader.Highlighter
import org.jline.reader.LineReader
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle

/**
 * The kinds of span the highlighter knows.
 */
internal enum class HighlightKind {

    /** The name of a command. */
    Command,

    /** A comment. */
    Comment,

    /** A string literal. */
    String,

    /** A reserved word of the language. */
    Keyword,

    /** A number. */
    Number,
}

/**
 * A span of a highlighted line.
 *
 * @property start the offset of the first character of the span.
 * @property end the offset past the last character of the span.
 * @property kind the kind of the span.
 */
internal data class HighlightSpan(val start: Int, val end: Int, val kind: HighlightKind)

/**
 * The spans of a line of the REPL.
 *
 * The scanner scans the lines of the REPL, not the programs: a line is
 * either a command or an input, and the scanner paints the pieces it
 * recognizes in either.
 *
 * @param buffer the line to scan.
 * @return the spans of the line, in order.
 */
internal fun highlightSpans(buffer: String): List<HighlightSpan> {
    val spans = mutableListOf<HighlightSpan>()
    var i = 0
    if (buffer.startsWith(":")) {
        val end = buffer.indexOfFirst { it.isWhitespace() }.let { if (it < 0) buffer.length else it }
        spans += HighlightSpan(0, end, HighlightKind.Command)
        i = end
    }
    while (i < buffer.length) {
        val c = buffer[i]
        when {
            c == '#' -> {
                spans += HighlightSpan(i, buffer.length, HighlightKind.Comment)
                i = buffer.length
            }

            c == '"' -> {
                val end = endOfString(buffer, i)
                spans += HighlightSpan(i, end, HighlightKind.String)
                i = end
            }

            c.isDigit() -> {
                val end = endOfNumber(buffer, i)
                spans += HighlightSpan(i, end, HighlightKind.Number)
                i = end
            }

            c.isLetter() || c == '_' || c == '$' -> {
                val end = endOfWord(buffer, i)
                if (buffer.substring(i, end) in RESERVED_WORDS) {
                    spans += HighlightSpan(i, end, HighlightKind.Keyword)
                }
                i = end
            }

            else -> i++
        }
    }
    return spans
}

/**
 * The end of a string literal.
 *
 * @param buffer the line to scan.
 * @param start the offset of the opening quote.
 * @return the offset past the closing quote, or the end of the line when the
 *   literal is not closed.
 */
private fun endOfString(buffer: String, start: Int): Int {
    var i = start + 1
    while (i < buffer.length) {
        val c = buffer[i]
        if (c == '\\' && i + 1 < buffer.length) {
            i += 2
        } else if (c == '"') {
            return i + 1
        } else {
            i++
        }
    }
    return buffer.length
}

/**
 * The end of a number.
 *
 * @param buffer the line to scan.
 * @param start the offset of the first digit.
 * @return the offset past the last digit.
 */
private fun endOfNumber(buffer: String, start: Int): Int {
    var i = start
    while (i < buffer.length && buffer[i].isDigit()) i++
    return i
}

/**
 * The end of a word.
 *
 * @param buffer the line to scan.
 * @param start the offset of the first character of the word.
 * @return the offset past the last character of the word.
 */
private fun endOfWord(buffer: String, start: Int): Int {
    var i = start
    while (i < buffer.length && isWordChar(buffer[i])) i++
    return i
}

/**
 * True when a character may be part of a word.
 *
 * The hyphen is one of them, because the primitives of the language spell
 * themselves with hyphens (`transitions-from`, and the like).
 *
 * @param c the character to test.
 * @return true when the character may be part of a word.
 */
private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$' || c == '-'

/**
 * The highlighter of the interactive REPL.
 *
 * @property enabled whether the line is painted at all.
 */
internal class QuiverHighlighter(private val enabled: () -> Boolean) : Highlighter {

    /**
     * Paints the line.
     *
     * @param reader the reader of the session.
     * @param buffer the line to paint.
     * @return the painted line.
     */
    override fun highlight(reader: LineReader, buffer: String): AttributedString {
        if (!enabled()) return AttributedString(buffer)
        val builder = AttributedStringBuilder()
        var position = 0
        for (span in highlightSpans(buffer)) {
            if (span.start > position) builder.append(buffer.substring(position, span.start))
            builder.append(buffer.substring(span.start, span.end), styleOf(span.kind))
            position = span.end
        }
        if (position < buffer.length) builder.append(buffer.substring(position))
        return builder.toAttributedString()
    }

    /**
     * The style of a span.
     *
     * @param kind the kind of the span.
     * @return the style the span is painted with.
     */
    private fun styleOf(kind: HighlightKind): AttributedStyle = when (kind) {
        HighlightKind.Command -> AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)
        HighlightKind.Comment -> AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE)
        HighlightKind.String -> AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)
        HighlightKind.Keyword -> AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA)
        HighlightKind.Number -> AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW)
    }
}
