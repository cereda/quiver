// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The tests of the epsilon transition.
 *
 * The epsilon transition is the internal transition of the automaton: it is
 * taken without consuming an input symbol, and the run takes it before the
 * first symbol and after every symbol.
 */
class QuiverEpsilonTest {

    // ---------------------------------------------------------------------
    // The closure.
    // ---------------------------------------------------------------------

    @Test
    fun `an internal transition consumes no input`() {
        // The automaton reaches q1 by an internal transition, and reads the
        // only symbol of the input there.
        assertEquals(true, runQuiver(load("epsilon.quiver"), listOf("a")))
    }

    @Test
    fun `the closure runs before the first symbol`() {
        // Without the closure, the automaton would be at q0 when the first
        // symbol arrives, and q0 has no transition on `a`.
        assertEquals(false, runQuiver(load("epsilon.quiver"), listOf("b")))
    }

    @Test
    fun `the closure runs after every symbol`() {
        // The acceptance is decided on q3, which the final closure reaches.
        assertEquals(true, runQuiver(load("epsilon-closure.quiver"), listOf("a")))
    }

    @Test
    fun `the acceptance is decided after the final closure`() {
        // The last symbol leads to the accepting state q1, but the final
        // closure leads to q2, which is not accepting: the acceptance is a
        // property of the closed frontier, and not of the state the symbol
        // led to.
        assertEquals(false, runQuiver(load("epsilon-final.quiver"), listOf("a")))
    }

    @Test
    fun `an empty input is decided by the closure of the start state`() {
        // The automaton rests on q1, which is not accepting.
        assertEquals(false, runQuiver(load("epsilon.quiver"), emptyList()))
    }

    // ---------------------------------------------------------------------
    // The rules on the internal transitions.
    // ---------------------------------------------------------------------

    @Test
    fun `the rules fire on the internal transitions`() {
        // The program counts four internal steps, and the input is read after
        // them: the run is accepted only if the closure ended.
        assertEquals(true, runQuiver(load("epsilon-adaptive.quiver"), listOf("a")))
    }

    @Test
    fun `the rules of the internal transition rewrite the transition function`() {
        val automaton = automatonOf("epsilon-adaptive.quiver")
        val strategy = strategyOf("epsilon-adaptive.quiver")
        val fuel = Fuel(100)
        // The closure of the start state takes four internal steps.
        val state = recognizeInternal(automaton, strategy, fuel)
        assertEquals("q0", state)
        assertEquals(4, fuel.taken)
        // The internal transition is gone, and the automaton is ready to read.
        assertEquals(0, automaton.transitions.count { it.label == Label.Internal })
    }

    // ---------------------------------------------------------------------
    // The budget.
    // ---------------------------------------------------------------------

    @Test
    fun `an internal step consumes one unit of the budget`() {
        // The run takes one internal step and reads one symbol: two units.
        val ex =
            assertFailsWith<FuelExhaustedException> {
                runQuiver(load("epsilon.quiver"), listOf("a"), maxSteps = 1)
            }
        assertContains(ex.message ?: "", "after 1 steps")
    }

    @Test
    fun `the budget of the run covers the internal steps`() {
        val ex =
            assertFailsWith<EpsilonClosureException> {
                runQuiver(load("epsilon-adaptive.quiver"), listOf("a"), maxSteps = 3)
            }
        assertContains(ex.message ?: "", "after 3 internal steps")
    }

    @Test
    fun `the exhaustion inside a closure is reported as a closure failure`() {
        val ex =
            assertFailsWith<EpsilonClosureException> {
                runQuiver(load("epsilon-loop.quiver"), listOf("a"), maxSteps = 20)
            }
        val msg = ex.message ?: ""
        assertContains(msg, "Epsilon closure did not terminate")
        assertContains(msg, "after 20 internal steps")
    }

    @Test
    fun `the closure failure is a fuel exhaustion`() {
        // The handlers of the budget treat the closure failure as one.
        val ex =
            assertFailsWith<FuelExhaustedException> {
                runQuiver(load("epsilon-loop.quiver"), listOf("a"), maxSteps = 5)
            }
        assertTrue(ex is EpsilonClosureException, "Expected an EpsilonClosureException, got: ${ex::class}")
    }

    @Test
    fun `the non-deterministic closure reports the failure as well`() {
        val ex =
            assertFailsWith<EpsilonClosureException> {
                runQuiver(load("epsilon-loop.quiver"), listOf("a"), maxSteps = 20, nondet = true)
            }
        val msg = ex.message ?: ""
        assertContains(msg, "Epsilon closure did not terminate")
        assertContains(msg, "after 20 internal steps")
    }

    @Test
    fun `the closure failure counts the internal steps of the closure alone`() {
        // The run reads one symbol before the closure, so the budget covers
        // one symbol and four internal steps: the message counts the
        // internal steps alone.
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q2 ]
              mapping => {
                (q0, a) -> (q1, 0)
                (q1, epsilon) -> (q1, 0)
              }
            }

            (q1, epsilon) -> (${'$'}to, ${'$'}n) => {
              (q1, epsilon) -> (${'$'}to, ${'$'}n) => _
              _ => (q1, epsilon) -> (${'$'}to, ${'$'}n)
            }
            """.trimIndent()
        val ex = assertFailsWith<EpsilonClosureException> { runQuiver(source, listOf("a"), maxSteps = 5) }
        assertContains(ex.message ?: "", "after 4 internal steps")
    }

    // ---------------------------------------------------------------------
    // The cycles among the internal transitions.
    // ---------------------------------------------------------------------

    @Test
    fun `a cycle among the internal transitions is refused`() {
        val ex = assertFailsWith<QuiverException> { runQuiver(load("epsilon-cycle.quiver"), listOf("a")) }
        val msg = ex.message ?: ""
        assertContains(msg, "Epsilon cycle detected")
        assertContains(msg, "q0 -> q1 -> q0")
    }

    @Test
    fun `a cycle among the internal transitions is refused by the repl`() {
        val output = runRepl(":load src/test/resources/epsilon-cycle.quiver\n:quit\n")
        assertContains(output, "error loading src/test/resources/epsilon-cycle.quiver:")
        assertContains(output, "Epsilon cycle detected")
    }

    @Test
    fun `a cycle among the internal transitions keeps the previous session`() {
        // The session that was working before the failed load must survive it.
        val output =
            runRepl(
                ":load src/test/resources/epsilon.quiver\n" +
                    ":load src/test/resources/epsilon-cycle.quiver\n" +
                    ":recognize a\n" +
                    ":quit\n",
            )
        assertContains(output, "Epsilon cycle detected")
        assertContains(output, "true")
    }

    @Test
    fun `a cycle in an unreachable part of the automaton is accepted`() {
        assertEquals(true, runQuiver(load("epsilon-unreachable-cycle.quiver"), listOf("a")))
    }

    @Test
    fun `a cycle the rules may break is accepted`() {
        // The rules rewrite the internal transition, so the program decides
        // when the closure ends.
        assertEquals(true, runQuiver(load("epsilon-adaptive.quiver"), listOf("a")))
    }

    @Test
    fun `a rule with a variable in the symbol position may break a cycle`() {
        // The symbol of the transition is a variable, so it may be the
        // internal one: the program is not refused.
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q2 ]
              mapping => {
                (q0, epsilon) -> (q1, 0)
                (q1, epsilon) -> (q0, 0)
                (q0, a) -> (q2, 0)
              }
            }

            (q0, ${'$'}s) -> (${'$'}to, ${'$'}n) => {
              _ => (q0, ${'$'}s) -> (${'$'}to, ${'$'}n)
            }
            """.trimIndent()
        val ex = assertFailsWith<EpsilonClosureException> { runQuiver(source, listOf("a"), maxSteps = 20) }
        assertContains(ex.message ?: "", "Epsilon closure did not terminate")
    }

    @Test
    fun `a rule with a wildcard in the symbol position may break a cycle`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q2 ]
              mapping => {
                (q0, epsilon) -> (q1, 0)
                (q1, epsilon) -> (q0, 0)
                (q0, a) -> (q2, 0)
              }
            }

            (q0, ${'$'}s) -> (${'$'}to, ${'$'}n) => {
              _ => (q0, _) -> (${'$'}to, ${'$'}n)
            }
            """.trimIndent()
        val ex = assertFailsWith<EpsilonClosureException> { runQuiver(source, listOf("a"), maxSteps = 20) }
        assertContains(ex.message ?: "", "Epsilon closure did not terminate")
    }

    @Test
    fun `a rule with a binding in the symbol position may break a cycle`() {
        // The binding may name the internal symbol, so the program is not
        // refused: the run reports the binding itself.
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q2 ]
              mapping => {
                (q0, epsilon) -> (q1, 0)
                (q1, epsilon) -> (q0, 0)
                (q0, a) -> (q2, 0)
              }
            }

            (q0, epsilon) -> (${'$'}to, ${'$'}n) => {
              (q0, s <- epsilon) -> (${'$'}to, ${'$'}n) => _
            }
            """.trimIndent()
        val ex = assertFailsWith<QuiverException> { runQuiver(source, listOf("a"), maxSteps = 20) }
        val msg = ex.message ?: ""
        assertContains(msg, "A binding is only allowed in a pattern")
    }

    @Test
    fun `a rule with an unknown term in the symbol position may break a cycle`() {
        // The analysis does not understand the term, so it assumes the worst.
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q2 ]
              mapping => {
                (q0, epsilon) -> (q1, 0)
                (q1, epsilon) -> (q0, 0)
                (q0, a) -> (q2, 0)
              }
            }

            (q0, ${'$'}s) -> (${'$'}to, ${'$'}n) => {
              _ => (q0, 1) -> (${'$'}to, ${'$'}n)
            }
            """.trimIndent()
        val ex = assertFailsWith<EpsilonClosureException> { runQuiver(source, listOf("a"), maxSteps = 20) }
        assertContains(ex.message ?: "", "Epsilon closure did not terminate")
    }

    @Test
    fun `a rule whose key is not a pair does not touch an internal transition`() {
        // The key of a transition is a pair, so a rule whose key is not one
        // cannot act on an internal transition: the cycle is still refused.
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q2 ]
              mapping => {
                (q0, epsilon) -> (q1, 0)
                (q1, epsilon) -> (q0, 0)
                (q0, a) -> (q2, 0)
              }
            }

            q0 -> (q0, a) => {
              _ => q0 -> (q0, a)
            }
            """.trimIndent()
        val ex = assertFailsWith<QuiverException> { runQuiver(source, listOf("a")) }
        assertContains(ex.message ?: "", "Epsilon cycle detected")
    }

    // ---------------------------------------------------------------------
    // The non-deterministic closure.
    // ---------------------------------------------------------------------

    @Test
    fun `every internal transition is a path of its own`() {
        // The automaton has two internal transitions out of q0: one of them
        // reads the input, and the other dies.
        assertEquals(true, runQuiver(load("epsilon-nondet.quiver"), listOf("a"), nondet = true))
    }

    @Test
    fun `the non-deterministic closure accepts the input it reaches`() {
        assertEquals(false, runQuiver(load("epsilon-nondet.quiver"), listOf("b"), nondet = true))
    }

    // ---------------------------------------------------------------------
    // The helpers.
    // ---------------------------------------------------------------------

    /**
     * Takes the internal transitions of the start state, as the closure of a
     * run does.
     *
     * @param automaton the automaton to close.
     * @param strategy the active rewriting strategy.
     * @param fuel the budget of the closure.
     * @return the state the closure ends in.
     */
    private fun recognizeInternal(automaton: Automaton, strategy: Strategy, fuel: Fuel): String {
        var state = automaton.start
        while (true) {
            val hasInternal = automaton.transitions.any { it.from == state && it.label == Label.Internal }
            if (!hasInternal) return state
            val result = step(state, Label.Internal, automaton, strategy, false, fuel)
            state = result.nextState ?: return state
        }
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

    /** Builds the rewriting strategy of a source file. */
    private fun strategyOf(name: String): Strategy {
        val rules = parseSource(load(name)).filterIsInstance<Decl.RuleDecl>().map { it.rule }
        return Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, rules)
    }

    /** Loads a resource of the test suite. */
    private fun load(name: String): String = loadResource(name)
}
