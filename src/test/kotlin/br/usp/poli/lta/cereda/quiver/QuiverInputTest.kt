// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tests of the input of a run.
 *
 * The input of a run comes either from the command line or from the `input`
 * key of the header of the program. The command line wins, and a run that
 * gets no input from either fails.
 */
class QuiverInputTest {

    @Test
    fun `the header declares no input when the key is absent`() {
        val fsm = parseSource(load("simple.quiver")).filterIsInstance<Decl.Fsm>().first()
        assertNull(fsm.input)
    }

    @Test
    fun `the header declares the input of the run`() {
        val fsm = parseSource(load("input.quiver")).filterIsInstance<Decl.Fsm>().first()
        assertEquals(listOf("a", "b"), fsm.input)
    }

    @Test
    fun `the input of the header is used when the command line gives none`() {
        assertTrue(runQuiver(load("input.quiver")))
    }

    @Test
    fun `the input of the command line overrides the input of the header`() {
        assertFalse(runQuiver(load("input.quiver"), listOf("x", "y")))
    }

    @Test
    fun `the input of the header is used when the input is null`() {
        assertTrue(runQuiver(load("input.quiver"), null))
    }

    @Test
    fun `a program that declares no input fails`() {
        val error = assertFailsWith<NoInputException> { runQuiver(load("simple.quiver")) }
        assertEquals("the program declares no input; give one on the command line", error.message)
    }

    @Test
    fun `the empty input is an input`() {
        // The empty input is not the absence of an input: the run reads
        // nothing, and the closure of the start state decides.
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q0 ]
              mapping => {
                (q0, a) -> (q1, 0)
              }
            }
            """.trimIndent()
        assertTrue(runQuiver(source, emptyList()))
    }

    @Test
    fun `the input of the header may be the empty input`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q0 ]
              input => ${'$'}seq [ ]
              mapping => {
                (q0, a) -> (q1, 0)
              }
            }
            """.trimIndent()
        assertTrue(runQuiver(source))
    }

    @Test
    fun `the input of the header holds quoted symbols`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              input => ${'$'}seq [ "a b" ]
              mapping => {
                (q0, "a b") -> (q1, 0)
              }
            }
            """.trimIndent()
        assertTrue(runQuiver(source))
    }

    @Test
    fun `the input of the header rejects a term that is not a symbol`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              input => ${'$'}seq [ 42 ]
              mapping => {
                (q0, a) -> (q1, 0)
              }
            }
            """.trimIndent()
        val error = assertFailsWith<QuiverException> { parseSource(source) }
        assertContains(error.message ?: "", "The input must be a list of symbols")
    }

    @Test
    fun `the input of the header is written without the dollar sign`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              input => [ a, b ]
              mapping => {
                (q0, a) -> (q1, 0)
              }
            }
            """.trimIndent()
        val fsm = parseSource(source).filterIsInstance<Decl.Fsm>().first()
        assertEquals(listOf("a", "b"), fsm.input)
    }

    /** Loads a resource of the test suite. */
    private fun load(name: String): String = loadResource(name)
}
