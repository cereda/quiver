// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tests of the data model: the typed values and the automaton.
 *
 * The accessors of a value report a Quiver error when the value has another
 * type, and every value knows its own type.
 */
class QuiverModelTest {

    @Test
    fun `a symbol is a symbol`() {
        val value = Value.Symbol("q0")
        assertTrue(value.isSymbol())
        assertEquals("q0", value.symbol().name)
        assertEquals(Value.Type.SYMBOL, value.type)
    }

    @Test
    fun `a pair is a pair`() {
        val value = Value.Pair(Value.Symbol("q0"), Value.IntVal(1))
        assertEquals(Value.Type.PAIR, value.type)
        assertEquals(Value.Symbol("q0"), value.asPair().left)
    }

    @Test
    fun `a sequence is a sequence`() {
        val value = Value.Seq(listOf(Value.Symbol("a"), Value.Symbol("b")))
        assertEquals(Value.Type.SEQ, value.type)
        assertEquals(2, value.asSeq().size)
    }

    @Test
    fun `an integer is an integer`() {
        val value = Value.IntVal(42)
        assertEquals(Value.Type.INT, value.type)
        assertEquals(BigInteger.valueOf(42), value.asInt())
    }

    @Test
    fun `a boolean is a boolean`() {
        val value = Value.BoolVal(true)
        assertEquals(Value.Type.BOOL, value.type)
        assertTrue(value.asBool())
    }

    @Test
    fun `a function is a function`() {
        val value = Value.Func(listOf("\$x"), Expr.Sym("\$x"), emptyMap())
        assertEquals(Value.Type.FUNC, value.type)
    }

    @Test
    fun `an automaton is an automaton`() {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())
        val value = Value.Automaton(automaton)
        assertEquals(Value.Type.AUTOMATON, value.type)
        assertEquals(automaton, value.asAutomaton())
    }

    @Test
    fun `a symbol is not an integer`() {
        val error = assertFailsWith<QuiverException> { Value.Symbol("q0").asInt() }
        assertContains(error.message ?: "", "Expected a integer value, got symbol")
    }

    @Test
    fun `a symbol is not a pair`() {
        val error = assertFailsWith<QuiverException> { Value.Symbol("q0").asPair() }
        assertContains(error.message ?: "", "Expected a pair value, got symbol")
    }

    @Test
    fun `a symbol is not a sequence`() {
        val error = assertFailsWith<QuiverException> { Value.Symbol("q0").asSeq() }
        assertContains(error.message ?: "", "Expected a sequence value, got symbol")
    }

    @Test
    fun `a symbol is not a boolean`() {
        val error = assertFailsWith<QuiverException> { Value.Symbol("q0").asBool() }
        assertContains(error.message ?: "", "Expected a boolean value, got symbol")
    }

    @Test
    fun `a symbol is not an automaton`() {
        val error = assertFailsWith<QuiverException> { Value.Symbol("q0").asAutomaton() }
        assertContains(error.message ?: "", "Expected a automaton value, got symbol")
    }

    @Test
    fun `a value renders itself in the human-readable notation`() {
        assertEquals("q0", valueToString(Value.Symbol("q0")))
        assertEquals("(q0,1)", valueToString(Value.Pair(Value.Symbol("q0"), Value.IntVal(1))))
        assertEquals("[seq a,b]", valueToString(Value.Seq(listOf(Value.Symbol("a"), Value.Symbol("b")))))
        assertEquals("[seq]", valueToString(Value.Seq(emptyList())))
        assertEquals("42", valueToString(Value.IntVal(42)))
        assertEquals("true", valueToString(Value.BoolVal(true)))
        assertEquals("false", valueToString(Value.BoolVal(false)))
        assertEquals("<fn \$x => ...>", valueToString(Value.Func(listOf("\$x"), Expr.Sym("\$x"), emptyMap())))
    }

    @Test
    fun `an integer is not a symbol`() {
        val error = assertFailsWith<QuiverException> { Value.IntVal(1).symbol() }
        assertContains(error.message ?: "", "Expected a symbol value, got int")
    }

    @Test
    fun `a value that is not a symbol is not a symbol`() {
        assertFalse(Value.IntVal(1).isSymbol())
    }

    @Test
    fun `a sequence that is not one is not one`() {
        val error = assertFailsWith<QuiverException> { Value.IntVal(1).asSeq() }
        assertContains(error.message ?: "", "Expected a sequence value, got int")
    }

    @Test
    fun `an automaton that is not one is not one`() {
        val error = assertFailsWith<QuiverException> { Value.IntVal(1).asAutomaton() }
        assertContains(error.message ?: "", "Expected a automaton value, got int")
    }

    @Test
    fun `an automaton renders itself`() {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf("q1"), mutableListOf())
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        val rendered = automaton.toString()
        assertContains(rendered, "states=[q0, q1]")
        assertContains(rendered, "accepting={q1}")
        assertContains(rendered, "(q0, a) -> q1")
    }

    @Test
    fun `an inserted transition is appended, keeping multiple transitions per key`() {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.addTransition("q0", "a", "q2", Value.IntVal(1))
        assertEquals(2, automaton.transitions.size)
        assertEquals(listOf("q1", "q2"), automaton.transitions.map { it.to })
    }

    @Test
    fun `an inserted transition has a default value`() {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())
        automaton.addTransition("q0", "a", "q1")
        assertEquals(Value.IntVal(0), automaton.transitions.single().value)
    }

    @Test
    fun `a cloned automaton is a copy`() {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf("q1"), mutableListOf())
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        val copy = automaton.clone()
        copy.addTransition("q0", "b", "q1", Value.IntVal(0))
        assertEquals(1, automaton.transitions.size)
        assertEquals(2, copy.transitions.size)
        assertEquals(automaton.states, copy.states)
        assertEquals(automaton.accepting, copy.accepting)
    }
}
