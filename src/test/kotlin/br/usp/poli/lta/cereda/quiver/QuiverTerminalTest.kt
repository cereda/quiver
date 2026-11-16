// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.ParsedLine
import org.jline.terminal.Terminal
import org.jline.terminal.impl.DumbTerminal
import org.jline.utils.AttributedStyle
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tests of the adapters that bind Quiver to JLine.
 *
 * The completion and the highlighting have a policy of their own, which is
 * tested on its own, and an adapter, which is tested here: the adapter
 * translates the policy into the world of JLine, and the world of JLine is
 * a terminal, a reader and a line.
 */
class QuiverTerminalTest {

    @Test
    fun `the completer offers the commands of the registry`() {
        val candidates = mutableListOf<Candidate>()
        QuiverCompleter { QuiverSession() }.complete(reader(), parsed(":re", 3), candidates)
        val values = candidates.map { it.value() }
        assertContains(values, ":recognize")
        assertContains(values, ":reset")
    }

    @Test
    fun `the completer offers the symbols of the program`() {
        val session = QuiverSession()
        session.load("src/test/resources/simple.quiver")
        val candidates = mutableListOf<Candidate>()
        QuiverCompleter { session }.complete(reader(), parsed("", 0), candidates)
        assertEquals(listOf("a", "b"), candidates.map { it.value() })
    }

    @Test
    fun `the completer of a file is the one of jline`() {
        // The names of the files are JLine's own business, and the adapter
        // steps aside when the lane is the file lane.
        val candidates = mutableListOf<Candidate>()
        QuiverCompleter { QuiverSession() }.complete(reader(), parsed(":load ", 6), candidates)
        assertTrue(candidates.isNotEmpty(), "The completer of JLine offered no file")
    }

    @Test
    fun `the highlighter paints the line when it is enabled`() {
        val painted = QuiverHighlighter { true }.highlight(reader(), ":load file.quiver")
        assertEquals(":load file.quiver", painted.toString())
        assertEquals(
            AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN),
            painted.styleAt(0),
        )
    }

    @Test
    fun `the highlighter paints nothing when it is disabled`() {
        val painted = QuiverHighlighter { false }.highlight(reader(), ":load file.quiver")
        assertEquals(":load file.quiver", painted.toString())
        assertEquals(AttributedStyle.DEFAULT, painted.styleAt(0))
    }

    @Test
    fun `the highlighter paints every kind of span`() {
        val painted = QuiverHighlighter { true }.highlight(reader(), "42 \"s\" when # c")
        assertEquals(
            AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW),
            painted.styleAt(0),
        )
        assertEquals(
            AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN),
            painted.styleAt(3),
        )
        assertEquals(
            AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA),
            painted.styleAt(7),
        )
        assertEquals(
            AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE),
            painted.styleAt(12),
        )
    }

    @Test
    fun `the highlighter of the session follows the choice of the colours`() {
        // The choice of the user reaches the line being edited, and not only
        // the lines the session prints.
        val terminal = terminal()
        val painted =
            buildReader(terminal, ReplOptions(noHistory = true, colour = true), QuiverRepl())
                .highlighter
                .highlight(reader(), ":load file.quiver")
        assertEquals(
            AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN),
            painted.styleAt(0),
        )
        val plain =
            buildReader(terminal, ReplOptions(noHistory = true, colour = false), QuiverRepl())
                .highlighter
                .highlight(reader(), ":load file.quiver")
        assertEquals(AttributedStyle.DEFAULT, plain.styleAt(0))
    }

    @Test
    fun `the session of a dumb terminal paints nothing`() {
        val terminal = terminal()
        assertTrue(!sessionPaints(ReplOptions(noHistory = true), terminal), "The session paints")
        assertTrue(sessionPaints(ReplOptions(noHistory = true, colour = true), terminal), "The session does not paint")
    }

    @Test
    fun `the session without a home keeps no history`() {
        // There is no place to keep the history, and the session goes on
        // without one.
        val terminal = terminal()
        val reader = buildReader(terminal, ReplOptions(noHistory = false), QuiverRepl())
        assertEquals(null, reader.getVariables()[org.jline.reader.LineReader.HISTORY_FILE])
    }

    /**
     * A reader over a terminal that reads nothing.
     *
     * @return the reader of the test.
     */
    private fun reader(): LineReader {
        val terminal = DumbTerminal(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream())
        return LineReaderBuilder.builder().terminal(terminal).build()
    }

    /**
     * A terminal of the kind that cannot paint.
     *
     * @return the terminal of the test.
     */
    private fun terminal(): Terminal = DumbTerminal(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream())

    /**
     * A line the adapters see.
     *
     * @param line the text of the line.
     * @param cursor the position of the cursor on the line.
     * @return the line, as JLine sees it.
     */
    private fun parsed(line: String, cursor: Int): ParsedLine = object : ParsedLine {
        override fun word(): String = wordAt(line, cursor)

        // (JLine counts the cursor from the start of the word, and the
        //  word of the policy ends at the cursor)
        override fun wordCursor(): Int = word().length

        override fun wordIndex(): Int = 0

        override fun words(): List<String> = listOf(word())

        override fun line(): String = line

        override fun cursor(): Int = cursor
    }
}
