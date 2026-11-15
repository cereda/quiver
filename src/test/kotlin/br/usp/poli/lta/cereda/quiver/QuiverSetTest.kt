// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tests of the set algebra.
 *
 * The sets are values that the programs manipulate, and they are the way a
 * clause denotes many transitions at once: an insertion whose symbol is a
 * set inserts one transition per element of the set, and a removal whose
 * symbol is a set removes every transition under those symbols.
 */
class QuiverSetTest {

    // ---------------------------------------------------------------------
    // The set primitives.
    // ---------------------------------------------------------------------

    @Test
    fun `the set constructor builds a set from a sequence`() {
        val value = evalSetPrimitive(Expr.Set(Expr.Seq(listOf(Expr.Sym("a"), Expr.Sym("b")))))
        assertEquals(Value.Set(setOf(Value.Symbol("a"), Value.Symbol("b"))), value)
    }

    @Test
    fun `the set constructor drops the repetitions`() {
        val value = evalSetPrimitive(Expr.Set(Expr.Seq(listOf(Expr.Sym("a"), Expr.Sym("a"), Expr.Sym("b")))))
        assertEquals(Value.Set(setOf(Value.Symbol("a"), Value.Symbol("b"))), value)
    }

    @Test
    fun `the set constructor keeps the order of the elements`() {
        val value = evalSetPrimitive(Expr.Set(Expr.Seq(listOf(Expr.Sym("b"), Expr.Sym("a")))))
        val items = (value as Value.Set).items.toList()
        assertEquals(listOf(Value.Symbol("b"), Value.Symbol("a")), items)
    }

    @Test
    fun `the union of two sets holds every element`() {
        val value =
            evalSetPrimitive(
                Expr.Union(
                    Expr.Set(Expr.Seq(listOf(Expr.Sym("a")))),
                    Expr.Set(Expr.Seq(listOf(Expr.Sym("b"), Expr.Sym("a")))),
                ),
            )
        assertEquals(Value.Set(setOf(Value.Symbol("a"), Value.Symbol("b"))), value)
    }

    @Test
    fun `the intersection of two sets holds the common elements`() {
        val value =
            evalSetPrimitive(
                Expr.Intersection(
                    Expr.Set(Expr.Seq(listOf(Expr.Sym("a"), Expr.Sym("b")))),
                    Expr.Set(Expr.Seq(listOf(Expr.Sym("b"), Expr.Sym("c")))),
                ),
            )
        assertEquals(Value.Set(setOf(Value.Symbol("b"))), value)
    }

    @Test
    fun `the difference of two sets holds the elements of the first one`() {
        val value =
            evalSetPrimitive(
                Expr.Difference(
                    Expr.Set(Expr.Seq(listOf(Expr.Sym("a"), Expr.Sym("b")))),
                    Expr.Set(Expr.Seq(listOf(Expr.Sym("b")))),
                ),
            )
        assertEquals(Value.Set(setOf(Value.Symbol("a"))), value)
    }

    @Test
    fun `the membership test tells whether an element belongs to a set`() {
        val inside =
            evalSetPrimitive(
                Expr.Contains(
                    Expr.Set(Expr.Seq(listOf(Expr.Sym("a")))),
                    Expr.Sym("a"),
                ),
            )
        val outside =
            evalSetPrimitive(
                Expr.Contains(
                    Expr.Set(Expr.Seq(listOf(Expr.Sym("a")))),
                    Expr.Sym("b"),
                ),
            )
        assertEquals(Value.BoolVal(true), inside)
        assertEquals(Value.BoolVal(false), outside)
    }

    @Test
    fun `the size of a set counts its elements`() {
        val value = evalSetPrimitive(Expr.Size(Expr.Set(Expr.Seq(listOf(Expr.Sym("a"), Expr.Sym("b"))))))
        assertEquals(Value.IntVal(2), value)
    }

    @Test
    fun `the emptiness of a set tells whether it has no elements`() {
        val empty = evalSetPrimitive(Expr.IsEmpty(Expr.Set(Expr.Seq(emptyList()))))
        val full = evalSetPrimitive(Expr.IsEmpty(Expr.Set(Expr.Seq(listOf(Expr.Sym("a"))))))
        assertEquals(Value.BoolVal(true), empty)
        assertEquals(Value.BoolVal(false), full)
    }

    @Test
    fun `the set constructor rejects a value that is not a sequence nor a set`() {
        val error = assertFailsWith<QuiverException> { evalSetPrimitive(Expr.Set(Expr.IntLit(1))) }
        assertContains(error.message ?: "", "set expects a sequence or a set value")
    }

    @Test
    fun `the set constructor leaves a set as it is`() {
        val value = evalSetPrimitive(Expr.Set(Expr.Set(Expr.Seq(listOf(Expr.Sym("a"))))))
        assertEquals(Value.Set(setOf(Value.Symbol("a"))), value)
    }

    @Test
    fun `the union rejects a value that is not a set`() {
        val error =
            assertFailsWith<QuiverException> {
                evalSetPrimitive(Expr.Union(Expr.Set(Expr.Seq(listOf(Expr.Sym("a")))), Expr.IntLit(1)))
            }
        assertContains(error.message ?: "", "union expects a set value")
    }

    @Test
    fun `the membership test rejects a set that is not a set`() {
        val error =
            assertFailsWith<QuiverException> {
                evalSetPrimitive(Expr.Contains(Expr.IntLit(1), Expr.Sym("a")))
            }
        assertContains(error.message ?: "", "contains expects a set value")
    }

    // ---------------------------------------------------------------------
    // The set variables.
    // ---------------------------------------------------------------------

    @Test
    fun `a set variable binds a set value`() {
        val set = Value.Set(setOf(Value.Symbol("a"), Value.Symbol("b")))
        val matched = matchTerm(Expr.Sym("%s"), set, emptyMap())
        assertNotNull(matched)
        assertEquals(set, matched["%s"])
    }

    @Test
    fun `a set variable does not match a value that is not a set`() {
        assertNull(matchTerm(Expr.Sym("%s"), Value.Symbol("a"), emptyMap()))
        assertNull(matchTerm(Expr.Sym("%s"), Value.IntVal(1), emptyMap()))
        assertNull(matchTerm(Expr.Sym("%s"), Value.Seq(listOf(Value.Symbol("a"))), emptyMap()))
    }

    @Test
    fun `a bound set variable matches the elements of its set`() {
        val env = mapOf("%s" to Value.Set(setOf(Value.Symbol("a"), Value.Symbol("b"))))
        assertNotNull(matchTerm(Expr.Sym("%s"), Value.Symbol("a"), env))
        assertNotNull(matchTerm(Expr.Sym("%s"), Value.Symbol("b"), env))
        assertNull(matchTerm(Expr.Sym("%s"), Value.Symbol("c"), env))
    }

    @Test
    fun `the head type of a set variable is the set type`() {
        assertEquals(Value.Type.SET, patternTypeOf(Expr.Sym("%s")))
        assertEquals(HeadKeys.SET, patternHeadKey(Expr.Sym("%s")))
    }

    @Test
    fun `a set value has the set head key`() {
        assertEquals(HeadKeys.SET, subjectHeadKey(Value.Set(setOf(Value.Symbol("a")))))
    }

    @Test
    fun `a set value has the set type`() {
        assertEquals(Value.Type.SET, Value.Set(setOf(Value.Symbol("a"))).type)
    }

    @Test
    fun `a set value returns its elements`() {
        val set = Value.Set(setOf(Value.Symbol("a"), Value.Symbol("b")))
        assertEquals(setOf(Value.Symbol("a"), Value.Symbol("b")), set.asSet())
    }

    @Test
    fun `a value that is not a set cannot be read as one`() {
        val error = assertFailsWith<QuiverException> { Value.Symbol("a").asSet() }
        assertContains(error.message ?: "", "Expected a set value, got symbol")
    }

    @Test
    fun `the set primitives never match a subject`() {
        assertNull(matchTerm(Expr.Set(Expr.Seq(emptyList())), Value.Seq(emptyList()), emptyMap()))
        assertNull(matchTerm(Expr.Union(Expr.Sym("a"), Expr.Sym("b")), Value.Symbol("a"), emptyMap()))
        assertNull(matchTerm(Expr.Intersection(Expr.Sym("a"), Expr.Sym("b")), Value.Symbol("a"), emptyMap()))
        assertNull(matchTerm(Expr.Difference(Expr.Sym("a"), Expr.Sym("b")), Value.Symbol("a"), emptyMap()))
        assertNull(matchTerm(Expr.Contains(Expr.Sym("a"), Expr.Sym("b")), Value.Symbol("a"), emptyMap()))
    }

    @Test
    fun `the set primitives have no head type of their own`() {
        val primitives =
            listOf(
                Expr.Set(Expr.Seq(emptyList())),
                Expr.Union(Expr.Sym("a"), Expr.Sym("b")),
                Expr.Intersection(Expr.Sym("a"), Expr.Sym("b")),
                Expr.Difference(Expr.Sym("a"), Expr.Sym("b")),
                Expr.Contains(Expr.Sym("a"), Expr.Sym("b")),
            )
        for (primitive in primitives) {
            assertEquals(null, patternTypeOf(primitive))
            assertEquals(HeadKeys.ANY, patternHeadKey(primitive))
        }
    }

    @Test
    fun `the set primitives are rendered`() {
        assertEquals("set([seq a,b])", exprToString(Expr.Set(Expr.Seq(listOf(Expr.Sym("a"), Expr.Sym("b"))))))
        assertEquals("union(a,b)", exprToString(Expr.Union(Expr.Sym("a"), Expr.Sym("b"))))
        assertEquals("intersection(a,b)", exprToString(Expr.Intersection(Expr.Sym("a"), Expr.Sym("b"))))
        assertEquals("difference(a,b)", exprToString(Expr.Difference(Expr.Sym("a"), Expr.Sym("b"))))
        assertEquals("contains(a,b)", exprToString(Expr.Contains(Expr.Sym("a"), Expr.Sym("b"))))
    }

    @Test
    fun `a set is rendered with its elements`() {
        assertEquals("{a,b}", valueToString(Value.Set(setOf(Value.Symbol("a"), Value.Symbol("b")))))
        assertEquals("{}", valueToString(Value.Set(emptySet())))
    }

    // ---------------------------------------------------------------------
    // The bulk actions.
    // ---------------------------------------------------------------------

    @Test
    fun `an insertion with a set in the symbol position inserts one transition per element`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, set([b, c]))
              }
            }

            (q0, a) -> (${'$'}to, %syms) => {
              _ => (q0, %syms) -> (q1, 0)
            }
            """.trimIndent()
        val automaton = automatonOf(source)
        val strategy = strategyOf(source)
        step("q0", Label.Symbol("a"), automaton, strategy)
        val symbols = automaton.transitions.filter { it.from == "q0" }.map { it.symbol }.toSet()
        assertEquals(setOf("a", "b", "c"), symbols)
    }

    @Test
    fun `an insertion with a set in the target position inserts one transition per element`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, set([q1, q2]))
              }
            }

            (q0, a) -> (${'$'}to, %targets) => {
              _ => (q0, b) -> (%targets, 0)
            }
            """.trimIndent()
        val automaton = automatonOf(source)
        val strategy = strategyOf(source)
        step("q0", Label.Symbol("a"), automaton, strategy)
        val targets = automaton.transitions.filter { it.symbol == "b" }.map { it.to }.toSet()
        assertEquals(setOf("q1", "q2"), targets)
    }

    @Test
    fun `an insertion with sets on both sides inserts their cartesian product`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, (set([b, c]), set([q1, q2])))
              }
            }

            (q0, a) -> (${'$'}to, (${'$'}syms, ${'$'}targets)) => {
              _ => (q0, ${'$'}syms) -> (${'$'}targets, 0)
            }
            """.trimIndent()
        val automaton = automatonOf(source)
        val strategy = strategyOf(source)
        step("q0", Label.Symbol("a"), automaton, strategy)
        val pairs = automaton.transitions.filter { it.symbol != "a" }.map { it.symbol to it.to }.toSet()
        assertEquals(setOf("b" to "q1", "b" to "q2", "c" to "q1", "c" to "q2"), pairs)
    }

    @Test
    fun `a removal with a set in the symbol position removes every transition under it`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, set([b, c]))
                (q0, b) -> (q1, set([b, c]))
                (q0, c) -> (q1, set([b, c]))
              }
            }

            (q0, b) -> (${'$'}to, %syms) => {
              (q0, %syms) -> (${'$'}to, ${'$'}n) => _
            }
            """.trimIndent()
        val automaton = automatonOf(source)
        val strategy = strategyOf(source)
        step("q0", Label.Symbol("b"), automaton, strategy)
        val symbols = automaton.transitions.filter { it.from == "q0" }.map { it.symbol }.toSet()
        assertEquals(setOf("a"), symbols)
    }

    @Test
    fun `an insertion that denotes no transition inserts nothing`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, 0)
              }
            }

            (q0, a) -> (${'$'}to, ${'$'}n) => {
              _ => (q0, 42) -> (q1, 0)
            }
            """.trimIndent()
        val automaton = automatonOf(source)
        val strategy = strategyOf(source)
        step("q0", Label.Symbol("a"), automaton, strategy)
        assertEquals(1, automaton.transitions.size)
    }

    @Test
    fun `an insertion whose set holds a value that is not a symbol inserts nothing`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, set([b, 1]))
              }
            }

            (q0, a) -> (${'$'}to, %syms) => {
              _ => (q0, %syms) -> (q1, 0)
            }
            """.trimIndent()
        val automaton = automatonOf(source)
        val strategy = strategyOf(source)
        step("q0", Label.Symbol("a"), automaton, strategy)
        assertEquals(1, automaton.transitions.size)
    }

    @Test
    fun `a removal whose key denotes no transition removes nothing`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, 0)
              }
            }

            (q0, a) -> (${'$'}to, ${'$'}n) => {
              (q0, 42) -> (${'$'}to, ${'$'}n) => _
            }
            """.trimIndent()
        val automaton = automatonOf(source)
        val strategy = strategyOf(source)
        step("q0", Label.Symbol("a"), automaton, strategy)
        assertEquals(1, automaton.transitions.size)
    }

    // ---------------------------------------------------------------------
    // The sweep that collects.
    // ---------------------------------------------------------------------

    @Test
    fun `the set variable of a sweep collects the values it matches`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, 0)
                (q0, b) -> (q0, 0)
                (q0, c) -> (q1, 0)
              }
            }

            (q0, a) -> (${'$'}to, ${'$'}n) => {
              each (q0, %s) -> (${'$'}t, ${'$'}v) => { }
              _ => (q0, a) -> (${'$'}to, %s)
            }
            """.trimIndent()
        val automaton = automatonOf(source)
        val strategy = strategyOf(source)
        step("q0", Label.Symbol("a"), automaton, strategy)
        val value = automaton.transitions.first { it.symbol == "a" }.value
        assertEquals(Value.Set(setOf(Value.Symbol("a"), Value.Symbol("b"), Value.Symbol("c"))), value)
    }

    @Test
    fun `the set variable of a sweep collects only the matching transitions`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, 0)
                (q0, b) -> (q1, 0)
              }
            }

            (q0, a) -> (${'$'}to, ${'$'}n) => {
              each (q0, %s) -> (${'$'}t, ${'$'}v) => { }
              _ => (q0, a) -> (${'$'}to, %s)
            }
            """.trimIndent()
        val automaton = automatonOf(source)
        val strategy = strategyOf(source)
        step("q0", Label.Symbol("a"), automaton, strategy)
        val value = automaton.transitions.first { it.symbol == "a" }.value
        assertEquals(Value.Set(setOf(Value.Symbol("a"), Value.Symbol("b"))), value)
    }

    @Test
    fun `the set variable of a sweep is bound to the collection during the firings`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, 0)
                (q0, b) -> (q0, 0)
              }
            }

            (q0, a) -> (${'$'}to, ${'$'}n) => {
              each (q0, %s) -> (${'$'}t, ${'$'}v) => {
                _ => emit(%s)
              }
            }
            """.trimIndent()
        val output = captureStdout { runQuiver(source, listOf("a")) }
        assertContains(output, "{a}")
        assertContains(output, "{a,b}")
    }

    @Test
    fun `a bound set variable of a sweep matches the elements of its set`() {
        // The rule binds the set of the transition's value, and the sweep
        // matches only the transitions whose symbol belongs to that set.
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, set([b]))
                (q0, b) -> (q0, 0)
                (q0, c) -> (q0, 0)
              }
            }

            (q0, a) -> (${'$'}to, %syms) => {
              each (q0, %syms) -> (${'$'}t, ${'$'}v) => {
                _ => emit(%syms)
              }
            }
            """.trimIndent()
        val output = captureStdout { runQuiver(source, listOf("a")) }
        assertContains(output, "{b}")
        assertTrue(!output.contains("{c}"), "Expected no collection of c, got: $output")
    }

    @Test
    fun `a set variable bound by the pattern of a sweep is collected`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, 0)
              }
            }

            (q0, a) -> (${'$'}to, ${'$'}n) => {
              each %s <- ${'$'}t => { }
              _ => (q0, a) -> (${'$'}to, size(%s))
            }
            """.trimIndent()
        val automaton = automatonOf(source)
        val strategy = strategyOf(source)
        step("q0", Label.Symbol("a"), automaton, strategy)
        val value = automaton.transitions.first { it.symbol == "a" }.value
        assertEquals(Value.IntVal(1), value)
    }

    // ---------------------------------------------------------------------
    // The example program.
    // ---------------------------------------------------------------------

    @Test
    fun `the set example accepts a symbol of the alphabet`() {
        assertEquals(true, runQuiver(load("set.quiver"), listOf("a", "b")))
    }

    @Test
    fun `the set example accepts every symbol of the alphabet`() {
        assertEquals(true, runQuiver(load("set.quiver"), listOf("a", "c")))
    }

    @Test
    fun `the set example rejects a symbol outside the alphabet`() {
        assertEquals(false, runQuiver(load("set.quiver"), listOf("a", "d")))
    }

    @Test
    fun `the set example rejects an input that ends before the alphabet`() {
        assertEquals(false, runQuiver(load("set.quiver"), listOf("a")))
    }

    @Test
    fun `the set example prints the alphabet and the symbols it reads`() {
        val output = captureStdout { runQuiver(load("set.quiver"), listOf("a", "b")) }
        assertContains(output, "{b,c}")
        assertContains(output, "{a,b,c}")
    }

    // ---------------------------------------------------------------------
    // The sets as guards.
    // ---------------------------------------------------------------------

    @Test
    fun `a set used as a guard is false when it is empty`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, set(${'$'}seq [ ]))
              }
            }

            (q0, a) -> (${'$'}to, %syms) when %syms => {
              _ => (q0, a) -> (q1, 0)
            }
            """.trimIndent()
        val automaton = automatonOf(source)
        val strategy = strategyOf(source)
        step("q0", Label.Symbol("a"), automaton, strategy)
        assertEquals("q0", automaton.transitions.first { it.symbol == "a" }.to)
    }

    @Test
    fun `a set used as a guard is true when it is not empty`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [ q1 ]
              mapping => {
                (q0, a) -> (q0, set([b]))
              }
            }

            (q0, a) -> (${'$'}to, %syms) when %syms => {
              _ => (q0, a) -> (q1, 0)
            }
            """.trimIndent()
        val automaton = automatonOf(source)
        val strategy = strategyOf(source)
        step("q0", Label.Symbol("a"), automaton, strategy)
        assertEquals("q1", automaton.transitions.first { it.symbol == "a" }.to)
    }

    // ---------------------------------------------------------------------
    // The helpers.
    // ---------------------------------------------------------------------

    /**
     * Evaluates a set primitive, with no bindings in scope.
     *
     * @param expr the primitive to evaluate.
     * @return the value of the primitive.
     */
    private fun evalSetPrimitive(expr: Expr): Value {
        val automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())
        return evalExpr(expr, emptyMap(), "q0", automaton)
    }

    /**
     * The head type of a pattern.
     *
     * @param pattern the pattern to analyse.
     * @return the type the pattern expects.
     */
    private fun patternTypeOf(pattern: Expr): Value.Type? = patternType(pattern)

    /** Builds the automaton declared by the FSM header of a source. */
    private fun automatonOf(source: String): Automaton {
        val fsm = parseSource(source).filterIsInstance<Decl.Fsm>().first()
        val automaton = Automaton(fsm.start, mutableSetOf(fsm.start), mutableSetOf(), mutableListOf())
        fsm.accepting.forEach { automaton.accepting.add(it) }
        fsm.mapping.forEach { arrow ->
            val entry = mappingEntry(arrow)
            val value = evalExpr(entry.value, emptyMap(), fsm.start, automaton)
            automaton.addTransition(entry.from, entry.label, entry.to, value)
        }
        return automaton
    }

    /** Builds the rewriting strategy of a source. */
    private fun strategyOf(source: String): Strategy {
        val rules = parseSource(source).filterIsInstance<Decl.RuleDecl>().map { it.rule }
        return Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, rules)
    }

    /** Loads a resource of the test suite. */
    private fun load(name: String): String = loadResource(name)
}
