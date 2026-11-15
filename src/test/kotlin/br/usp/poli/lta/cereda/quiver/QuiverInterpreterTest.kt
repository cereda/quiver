// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tests of the interpreter.
 *
 * They cover the paths a well-formed program does not take: the patterns that
 * never match, the strategies that do not fire, and the guards whose value is
 * neither a boolean nor an integer.
 */
class QuiverInterpreterTest {

    // ---------------------------------------------------------------------
    // The matcher.
    // ---------------------------------------------------------------------

    @Test
    fun `a pair pattern does not match a symbol`() {
        val matched = matchTerm(Expr.Pair(Expr.Sym("q0"), Expr.Sym("a")), Value.Symbol("q0"), emptyMap())
        assertNull(matched)
    }

    @Test
    fun `a sequence pattern matches a sequence`() {
        val matched =
            matchTerm(
                Expr.Seq(listOf(Expr.Sym("\$x"))),
                Value.Seq(listOf(Value.Symbol("a"))),
                emptyMap(),
            )
        assertNotNull(matched)
        assertEquals(Value.Symbol("a"), matched["\$x"])
    }

    @Test
    fun `a sequence pattern does not match a pair`() {
        val matched =
            matchTerm(Expr.Seq(listOf(Expr.Sym("a"))), Value.Pair(Value.Symbol("a"), Value.Symbol("b")), emptyMap())
        assertNull(matched)
    }

    @Test
    fun `a sequence of a different length does not match`() {
        val matched =
            matchTerm(
                Expr.Seq(listOf(Expr.Sym("a"), Expr.Sym("b"))),
                Value.Seq(listOf(Value.Symbol("a"))),
                emptyMap(),
            )
        assertNull(matched)
    }

    @Test
    fun `an arrow pattern does not match a symbol`() {
        val matched =
            matchTerm(
                Expr.Arrow(Expr.Pair(Expr.Sym("q0"), Expr.Sym("a")), Expr.Pair(Expr.Sym("q1"), Expr.IntLit(0))),
                Value.Symbol("q0"),
                emptyMap(),
            )
        assertNull(matched)
    }

    @Test
    fun `an arithmetic pattern never matches`() {
        assertNull(matchTerm(Expr.Plus(Expr.IntLit(1), Expr.IntLit(2)), Value.IntVal(3), emptyMap()))
        assertNull(matchTerm(Expr.Minus(Expr.IntLit(3), Expr.IntLit(2)), Value.IntVal(1), emptyMap()))
    }

    @Test
    fun `a function pattern never matches`() {
        val function = Expr.Fun(listOf("\$x"), Expr.Sym("\$x"))
        assertNull(matchTerm(function, Value.Symbol("q0"), emptyMap()))
        assertNull(matchTerm(Expr.App(function, Expr.IntLit(1)), Value.Symbol("q0"), emptyMap()))
    }

    @Test
    fun `an emitting pattern never matches`() {
        assertNull(matchTerm(Expr.Emit(Expr.IntLit(1)), Value.IntVal(1), emptyMap()))
    }

    @Test
    fun `a binding whose term does not match fails`() {
        assertNull(matchTerm(Expr.Bind("x", Expr.Sym("a")), Value.Symbol("b"), emptyMap()))
    }

    @Test
    fun `a binding may name its variable explicitly`() {
        val matched = matchTerm(Expr.Bind("\$x", Expr.Sym("a")), Value.Symbol("a"), emptyMap())
        assertNotNull(matched)
        assertEquals(Value.Symbol("a"), matched["\$x"])
    }

    @Test
    fun `an underscore symbol matches anything`() {
        assertNotNull(matchTerm(Expr.Sym("_"), Value.Symbol("q0"), emptyMap()))
    }

    @Test
    fun `a sequence whose second item does not match fails`() {
        val matched =
            matchTerm(
                Expr.Seq(listOf(Expr.Sym("a"), Expr.Sym("b"))),
                Value.Seq(listOf(Value.Symbol("a"), Value.Symbol("c"))),
                emptyMap(),
            )
        assertNull(matched)
    }

    @Test
    fun `a primitive pattern never matches`() {
        assertNull(matchTerm(Expr.StateRef, Value.Symbol("q0"), emptyMap()))
        assertNull(matchTerm(Expr.TransitionsFrom(Expr.Sym("q0")), Value.Symbol("q0"), emptyMap()))
        assertNull(matchTerm(Expr.CloneAutomaton, Value.Symbol("q0"), emptyMap()))
    }

    @Test
    fun `a repeated variable must match the same term`() {
        val bound = mapOf("\$x" to Value.Symbol("a"))
        assertNull(matchTerm(Expr.Sym("\$x"), Value.Symbol("b"), bound))
        assertNotNull(matchTerm(Expr.Sym("\$x"), Value.Symbol("a"), bound))
    }

    @Test
    fun `a literal symbol must match exactly`() {
        assertNull(matchTerm(Expr.Sym("a"), Value.Symbol("b"), emptyMap()))
        assertNotNull(matchTerm(Expr.Sym("a"), Value.Symbol("a"), emptyMap()))
    }

    @Test
    fun `a boolean literal matches its own value`() {
        assertNotNull(matchTerm(Expr.BoolLit(true), Value.BoolVal(true), emptyMap()))
        assertNull(matchTerm(Expr.BoolLit(true), Value.BoolVal(false), emptyMap()))
    }

    @Test
    fun `an integer literal matches its own value`() {
        assertNotNull(matchTerm(Expr.IntLit(3), Value.IntVal(3), emptyMap()))
        assertNull(matchTerm(Expr.IntLit(3), Value.IntVal(4), emptyMap()))
    }

    @Test
    fun `an integer literal does not match a value of another type`() {
        assertNull(matchTerm(Expr.IntLit(3), Value.Symbol("a"), emptyMap()))
        assertNull(matchTerm(Expr.BoolLit(true), Value.Symbol("a"), emptyMap()))
        assertNull(matchTerm(Expr.BoolLit(true), Value.IntVal(1), emptyMap()))
    }

    // ---------------------------------------------------------------------
    // The evaluator.
    // ---------------------------------------------------------------------

    @Test
    fun `a binding in a value position is an error`() {
        val error =
            assertFailsWith<QuiverException> {
                evalExpr(Expr.Bind("x", Expr.Sym("a")), emptyMap(), "q0", emptyAutomaton())
            }
        assertContains(error.message ?: "", "A binding is only allowed in a pattern")
    }

    @Test
    fun `the primitives evaluate`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        assertEquals(Value.Symbol("q0"), evalExpr(Expr.StateRef, emptyMap(), "q0", automaton))
        val transitions = evalExpr(Expr.TransitionsFrom(Expr.Sym("q0")), emptyMap(), "q0", automaton)
        assertTrue(transitions is Value.Seq, "Expected a sequence, got: $transitions")
        assertEquals(1, transitions.asSeq().size)
        assertTrue(evalExpr(Expr.CloneAutomaton, emptyMap(), "q0", automaton) is Value.Automaton)
    }

    @Test
    fun `a sequence evaluates to a sequence of values`() {
        val sequence = evalExpr(Expr.Seq(listOf(Expr.Sym("a"), Expr.IntLit(1))), emptyMap(), "q0", emptyAutomaton())
        assertEquals(Value.Seq(listOf(Value.Symbol("a"), Value.IntVal(1))), sequence)
    }

    @Test
    fun `the wildcard evaluates to the underscore symbol`() {
        assertEquals(Value.Symbol("_"), evalExpr(Expr.Wildcard, emptyMap(), "q0", emptyAutomaton()))
    }

    @Test
    fun `applying a value that is not a function is an error`() {
        val error =
            assertFailsWith<QuiverException> {
                evalExpr(Expr.App(Expr.Sym("f"), Expr.IntLit(1)), emptyMap(), "q0", emptyAutomaton())
            }
        assertContains(error.message ?: "", "Cannot apply a non-function value")
    }

    @Test
    fun `transitions-from expects a state symbol`() {
        val error =
            assertFailsWith<QuiverException> {
                evalExpr(Expr.TransitionsFrom(Expr.IntLit(1)), emptyMap(), "q0", emptyAutomaton())
            }
        assertContains(error.message ?: "", "transitions-from expects a state symbol")
    }

    // ---------------------------------------------------------------------
    // The fuel.
    // ---------------------------------------------------------------------

    @Test
    fun `the fuel counts the steps taken`() {
        val fuel = Fuel(3)
        assertEquals(3, fuel.remaining)
        repeat(3) { fuel.consume() }
        assertEquals(0, fuel.remaining)
        assertEquals(3, fuel.taken)
        val error = assertFailsWith<FuelExhaustedException> { fuel.consume() }
        assertContains(error.message ?: "", "after 3 steps")
    }

    // ---------------------------------------------------------------------
    // The strategies and the guards.
    // ---------------------------------------------------------------------

    @Test
    fun `the all strategy fires every matching rule`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        val rules =
            listOf(
                rule("(q0, a) -> (\$x, \$n)", "_ => (q0, b) -> (q1, 0)"),
                rule("(q0, a) -> (\$x, \$n)", "_ => (q0, c) -> (q1, 0)"),
            )
        val strategy = Strategy("all", StrategyKind.ALL, DEFAULT_MAX_ITERATIONS, rules)
        assertTrue(recognize("a", automaton, strategy))
        assertEquals(3, automaton.transitions.size)
    }

    @Test
    fun `a guard whose value is a symbol passes`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        val rule = rule("(q0, a) -> (\$x, \$n) when q0", "_ => (q0, b) -> (q1, 0)")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("a", automaton, strategy))
        assertEquals(2, automaton.transitions.size)
    }

    @Test
    fun `a rule whose pattern never matches does not fire`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        val rules =
            listOf(
                rule("\$state", "_ => (q0, b) -> (q1, 0)"),
                rule("transitions-from(q0)", "_ => (q0, c) -> (q1, 0)"),
                rule("clone-automaton()", "_ => (q0, d) -> (q1, 0)"),
                rule("true", "_ => (q0, e) -> (q1, 0)"),
                rule("42", "_ => (q0, f) -> (q1, 0)"),
            )
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, rules)
        assertTrue(recognize("a", automaton, strategy))
        assertEquals(1, automaton.transitions.size)
    }

    @Test
    fun `a rule of every pattern shape is tried`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        // Every pattern shape is offered to the subject; none of them matches
        // it, and the wildcard's guard rejects it, so no rule fires.
        val rules =
            listOf(
                rule("(q9, z9)", "_ => (q0, b) -> (q1, 0)"),
                rule("\$seq [ a ]", "_ => (q0, c) -> (q1, 0)"),
                rule("1 + 2", "_ => (q0, d) -> (q1, 0)"),
                rule("1 - 2", "_ => (q0, e) -> (q1, 0)"),
                rule("fn x => x", "_ => (q0, f) -> (q1, 0)"),
                rule("f(1)", "_ => (q0, g) -> (q1, 0)"),
                rule("emit(1)", "_ => (q0, h) -> (q1, 0)"),
                rule("x <- (q9, z9)", "_ => (q0, i) -> (q1, 0)"),
                rule("\$x when false", "_ => (q0, j) -> (q1, 0)"),
                rule("_ when false", "_ => (q0, j) -> (q1, 0)"),
            )
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, rules)
        assertTrue(recognize("a", automaton, strategy))
        assertEquals(1, automaton.transitions.size)
    }

    @Test
    fun `recognition works without a rule index`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        assertTrue(recognize("a", automaton, null))
        assertTrue(!recognize("b", automaton, null))
    }

    @Test
    fun `the all strategy fires nothing when no rule matches`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        val rules = listOf(rule("42", "_ => (q0, b) -> (q1, 0)"))
        val strategy = Strategy("all", StrategyKind.ALL, DEFAULT_MAX_ITERATIONS, rules)
        assertTrue(recognize("a", automaton, strategy))
        assertEquals(1, automaton.transitions.size)
    }

    @Test
    fun `the trace reports a missing transition`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        val errors =
            captureStderr {
                assertTrue(!recognize("b", automaton, null, trace = true))
            }
        assertContains(errors, "transition=NULL")
    }

    // ---------------------------------------------------------------------
    // The clauses.
    // ---------------------------------------------------------------------

    @Test
    fun `a clause whose right-hand side is not a transition changes nothing`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        val rule = rule("(q0, a) -> (\$x, \$n)", "_ => 42")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("a", automaton, strategy))
        assertEquals(1, automaton.transitions.size)
    }

    @Test
    fun `a clause whose target is not a state changes nothing`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        // The target of the transition is not a symbol, so the clause does not
        // denote a transition and nothing is inserted.
        val rule = rule("(q0, a) -> (\$x, \$n)", "_ => (q0, a) -> (0, 0)")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("a", automaton, strategy))
        assertEquals(1, automaton.transitions.size)
    }

    @Test
    fun `a clause may remove the transition through a pair of symbols`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        // The left-hand side matches the (state, symbol) component of the
        // subject, and denotes the consulted transition.
        val rule = rule("(q0, a) -> (\$x, \$n)", "(\$f, \$s) => _")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("a", automaton, strategy))
        assertEquals(0, automaton.transitions.size)
    }

    @Test
    fun `a clause whose left-hand side is a plain pair does not apply`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        // The left-hand side is matched against the subject of the step, which
        // is the whole transition, so a plain pair does not match it and the
        // clause is skipped.
        val rule = rule("(q0, a) -> (\$x, \$n)", "(q0, a) => _")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("a", automaton, strategy))
        assertEquals(1, automaton.transitions.size)
    }

    @Test
    fun `a clause whose left-hand side denotes no transition removes nothing`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        // The wildcard denotes no transition, so the clause removes nothing.
        val rule = rule("(q0, a) -> (\$x, \$n)", "_ => _")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("a", automaton, strategy))
        assertEquals(1, automaton.transitions.size)
    }

    @Test
    fun `a clause whose right-hand side is a plain pair inserts nothing`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        // The right-hand side is not an arrow, so it denotes no transition and
        // nothing is inserted.
        val rule = rule("(q0, a) -> (\$x, \$n)", "_ => (q0, b)")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("a", automaton, strategy))
        assertEquals(1, automaton.transitions.size)
    }

    @Test
    fun `a step without a strategy takes the transition`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        val result = step("q0", "a", automaton, null)
        assertEquals("q1", result.nextState)
    }

    @Test
    fun `a step whose rule removed the transition keeps the original target`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        val rule = rule("(q0, a) -> (\$x, \$n)", "(\$f, \$s) => _")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        val result = step("q0", "a", automaton, strategy)
        assertEquals("q1", result.nextState)
        assertEquals(0, automaton.transitions.size)
    }

    // ---------------------------------------------------------------------
    // The sweep.
    // ---------------------------------------------------------------------

    @Test
    fun `a sweep fires once per matching transition`() {
        val automaton = sweepAutomaton()
        val rule = rule("(q1, c) -> (\$to, \$n)", "each (q0, \$s) -> (\$t, \$v) => { _ => (q0, \$s) -> (q2, \$v) }")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("ac", automaton, strategy))
        // Both transitions that leave q0 were retargeted to q2.
        assertEquals(2, automaton.transitions.count { it.from == "q0" && it.to == "q2" })
    }

    @Test
    fun `the bindings of a sweep are per firing`() {
        val automaton = sweepAutomaton()
        val rule = rule("(q1, c) -> (\$to, \$n)", "each (q0, \$s) -> (\$t, \$v) => { _ => (q1, \$s) -> (q2, \$s) }")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("ac", automaton, strategy))
        // Each firing bound the symbol of the transition it swept.
        val inserted = automaton.transitions.filter { it.from == "q1" }
        assertEquals(3, inserted.size)
        assertTrue(inserted.any { it.symbol == "a" && it.value == Value.Symbol("a") })
        assertTrue(inserted.any { it.symbol == "b" && it.value == Value.Symbol("b") })
    }

    @Test
    fun `a sweep that matches nothing changes nothing`() {
        val automaton = sweepAutomaton()
        val rule = rule("(q1, c) -> (\$to, \$n)", "each (q9, \$s) -> (\$t, \$v) => { _ => (q9, \$s) -> (q0, \$v) }")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("ac", automaton, strategy))
        assertEquals(0, automaton.transitions.count { it.from == "q9" })
        assertEquals(2, automaton.transitions.count { it.from == "q0" })
    }

    @Test
    fun `a sweep may remove the transitions it matches`() {
        val automaton = sweepAutomaton()
        val rule = rule("(q1, c) -> (\$to, \$n)", "each (q0, \$s) -> (\$t, \$v) => { (q0, \$s) -> (\$t, \$v) => _ }")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("ac", automaton, strategy))
        assertEquals(0, automaton.transitions.count { it.from == "q0" })
    }

    @Test
    fun `a sweep sees the automaton as it was when it started`() {
        val automaton = sweepAutomaton()
        // The inserted transition matches the pattern of the sweep, so a sweep
        // that did not work on a snapshot would never end.
        val rule = rule("(q1, c) -> (\$to, \$n)", "each (q0, \$s) -> (\$t, \$v) => { _ => (q0, \$s) -> (q2, \$v) }")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("ac", automaton, strategy))
        assertEquals(3, automaton.transitions.size)
    }

    @Test
    fun `a sweep may appear in a nested block`() {
        val automaton = sweepAutomaton()
        val rule =
            rule(
                "(q1, c) -> (\$to, \$n)",
                "_ => { each (q0, \$s) -> (\$t, \$v) => { _ => (q0, \$s) -> (q2, \$v) } }",
            )
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("ac", automaton, strategy))
        assertEquals(2, automaton.transitions.count { it.from == "q0" && it.to == "q2" })
    }

    @Test
    fun `the bindings of a firing do not leak into the next`() {
        val automaton = sweepAutomaton()
        // The pattern binds $s twice, so the second occurrence is occurrence
        // checked: only the transition whose symbol is the same as the target
        // matches.
        val rule =
            rule(
                "(q1, c) -> (\$to, \$n)",
                "each (q0, \$s) -> (\$s, \$v) => { _ => (q0, \$s) -> (q2, \$v) }",
            )
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        assertTrue(recognize("ac", automaton, strategy))
        assertEquals(0, automaton.transitions.count { it.from == "q0" && it.to == "q2" })
    }

    // ---------------------------------------------------------------------
    // The inspection primitives.
    // ---------------------------------------------------------------------

    @Test
    fun `targets-from returns the distinct targets of a state`() {
        val automaton = sweepAutomaton()
        val value = evalExpr(Expr.TargetsFrom(Expr.Sym("q0")), emptyMap(), "q0", automaton)
        assertEquals(Value.Seq(listOf(Value.Symbol("q1"))), value)
    }

    @Test
    fun `targets-from of a state without transitions is empty`() {
        val automaton = sweepAutomaton()
        val value = evalExpr(Expr.TargetsFrom(Expr.Sym("q9")), emptyMap(), "q0", automaton)
        assertEquals(Value.Seq(emptyList()), value)
    }

    @Test
    fun `symbols-from returns the distinct symbols of a state`() {
        val automaton = sweepAutomaton()
        val value = evalExpr(Expr.SymbolsFrom(Expr.Sym("q0")), emptyMap(), "q0", automaton)
        assertEquals(Value.Seq(listOf(Value.Symbol("a"), Value.Symbol("b"))), value)
    }

    @Test
    fun `is-empty tells whether a sequence has no elements`() {
        val automaton = sweepAutomaton()
        val empty = evalExpr(Expr.IsEmpty(Expr.TargetsFrom(Expr.Sym("q9"))), emptyMap(), "q0", automaton)
        assertEquals(Value.BoolVal(true), empty)
        val full = evalExpr(Expr.IsEmpty(Expr.TargetsFrom(Expr.Sym("q0"))), emptyMap(), "q0", automaton)
        assertEquals(Value.BoolVal(false), full)
    }

    @Test
    fun `size counts the elements of a sequence`() {
        val automaton = sweepAutomaton()
        val value = evalExpr(Expr.Size(Expr.SymbolsFrom(Expr.Sym("q0"))), emptyMap(), "q0", automaton)
        assertEquals(Value.IntVal(2), value)
    }

    @Test
    fun `is-empty rejects a value that is not a sequence`() {
        val automaton = sweepAutomaton()
        val error =
            assertFailsWith<QuiverException> { evalExpr(Expr.IsEmpty(Expr.IntLit(1)), emptyMap(), "q0", automaton) }
        assertContains(error.message ?: "", "is-empty expects a sequence value")
    }

    @Test
    fun `size rejects a value that is not a sequence`() {
        val automaton = sweepAutomaton()
        val error =
            assertFailsWith<QuiverException> { evalExpr(Expr.Size(Expr.IntLit(1)), emptyMap(), "q0", automaton) }
        assertContains(error.message ?: "", "size expects a sequence value")
    }

    @Test
    fun `targets-from rejects a state that is not a symbol`() {
        val automaton = sweepAutomaton()
        val error =
            assertFailsWith<QuiverException> { evalExpr(Expr.TargetsFrom(Expr.IntLit(1)), emptyMap(), "q0", automaton) }
        assertContains(error.message ?: "", "targets-from expects a state symbol")
    }

    @Test
    fun `symbols-from rejects a state that is not a symbol`() {
        val automaton = sweepAutomaton()
        val error =
            assertFailsWith<QuiverException> { evalExpr(Expr.SymbolsFrom(Expr.IntLit(1)), emptyMap(), "q0", automaton) }
        assertContains(error.message ?: "", "symbols-from expects a state symbol")
    }

    @Test
    fun `an empty sequence is false in a guard`() {
        val automaton = sweepAutomaton()
        // The guard is the empty sequence itself, so it is false and the rule
        // does not fire: the transition keeps its original target.
        val rule = rule("(q1, c) -> (\$to, \$n) when targets-from(q9)", "_ => (q1, c) -> (q3, 0)")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        recognize("ac", automaton, strategy)
        assertEquals("q2", automaton.transitions.first { it.from == "q1" && it.symbol == "c" }.to)
    }

    @Test
    fun `a non-empty sequence is true in a guard`() {
        val automaton = sweepAutomaton()
        val rule = rule("(q1, c) -> (\$to, \$n) when targets-from(q0)", "_ => (q1, c) -> (q3, 0)")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        recognize("ac", automaton, strategy)
        assertEquals("q3", automaton.transitions.first { it.from == "q1" && it.symbol == "c" }.to)
    }

    @Test
    fun `is-empty is true in a guard`() {
        val automaton = sweepAutomaton()
        val rule = rule("(q1, c) -> (\$to, \$n) when is-empty(targets-from(q9))", "_ => (q1, c) -> (q3, 0)")
        val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, listOf(rule))
        recognize("ac", automaton, strategy)
        assertEquals("q3", automaton.transitions.first { it.from == "q1" && it.symbol == "c" }.to)
    }

    // ---------------------------------------------------------------------
    // The non-deterministic recognizer.
    // ---------------------------------------------------------------------

    @Test
    fun `non-deterministic recognition works without a strategy`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        assertTrue(recognizeNondet("a", automaton, null))
        assertTrue(!recognizeNondet("b", automaton, null))
    }

    @Test
    fun `a non-deterministic step works without a rule index`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        val frontier = listOf(FrontierState("q0", automaton))
        val next = stepNondet(frontier, "a", null, false, Fuel(10))
        assertEquals(1, next.size)
        assertEquals("q1", next.single().state)
    }

    @Test
    fun `a non-deterministic step follows every matching transition`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.addTransition("q0", "a", "q2", Value.IntVal(0))
        val frontier = listOf(FrontierState("q0", automaton))
        val next = stepNondet(frontier, "a", null, false, Fuel(10))
        assertEquals(listOf("q1", "q2"), next.map { it.state })
    }

    @Test
    fun `a non-deterministic step kills a path without a transition`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        val frontier = listOf(FrontierState("q0", automaton))
        val next = stepNondet(frontier, "b", null, false, Fuel(10))
        assertTrue(next.isEmpty())
    }

    @Test
    fun `the all strategy fires nothing in a non-deterministic run`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        val rules = listOf(rule("42", "_ => (q0, b) -> (q1, 0)"))
        val strategy = Strategy("all", StrategyKind.ALL, DEFAULT_MAX_ITERATIONS, rules)
        assertTrue(recognizeNondet("a", automaton, strategy))
        assertEquals(1, automaton.transitions.size)
    }

    @Test
    fun `the repeat strategy fires in a non-deterministic run`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.accepting.add("q1")
        val rules = listOf(rule("(q0, a) -> (\$x, \$n)", "_ => (q0, b) -> (q1, 0)"))
        val strategy = Strategy("repeat", StrategyKind.REPEAT, 3, rules)
        assertTrue(recognizeNondet("a", automaton, strategy))
    }

    // ---------------------------------------------------------------------
    // The includes.
    // ---------------------------------------------------------------------

    @Test
    fun `an include with an absolute path is resolved`() {
        val included = File("src/test/resources/simple.quiver").absolutePath
        val source = "include \"$included\""
        assertTrue(runQuiver(source, "ab", baseDir = "src/test/resources"))
    }

    // ---------------------------------------------------------------------
    // The programs without an FSM header.
    // ---------------------------------------------------------------------

    @Test
    fun `a program without an fsm header runs`() {
        // Without a header the automaton starts at `q0` and has no
        // transitions: every input is rejected.
        assertTrue(!runQuiver("(q0, a) -> (\$x, \$n) => { _ => (q0, a) -> (\$x, \$n) }", "a"))
    }

    @Test
    fun `a program without an fsm header builds an automaton at q0`() {
        val automaton = buildAutomaton(null)
        assertEquals("q0", automaton.start)
        assertTrue(automaton.transitions.isEmpty())
        assertTrue(automaton.accepting.isEmpty())
    }

    @Test
    fun `the accepting states are states of the automaton`() {
        val fsm = parseSource("@fsm => { start => q0; accepting => \$seq [ q1 ]; mapping => { } }")
            .filterIsInstance<Decl.Fsm>()
            .single()
        val automaton = buildAutomaton(fsm)
        assertTrue(automaton.states.contains("q1"), "The accepting state is not a state: ${automaton.states}")
        assertTrue(automaton.accepting.contains("q1"))
    }

    @Test
    fun `a program whose named strategy is missing falls back on the default one`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              strategy => missingStrategy
              mapping => {
                (q0, a) -> (q1, 0)
              }
            }
            """.trimIndent()
        assertTrue(runQuiver(source, "a"))
    }

    // ---------------------------------------------------------------------
    // The head keys.
    // ---------------------------------------------------------------------

    @Test
    fun `the head key of a pattern is its root type`() {
        assertEquals(HeadKeys.SYMBOL, patternHeadKey(Expr.Sym("q0")))
        assertEquals(HeadKeys.ANY, patternHeadKey(Expr.Sym("_")))
        assertEquals(HeadKeys.ANY, patternHeadKey(Expr.Sym("\$x")))
        assertEquals(HeadKeys.PAIR, patternHeadKey(Expr.Pair(Expr.Sym("a"), Expr.Sym("b"))))
        assertEquals(HeadKeys.SEQ, patternHeadKey(Expr.Seq(emptyList())))
        assertEquals(HeadKeys.INT, patternHeadKey(Expr.IntLit(1)))
        assertEquals(HeadKeys.INT, patternHeadKey(Expr.Plus(Expr.IntLit(1), Expr.IntLit(2))))
        assertEquals(HeadKeys.INT, patternHeadKey(Expr.Minus(Expr.IntLit(2), Expr.IntLit(1))))
        assertEquals(HeadKeys.BOOL, patternHeadKey(Expr.BoolLit(true)))
        assertEquals(HeadKeys.FUNC, patternHeadKey(Expr.Fun(listOf("x"), Expr.Sym("x"))))
        assertEquals(HeadKeys.ANY, patternHeadKey(Expr.Wildcard))
        // A binding matches the term it binds, so its head key is that of the
        // term.
        assertEquals(HeadKeys.SYMBOL, patternHeadKey(Expr.Bind("x", Expr.Sym("a"))))
        assertEquals(HeadKeys.ANY, patternHeadKey(Expr.Bind("x", Expr.Wildcard)))
        assertEquals(HeadKeys.ANY, patternHeadKey(Expr.Emit(Expr.IntLit(1))))
        assertEquals(HeadKeys.ANY, patternHeadKey(Expr.StateRef))
    }

    @Test
    fun `the head key of a subject is its root type`() {
        assertEquals(HeadKeys.SYMBOL, subjectHeadKey(Value.Symbol("q0")))
        assertEquals(HeadKeys.PAIR, subjectHeadKey(Value.Pair(Value.Symbol("a"), Value.Symbol("b"))))
        assertEquals(HeadKeys.SEQ, subjectHeadKey(Value.Seq(emptyList())))
        assertEquals(HeadKeys.INT, subjectHeadKey(Value.IntVal(1)))
        assertEquals(HeadKeys.BOOL, subjectHeadKey(Value.BoolVal(true)))
        assertEquals(HeadKeys.FUNC, subjectHeadKey(Value.Func(listOf("x"), Expr.Sym("x"), emptyMap())))
        assertEquals(HeadKeys.FUNC, subjectHeadKey(Value.Automaton(emptyAutomaton())))
    }

    // ---------------------------------------------------------------------
    // The rendering of the patterns.
    // ---------------------------------------------------------------------

    @Test
    fun `a pattern renders itself`() {
        assertEquals("[seq a,b]", exprToString(Expr.Seq(listOf(Expr.Sym("a"), Expr.Sym("b")))))
        assertEquals("[seq]", exprToString(Expr.Seq(emptyList())))
        assertEquals("<bind>", exprToString(Expr.Bind("x", Expr.Sym("a"))))
        assertContains(exprToString(Expr.StateRef), "StateRef")
    }

    // ---------------------------------------------------------------------
    // The malformed mapping entries.
    // ---------------------------------------------------------------------

    @Test
    fun `a mapping entry whose source is not a pair is reported`() {
        val error =
            assertFailsWith<QuiverException> {
                mappingEntry(Expr.Arrow(Expr.Sym("q0"), Expr.Pair(Expr.Sym("q1"), Expr.IntLit(0))))
            }
        assertContains(error.message ?: "", "Invalid FSM mapping")
    }

    @Test
    fun `a mapping entry whose target is not a pair is reported`() {
        val error =
            assertFailsWith<QuiverException> {
                mappingEntry(Expr.Arrow(Expr.Pair(Expr.Sym("q0"), Expr.Sym("a")), Expr.IntLit(0)))
            }
        assertContains(error.message ?: "", "Invalid FSM mapping")
    }

    @Test
    fun `a mapping entry whose source is not a symbol is reported`() {
        val source = Expr.Pair(Expr.IntLit(0), Expr.Sym("a"))
        val target = Expr.Pair(Expr.Sym("q1"), Expr.IntLit(0))
        val error = assertFailsWith<QuiverException> { mappingEntry(Expr.Arrow(source, target)) }
        assertContains(error.message ?: "", "Invalid FSM mapping")
    }

    @Test
    fun `a mapping entry whose symbol is not a symbol is reported`() {
        val source = Expr.Pair(Expr.Sym("q0"), Expr.IntLit(0))
        val target = Expr.Pair(Expr.Sym("q1"), Expr.IntLit(0))
        val error = assertFailsWith<QuiverException> { mappingEntry(Expr.Arrow(source, target)) }
        assertContains(error.message ?: "", "Invalid FSM mapping")
    }

    @Test
    fun `a mapping entry whose target state is not a symbol is reported`() {
        val source = Expr.Pair(Expr.Sym("q0"), Expr.Sym("a"))
        val target = Expr.Pair(Expr.IntLit(0), Expr.IntLit(0))
        val error = assertFailsWith<QuiverException> { mappingEntry(Expr.Arrow(source, target)) }
        assertContains(error.message ?: "", "Invalid FSM mapping")
    }

    // ---------------------------------------------------------------------
    // The helpers.
    // ---------------------------------------------------------------------

    @Test
    fun `a recognizer that has finished does not step`() {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        val recognizer = Recognizer(automaton, null)
        recognizer.input = "a"
        assertNotNull(recognizer.step())
        // The input is exhausted, and the recognizer has not finished yet.
        assertNull(recognizer.step())
    }

    @Test
    fun `a recognizer runs to completion over an empty input`() {
        val automaton = emptyAutomaton()
        automaton.accepting.add("q0")
        val recognizer = Recognizer(automaton, null)
        recognizer.input = ""
        assertTrue(recognizer.runToCompletion())
        assertTrue(recognizer.finished)
    }

    @Test
    fun `a recognizer runs to completion over a rejected input`() {
        val automaton = emptyAutomaton()
        val recognizer = Recognizer(automaton, null)
        recognizer.input = ""
        assertTrue(!recognizer.runToCompletion())
        assertTrue(recognizer.finished)
    }

    /** Builds an empty automaton, used when a value is evaluated in isolation. */
    private fun emptyAutomaton(): Automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())

    /**
     * Builds the automaton the sweep tests work on: two transitions leave `q0`
     * and one leaves `q1`.
     */
    private fun sweepAutomaton(): Automaton {
        val automaton = emptyAutomaton()
        automaton.addTransition("q0", "a", "q1", Value.IntVal(0))
        automaton.addTransition("q0", "b", "q1", Value.IntVal(0))
        automaton.addTransition("q1", "c", "q2", Value.IntVal(0))
        automaton.accepting.add("q2")
        return automaton
    }

    /**
     * Builds a rule from its pattern and its body.
     *
     * @param pattern the pattern of the rule.
     * @param body the clauses of the rule.
     * @return the rule.
     */
    private fun rule(pattern: String, body: String): Rule {
        val source = "$pattern => { $body }"
        return parseSource(source).filterIsInstance<Decl.RuleDecl>().single().rule
    }
}
