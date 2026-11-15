// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tests of the generator of fresh states.
 *
 * The primitive `fresh-state()` mints a state the automaton does not know yet:
 * the names are `g0`, `g1`, and so on, the set of the states only grows, and
 * every evaluation mints a name of its own.
 */
class QuiverFreshStateTest {

    @Test
    fun `the generator mints the first fresh state`() {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())
        assertEquals("g0", automaton.freshState())
    }

    @Test
    fun `the generator mints a new name every time`() {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())
        assertEquals("g0", automaton.freshState())
        assertEquals("g1", automaton.freshState())
        assertEquals("g2", automaton.freshState())
    }

    @Test
    fun `the fresh state joins the set of the states`() {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())
        automaton.freshState()
        assertTrue(automaton.states.contains("g0"), "The fresh state is missing: ${automaton.states}")
    }

    @Test
    fun `the set of the states only grows`() {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())
        automaton.freshState()
        automaton.freshState()
        assertEquals(setOf("q0", "g0", "g1"), automaton.states)
    }

    @Test
    fun `the counter of the clone continues the sequence`() {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())
        automaton.freshState()
        val clone = automaton.clone()
        assertEquals("g1", clone.freshState())
    }

    @Test
    fun `the primitive evaluates to the name of the fresh state`() {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())
        val value = evalExpr(Expr.FreshState, emptyMap(), "q0", automaton)
        assertEquals(Value.Symbol("g0"), value)
    }

    @Test
    fun `the primitive is parsed as a call`() {
        val source =
            """
            (q0, a) -> (${'$'}to, ${'$'}n) => {
              _ => (q0, a) -> (fresh-state(), ${'$'}n)
            }
            """.trimIndent()
        val rule = parseSource(source).filterIsInstance<Decl.RuleDecl>().first().rule
        val clause = rule.clauses.first()
        val arrow = (clause.right as ClauseRight.Rhs).expr as Expr.Arrow
        val target = arrow.right as Expr.Pair
        assertEquals(Expr.FreshState, target.left)
    }

    @Test
    fun `the primitive is not a pattern`() {
        // A pattern is matched against the subject, and the generator has no
        // counterpart to match against.
        val source =
            """
            fresh-state() => {
              _ => (q0, a) -> (q1, 0)
            }
            """.trimIndent()
        val error = assertFailsWith<QuiverException> { parseSource(source) }
        assertContains(error.message ?: "", "fresh-state() is a generator")
    }

    @Test
    fun `the primitive is not a pattern in the symbol of a transition`() {
        // The pattern of a rule may be a whole transition, and the generator
        // is an error in every slot of it.
        val source =
            """
            (q0, fresh-state()) -> (q1, 0) => {
              _ => (q1, 0)
            }
            """.trimIndent()
        val error = assertFailsWith<QuiverException> { parseSource(source) }
        assertContains(error.message ?: "", "fresh-state() is a generator")
    }

    @Test
    fun `the primitive is not a pattern in the state of a transition`() {
        val source =
            """
            (fresh-state(), a) -> (q1, 0) => {
              _ => (q1, 0)
            }
            """.trimIndent()
        val error = assertFailsWith<QuiverException> { parseSource(source) }
        assertContains(error.message ?: "", "fresh-state() is a generator")
    }

    @Test
    fun `the primitive is not a pattern in the value of a transition`() {
        val source =
            """
            (q0, a) -> (q1, fresh-state()) => {
              _ => (q1, 0)
            }
            """.trimIndent()
        val error = assertFailsWith<QuiverException> { parseSource(source) }
        assertContains(error.message ?: "", "fresh-state() is a generator")
    }

    @Test
    fun `the primitive is not a pattern in a clause`() {
        val source =
            """
            (q0, a) -> (${'$'}to, ${'$'}n) => {
              (q0, fresh-state()) -> (${'$'}to, ${'$'}n) => _
            }
            """.trimIndent()
        val error = assertFailsWith<QuiverException> { parseSource(source) }
        assertContains(error.message ?: "", "fresh-state() is a generator")
    }

    @Test
    fun `the primitive is not a pattern in a sweep`() {
        val source =
            """
            (q0, a) -> (${'$'}to, ${'$'}n) => {
              each (q0, fresh-state()) -> (${'$'}t, ${'$'}v) => {
                _ => (${'$'}t, ${'$'}v)
              }
            }
            """.trimIndent()
        val error = assertFailsWith<QuiverException> { parseSource(source) }
        assertContains(error.message ?: "", "fresh-state() is a generator")
    }

    @Test
    fun `the primitive is a value in the target of a transition`() {
        // The target of a transition is evaluated, and not matched, so the
        // generator is welcome there.
        val source =
            """
            (q0, a) -> (${'$'}to, ${'$'}n) => {
              _ => (q0, a) -> (fresh-state(), ${'$'}n)
            }
            """.trimIndent()
        val rule = parseSource(source).filterIsInstance<Decl.RuleDecl>().first().rule
        val clause = rule.clauses.first()
        val arrow = (clause.right as ClauseRight.Rhs).expr as Expr.Arrow
        val target = arrow.right as Expr.Pair
        assertEquals(Expr.FreshState, target.left)
    }

    @Test
    fun `the program of the sample recognizes its input`() {
        assertTrue(runQuiver(load("fresh.quiver"), listOf("a", "b")))
    }

    @Test
    fun `the program of the sample rejects another input`() {
        assertFalse(runQuiver(load("fresh.quiver"), listOf("b", "a")))
    }

    @Test
    fun `the program of the sample grows the automaton`() {
        val output = captureStdout {
            assertTrue(runQuiver(load("fresh.quiver"), listOf("a", "b")))
        }
        // The program itself prints nothing; the run is quiet.
        assertEquals("", output)
    }

    @Test
    fun `the fresh state is the target of the rewritten transition`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q2 ]
              mapping => {
                (q0, a) -> (q1, 0)
                (q1, b) -> (q2, 0)
              }
            }

            (q0, a) -> (${'$'}to, ${'$'}n) => {
              _ => (q0, a) -> (fresh-state(), ${'$'}n)
            }
            """.trimIndent()
        assertFalse(runQuiver(source, listOf("a", "b")))
    }

    @Test
    fun `every evaluation of the primitive mints a name of its own`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q1, 0)
              }
            }

            (q0, a) -> (${'$'}to, ${'$'}n) => {
              _ => (q0, a) -> (fresh-state(), ${'$'}n)
              _ => (q0, a) -> (fresh-state(), ${'$'}n)
            }
            """.trimIndent()
        val output = captureStdout {
            assertFalse(runQuiver(source, listOf("a")))
        }
        assertEquals("", output)
    }

    @Test
    fun `the name of the fresh state is emitted`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q1, 0)
              }
            }

            (q0, a) -> (${'$'}to, ${'$'}n) => {
              _ => (q0, a) -> (q1, emit(fresh-state()))
            }
            """.trimIndent()
        val output = captureStdout {
            assertTrue(runQuiver(source, listOf("a")))
        }
        assertContains(output, "g0")
    }

    /** Loads a resource of the test suite. */
    private fun load(name: String): String = loadResource(name)
}
