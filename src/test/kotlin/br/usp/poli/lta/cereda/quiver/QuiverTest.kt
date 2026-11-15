// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuiverTest {

    @Test
    fun `simple recognizer accepts ab`() {
        assertEquals(true, runQuiver(load("simple.quiver"), listOf("a", "b")))
    }

    @Test
    fun `simple recognizer rejects a`() {
        assertEquals(false, runQuiver(load("simple.quiver"), listOf("a")))
    }

    @Test
    fun `simple recognizer rejects abc`() {
        assertEquals(false, runQuiver(load("simple.quiver"), listOf("a", "b", "c")))
    }

    @Test
    fun `simple recognizer rejects b`() {
        assertEquals(false, runQuiver(load("simple.quiver"), listOf("b")))
    }

    @Test
    fun `rec recognizer accepts aabbcc`() {
        assertEquals(true, runQuiver(load("rec.quiver"), listOf("a", "a", "b", "b", "c", "c")))
    }

    @Test
    fun `rec recognizer accepts abc`() {
        assertEquals(true, runQuiver(load("rec.quiver"), listOf("a", "b", "c")))
    }

    @Test
    fun `rec recognizer accepts aaabbbccc`() {
        assertEquals(true, runQuiver(load("rec.quiver"), listOf("a", "a", "a", "b", "b", "b", "c", "c", "c")))
    }

    @Test
    fun `rec recognizer accepts aabbc`() {
        assertEquals(true, runQuiver(load("rec.quiver"), listOf("a", "a", "b", "b", "c")))
    }

    @Test
    fun `rec recognizer rejects a`() {
        assertEquals(false, runQuiver(load("rec.quiver"), listOf("a")))
    }

    @Test
    fun `rec recognizer rejects empty`() {
        assertEquals(false, runQuiver(load("rec.quiver"), emptyList()))
    }

    @Test
    fun `rec recognizer rejects aabb`() {
        assertEquals(false, runQuiver(load("rec.quiver"), listOf("a", "a", "b", "b")))
    }

    @Test
    fun `rec recognizer rejects ac`() {
        assertEquals(false, runQuiver(load("rec.quiver"), listOf("a", "c")))
    }

    @Test
    fun `rec recognizer rejects aabbccd`() {
        assertEquals(false, runQuiver(load("rec.quiver"), listOf("a", "a", "b", "b", "c", "c", "d")))
    }

    @Test
    fun `value type is symbol for Sym`() {
        assertEquals(Value.Type.SYMBOL, Value.Symbol("q0").type)
    }

    @Test
    fun `value type is int for IntVal`() {
        assertEquals(Value.Type.INT, Value.IntVal(5).type)
    }

    @Test
    fun `value type is pair for Pair`() {
        assertEquals(Value.Type.PAIR, Value.Pair(Value.Symbol("a"), Value.IntVal(1)).type)
    }

    @Test
    fun `value type is seq for Seq`() {
        assertEquals(Value.Type.SEQ, Value.Seq(emptyList()).type)
    }

    @Test
    fun `value type is bool for BoolVal`() {
        assertEquals(Value.Type.BOOL, Value.BoolVal(true).type)
    }

    @Test
    fun `type-compatible rule applies to matching subject`() {
        // Pattern (q0, a) is PAIR; subject is a PAIR transition → applies.
        assertEquals(true, runQuiver(load("simple.quiver"), listOf("a", "b")))
    }

    @Test
    fun `closure recognizer accepts abc`() {
        assertEquals(true, runQuiver(load("closure.quiver"), listOf("a", "b", "c")))
    }

    @Test
    fun `closure recognizer rejects ab`() {
        assertEquals(false, runQuiver(load("closure.quiver"), listOf("a", "b")))
    }

    @Test
    fun `closure recognizer rejects a`() {
        assertEquals(false, runQuiver(load("closure.quiver"), listOf("a")))
    }

    @Test
    fun `closure recognizer rejects abcd`() {
        assertEquals(false, runQuiver(load("closure.quiver"), listOf("a", "b", "c", "d")))
    }

    @Test
    fun `strategy recognizer accepts a`() {
        assertEquals(true, runQuiver(load("strategy.quiver"), listOf("a")))
    }

    @Test
    fun `strategy recognizer rejects empty`() {
        assertEquals(false, runQuiver(load("strategy.quiver"), emptyList()))
    }

    @Test
    fun `strategy recognizer rejects b`() {
        assertEquals(false, runQuiver(load("strategy.quiver"), listOf("b")))
    }

    @Test
    fun `trace does not change recognition result`() {
        assertEquals(
            runQuiver(load("simple.quiver"), listOf("a", "b")),
            runQuiver(load("simple.quiver"), listOf("a", "b"), trace = true),
        )
        assertEquals(
            runQuiver(load("rec.quiver"), listOf("a", "a", "b", "b", "c", "c")),
            runQuiver(load("rec.quiver"), listOf("a", "a", "b", "b", "c", "c"), trace = true),
        )
    }

    @Test
    fun `trace accepts ab`() {
        assertEquals(true, runQuiver(load("simple.quiver"), listOf("a", "b"), trace = true))
    }

    @Test
    fun `trace rejects a`() {
        assertEquals(false, runQuiver(load("simple.quiver"), listOf("a"), trace = true))
    }

    @Test
    fun `exprToString renders symbol`() {
        assertEquals("q0", exprToString(Expr.Sym("q0")))
    }

    @Test
    fun `exprToString renders pair`() {
        assertEquals("(q0,a)", exprToString(Expr.Pair(Expr.Sym("q0"), Expr.Sym("a"))))
    }

    @Test
    fun `exprToString renders arrow`() {
        assertEquals(
            "(q0,a) -> (q1,0)",
            exprToString(
                Expr.Arrow(
                    Expr.Pair(Expr.Sym("q0"), Expr.Sym("a")),
                    Expr.Pair(Expr.Sym("q1"), Expr.IntLit(0)),
                ),
            ),
        )
    }

    @Test
    fun `exprToString renders wildcard`() {
        assertEquals("_", exprToString(Expr.Wildcard))
    }

    @Test
    fun `exprToString renders function`() {
        assertEquals(
            "fn \$x,\$y => \$x + \$y",
            exprToString(
                Expr.Fun(
                    listOf("\$x", "\$y"),
                    Expr.Plus(Expr.Sym("\$x"), Expr.Sym("\$y")),
                ),
            ),
        )
    }

    @Test
    fun `exprToString renders application`() {
        assertEquals("f(21)", exprToString(Expr.App(Expr.Sym("f"), Expr.IntLit(21))))
    }

    @Test
    fun `abn recognizer accepts ab`() {
        assertEquals(true, runQuiver(load("abn.quiver"), listOf("a", "b")))
    }

    @Test
    fun `abn recognizer accepts aabb`() {
        assertEquals(true, runQuiver(load("abn.quiver"), listOf("a", "a", "b", "b")))
    }

    @Test
    fun `abn recognizer accepts aaabbb`() {
        assertEquals(true, runQuiver(load("abn.quiver"), listOf("a", "a", "a", "b", "b", "b")))
    }

    @Test
    fun `abn recognizer rejects empty`() {
        assertEquals(false, runQuiver(load("abn.quiver"), emptyList()))
    }

    @Test
    fun `abn recognizer rejects a`() {
        assertEquals(false, runQuiver(load("abn.quiver"), listOf("a")))
    }

    @Test
    fun `abn recognizer rejects b`() {
        assertEquals(false, runQuiver(load("abn.quiver"), listOf("b")))
    }

    @Test
    fun `abn recognizer rejects aab`() {
        assertEquals(false, runQuiver(load("abn.quiver"), listOf("a", "a", "b")))
    }

    @Test
    fun `abn recognizer rejects abb`() {
        assertEquals(false, runQuiver(load("abn.quiver"), listOf("a", "b", "b")))
    }

    @Test
    fun `abn recognizer rejects aaabbbb`() {
        assertEquals(false, runQuiver(load("abn.quiver"), listOf("a", "a", "a", "b", "b", "b", "b")))
    }

    @Test
    fun `abn recognizer rejects aabbcc`() {
        assertEquals(false, runQuiver(load("abn.quiver"), listOf("a", "a", "b", "b", "c", "c")))
    }

    @Test
    fun `exprToString renders minus`() {
        assertEquals(
            "\$n - 1",
            exprToString(Expr.Minus(Expr.Sym("\$n"), Expr.IntLit(1))),
        )
    }

    @Test
    fun `exprToString renders bool lit`() {
        assertEquals("true", exprToString(Expr.BoolLit(true)))
        assertEquals("false", exprToString(Expr.BoolLit(false)))
    }

    @Test
    fun `guard recognizer accepts ab`() {
        assertEquals(true, runQuiver(load("guard.quiver"), listOf("a", "b")))
    }

    @Test
    fun `guard recognizer accepts aab`() {
        assertEquals(true, runQuiver(load("guard.quiver"), listOf("a", "a", "b")))
    }

    @Test
    fun `guard recognizer rejects b`() {
        assertEquals(false, runQuiver(load("guard.quiver"), listOf("b")))
    }

    @Test
    fun `guard recognizer rejects empty`() {
        assertEquals(false, runQuiver(load("guard.quiver"), emptyList()))
    }

    @Test
    fun `guard recognizer accepts aaab`() {
        assertEquals(true, runQuiver(load("guard.quiver"), listOf("a", "a", "a", "b")))
    }

    @Test
    fun `guard literal recognizer accepts a`() {
        assertEquals(true, runQuiver(load("guard-literal.quiver"), listOf("a")))
    }

    @Test
    fun `guard literal recognizer rejects empty`() {
        assertEquals(false, runQuiver(load("guard-literal.quiver"), emptyList()))
    }

    @Test
    fun `emit recognizer accepts ab`() {
        assertEquals(true, runQuiver(load("emit.quiver"), listOf("a", "b")))
    }

    @Test
    fun `emit recognizer accepts aab`() {
        assertEquals(true, runQuiver(load("emit.quiver"), listOf("a", "a", "b")))
    }

    @Test
    fun `emit exprToString renders emit`() {
        assertEquals(
            "emit(\$n + 1)",
            exprToString(Expr.Emit(Expr.Plus(Expr.Sym("\$n"), Expr.IntLit(1)))),
        )
    }

    @Test
    fun `emit prints counter to stdout`() {
        val output = captureStdout { runQuiver(load("emit.quiver"), listOf("a", "a", "b")) }
        assertTrue(output.contains("1"))
        assertTrue(output.contains("2"))
    }

    @Test
    fun `recognizer steps through ab`() {
        val source = load("simple.quiver")
        val decls = parseSource(source)
        val fsm = decls.filterIsInstance<Decl.Fsm>().firstOrNull()!!
        val strategies = decls.filterIsInstance<Decl.StrategyDecl>().associateBy {
            it.strategy.label
        }.mapValues { it.value.strategy }
        val strategy = fsm.strategyLabel?.let { strategies[it] }
            ?: Strategy("default", StrategyKind.FIRST, 100, emptyList())

        val automaton = Automaton(fsm.start, mutableSetOf(fsm.start), mutableSetOf(), mutableListOf())
        fsm.accepting.forEach { automaton.accepting.add(it) }
        fsm.mapping.forEach { arrow ->
            val fromPair = arrow.left as Expr.Pair
            val toPair = arrow.right as Expr.Pair
            val from = fromPair.left as Expr.Sym
            val sym = fromPair.right as Expr.Sym
            val to = toPair.left as Expr.Sym
            val value = evalExpr(toPair.right, emptyMap(), fsm.start, automaton)
            automaton.addTransition(from.name, sym.name, to.name, value)
        }

        val rec = Recognizer(automaton, strategy)
        rec.input = listOf("a", "b")

        // Step 1: 'a'
        val r1 = rec.step()
        assertNotNull(r1)
        assertEquals("q1", r1.nextState)
        assertEquals(1, rec.position)
        assertEquals("q1", rec.state)

        // Step 2: 'b'
        val r2 = rec.step()
        assertNotNull(r2)
        assertEquals("q2", r2.nextState)
        assertEquals(2, rec.position)
        assertEquals("q2", rec.state)

        // No more input
        val r3 = rec.step()
        assertNull(r3)

        // Run to completion
        val result = rec.runToCompletion()
        assertTrue(result)
    }

    @Test
    fun `recognizer rejects on missing transition`() {
        val source = load("simple.quiver")
        val decls = parseSource(source)
        val fsm = decls.filterIsInstance<Decl.Fsm>().firstOrNull()!!

        val automaton = Automaton(fsm.start, mutableSetOf(fsm.start), mutableSetOf(), mutableListOf())
        fsm.accepting.forEach { automaton.accepting.add(it) }
        fsm.mapping.forEach { arrow ->
            val fromPair = arrow.left as Expr.Pair
            val toPair = arrow.right as Expr.Pair
            val from = fromPair.left as Expr.Sym
            val sym = fromPair.right as Expr.Sym
            val to = toPair.left as Expr.Sym
            val value = evalExpr(toPair.right, emptyMap(), fsm.start, automaton)
            automaton.addTransition(from.name, sym.name, to.name, value)
        }

        val rec = Recognizer(automaton, Strategy("test", StrategyKind.FIRST, 100, emptyList()))
        rec.input = listOf("a", "b", "c")

        // Step 1: 'a' -> q1
        val r1 = rec.step()!!
        assertEquals("q1", r1.nextState)

        // Step 2: 'b' -> q2
        val r2 = rec.step()!!
        assertEquals("q2", r2.nextState)

        // Step 3: 'c' -> no transition
        val r3 = rec.step()!!
        assertNull(r3.nextState)
        assertFalse(rec.finished == false || rec.result == null) // finished and result=false
        assertTrue(rec.finished)
        assertFalse(rec.result!!)
    }

    @Test
    fun `recognizer runToCompletion accepts ab`() {
        val source = load("simple.quiver")
        val decls = parseSource(source)
        val fsm = decls.filterIsInstance<Decl.Fsm>().firstOrNull()!!

        val automaton = Automaton(fsm.start, mutableSetOf(fsm.start), mutableSetOf(), mutableListOf())
        fsm.accepting.forEach { automaton.accepting.add(it) }
        fsm.mapping.forEach { arrow ->
            val fromPair = arrow.left as Expr.Pair
            val toPair = arrow.right as Expr.Pair
            val from = fromPair.left as Expr.Sym
            val sym = fromPair.right as Expr.Sym
            val to = toPair.left as Expr.Sym
            val value = evalExpr(toPair.right, emptyMap(), fsm.start, automaton)
            automaton.addTransition(from.name, sym.name, to.name, value)
        }

        val rec = Recognizer(automaton, Strategy("test", StrategyKind.FIRST, 100, emptyList()))
        rec.input = listOf("a", "b")
        assertTrue(rec.runToCompletion())
    }

    @Test
    fun `error reporting unknown FSM key`() {
        val ex = assertFailsWith<QuiverException> {
            runQuiver(load("error-bad-key.quiver"), listOf("x"))
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("line 4:col 12"), "Expected line 4:col 12, got: $msg")
        assertTrue(msg.contains("Unknown FSM key"), "Expected 'Unknown FSM key', got: $msg")
    }

    @Test
    fun `error reporting unexpected character`() {
        val ex = assertFailsWith<QuiverException> {
            runQuiver(load("error-unexpected-char.quiver"), listOf("x"))
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("line 4:col 3"), "Expected line 4:col 3, got: $msg")
        assertTrue(msg.contains("Unexpected character '!'"), "Expected 'Unexpected character', got: $msg")
    }

    @Test
    fun `fuel counter allows normal execution`() {
        assertEquals(true, runQuiver(load("simple.quiver"), listOf("a", "b"), maxSteps = 1000))
        assertEquals(false, runQuiver(load("simple.quiver"), listOf("a"), maxSteps = 1000))
    }

    @Test
    fun `fuel counter exhausts with low budget`() {
        val ex = assertFailsWith<FuelExhaustedException> {
            runQuiver(load("simple.quiver"), listOf("a", "b"), maxSteps = 1)
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Step budget exhausted"), "Expected 'Step budget exhausted', got: $msg")
    }

    @Test
    fun `include loads rules from another file`() {
        assertEquals(true, runQuiver(load("include-main.quiver"), listOf("a", "b"), baseDir = "src/test/resources"))
        assertEquals(true, runQuiver(load("include-main.quiver"), listOf("b"), baseDir = "src/test/resources"))
        assertEquals(false, runQuiver(load("include-main.quiver"), listOf("a"), baseDir = "src/test/resources"))
    }

    @Test
    fun `include detects circular include`() {
        val ex = assertFailsWith<QuiverException> {
            runQuiver(load("include-circular.quiver"), listOf("x"), baseDir = "src/test/resources")
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Circular include"), "Expected 'Circular include', got: $msg")
    }

    @Test
    fun `include merges a file reached through different path spellings once`() {
        // The same file, reached through two spellings of its path, is one
        // file: its declarations are merged once, not twice.
        val decls = parseSource(load("include-alias.quiver"))
        val resolved = resolveIncludes(decls, "src/test/resources", mutableSetOf())
        val rules = resolved.filterIsInstance<Decl.RuleDecl>()
        assertEquals(1, rules.size, "Expected the aliased file to be merged once")
    }

    @Test
    fun `the canonical path of a file does not depend on the spelling`() {
        val direct = canonicalPath(File("src/test/resources/include-sub.quiver"))
        val dotted = canonicalPath(File("src/test/resources/./include-sub.quiver"))
        assertEquals(direct, dotted, "Expected the two spellings to name the same file")
    }

    @Test
    fun `primitive state emits current state`() {
        val output = captureStdout { runQuiver(load("primitives.quiver"), listOf("a"), baseDir = "src/test/resources") }
        assertTrue(output.contains("q0"), "Expected output to contain 'q0', got: $output")
    }

    @Test
    fun `primitive transitions-from emits transitions`() {
        val output =
            captureStdout { runQuiver(load("primitives.quiver"), listOf("a", "b"), baseDir = "src/test/resources") }
        assertTrue(output.contains("q1"), "Expected output to contain 'q1', got: $output")
        assertTrue(output.contains("q0"), "Expected output to contain 'q0', got: $output")
    }

    @Test
    fun `primitive clone-automaton emits clone`() {
        val source = load("primitives.quiver")
        val output = captureStdout { runQuiver(source, listOf("a", "b", "c"), baseDir = "src/test/resources") }
        assertTrue(output.contains("automaton"), "Expected output to contain 'automaton', got: $output")
    }

    @Test
    fun `patternHeadKey returns Symbol for literal sym`() {
        assertEquals("Symbol", patternHeadKey(Expr.Sym("q0")))
        assertEquals("Symbol", patternHeadKey(Expr.Sym("a")))
    }

    @Test
    fun `patternHeadKey returns Any for variable sym`() {
        assertEquals("Any", patternHeadKey(Expr.Sym("\$x")))
        assertEquals("Any", patternHeadKey(Expr.Sym("\$n")))
        assertEquals("Any", patternHeadKey(Expr.Wildcard))
    }

    @Test
    fun `patternHeadKey returns Pair for Pair and Arrow`() {
        assertEquals("Pair", patternHeadKey(Expr.Pair(Expr.Sym("q0"), Expr.Sym("a"))))
        assertEquals(
            "Pair",
            patternHeadKey(
                Expr.Arrow(
                    Expr.Pair(Expr.Sym("q0"), Expr.Sym("a")),
                    Expr.Pair(Expr.Sym("q1"), Expr.IntLit(1)),
                ),
            ),
        )
    }

    @Test
    fun `patternHeadKey returns IntVal for IntLit and Plus`() {
        assertEquals("IntVal", patternHeadKey(Expr.IntLit(1)))
        assertEquals("IntVal", patternHeadKey(Expr.Plus(Expr.Sym("\$n"), Expr.IntLit(1))))
    }

    @Test
    fun `patternHeadKey returns BoolVal for BoolLit`() {
        assertEquals("BoolVal", patternHeadKey(Expr.BoolLit(true)))
        assertEquals("BoolVal", patternHeadKey(Expr.BoolLit(false)))
    }

    @Test
    fun `patternHeadKey returns Func for Fun and App`() {
        assertEquals("Func", patternHeadKey(Expr.Fun(listOf("\$x"), Expr.Sym("\$x"))))
        assertEquals("Func", patternHeadKey(Expr.App(Expr.Sym("f"), Expr.IntLit(1))))
    }

    @Test
    fun `patternHeadKey returns the head key of the term a bind matches`() {
        // A binding matches the term it binds, so its head key is that of the
        // term: a bind over a literal symbol is a symbol-shaped pattern.
        assertEquals("Symbol", patternHeadKey(Expr.Bind("\$x", Expr.Sym("y"))))
        assertEquals("Pair", patternHeadKey(Expr.Bind("\$x", Expr.Pair(Expr.Sym("q0"), Expr.Sym("a")))))
    }

    @Test
    fun `patternHeadKey returns Any for a bind over any term and for Emit`() {
        assertEquals("Any", patternHeadKey(Expr.Bind("\$x", Expr.Wildcard)))
        assertEquals("Any", patternHeadKey(Expr.Bind("\$x", Expr.Sym("\$y"))))
        assertEquals("Any", patternHeadKey(Expr.Emit(Expr.Sym("\$n"))))
    }

    @Test
    fun `subjectHeadKey returns Symbol for Symbol`() {
        assertEquals("Symbol", subjectHeadKey(Value.Symbol("q0")))
    }

    @Test
    fun `subjectHeadKey returns Pair for Pair`() {
        assertEquals("Pair", subjectHeadKey(Value.Pair(Value.Symbol("q0"), Value.Symbol("a"))))
    }

    @Test
    fun `subjectHeadKey returns Seq for Seq`() {
        assertEquals("Seq", subjectHeadKey(Value.Seq(emptyList())))
    }

    @Test
    fun `subjectHeadKey returns IntVal for IntVal`() {
        assertEquals("IntVal", subjectHeadKey(Value.IntVal(42)))
    }

    @Test
    fun `subjectHeadKey returns BoolVal for BoolVal`() {
        assertEquals("BoolVal", subjectHeadKey(Value.BoolVal(true)))
    }

    @Test
    fun `subjectHeadKey returns Func for Func`() {
        assertEquals("Func", subjectHeadKey(Value.Func(emptyList(), Expr.Sym("\$x"), emptyMap())))
    }

    @Test
    fun `subjectHeadKey returns Func for Automaton`() {
        assertEquals(
            "Func",
            subjectHeadKey(Value.Automaton(Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf()))),
        )
    }

    @Test
    fun `ruleIndex filters rules correctly`() {
        val rules = listOf(
            Decl.RuleDecl(Rule(Expr.Sym("q0"), null, emptyList())),
            Decl.RuleDecl(Rule(Expr.Pair(Expr.Sym("q0"), Expr.Sym("a")), null, emptyList())),
            Decl.RuleDecl(Rule(Expr.Sym("\$x"), null, emptyList())),
            Decl.RuleDecl(Rule(Expr.IntLit(1), null, emptyList())),
        )
        val index = rules.groupBy { patternHeadKey(it.rule.pattern) }

        assertEquals(1, index["Symbol"]?.size) // just (q0)
        assertEquals(1, index["Pair"]?.size) // just (q0, a)
        assertEquals(1, index["IntVal"]?.size) // just (1)
        assertEquals(1, index["Any"]?.size) // just ($x)
    }

    @Test
    fun `property deterministic recognition`() {
        val testInputs = listOf("ab", "aabb", "aaabbb", "aabbcc", "abc", "a", "b", "")
        for (text in testInputs) {
            val input = text.map { it.toString() }
            val result1 = runQuiver(load("rec.quiver"), input)
            val result2 = runQuiver(load("rec.quiver"), input)
            assertEquals(result1, result2, "Determinism violated for input '$text'")
        }
    }

    @Test
    fun `property trace invariance`() {
        val testInputs = listOf("ab", "aabb", "aaabbb", "aabbcc", "abc", "a", "b", "")
        for (text in testInputs) {
            val input = text.map { it.toString() }
            val resultNoTrace = runQuiver(load("rec.quiver"), input, trace = false)
            val resultWithTrace = runQuiver(load("rec.quiver"), input, trace = true)
            assertEquals(resultNoTrace, resultWithTrace, "Trace changed result for input '$text'")
        }
    }

    @Test
    fun `property fuel invariance for terminating programs`() {
        val input = listOf("a", "a", "b", "b", "c", "c")
        val resultDefault = runQuiver(load("rec.quiver"), input, maxSteps = 10_000)
        val resultHighFuel = runQuiver(load("rec.quiver"), input, maxSteps = 100_000)
        val resultLowFuel = runQuiver(load("rec.quiver"), input, maxSteps = 500)
        assertEquals(resultDefault, resultHighFuel)
        assertEquals(resultDefault, resultLowFuel, "Changing maxSteps changed result for '$input'")
    }

    @Test
    fun `property random input determinism`() {
        val rng = Random(12_345)
        val symbols = listOf('a', 'b', 'c')
        repeat(50) {
            val len = rng.nextInt(9)
            val input = List(len) { symbols[rng.nextInt(symbols.size)].toString() }
            val result1 = runQuiver(load("rec.quiver"), input)
            val result2 = runQuiver(load("rec.quiver"), input)
            assertEquals(result1, result2, "Determinism violated for random input '$input'")
        }
    }

    @Test
    fun `property ruleIndex produces same results as no index`() {
        val rng = Random(999)
        val testInputs = listOf("ab", "aabb", "aaabbb", "aabbcc", "abc", "a", "b", "")
        for (text in testInputs) {
            val input = text.map { it.toString() }
            val decls = parseSource(load("rec.quiver"))
            val resolvedDecls = resolveIncludes(decls, ".", mutableSetOf())
            val fsm = resolvedDecls.filterIsInstance<Decl.Fsm>().firstOrNull()
            val rules = resolvedDecls.filterIsInstance<Decl.RuleDecl>().map { it.rule }
            val strategies = resolvedDecls.filterIsInstance<Decl.StrategyDecl>().associateBy { it.strategy.label }
            val activeStrategy: Strategy? = fsm?.strategyLabel?.let { strategies[it]?.strategy }
                ?: Strategy("default", StrategyKind.FIRST, 100, rules)
            val ruleIndex = rules.groupBy { patternHeadKey(it.pattern) }

            val startState = fsm?.start ?: "q0"
            val automaton = Automaton(startState, mutableSetOf(startState), mutableSetOf(), mutableListOf())
            fsm?.let {
                it.accepting.forEach { automaton.accepting.add(it) }
                it.mapping.forEach { arrow ->
                    val fromPair = arrow.left as Expr.Pair
                    val toPair = arrow.right as Expr.Pair
                    val from = fromPair.left as Expr.Sym
                    val sym = fromPair.right as Expr.Sym
                    val to = toPair.left as Expr.Sym
                    val value = evalExpr(toPair.right, emptyMap(), startState, automaton)
                    automaton.addTransition(from.name, sym.name, to.name, value)
                }
            }

            val resultNoIndex = recognize(input, automaton, activeStrategy, maxSteps = 10_000, ruleIndex = null)
            val resultWithIndex = recognize(input, automaton, activeStrategy, maxSteps = 10_000, ruleIndex = ruleIndex)
            assertEquals(resultNoIndex, resultWithIndex, "Rule index changed result for input '$text'")
        }
    }

    // ---------------------------------------------------------------------
    // The shipped example programs: sequences, bindings, and the adaptive
    // actions (insert, retarget and remove).
    // ---------------------------------------------------------------------

    @Test
    fun `seq example accepts ab`() {
        assertEquals(true, runQuiver(load("seq.quiver"), listOf("a", "b")))
    }

    @Test
    fun `seq example rejects a`() {
        assertEquals(false, runQuiver(load("seq.quiver"), listOf("a")))
    }

    @Test
    fun `seq example rejects b`() {
        assertEquals(false, runQuiver(load("seq.quiver"), listOf("b")))
    }

    @Test
    fun `seq example rejects aba`() {
        assertEquals(false, runQuiver(load("seq.quiver"), listOf("a", "b", "a")))
    }

    @Test
    fun `seq example emits the sequence it stored`() {
        val output = captureStdout { runQuiver(load("seq.quiver"), listOf("a", "b")) }
        assertTrue(output.contains("[seq q1,0]"), "Expected the stored sequence, got: $output")
    }

    @Test
    fun `binding example accepts ab`() {
        assertEquals(true, runQuiver(load("binding.quiver"), listOf("a", "b")))
    }

    @Test
    fun `binding example rejects a`() {
        assertEquals(false, runQuiver(load("binding.quiver"), listOf("a")))
    }

    @Test
    fun `binding example rejects aa`() {
        assertEquals(false, runQuiver(load("binding.quiver"), listOf("a", "a")))
    }

    @Test
    fun `binding example creates the transition that accepts the rest`() {
        val automaton = automatonOf("binding.quiver")
        step("q0", Label.Symbol("a"), automaton, strategyOf("binding.quiver"))
        val created = automaton.transitions.any { it.from == "q0" && it.symbol == "b" }
        assertTrue(created, "The (q0,b) transition was not inserted")
    }

    @Test
    fun `adaptive insert example accepts ab`() {
        assertEquals(true, runQuiver(load("adaptive-insert.quiver"), listOf("a", "b")))
    }

    @Test
    fun `adaptive insert example rejects b`() {
        assertEquals(false, runQuiver(load("adaptive-insert.quiver"), listOf("b")))
    }

    @Test
    fun `adaptive insert example rejects a`() {
        assertEquals(false, runQuiver(load("adaptive-insert.quiver"), listOf("a")))
    }

    @Test
    fun `adaptive insert example creates the transition`() {
        val automaton = automatonOf("adaptive-insert.quiver")
        step("q0", Label.Symbol("a"), automaton, strategyOf("adaptive-insert.quiver"))
        val created = automaton.transitions.any { it.from == "q0" && it.symbol == "b" }
        assertTrue(created, "The (q0,b) transition was not inserted")
    }

    @Test
    fun `adaptive threshold example accepts four a's`() {
        assertEquals(true, runQuiver(load("adaptive-threshold.quiver"), listOf("a", "a", "a", "a")))
    }

    @Test
    fun `adaptive threshold example rejects three a's`() {
        assertEquals(false, runQuiver(load("adaptive-threshold.quiver"), listOf("a", "a", "a")))
    }

    @Test
    fun `adaptive threshold example rejects five a's`() {
        assertEquals(false, runQuiver(load("adaptive-threshold.quiver"), listOf("a", "a", "a", "a", "a")))
    }

    @Test
    fun `adaptive threshold example retargets the transition at the threshold`() {
        val automaton = automatonOf("adaptive-threshold.quiver")
        val strategy = strategyOf("adaptive-threshold.quiver")
        repeat(3) { step("q0", Label.Symbol("a"), automaton, strategy) }
        val before = automaton.transitions.first { it.from == "q0" && it.symbol == "a" }.to
        assertEquals("q0", before, "The (q0,a) transition was retargeted before the threshold")
        step("q0", Label.Symbol("a"), automaton, strategy)
        val after = automaton.transitions.first { it.from == "q0" && it.symbol == "a" }.to
        assertEquals("q2", after, "The (q0,a) transition was not retargeted at the threshold")
    }

    @Test
    fun `adaptive remove example accepts two a's`() {
        assertEquals(true, runQuiver(load("adaptive-remove.quiver"), listOf("a", "a")))
    }

    @Test
    fun `adaptive remove example rejects three a's`() {
        assertEquals(false, runQuiver(load("adaptive-remove.quiver"), listOf("a", "a", "a")))
    }

    @Test
    fun `adaptive remove example deletes the transition`() {
        val automaton = automatonOf("adaptive-remove.quiver")
        step("q1", Label.Symbol("a"), automaton, strategyOf("adaptive-remove.quiver"))
        val survives = automaton.transitions.any { it.from == "q1" && it.symbol == "a" }
        assertFalse(survives, "The (q1,a) transition was not removed")
    }

    @Test
    fun `sweep example accepts bz`() {
        assertEquals(true, runQuiver(load("sweep.quiver"), listOf("b", "z")))
    }

    @Test
    fun `sweep example rejects b`() {
        assertEquals(false, runQuiver(load("sweep.quiver"), listOf("b")))
    }

    @Test
    fun `sweep example rejects bzz`() {
        assertEquals(false, runQuiver(load("sweep.quiver"), listOf("b", "z", "z")))
    }

    @Test
    fun `sweep example retargets every transition that leaves q0`() {
        val automaton = automatonOf("sweep.quiver")
        val strategy = strategyOf("sweep.quiver")
        step("q0", Label.Symbol("b"), automaton, strategy)
        step("q1", Label.Symbol("z"), automaton, strategy)
        val retargeted = automaton.transitions.filter { it.from == "q0" }
        assertEquals(3, retargeted.size)
        assertTrue(retargeted.all { it.to == "q2" }, "The transitions leaving q0 were not all retargeted")
    }

    @Test
    fun `sweep example prints the symbol of every transition it sweeps`() {
        val output = captureStdout { runQuiver(load("sweep.quiver"), listOf("b", "z")) }
        assertTrue(output.contains("a"), "Expected the symbol a, got: $output")
        assertTrue(output.contains("b"), "Expected the symbol b, got: $output")
        assertTrue(output.contains("c"), "Expected the symbol c, got: $output")
    }

    @Test
    fun `the trace of a sweep names the transition it sweeps`() {
        val output = captureStderr { runQuiver(load("sweep.quiver"), listOf("b", "z"), trace = true) }
        assertTrue(
            output.contains("sweep: (q0,\$s) -> (\$t,\$v) on (q0,a)->(q0,0)"),
            "Expected the sweep of the first transition, got: $output",
        )
    }

    @Test
    fun `inspect example accepts ab`() {
        assertEquals(true, runQuiver(load("inspect.quiver"), listOf("a", "b")))
    }

    @Test
    fun `inspect example rejects a`() {
        assertEquals(false, runQuiver(load("inspect.quiver"), listOf("a")))
    }

    @Test
    fun `inspect example prints the targets and the symbols of q0`() {
        val output = captureStdout { runQuiver(load("inspect.quiver"), listOf("a", "b")) }
        assertTrue(output.contains("[seq q0,q1]"), "Expected the targets of q0, got: $output")
        assertTrue(output.contains("[seq a,b]"), "Expected the symbols of q0, got: $output")
    }

    @Test
    fun `inspect example prints the size of the sequence and the emptiness of q2`() {
        val output = captureStdout { runQuiver(load("inspect.quiver"), listOf("a", "b")) }
        assertTrue(output.contains("2"), "Expected the size of the sequence, got: $output")
        assertTrue(output.contains("true"), "Expected the emptiness of q2, got: $output")
    }

    @Test
    fun `counter machine example accepts an even number`() {
        assertEquals(true, runQuiver(load("counter-machine.quiver"), listOf("a", "a", "z")))
    }

    @Test
    fun `counter machine example accepts the empty number`() {
        assertEquals(true, runQuiver(load("counter-machine.quiver"), listOf("z")))
    }

    @Test
    fun `counter machine example rejects an odd number`() {
        assertEquals(false, runQuiver(load("counter-machine.quiver"), listOf("a", "a", "a", "z")))
    }

    @Test
    fun `counter machine example rejects an input without the marker`() {
        // The marker is what starts the machine: without it, the automaton
        // rests on the loading state, which is not accepting.
        assertEquals(false, runQuiver(load("counter-machine.quiver"), listOf("a", "a")))
    }

    @Test
    fun `counter machine example rejects an input after the machine halts`() {
        assertEquals(false, runQuiver(load("counter-machine.quiver"), listOf("a", "a", "z", "z")))
    }

    @Test
    fun `counter machine example computes a long number`() {
        // The counters are arbitrary-precision, and the budget covers the
        // internal steps of the simulation.
        val input = List(100) { "a" } + "z"
        assertEquals(true, runQuiver(load("counter-machine.quiver"), input))
    }

    @Test
    fun `counter machine example reports the closure failure when the budget is small`() {
        // The six symbols of the loading phase and the marker take seven
        // units of the budget, so the closure takes one internal step and
        // then the budget is exhausted.
        val ex =
            assertFailsWith<EpsilonClosureException> {
                runQuiver(load("counter-machine.quiver"), listOf("a", "a", "a", "a", "a", "a", "z"), maxSteps = 8)
            }
        val msg = ex.message ?: ""
        assertContains(msg, "Epsilon closure did not terminate")
        assertContains(msg, "after 1 internal steps")
    }

    // ---------------------------------------------------------------------
    // Non-determinism: multiple transitions per (state, symbol) key,
    // each explored in parallel on its own automaton clone.
    // ---------------------------------------------------------------------

    @Test
    fun `nondet example accepts a via the q2 path`() {
        assertEquals(true, runQuiver(load("nondet.quiver"), listOf("a"), nondet = true))
    }

    @Test
    fun `nondet example rejects ab`() {
        assertEquals(false, runQuiver(load("nondet.quiver"), listOf("a", "b"), nondet = true))
    }

    @Test
    fun `nondet example rejects ac`() {
        assertEquals(false, runQuiver(load("nondet.quiver"), listOf("a", "c"), nondet = true))
    }

    @Test
    fun `nondet example rejects b`() {
        assertEquals(false, runQuiver(load("nondet.quiver"), listOf("b"), nondet = true))
    }

    @Test
    fun `nondet example rejects empty`() {
        assertEquals(false, runQuiver(load("nondet.quiver"), emptyList(), nondet = true))
    }

    @Test
    fun `nondet example behaves deterministically without --nondet`() {
        // Without --nondet, only the first (q0,a)->(q1,0) transition is used.
        assertEquals(false, runQuiver(load("nondet.quiver"), listOf("a")))
        assertEquals(false, runQuiver(load("nondet.quiver"), listOf("a", "b")))
    }

    @Test
    fun `nondet adaptive accepts ab via q1 path`() {
        assertEquals(true, runQuiver(load("nondet-adaptive.quiver"), listOf("a", "b"), nondet = true))
    }

    @Test
    fun `nondet adaptive accepts ac via q2 path`() {
        assertEquals(true, runQuiver(load("nondet-adaptive.quiver"), listOf("a", "c"), nondet = true))
    }

    @Test
    fun `nondet adaptive rejects a`() {
        assertEquals(false, runQuiver(load("nondet-adaptive.quiver"), listOf("a"), nondet = true))
    }

    @Test
    fun `nondet adaptive path-specific rewrites do not leak`() {
        // Path via q1 retargets (q1,b) to q3; path via q2 retargets (q2,c) to q3.
        // Each path's automaton is independent: neither path's insert/remove
        // affects the other path's future transitions.
        assertEquals(true, runQuiver(load("nondet-adaptive.quiver"), listOf("a", "b"), nondet = true))
        assertEquals(true, runQuiver(load("nondet-adaptive.quiver"), listOf("a", "c"), nondet = true))
    }

    @Test
    fun `nondet adaptive behaves deterministically without --nondet`() {
        // Without --nondet, only the first (q0,a)->(q1,0) transition is used.
        assertEquals(true, runQuiver(load("nondet-adaptive.quiver"), listOf("a", "b")))
        assertEquals(false, runQuiver(load("nondet-adaptive.quiver"), listOf("a", "c")))
    }

    @Test
    fun `nondet example kills all paths mid input`() {
        // After 'a': frontier = {q1, q2}. After 'b': q1 survives, q2 dies.
        // After 'c': q1 has no c-transition, frontier becomes empty.
        assertEquals(false, runQuiver(load("nondet.quiver"), listOf("a", "b", "c"), nondet = true))
    }

    @Test
    fun `nondet example with trace exercises the trace path`() {
        // trace = true exercises the trace output lines in stepNondet and
        // processTransitionNondet without changing the result.
        assertEquals(true, runQuiver(load("nondet.quiver"), listOf("a"), trace = true, nondet = true))
        assertEquals(false, runQuiver(load("nondet.quiver"), listOf("a", "b", "c"), trace = true, nondet = true))
    }

    @Test
    fun `nondet all strategy accepts a`() {
        // The ALL strategy fires the rule on every matching transition,
        // each on its own automaton clone. Both q1 and q2 are accepting.
        assertEquals(true, runQuiver(load("nondet-strategy.quiver"), listOf("a"), nondet = true))
    }

    @Test
    fun `nondet all strategy rejects b`() {
        assertEquals(false, runQuiver(load("nondet-strategy.quiver"), listOf("b"), nondet = true))
    }

    @Test
    fun `nondet adaptive with trace exercises trace branches`() {
        // trace = true on a program with rules covers the trace output lines
        // in processTransitionNondet, including "no rule fired" when a path
        // has no applicable rule.
        assertEquals(true, runQuiver(load("nondet-adaptive.quiver"), listOf("a", "b"), trace = true, nondet = true))
        assertEquals(false, runQuiver(load("nondet-adaptive.quiver"), listOf("a"), trace = true, nondet = true))
    }

    @Test
    fun `nondet repeat strategy accepts a`() {
        // The REPEAT strategy re-applies the rule on every matching transition,
        // each on its own automaton clone. Both q1 and q2 are accepting.
        assertEquals(true, runQuiver(load("nondet-repeat.quiver"), listOf("a"), nondet = true))
    }

    @Test
    fun `nondet repeat strategy rejects b`() {
        assertEquals(false, runQuiver(load("nondet-repeat.quiver"), listOf("b"), nondet = true))
    }

    // ---------------------------------------------------------------------
    // The rule index: it must never hide a rule from the strategy.
    // ---------------------------------------------------------------------

    @Test
    fun `a wildcard rule fires next to a rule with another head key`() {
        // The rule index groups the rules by the head key of their pattern.
        // A wildcard matches every subject, so it belongs to every group and
        // must not be hidden by the rules of the group it shares.
        assertEquals(true, runQuiver(load("index-wildcard.quiver"), listOf("a", "b")))
    }

    @Test
    fun `a variable rule fires next to a rule with another head key`() {
        assertEquals(true, runQuiver(load("index-variable.quiver"), listOf("a", "b")))
    }

    @Test
    fun `a binding rule fires next to a rule with another head key`() {
        assertEquals(true, runQuiver(load("index-binding.quiver"), listOf("a", "b")))
    }

    @Test
    fun `the rules of the named strategy fire next to the top-level rules`() {
        // The FSM header selects the named strategy, so the rules of that
        // strategy are the ones the run consults. The top-level rule of the
        // program belongs to no strategy and must not shadow them.
        assertEquals(true, runQuiver(load("index-strategy.quiver"), listOf("a", "b")))
    }

    @Test
    fun `the rule index preserves the source order of the rules`() {
        // The `first` strategy fires the first rule that matches, so the
        // wildcard rule, which is declared first, wins over the arrow-shaped
        // rule that follows it.
        assertEquals(true, runQuiver(load("index-order.quiver"), listOf("a", "b")))
        assertEquals(false, runQuiver(load("index-order.quiver"), listOf("a", "c")))
    }

    @Test
    fun `the rule index and the whole rule list agree`() {
        // The index is an optimisation: it must not change what a program
        // recognizes. The programs below carry rules of every shape, so the
        // comparison covers the buckets of the index.
        for (name in listOf(
            "index-wildcard.quiver",
            "index-variable.quiver",
            "index-binding.quiver",
            "index-order.quiver",
        )) {
            val automaton = automatonOf(name)
            val rules = parseSource(load(name)).filterIsInstance<Decl.RuleDecl>().map { it.rule }
            val strategy = Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, rules)
            val index = buildRuleIndex(rules)
            for (text in listOf("a", "ab", "ac", "b", "")) {
                val input = text.map { it.toString() }
                val withIndex = recognize(input, automaton.clone(), strategy, ruleIndex = index)
                val withoutIndex = recognize(input, automaton.clone(), strategy, ruleIndex = null)
                assertEquals(withIndex, withoutIndex, "The rule index changed the result of $name for '$text'")
            }
        }
    }

    // ---------------------------------------------------------------------
    // The non-deterministic frontier: every transition is a path of its own.
    // ---------------------------------------------------------------------

    @Test
    fun `two identical transitions are two paths`() {
        // The frontier keeps the paths apart, so the rule fires once on each
        // of them and the value is emitted twice.
        val output = captureStdout {
            assertEquals(false, runQuiver(load("nondet-identical.quiver"), listOf("a"), nondet = true))
        }
        assertEquals("1\n1\n", output)
    }

    @Test
    fun `the fuel bounds the paths a non-deterministic run explores`() {
        // Every transition the run follows consumes one unit of fuel, so a
        // run that follows two transitions out of the same state exhausts a
        // budget of one unit.
        val ex = assertFailsWith<FuelExhaustedException> {
            runQuiver(load("nondet-identical.quiver"), listOf("a"), nondet = true, maxSteps = 1)
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("after 1 steps"), "Expected 'after 1 steps', got: $msg")
    }

    // ---------------------------------------------------------------------
    // Error reporting: malformed programs must raise a Quiver error, never an
    // unchecked exception.
    // ---------------------------------------------------------------------

    @Test
    fun `malformed mapping source reports a Quiver error`() {
        val ex = assertFailsWith<QuiverException> {
            runQuiver(load("error-bad-mapping-left.quiver"), listOf("a"))
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Invalid FSM mapping"), "Expected 'Invalid FSM mapping', got: $msg")
    }

    @Test
    fun `malformed mapping target reports a Quiver error`() {
        val ex = assertFailsWith<QuiverException> {
            runQuiver(load("error-bad-mapping-right.quiver"), listOf("a"))
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Invalid FSM mapping"), "Expected 'Invalid FSM mapping', got: $msg")
    }

    @Test
    fun `mapping entry that is not an arrow reports a Quiver error`() {
        val ex = assertFailsWith<QuiverException> {
            runQuiver(load("error-bad-mapping-entry.quiver"), listOf("a"))
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Invalid FSM mapping"), "Expected 'Invalid FSM mapping', got: $msg")
    }

    @Test
    fun `arithmetic over a non-integer reports a Quiver error`() {
        val ex = assertFailsWith<QuiverException> {
            runQuiver(load("error-bad-arithmetic.quiver"), listOf("a"))
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Expected a integer"), "Expected 'Expected a integer', got: $msg")
    }

    // ---------------------------------------------------------------------
    // Lexical and syntactic edge cases.
    // ---------------------------------------------------------------------

    @Test
    fun `minus followed by less is rejected`() {
        val ex = assertFailsWith<QuiverException> { lex("a -< b") }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Unexpected character '-'"), "Expected \"Unexpected character '-'\", got: $msg")
    }

    @Test
    fun `negative integer literal is parsed and evaluated`() {
        val (negative, positive) = numericValues()
        assertEquals(BigInteger.valueOf(-1), negative)
        assertEquals(BigInteger.valueOf(7), positive)
    }

    @Test
    fun `rule without a body reports the end of the input`() {
        // The parser must not choke on the end of the input while it looks
        // ahead for the `+` of an arithmetic expression: the error must be
        // reported by the parser proper, with the position of the input.
        val ex = assertFailsWith<QuiverException> { parseSource("x <- 42") }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Unexpected end of input"), "Expected 'Unexpected end of input', got: $msg")
    }

    @Test
    fun `binding with a numeric value parses`() {
        val decls = parseSource("x <- 42 => { _ => 1 }")
        assertEquals(1, decls.size)
    }

    @Test
    fun `the sequence special form parses`() {
        val decls = parseSource("x <- \$seq [ |\$seq| ] => { _ => 1 }")
        assertEquals(1, decls.size)
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern
        assertTrue(bind is Expr.Bind, "Expected a binding, got: $bind")
        assertEquals(Expr.Sym("|\$seq|"), bind.expr)
    }

    @Test
    fun `unary plus evaluates as the identity`() {
        val (negative, positive) = numericValues()
        assertEquals(BigInteger.valueOf(-1), negative)
        assertEquals(BigInteger.valueOf(7), positive)
    }

    // ---------------------------------------------------------------------
    // Arbitrary-precision integers: the counters a program carries must not
    // be limited by the width of a machine integer.
    // ---------------------------------------------------------------------

    @Test
    fun `a twenty-digit literal keeps every digit`() {
        val decls = parseSource("x <- 12345678901234567890 => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        assertEquals(Expr.IntLit(BigInteger("12345678901234567890")), bind.expr)
        val value = evalExpr(bind.expr, emptyMap(), "q0", emptyAutomaton())
        assertEquals(BigInteger("12345678901234567890"), (value as Value.IntVal).n)
    }

    @Test
    fun `arithmetic beyond the machine width does not wrap around`() {
        // The sum is far beyond the range of a 64-bit integer: a machine-width
        // counter would wrap around to a negative number here.
        val decls = parseSource("x <- 9223372036854775807 + 1 => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        val value = evalExpr(bind.expr, emptyMap(), "q0", emptyAutomaton())
        assertEquals(BigInteger("9223372036854775808"), (value as Value.IntVal).n)
    }

    @Test
    fun `a big literal matches a big counter`() {
        val literal = Expr.IntLit(BigInteger("98765432109876543210"))
        val counter = Value.IntVal(BigInteger("98765432109876543210"))
        assertNotNull(matchTerm(literal, counter, emptyMap()))
        val other = Value.IntVal(BigInteger("98765432109876543211"))
        assertNull(matchTerm(literal, other, emptyMap()))
    }

    @Test
    fun `a big counter is rendered exactly`() {
        assertEquals("98765432109876543210", valueToString(Value.IntVal(BigInteger("98765432109876543210"))))
    }

    @Test
    fun `a big counter is carried by the automaton`() {
        // The counter starts at a value beyond the machine width; the mapping
        // entry that carries it must hold the exact value.
        val source =
            """
            @fsm => {
              start => q0
              accepting => ${'$'}seq [q1]
              mapping => {
                (q0, a) -> (q0, 98765432109876543210)
                (q0, b) -> (q1, 0)
              }
            }
            """.trimIndent()
        val automaton = buildAutomaton(parseSource(source).filterIsInstance<Decl.Fsm>().first())
        val counter =
            automaton.transitions.first { it.from == "q0" && it.symbol == "a" }.value
                as Value.IntVal
        assertEquals(BigInteger("98765432109876543210"), counter.n)
    }

    @Test
    fun `an iteration cap beyond the machine width is rejected`() {
        val ex = assertFailsWith<QuiverException> { parseSource("strategy s repeat max 99999999999999999999 => { }") }
        val msg = ex.message ?: ""
        assertContains(msg, "The iteration cap must fit in a machine integer")
    }

    // ---------------------------------------------------------------------
    // Rewriting semantics.
    // ---------------------------------------------------------------------

    @Test
    fun `clause whose pattern does not match is skipped`() {
        val automaton = automatonOf("clause-skip.quiver")
        step("q0", Label.Symbol("a"), automaton, strategyOf("clause-skip.quiver"))
        val survives = automaton.transitions.any { it.from == "q1" && it.symbol == "b" }
        assertTrue(survives, "The (q1,b) transition was removed by a clause that did not match")
    }

    @Test
    fun `binding in a pattern captures the matched term`() {
        val automaton = automatonOf("binding.quiver")
        step("q0", Label.Symbol("a"), automaton, strategyOf("binding.quiver"))
        val survives = automaton.transitions.any { it.from == "q0" && it.symbol == "a" }
        assertFalse(survives, "The bound transition was not removed")
    }

    // ---------------------------------------------------------------------
    // Includes.
    // ---------------------------------------------------------------------

    @Test
    fun `diamond include is not a cycle`() {
        assertEquals(true, runQuiver(load("diamond-main.quiver"), listOf("a"), baseDir = "src/test/resources"))
    }

    // ---------------------------------------------------------------------
    // Fuel accounting.
    // ---------------------------------------------------------------------

    @Test
    fun `fuel exhaustion reports the number of steps taken`() {
        val ex = assertFailsWith<FuelExhaustedException> {
            runQuiver(load("simple.quiver"), listOf("a", "b"), maxSteps = 1)
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("after 1 steps"), "Expected 'after 1 steps', got: $msg")
    }

    @Test
    fun `fuel counter exhausts with zero budget`() {
        val ex = assertFailsWith<FuelExhaustedException> {
            runQuiver(load("simple.quiver"), listOf("a", "b"), maxSteps = 0)
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("after 0 steps"), "Expected 'after 0 steps', got: $msg")
    }

    // ---------------------------------------------------------------------
    // Helpers.
    // ---------------------------------------------------------------------

    /** Builds an empty automaton, used when a value is evaluated in isolation. */
    private fun emptyAutomaton(): Automaton = Automaton("q0", mutableSetOf("q0"), mutableSetOf(), mutableListOf())

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

    /** Evaluates the values of the two transitions of the numeric source file. */
    private fun numericValues(): Pair<BigInteger, BigInteger> {
        val fsm = parseSource(load("numeric.quiver")).filterIsInstance<Decl.Fsm>().first()
        val automaton = emptyAutomaton()
        val values =
            fsm.mapping.map { arrow ->
                val entry = mappingEntry(arrow)
                val value = evalExpr(entry.value, emptyMap(), fsm.start, automaton)
                (value as Value.IntVal).n
            }
        return values[0] to values[1]
    }

    /** Loads a resource of the test suite. */
    private fun load(name: String): String = loadResource(name)
}
