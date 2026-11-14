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
}
