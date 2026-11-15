// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The tests of the lexer and the parser.
 *
 * They cover the paths a well-formed program does not take: the lexical and
 * the syntactic errors, and the forms the common programs do not use.
 */
class QuiverParserTest {

    @Test
    fun `a bare less-than sign is a lexical error`() {
        val error = assertFailsWith<QuiverException> { lex("a < b") }
        assertContains(error.message ?: "", "Unexpected character '<'")
    }

    @Test
    fun `a transition shape is a valid pattern`() {
        // The pattern of a rule may be the shape of a transition: the pair of
        // the key and the target of the transition.
        val source =
            """
            ((q0, a), (q1, 0)) => {
              _ => (q0, a) -> (q1, 0)
            }
            """.trimIndent()
        val decls = parseSource(source)
        assertTrue(decls.isNotEmpty(), "The parser rejected the pattern: $decls")
    }

    @Test
    fun `a transition shape is a valid term`() {
        // The body of a clause may hold the shape of a transition, and not
        // only an arrow.
        val source =
            """
            (q0, a) -> (${'$'}to, ${'$'}n) => {
              _ => ((q0, a), (q1, 0))
            }
            """.trimIndent()
        val decls = parseSource(source)
        assertTrue(decls.isNotEmpty(), "The parser rejected the term: $decls")
    }

    @Test
    fun `a bare equals sign is a lexical error`() {
        val error = assertFailsWith<QuiverException> { lex("a = b") }
        assertContains(error.message ?: "", "Unexpected character '='")
    }

    @Test
    fun `a greater-than sign alone is a lexical error`() {
        val error = assertFailsWith<QuiverException> { lex("a > b") }
        assertContains(error.message ?: "", "Unexpected character '>'")
    }

    @Test
    fun `a greater-than sign followed by an equals sign is a lexical error`() {
        // `>=` is not a token of Quiver: it must not be read as the `=>` arrow.
        val error = assertFailsWith<QuiverException> { lex("a >= b") }
        assertContains(error.message ?: "", "Unexpected character '>'")
    }

    @Test
    fun `a minus followed by a less-than sign is a lexical error`() {
        val error = assertFailsWith<QuiverException> { lex("a -< b") }
        assertContains(error.message ?: "", "Unexpected character '-'")
    }

    @Test
    fun `the assignment token is lexed`() {
        val tokens = lex("x <- y")
        assertTrue(tokens.any { it is Token.Assign }, "Expected an assignment token in: $tokens")
    }

    @Test
    fun `the arrow token is lexed`() {
        val tokens = lex("(q0, a) -> (q1, 0)")
        assertTrue(tokens.any { it is Token.Arrow }, "Expected an arrow token in: $tokens")
    }

    @Test
    fun `the minus token is lexed`() {
        val tokens = lex("1 - 2")
        assertTrue(tokens.any { it is Token.Minus }, "Expected a minus token in: $tokens")
    }

    @Test
    fun `a comment at the end of the file is lexed`() {
        val tokens = lex("# the last line of the file")
        val comment = tokens.filterIsInstance<Token.Comment>().single()
        assertContains(comment.text, "the last line of the file")
    }

    @Test
    fun `a less-than sign at the end of the input is a lexical error`() {
        val error = assertFailsWith<QuiverException> { lex("a <") }
        assertContains(error.message ?: "", "Unexpected character '<'")
    }

    @Test
    fun `a minus at the end of the input is lexed`() {
        val tokens = lex("a -")
        assertTrue(tokens.any { it is Token.Minus }, "Expected a minus token in: $tokens")
    }

    @Test
    fun `an equals sign at the end of the input is a lexical error`() {
        val error = assertFailsWith<QuiverException> { lex("a =") }
        assertContains(error.message ?: "", "Unexpected character '='")
    }

    @Test
    fun `an unterminated string is a lexical error`() {
        val error = assertFailsWith<QuiverException> { lex("include \"unterminated") }
        assertContains(error.message ?: "", "Unterminated string literal")
    }

    @Test
    fun `the escapes of a string are understood`() {
        val tokens = lex("include \"a\\nb\\tc\\\"d\\\\e\"")
        val literal = tokens.filterIsInstance<Token.StringLit>().single()
        assertEquals("a\nb\tc\"d\\e", literal.text)
    }

    @Test
    fun `an unknown escape is kept as it is`() {
        val tokens = lex("include \"a\\qb\"")
        val literal = tokens.filterIsInstance<Token.StringLit>().single()
        assertEquals("aqb", literal.text)
    }

    @Test
    fun `a missing token is a syntax error`() {
        val error = assertFailsWith<QuiverException> { parseSource("@fsm => { start => 0 }") }
        assertContains(error.message ?: "", "Expected")
    }

    @Test
    fun `an incomplete fsm header is a syntax error`() {
        val error = assertFailsWith<QuiverException> { parseSource("@fsm => { start => q0") }
        assertContains(error.message ?: "", "Unexpected end of input")
    }

    @Test
    fun `an unknown fsm key is a syntax error`() {
        val error = assertFailsWith<QuiverException> { parseSource("@fsm => { nope => q0 }") }
        assertContains(error.message ?: "", "Unknown FSM key 'nope'")
    }

    @Test
    fun `an unknown fsm key at the end of the input is a syntax error`() {
        val error = assertFailsWith<QuiverException> { parseSource("@fsm => { nope =>") }
        assertContains(error.message ?: "", "Unknown FSM key 'nope'")
    }

    @Test
    fun `an unknown strategy kind is a syntax error`() {
        val error = assertFailsWith<QuiverException> { parseSource("strategy mine bogus => { }") }
        assertContains(error.message ?: "", "Unknown strategy kind 'bogus'")
    }

    @Test
    fun `an unknown strategy kind at the end of the input is a syntax error`() {
        val error = assertFailsWith<QuiverException> { parseSource("strategy mine bogus") }
        assertContains(error.message ?: "", "Unknown strategy kind 'bogus'")
    }

    @Test
    fun `a strategy holds several rules`() {
        val strategy =
            parseSource(
                "strategy mine first => { (q0, a) -> (\$x, \$n) => { _ => 1 }; (q1, b) -> (\$x, \$n) => { _ => 2 } }",
            ).filterIsInstance<Decl.StrategyDecl>().single().strategy
        assertEquals(2, strategy.rules.size)
    }

    @Test
    fun `an fsm without a start state is a syntax error`() {
        val error = assertFailsWith<QuiverException> { parseSource("@fsm => { accepting => (q1); mapping => { } }") }
        assertContains(error.message ?: "", "FSM missing 'start'")
    }

    @Test
    fun `the first and all strategies are parsed`() {
        val strategies = parseSource("strategy mine first => { }\nstrategy other all => { }")
            .filterIsInstance<Decl.StrategyDecl>()
        assertEquals(2, strategies.size)
        assertEquals(StrategyKind.FIRST, strategies[0].strategy.kind)
        assertEquals(StrategyKind.ALL, strategies[1].strategy.kind)
    }

    @Test
    fun `the accepting states may be a parenthesized list`() {
        val fsm = parseSource("@fsm => { start => q0; accepting => (q1, q2); mapping => { } }")
            .filterIsInstance<Decl.Fsm>()
            .single()
        assertEquals(listOf("q1", "q2"), fsm.accepting)
    }

    @Test
    fun `an empty parenthesized accepting list is parsed`() {
        val fsm = parseSource("@fsm => { start => q0; accepting => (); mapping => { } }")
            .filterIsInstance<Decl.Fsm>()
            .single()
        assertEquals(emptyList(), fsm.accepting)
    }

    @Test
    fun `the accepting states may be a sequence of several states`() {
        val fsm = parseSource("@fsm => { start => q0; accepting => \$seq [ q1, q2 ]; mapping => { } }")
            .filterIsInstance<Decl.Fsm>()
            .single()
        assertEquals(listOf("q1", "q2"), fsm.accepting)
    }

    @Test
    fun `the mapping may hold several entries`() {
        val source =
            """
            @fsm => {
              start => q0
              accepting => (q2)
              mapping => {
                (q0, a) -> (q1, 0)
                (q1, b) -> (q2, 0)
              }
            }
            """.trimIndent()
        val fsm = parseSource(source).filterIsInstance<Decl.Fsm>().single()
        assertEquals(2, fsm.mapping.size)
    }

    @Test
    fun `a sequence literal is parsed`() {
        val decls = parseSource("x <- \$seq [ a, b ] => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern
        assertEquals(Expr.Seq(listOf(Expr.Sym("a"), Expr.Sym("b"))), (bind as Expr.Bind).expr)
    }

    @Test
    fun `a compound item of a sequence is parenthesized`() {
        val decls = parseSource("x <- \$seq [ (a, b), c ] => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern
        val expected = Expr.Seq(listOf(Expr.Pair(Expr.Sym("a"), Expr.Sym("b")), Expr.Sym("c")))
        assertEquals(expected, (bind as Expr.Bind).expr)
    }

    @Test
    fun `a bracketed sequence literal is parsed`() {
        val decls = parseSource("x <- [ a, b ] => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern
        assertEquals(Expr.Seq(listOf(Expr.Sym("a"), Expr.Sym("b"))), (bind as Expr.Bind).expr)
    }

    @Test
    fun `an empty sequence literal is parsed`() {
        val decls = parseSource("x <- \$seq [ ] => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern
        assertEquals(Expr.Seq(emptyList()), (bind as Expr.Bind).expr)
    }

    @Test
    fun `an integer followed by an addition is parsed`() {
        val decls = parseSource("x <- 2 + 3 => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern
        assertEquals(Expr.Plus(Expr.IntLit(2), Expr.IntLit(3)), (bind as Expr.Bind).expr)
    }

    @Test
    fun `the set constructor is parsed`() {
        val decls = parseSource("x <- set([ a, b ]) => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        val expected = Expr.Set(Expr.Seq(listOf(Expr.Sym("a"), Expr.Sym("b"))))
        assertEquals(expected, bind.expr)
    }

    @Test
    fun `the union of two sets is parsed`() {
        val decls = parseSource("x <- union(a, b) => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        val expected = Expr.Union(Expr.Sym("a"), Expr.Sym("b"))
        assertEquals(expected, bind.expr)
    }

    @Test
    fun `the intersection of two sets is parsed`() {
        val decls = parseSource("x <- intersection(a, b) => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        val expected = Expr.Intersection(Expr.Sym("a"), Expr.Sym("b"))
        assertEquals(expected, bind.expr)
    }

    @Test
    fun `the difference of two sets is parsed`() {
        val decls = parseSource("x <- difference(a, b) => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        val expected = Expr.Difference(Expr.Sym("a"), Expr.Sym("b"))
        assertEquals(expected, bind.expr)
    }

    @Test
    fun `the membership test is parsed`() {
        val decls = parseSource("x <- contains(a, b) => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        val expected = Expr.Contains(Expr.Sym("a"), Expr.Sym("b"))
        assertEquals(expected, bind.expr)
    }

    @Test
    fun `the arguments of a set primitive may be arithmetic expressions`() {
        val decls = parseSource("x <- union(\$a + 1, \$b) => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        val expected = Expr.Union(Expr.Plus(Expr.Sym("\$a"), Expr.IntLit(1)), Expr.Sym("\$b"))
        assertEquals(expected, bind.expr)
    }

    @Test
    fun `a set variable is parsed`() {
        val decls = parseSource("x <- %syms => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        assertEquals(Expr.Sym("%syms"), bind.expr)
    }

    @Test
    fun `a set variable may be bound`() {
        val decls = parseSource("%syms <- \$seq [ a ] => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        assertEquals("%syms", bind.varName)
        assertEquals(Expr.Seq(listOf(Expr.Sym("a"))), bind.expr)
    }

    @Test
    fun `a set primitive without its arguments is a syntax error`() {
        assertFailsWith<QuiverException> { parseSource("x <- union(a) => { _ => 1 }") }
    }

    @Test
    fun `an arithmetic expression is an element of a pair`() {
        // The grammar says the elements of a pair are terms, and a term may be
        // an arithmetic expression.
        val decls = parseSource("x <- (\$n + 1, \$m) => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        val expected =
            Expr.Pair(
                Expr.Plus(Expr.Sym("\$n"), Expr.IntLit(1)),
                Expr.Sym("\$m"),
            )
        assertEquals(expected, bind.expr)
    }

    @Test
    fun `the arithmetic operators are left associative`() {
        val decls = parseSource("x <- 1 + 2 + 3 => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        val expected = Expr.Plus(Expr.Plus(Expr.IntLit(1), Expr.IntLit(2)), Expr.IntLit(3))
        assertEquals(expected, bind.expr)
    }

    @Test
    fun `an arithmetic expression may be the operand of a function application`() {
        val decls = parseSource("x <- 1 + f(y) => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern as Expr.Bind
        val expected = Expr.Plus(Expr.IntLit(1), Expr.App(Expr.Sym("f"), Expr.Sym("y")))
        assertEquals(expected, bind.expr)
    }

    @Test
    fun `a function literal without a parameter is a syntax error`() {
        val error = assertFailsWith<QuiverException> { parseSource("x <- fn 1 => 1") }
        assertContains(error.message ?: "", "Expected parameter name in 'fn'")
    }

    @Test
    fun `a function literal at the end of the input is a syntax error`() {
        val error = assertFailsWith<QuiverException> { parseSource("x <- fn") }
        assertContains(error.message ?: "", "Expected parameter name in 'fn'")
    }

    @Test
    fun `a function literal may have several parameters`() {
        val decls = parseSource("x <- fn p, q => p => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern
        val function = (bind as Expr.Bind).expr as Expr.Fun
        assertEquals(listOf("p", "q"), function.params)
    }

    @Test
    fun `an unexpected token is a syntax error`() {
        val error = assertFailsWith<QuiverException> { parseSource("x <- ) => { }") }
        assertContains(error.message ?: "", "Unexpected token")
    }

    @Test
    fun `an incomplete expression is a syntax error`() {
        val error = assertFailsWith<QuiverException> { parseSource("x <- (") }
        assertContains(error.message ?: "", "Unexpected end of input")
    }

    @Test
    fun `a clause without an arrow is ignored`() {
        // The clause parser tolerates a statement that is not a clause: the
        // clause is skipped, and the rest of the body is parsed.
        val rule = parseSource("(q0, a) -> (\$x, \$n) => { garbage (q0, a) -> (\$x, \$n) => _ }")
            .filterIsInstance<Decl.RuleDecl>()
            .single()
            .rule
        assertEquals(1, rule.clauses.size)
    }

    @Test
    fun `a clause body that starts with a separator is ignored`() {
        // The lookahead probe fails on the separator, and the parser recovers
        // by skipping it.
        val rule = parseSource("(q0, a) -> (\$x, \$n) => { , (q0, a) -> (\$x, \$n) => _ }")
            .filterIsInstance<Decl.RuleDecl>()
            .single()
            .rule
        assertEquals(1, rule.clauses.size)
    }

    @Test
    fun `a clause body that starts with an arrow is ignored`() {
        // The lookahead probe fails on the arrow without consuming it, so the
        // parser skips it to guarantee its forward progress.
        val rule = parseSource("(q0, a) -> (\$x, \$n) => { => (q0, a) -> (\$x, \$n) => _ }")
            .filterIsInstance<Decl.RuleDecl>()
            .single()
            .rule
        assertEquals(1, rule.clauses.size)
    }

    @Test
    fun `a comment inside a rule body is ignored`() {
        val rule = parseSource("(q0, a) -> (\$x, \$n) => { # a comment\n (q0, a) -> (\$x, \$n) => _ }")
            .filterIsInstance<Decl.RuleDecl>()
            .single()
            .rule
        assertEquals(1, rule.clauses.size)
    }

    @Test
    fun `the clauses may be separated by commas and semicolons`() {
        val rule =
            parseSource("(q0, a) -> (\$x, \$n) => { (q0, a) -> (\$x, \$n) => _; (q0, a) -> (\$x, \$n) => _ }")
                .filterIsInstance<Decl.RuleDecl>()
                .single()
                .rule
        assertEquals(2, rule.clauses.size)
    }

    @Test
    fun `the lexer reports the position of the error`() {
        val error = assertFailsWith<QuiverException> { lex("a\nb < c") }
        assertTrue(error.line == 2, "Expected the error on line 2, got line ${error.line}")
    }

    @Test
    fun `a sweep clause is parsed`() {
        val rule = parseSource("(q0, a) -> (\$x, \$n) => { each (q0, \$s) -> (\$t, \$v) => { _ => 1 } }")
            .filterIsInstance<Decl.RuleDecl>()
            .single()
            .rule
        assertEquals(1, rule.clauses.size)
        val clause = rule.clauses.single()
        val pattern =
            Expr.Arrow(
                Expr.Pair(Expr.Sym("q0"), Expr.Sym("\$s")),
                Expr.Pair(Expr.Sym("\$t"), Expr.Sym("\$v")),
            )
        assertEquals(pattern, clause.left)
        val sweep = clause.right as ClauseRight.Sweep
        assertEquals(1, sweep.clauses.size)
    }

    @Test
    fun `a sweep clause may appear in a nested block`() {
        val rule =
            parseSource("(q0, a) -> (\$x, \$n) => { _ => { each (q0, \$s) -> (\$t, \$v) => { _ => 1 } } }")
                .filterIsInstance<Decl.RuleDecl>()
                .single()
                .rule
        val block = rule.clauses.single().right as ClauseRight.Block
        val sweep = block.clauses.single().right as ClauseRight.Sweep
        assertEquals(1, sweep.clauses.size)
    }

    @Test
    fun `a sweep clause without a block is a syntax error`() {
        val source = "(q0, a) -> (\$x, \$n) => { each (q0, \$s) -> (\$t, \$v) => 1 }"
        val error = assertFailsWith<QuiverException> { parseSource(source) }
        assertContains(error.message ?: "", "Expected LCurly")
    }

    @Test
    fun `the inspection primitives are parsed`() {
        val decls = parseSource("x <- targets-from(q0) => { _ => 1 }")
        val bind = (decls.single() as Decl.RuleDecl).rule.pattern
        assertEquals(Expr.TargetsFrom(Expr.Sym("q0")), (bind as Expr.Bind).expr)

        val symbols = parseSource("x <- symbols-from(q0) => { _ => 1 }")
        val symbolBind = (symbols.single() as Decl.RuleDecl).rule.pattern
        assertEquals(Expr.SymbolsFrom(Expr.Sym("q0")), (symbolBind as Expr.Bind).expr)

        val empty = parseSource("x <- is-empty(targets-from(q0)) => { _ => 1 }")
        val emptyBind = (empty.single() as Decl.RuleDecl).rule.pattern
        assertEquals(Expr.IsEmpty(Expr.TargetsFrom(Expr.Sym("q0"))), (emptyBind as Expr.Bind).expr)

        val size = parseSource("x <- size(targets-from(q0)) => { _ => 1 }")
        val sizeBind = (size.single() as Decl.RuleDecl).rule.pattern
        assertEquals(Expr.Size(Expr.TargetsFrom(Expr.Sym("q0"))), (sizeBind as Expr.Bind).expr)
    }
}
