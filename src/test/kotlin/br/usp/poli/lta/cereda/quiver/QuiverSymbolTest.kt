// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tests of the symbols.
 *
 * A double-quoted string is a symbol: its name is the text of the string,
 * and the name may hold any character. The bare word `epsilon` is the
 * internal transition, and it is reserved: the quoted string `"epsilon"` is
 * the ordinary symbol named epsilon.
 */
class QuiverSymbolTest {

    @Test
    fun `a quoted string is a symbol`() {
        val automaton = automatonOf("symbols.quiver")
        val transition = automaton.transitions.first { it.from == "q0" }
        assertEquals(Label.Symbol("golf"), transition.label)
    }

    @Test
    fun `a quoted symbol is read from the input`() {
        assertTrue(runQuiver(load("symbols.quiver"), listOf("golf", "epsilon")))
    }

    @Test
    fun `a quoted symbol that is not read rejects the input`() {
        assertFalse(runQuiver(load("symbols.quiver"), listOf("golf")))
    }

    @Test
    fun `the bare word epsilon is the internal transition`() {
        val automaton = automatonOf("symbols.quiver")
        val transition = automaton.transitions.first { it.from == "q1" }
        assertEquals(Label.Internal, transition.label)
    }

    @Test
    fun `the quoted word epsilon is an ordinary symbol`() {
        val automaton = automatonOf("symbols.quiver")
        val transition = automaton.transitions.first { it.from == "q2" }
        assertEquals(Label.Symbol("epsilon"), transition.label)
    }

    @Test
    fun `the internal marker and the symbol named epsilon never collide`() {
        val automaton = automatonOf("symbols.quiver")
        val labels = automaton.transitions.map { it.label }
        assertTrue(Label.Internal in labels, "The internal marker is missing: $labels")
        assertTrue(Label.Symbol("epsilon") in labels, "The symbol named epsilon is missing: $labels")
    }

    @Test
    fun `a symbol whose name is not plain is rendered quoted`() {
        assertEquals("\"a b\"", Label.Symbol("a b").render())
        assertEquals("\"epsilon\"", Label.Symbol("epsilon").render())
        assertEquals("a", Label.Symbol("a").render())
        assertEquals("q0", Label.Symbol("q0").render())
    }

    @Test
    fun `a symbol whose name starts with a digit is rendered quoted`() {
        assertEquals("\"1a\"", Label.Symbol("1a").render())
    }

    @Test
    fun `a symbol whose name is empty is rendered quoted`() {
        assertEquals("\"\"", Label.Symbol("").render())
    }

    @Test
    fun `the name of a symbol is escaped when it is rendered`() {
        assertEquals("\"a\\\"b\"", Label.Symbol("a\"b").render())
        assertEquals("\"a\\\\b\"", Label.Symbol("a\\b").render())
        assertEquals("\"a\\nb\"", Label.Symbol("a\nb").render())
        assertEquals("\"a\\tb\"", Label.Symbol("a\tb").render())
    }

    @Test
    fun `the internal marker is rendered as the bare word`() {
        assertEquals("epsilon", Label.Internal.render())
    }

    @Test
    fun `the text of the internal marker is the name of the marker`() {
        assertEquals("epsilon", Label.Internal.text)
        assertEquals("golf", Label.Symbol("golf").text)
    }

    @Test
    fun `a transition carries the default value`() {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())
        automaton.addTransition("q0", Label.Symbol("a"), "q1")
        val transition = automaton.transitions.first()
        assertEquals(Value.IntVal(0), transition.value)
    }

    @Test
    fun `a symbol with a space in its name is matched exactly`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, "a b") -> (q1, 0)
              }
            }
            """.trimIndent()
        assertTrue(runQuiver(source, listOf("a b")))
        assertFalse(runQuiver(source, listOf("a", "b")))
    }

    @Test
    fun `a quoted symbol is a symbol in the body of a rule`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, "golf") -> (q1, 0)
              }
            }

            (q0, "golf") -> (${'$'}to, ${'$'}n) => {
              (q0, "golf") -> (${'$'}to, ${'$'}n) => _
              _ => (q0, "golf") -> (q1, ${'$'}n + 1)
            }
            """.trimIndent()
        assertTrue(runQuiver(source, listOf("golf")))
    }

    @Test
    fun `the reserved word is an error in the value of a transition`() {
        val error = assertFailsWith<QuiverException> { parseSource(load("error-reserved-epsilon-value.quiver")) }
        assertContains(error.message ?: "", "epsilon is reserved")
    }

    @Test
    fun `the reserved word is an error in the pattern of a rule`() {
        val error = assertFailsWith<QuiverException> { parseSource(load("error-reserved-epsilon-pattern.quiver")) }
        assertContains(error.message ?: "", "epsilon is reserved")
    }

    @Test
    fun `the reserved word is legal as a state name`() {
        val automaton = automatonOf("state-epsilon.quiver")
        assertEquals("epsilon", automaton.start)
        assertTrue(automaton.states.contains("epsilon"), "The state is missing: ${automaton.states}")
    }

    @Test
    fun `a state named epsilon is reached by the run`() {
        // The run reads `a` and reaches the accepting state q1; the second
        // symbol takes the run back to the state named epsilon, which does
        // not accept.
        assertTrue(runQuiver(load("state-epsilon.quiver"), listOf("a")))
        assertFalse(runQuiver(load("state-epsilon.quiver"), listOf("a", "b")))
    }

    @Test
    fun `the reserved word is an error in the input of the program`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              input => ${'$'}seq [ epsilon ]
              mapping => {
                (q0, a) -> (q1, 0)
              }
            }
            """.trimIndent()
        val error = assertFailsWith<QuiverException> { parseSource(source) }
        assertContains(error.message ?: "", "epsilon is reserved")
    }

    @Test
    fun `the quoted word epsilon is legal in the input of the program`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              input => ${'$'}seq [ "epsilon" ]
              mapping => {
                (q0, "epsilon") -> (q1, 0)
              }
            }
            """.trimIndent()
        assertTrue(runQuiver(source))
    }

    /** Builds the automaton declared by the FSM header of a source file. */
    private fun automatonOf(name: String): Automaton {
        val fsm = parseSource(load(name)).filterIsInstance<Decl.Fsm>().first()
        val automaton = Automaton(fsm.start, mutableSetOf(fsm.start), mutableSetOf(), mutableListOf())
        fsm.accepting.forEach { automaton.accepting.add(it) }
        fsm.mapping.forEach { arrow ->
            val entry = mappingEntry(arrow)
            val value = evalExpr(entry.value, emptyMap(), fsm.start, automaton)
            automaton.addTransition(entry.from, entry.label, entry.to, value)
        }
        return automaton
    }

    /** Loads a resource of the test suite. */
    private fun load(name: String): String = loadResource(name)
}
