// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import java.math.BigInteger

/**
 * A single token produced by the Quiver lexer.
 *
 * Every token carries its source location (line, column) so that parse errors
 * can be reported precisely.
 */
sealed class Token {
    /** Source line where this token starts (0 for structural singleton tokens). */
    open val line: Int
        get() = 0

    /** Source column where this token starts (0 for structural singleton tokens). */
    open val column: Int
        get() = 0

    /** The `(` token. */
    object Lparen : Token()

    /** The `)` token. */
    object Rparen : Token()

    /** The `{` token. */
    object LCurly : Token()

    /** The `}` token. */
    object RCurly : Token()

    /** The `[` token. */
    object LBracket : Token()

    /** The `]` token. */
    object RBracket : Token()

    /** The `,` token. */
    object Comma : Token()

    /** The `->` arrow token. */
    object Arrow : Token()

    /** The `=>` double-arrow token. */
    object DoubleArrow : Token()

    /** The `_` wildcard token. */
    object Underscore : Token()

    /** The `$seq` keyword token. */
    object DollarSeq : Token()

    /** The `|` token (used by the `|$seq|` special form). */
    object Pipe : Token()

    /** The `+` token. */
    object Plus : Token()

    /** The `-` token. */
    object Minus : Token()

    /** The `<-` assignment token. */
    object Assign : Token()

    /** The `;` separator token. */
    object Semicolon : Token()

    /** The `fn` keyword token. */
    object Fn : Token()

    /** The `when` keyword token (rule guards). */
    object When : Token()

    /** The `true` literal token. */
    object BoolTrue : Token()

    /** The `false` literal token. */
    object BoolFalse : Token()

    /** The `emit` keyword token. */
    object Emit : Token()

    /** The `include` keyword token. */
    object Include : Token()

    /** The `each` keyword token, which introduces a sweep clause. */
    object Each : Token()

    /** The `size` keyword token (the sequence-size primitive). */
    object Size : Token()

    /** The `set` keyword token (the set constructor). */
    object Set : Token()

    /** The `union` keyword token (the set-union primitive). */
    object Union : Token()

    /** The `intersection` keyword token (the set-intersection primitive). */
    object Intersection : Token()

    /** The `difference` keyword token (the set-difference primitive). */
    object Difference : Token()

    /** The `contains` keyword token (the set-membership primitive). */
    object Contains : Token()

    /** The `$state` adaptive primitive token. */
    data class StateRef(override val line: Int, override val column: Int) : Token()

    /** The `transitions-from` adaptive primitive token. */
    data class TransitionsFrom(override val line: Int, override val column: Int) : Token()

    /** The `targets-from` adaptive primitive token. */
    data class TargetsFrom(override val line: Int, override val column: Int) : Token()

    /** The `symbols-from` adaptive primitive token. */
    data class SymbolsFrom(override val line: Int, override val column: Int) : Token()

    /** The `is-empty` primitive token. */
    data class IsEmpty(override val line: Int, override val column: Int) : Token()

    /** The `clone-automaton` adaptive primitive token. */
    data class CloneAutomaton(override val line: Int, override val column: Int) : Token()

    /** The `fresh-state` generator token. */
    data class FreshState(override val line: Int, override val column: Int) : Token()

    /**
     * The `epsilon` keyword token: the internal marker of a transition.
     *
     * The marker is a reserved word: the symbol named `epsilon` is written
     * as a quoted string, and the two never collide.
     *
     * @property line the line where the keyword starts.
     * @property column the column where the keyword starts.
     */
    data class Epsilon(override val line: Int, override val column: Int) : Token()

    /**
     * A double-quoted string literal, used by `include` paths.
     *
     * @property text the literal's contents.
     */
    data class StringLit(val text: String, override val line: Int, override val column: Int) : Token()

    /**
     * An identifier: plain symbol, `$`-prefixed variable, or keyword-like name.
     *
     * @property name the identifier's name.
     */
    data class Ident(val name: String, override val line: Int, override val column: Int) : Token()

    /**
     * An integer literal, possibly negative.
     *
     * The literal is an arbitrary-precision integer: the digits of the source
     * are read as they are, without a machine-width limit.
     *
     * @property n the literal's value.
     */
    data class IntLit(val n: BigInteger, override val line: Int, override val column: Int) : Token()

    /**
     * A comment, from `#` to the end of the line.
     *
     * @property text the comment's text, without the leading `#`.
     */
    data class Comment(val text: String, override val line: Int, override val column: Int) : Token()
}

/** Reserved words recognized by the lexer, mapped to their token kinds. */
private val KEYWORDS = mapOf(
    "\$seq" to Token.DollarSeq,
    "fn" to Token.Fn,
    "when" to Token.When,
    "true" to Token.BoolTrue,
    "false" to Token.BoolFalse,
    "emit" to Token.Emit,
    "include" to Token.Include,
    "each" to Token.Each,
    "size" to Token.Size,
    "set" to Token.Set,
    "union" to Token.Union,
    "intersection" to Token.Intersection,
    "difference" to Token.Difference,
    "contains" to Token.Contains,
)

/**
 * The characters an identifier may start with: letters, and the five symbols
 * that begin variable names (`$` and `%`), include paths (`.` and `/`) and the
 * `$seq` positional marker (`@`) accepted for historical reasons.
 */
private const val IDENTIFIER_START_CHARS = "\$@./%"

/** The characters an identifier may continue with, beyond letters and digits. */
private const val IDENTIFIER_CONTINUATION_CHARS = "\$._/"

/** The `transitions-from` adaptive primitive, matched before the identifier scan. */
private const val TRANSITIONS_FROM_KEYWORD = "transitions-from"

/** The `targets-from` adaptive primitive, matched before the identifier scan. */
private const val TARGETS_FROM_KEYWORD = "targets-from"

/** The `symbols-from` adaptive primitive, matched before the identifier scan. */
private const val SYMBOLS_FROM_KEYWORD = "symbols-from"

/** The `is-empty` primitive, matched before the identifier scan. */
private const val IS_EMPTY_KEYWORD = "is-empty"

/** The `clone-automaton` adaptive primitive, matched before the identifier scan. */
private const val CLONE_AUTOMATON_KEYWORD = "clone-automaton"

/** The `fresh-state` generator, matched before the identifier scan. */
private const val FRESH_STATE_KEYWORD = "fresh-state"

/**
 * The hyphenated primitives, matched before the generic identifier scan: the
 * hyphen is not a character an identifier may contain, so these words would
 * otherwise be lexed as two identifiers.
 */
private val HYPHENATED_KEYWORDS =
    listOf(
        TRANSITIONS_FROM_KEYWORD,
        TARGETS_FROM_KEYWORD,
        SYMBOLS_FROM_KEYWORD,
        IS_EMPTY_KEYWORD,
        CLONE_AUTOMATON_KEYWORD,
        FRESH_STATE_KEYWORD,
    )

/** The message reported when the source ends in the middle of a term. */
private const val UNEXPECTED_END = "Unexpected end of input"

/**
 * The Quiver lexer: turns a source string into a token list, tracking line and
 * column so parse errors can point at their source location.
 *
 * Whitespace and comments (`#` ... end of line) are skipped; every token
 * carries its source location.
 */
private class Lexer(private val source: String) {
    /** The offset of the next unread character. */
    private var i = 0

    /** The line of the next unread character (1-based). */
    private var line = 1

    /** The column of the next unread character (1-based). */
    private var col = 1

    /** The line where the current token starts, captured before scanning it. */
    private var tokenLine = 1

    /** The column where the current token starts, captured before scanning it. */
    private var tokenColumn = 1

    /** The tokens lexed so far. */
    private val tokens = mutableListOf<Token>()

    /**
     * The single-character tokens: the character is the key, the token kind
     * the value.
     */
    private val singleCharTokens: Map<Char, Token> =
        mapOf(
            '(' to Token.Lparen,
            ')' to Token.Rparen,
            '{' to Token.LCurly,
            '}' to Token.RCurly,
            '[' to Token.LBracket,
            ']' to Token.RBracket,
            ',' to Token.Comma,
            ';' to Token.Semicolon,
            '|' to Token.Pipe,
            '+' to Token.Plus,
            '_' to Token.Underscore,
        )

    /** Lexes the whole source; returns the tokens in source order. */
    fun lex(): List<Token> {
        while (i < source.length) {
            tokenLine = line
            tokenColumn = col
            val c = source[i]
            when {
                c.isWhitespace() -> advance()
                c == '#' -> lexComment()
                c in singleCharTokens -> emit(singleCharTokens.getValue(c), 1)
                c == '<' -> lexAssign()
                c == '-' -> lexMinus()
                c == '=' -> lexDoubleArrow()
                c == '>' -> throw QuiverException(tokenLine, tokenColumn, "Unexpected character '>'")
                c == '"' -> lexString()
                else -> lexAtom()
            }
        }
        return tokens
    }

    /**
     * Advances one source character, updating the line/column tracking.
     */
    private fun advance() {
        if (i < source.length && source[i] == '\n') {
            line++
            col = 1
        } else {
            col++
        }
        i++
    }

    /** Returns the character [offset] positions ahead, or null past the end. */
    private fun peek(offset: Int): Char? = source.getOrNull(i + offset)

    /** Emits a token and advances over its [width] characters. */
    private fun emit(token: Token, width: Int) {
        tokens += token
        repeat(width) { advance() }
    }

    /** Lexes a comment, from `#` to the end of the line. */
    private fun lexComment() {
        val end = source.indexOf('\n', i)
        val text = if (end == -1) source.substring(i) else source.substring(i, end)
        emit(Token.Comment(text, tokenLine, tokenColumn), text.length)
        if (i < source.length) advance() // consume the '\n'
    }

    /** Lexes the `<-` assignment token; a bare `<` is an error. */
    private fun lexAssign() {
        if (peek(1) == '-') {
            emit(Token.Assign, 2)
        } else {
            throw QuiverException(tokenLine, tokenColumn, "Unexpected character '<'")
        }
    }

    /** Lexes the `->` arrow or the `-` minus; `-<` is not a valid token. */
    private fun lexMinus() {
        when (peek(1)) {
            '>' -> emit(Token.Arrow, 2)
            '<' -> throw QuiverException(tokenLine, tokenColumn, "Unexpected character '-': the assignment is '<-'")
            else -> emit(Token.Minus, 1)
        }
    }

    /** Lexes the `=>` double arrow; a bare `=` is an error. */
    private fun lexDoubleArrow() {
        if (peek(1) == '>') {
            emit(Token.DoubleArrow, 2)
        } else {
            throw QuiverException(tokenLine, tokenColumn, "Unexpected character '='")
        }
    }

    /** Lexes a double-quoted string literal, with `\n`, `\t`, `\"` escapes. */
    private fun lexString() {
        advance() // consume opening "
        val sb = StringBuilder()
        while (i < source.length && source[i] != '"') {
            if (source[i] == '\\' && i + 1 < source.length) {
                i++
                sb.append(
                    when (source[i]) {
                        'n' -> '\n'
                        't' -> '\t'
                        '"' -> '"'
                        '\\' -> '\\'
                        else -> source[i]
                    },
                )
            } else {
                sb.append(source[i])
            }
            i++
        }
        if (i >= source.length) {
            throw QuiverException(tokenLine, tokenColumn, "Unterminated string literal")
        }
        advance() // consume closing "
        tokens += Token.StringLit(sb.toString(), tokenLine, tokenColumn)
    }

    /** Lexes an integer, an identifier, or reports an unexpected character. */
    private fun lexAtom() {
        val c = source[i]
        when {
            c.isDigit() -> lexNumber()
            c.isLetter() || c in IDENTIFIER_START_CHARS -> lexIdentifier()
            else -> throw QuiverException(tokenLine, tokenColumn, "Unexpected character '$c'")
        }
    }

    /** Lexes an integer literal (possibly negative-looking digits). */
    private fun lexNumber() {
        val start = i
        advance() // consume first char
        while (i < source.length && source[i].isDigit()) {
            advance()
        }
        tokens += Token.IntLit(source.substring(start, i).toBigInteger(), tokenLine, tokenColumn)
    }

    /** Lexes a hyphenated primitive, a `$state` reference, or an identifier. */
    private fun lexIdentifier() {
        // Multi-char keywords with hyphens are matched before the generic scan.
        for (keyword in HYPHENATED_KEYWORDS) {
            if (source.startsWith(keyword, i)) {
                emit(keywordPrimitive(keyword), keyword.length)
                return
            }
        }
        val start = i
        advance() // consume first char
        while (i < source.length && (source[i].isLetterOrDigit() || source[i] in IDENTIFIER_CONTINUATION_CHARS)) {
            advance()
        }
        val text = source.substring(start, i)
        tokens +=
            when (text) {
                "\$state" -> Token.StateRef(tokenLine, tokenColumn)
                Label.INTERNAL_NAME -> Token.Epsilon(tokenLine, tokenColumn)
                else -> KEYWORDS[text] ?: Token.Ident(text, tokenLine, tokenColumn)
            }
    }

    /** Returns the token kind of a hyphenated primitive keyword. */
    private fun keywordPrimitive(keyword: String): Token = when (keyword) {
        TRANSITIONS_FROM_KEYWORD -> Token.TransitionsFrom(tokenLine, tokenColumn)
        TARGETS_FROM_KEYWORD -> Token.TargetsFrom(tokenLine, tokenColumn)
        SYMBOLS_FROM_KEYWORD -> Token.SymbolsFrom(tokenLine, tokenColumn)
        IS_EMPTY_KEYWORD -> Token.IsEmpty(tokenLine, tokenColumn)
        FRESH_STATE_KEYWORD -> Token.FreshState(tokenLine, tokenColumn)
        else -> Token.CloneAutomaton(tokenLine, tokenColumn)
    }
}

/**
 * Lexes a Quiver source string into a list of tokens.
 *
 * Whitespace and comments (# ... end of line) are skipped.
 * Every token carries its source location (line, column).
 */
fun lex(source: String): List<Token> = Lexer(source).lex()

/**
 * The Quiver parser. Produces a list of top-level declarations:
 * either an FSM header or a rewrite rule.
 */
sealed class Decl {
    /**
     * The `@fsm => { ... }` header: start state, accepting states, transition
     * mapping, and optional strategy label.
     *
     * The header declares the automaton and the settings of the run: the
     * `input` key is the default input of the run, which the command line
     * overrides.
     *
     * @property start the initial state name.
     * @property accepting the list of accepting state names.
     * @property mapping the declared transitions, each an [Expr.Arrow].
     * @property strategyLabel the optional strategy to activate, if declared.
     * @property input the default input of the run, or null when the program
     *   declares none.
     */
    data class Fsm(
        val start: String,
        val accepting: List<String>,
        val mapping: List<Expr.Arrow>,
        val strategyLabel: String? = null,
        val input: List<String>? = null,
    ) : Decl()

    /**
     * A top-level rewrite rule declaration.
     *
     * @property rule the declared rule.
     */
    data class RuleDecl(val rule: Rule) : Decl()

    /**
     * A `strategy` declaration grouping rules under a named rewriting strategy.
     *
     * @property strategy the declared strategy and its rules.
     */
    data class StrategyDecl(val strategy: Strategy) : Decl()

    /**
     * An `include "file.quiver"` directive naming another Quiver source file.
     *
     * @property path the path of the included file.
     */
    data class IncludeDecl(val path: String) : Decl()
}

/**
 * Recursive-descent parser over a token list produced by [lex].
 *
 * @property tokens the token stream to parse.
 */
class Parser(private val tokens: List<Token>) {
    /** Index of the next token to consume. */
    private var pos = 0

    /** Line of the most recently consumed token, used for error reporting. */
    private var currentLine = 1

    /** Column of the most recently consumed token, used for error reporting. */
    private var currentColumn = 1

    /** Returns the next token without consuming it; null at the end of the input. */
    private fun peek(): Token? = tokens.getOrNull(pos)

    /** The line of the next token, or of the last consumed one at the end of the input. */
    private fun peekLine(): Int = peek()?.line ?: currentLine

    /** The column of the next token, or of the last consumed one at the end of the input. */
    private fun peekColumn(): Int = peek()?.column ?: currentColumn

    /** Consumes and returns the next token, updating the error position. */
    private fun advance(): Token {
        val t = tokens[pos]
        currentLine = t.line
        currentColumn = t.column
        return tokens[pos++].also { pos = pos.coerceAtMost(tokens.size) }
    }

    /** Consumes the next token, requiring it to be of the given [kind]. */
    private fun expect(kind: Token): Token {
        val t = peek() ?: throw QuiverException(currentLine, currentColumn, UNEXPECTED_END)
        if (t::class == kind::class) return advance()
        throw QuiverException(t.line, t.column, "Expected ${kind::class.simpleName} but found $t at position $pos")
    }

    /** True when the next token is of the given [kind]. */
    private fun isToken(kind: Token): Boolean = peek()?.let { it::class == kind::class } ?: false

    /** Parses a full list of top-level declarations until end of input. */
    fun parseDecls(): List<Decl> {
        val decls = mutableListOf<Decl>()
        while (pos < tokens.size) {
            val t = peek() ?: break
            when {
                t is Token.Ident && t.name == "@fsm" -> {
                    advance() // @fsm
                    expect(Token.DoubleArrow)
                    expect(Token.LCurly) // {
                    decls += parseFsm()
                    expect(Token.RCurly) // }
                }

                t is Token.Comment -> advance()

                t is Token.Include -> {
                    advance() // include
                    val pathTok = expect(Token.StringLit("", 0, 0))
                    val path = (pathTok as Token.StringLit).text
                    decls += Decl.IncludeDecl(path)
                }

                t is Token.Ident && t.name == "strategy" -> {
                    advance() // strategy
                    decls += parseStrategy()
                }

                else -> {
                    decls += parseRule()
                }
            }
        }
        return decls
    }

    private companion object {
        /**
         * Probe tokens: [expect] and [isToken] match by class, so any
         * instance of the wanted kind works as the expected value.
         */
        private val IDENT_PROBE = Token.Ident("", 0, 0)
        private val INT_LIT_PROBE = Token.IntLit(BigInteger.ZERO, 0, 0)
    }

    /** Parses `strategy <label> <kind> [max N] => { rules }`. */
    private fun parseStrategy(): Decl.StrategyDecl {
        val label = (expect(IDENT_PROBE) as Token.Ident).name
        val kindStr = (expect(IDENT_PROBE) as Token.Ident).name
        val kind =
            when (kindStr) {
                "first" -> StrategyKind.FIRST
                "all" -> StrategyKind.ALL
                "repeat" -> StrategyKind.REPEAT
                else -> throw QuiverException(peekLine(), peekColumn(), "Unknown strategy kind '$kindStr'")
            }
        var maxIterations = DEFAULT_MAX_ITERATIONS
        if (isToken(IDENT_PROBE)) {
            val t = peek()
            if ((t as Token.Ident).name == "max") {
                advance()
                val n = (expect(INT_LIT_PROBE) as Token.IntLit).n
                // The cap is a loop bound, so it has to fit in a machine
                // integer; a larger literal is rejected rather than wrapped.
                if (n.signum() < 0 || n > BigInteger.valueOf(Int.MAX_VALUE.toLong())) {
                    throw QuiverException(peekLine(), peekColumn(), "The iteration cap must fit in a machine integer")
                }
                maxIterations = n.toInt()
            }
        }
        expect(Token.DoubleArrow) // =>
        expect(Token.LCurly) // {
        val rules = mutableListOf<Rule>()
        while (!isToken(Token.RCurly)) {
            rules += parseRule().rule
            if (isToken(Token.Semicolon) || isToken(Token.Comma)) advance()
        }
        expect(Token.RCurly) // }
        return Decl.StrategyDecl(Strategy(label, kind, maxIterations, rules))
    }

    /** Parses the FSM body keys: start, accepting, mapping, strategy, input. */
    private fun parseFsm(): Decl.Fsm {
        var start: String? = null
        var accepting: List<String> = emptyList()
        val mapping = mutableListOf<Expr.Arrow>()
        var strategyLabel: String? = null
        var input: List<String>? = null

        while (!isToken(Token.RCurly)) {
            val key = (expect(IDENT_PROBE) as Token.Ident).name
            expect(Token.DoubleArrow) // =>
            when (key) {
                "start" -> start = expectAtom().name
                "accepting" -> accepting = parseAccepting()
                "mapping" -> mapping += parseMapping()
                "strategy" -> strategyLabel = expectAtom().name
                "input" -> input = parseInput()
                else -> throw QuiverException(peekLine(), peekColumn(), "Unknown FSM key '$key'")
            }
            if (isToken(Token.Semicolon) || isToken(Token.Comma)) advance()
        }
        return Decl.Fsm(
            start ?: throw QuiverException(0, 0, "FSM missing 'start'"),
            accepting,
            mapping,
            strategyLabel,
            input,
        )
    }

    /**
     * Parses the `input` key: the default input of the run, a sequence of
     * symbols.
     *
     * The sequence is written `$seq [ ... ]` or `[ ... ]`, and every item is
     * one symbol: a bare identifier, or a quoted string when the name of the
     * symbol is not a plain one.
     */
    private fun parseInput(): List<String> {
        if (isToken(Token.DollarSeq)) advance() // $seq
        expect(Token.LBracket) // [
        val result = mutableListOf<String>()
        while (!isToken(Token.RBracket)) {
            result += parseInputSymbol()
            if (isToken(Token.Comma)) advance()
        }
        expect(Token.RBracket) // ]
        return result
    }

    /** Parses one symbol of the `input` key. */
    private fun parseInputSymbol(): String {
        val t = peek() ?: throw QuiverException(currentLine, currentColumn, UNEXPECTED_END)
        return when (t) {
            is Token.Ident -> {
                advance()
                t.name
            }

            is Token.StringLit -> {
                advance()
                t.text
            }

            else -> inputSymbolError(t)
        }
    }

    /**
     * Builds the error reported when the input of the program holds a term
     * that is not a symbol.
     *
     * @param t the offending token.
     * @return nothing: the function always fails.
     */
    private fun inputSymbolError(t: Token): Nothing = if (t is Token.Epsilon) {
        throw QuiverException(t.line, t.column, RESERVED_EPSILON)
    } else {
        throw QuiverException(t.line, t.column, "The input must be a list of symbols")
    }

    /**
     * Parses the `mapping => { ... }` transition arrows, consuming the braces.
     *
     * The entries are separated by whitespace: a comma after an entry is part
     * of the pair syntax of the entry's value, not a separator.
     */
    private fun parseMapping(): List<Expr.Arrow> {
        expect(Token.LCurly)
        val mapping = mutableListOf<Expr.Arrow>()
        while (!isToken(Token.RCurly)) {
            val entry = parseArrow()
            if (entry !is Expr.Arrow) {
                throw QuiverException(
                    peekLine(),
                    peekColumn(),
                    "Invalid FSM mapping: expected (state, symbol) -> (state, value)",
                )
            }
            mapping += entry
        }
        expect(Token.RCurly)
        return mapping
    }

    /** Parses the accepting-state list, in `$seq [...]` or `( ... )` form. */
    private fun parseAccepting(): List<String> =
        if (isToken(Token.DollarSeq)) parseAcceptingSeq() else parseAcceptingParenList()

    /** Parses the accepting-state list in `$seq [ ... ]` form. */
    private fun parseAcceptingSeq(): List<String> {
        advance() // $seq
        expect(Token.LBracket)
        val result = mutableListOf<String>()
        while (!isToken(Token.RBracket)) {
            result += expectAtom().name
            if (isToken(Token.Comma)) advance()
        }
        expect(Token.RBracket)
        return result
    }

    /** Parses the accepting-state list in parenthesized `( ... )` form. */
    private fun parseAcceptingParenList(): List<String> {
        expect(Token.Lparen)
        val result = mutableListOf<String>()
        if (!isToken(Token.Rparen)) {
            while (true) {
                result += expectAtom().name
                if (isToken(Token.Comma)) advance() else break
            }
        }
        expect(Token.Rparen)
        return result
    }

    /** Parses a single rule: `pattern [when guard] => { clauses }`. */
    private fun parseRule(): Decl.RuleDecl {
        val pattern = parseExpr()
        val guard = if (isToken(Token.When)) {
            advance() // when
            parseExpr()
        } else {
            null
        }
        expect(Token.DoubleArrow) // =>
        expect(Token.LCurly) // {
        val clauses = parseClausesUntilRCurly()
        return Decl.RuleDecl(Rule(pattern, guard, clauses))
    }

    /** Parses the right-hand side of a clause: a term, `_`, or a nested block. */
    private fun parseClauseRight(): ClauseRight = when {
        isToken(Token.Underscore) -> {
            advance()
            ClauseRight.Wildcard
        }

        isToken(Token.LCurly) -> {
            advance()
            ClauseRight.Block(parseClausesUntilRCurly())
        }

        else -> ClauseRight.Rhs(parseExpr())
    }

    /** Parses clauses until the closing `}`, tolerating separators and comments. */
    private fun parseClausesUntilRCurly(): List<Clause> {
        val clauses = mutableListOf<Clause>()
        var lastPos = -1
        while (!isToken(Token.RCurly)) {
            if (isToken(Token.Comment("", 0, 0))) {
                advance()
            } else if (isToken(Token.Each)) {
                // A sweep is a clause of its own: `each <pattern> => { clauses }`.
                advance() // each
                val pattern = parseExpr()
                expect(Token.DoubleArrow) // =>
                expect(Token.LCurly) // {
                clauses += Clause(pattern, ClauseRight.Sweep(parseClausesUntilRCurly()))
            } else {
                // Try to read a left-hand side; a real clause is followed by `=>`.
                // The lookahead probe may legitimately fail mid-expression: the
                // failure is expected, the token stream recovers by the forward
                // progress guarantee below.
                val left =
                    try {
                        parseExpr()
                    } catch (expectedException: QuiverException) {
                        null
                    }
                if (left != null && isToken(Token.DoubleArrow)) {
                    advance()
                    clauses += Clause(left, parseClauseRight())
                }
            }
            // Guarantee forward progress to avoid infinite loops on bare statements.
            if (pos == lastPos) advance()
            lastPos = pos
            if (isToken(Token.Semicolon) || isToken(Token.Comma)) advance()
        }
        expect(Token.RCurly)
        return clauses
    }

    /** Parses a full expression, starting at the arrow level. */
    private fun parseExpr(): Expr = parseArrow()

    /** Parses `pair (-> pair)?`, producing an [Expr.Arrow] when `->` is present. */
    private fun parseArrow(): Expr {
        val left = parsePair()
        if (isToken(Token.Arrow)) {
            advance()
            val right = parsePair()
            return Expr.Arrow(left, right)
        }
        return left
    }

    /**
     * Parses a term: an operand, the arithmetic operators over it, and the
     * comma that makes a pair out of two terms.
     *
     * The arithmetic operators are left-associative, so `a + b + c` is
     * `(a + b) + c`, and a term may be followed by a comma, so the elements of
     * a pair may be arithmetic expressions, as in `($n + 1, $m)`.
     */
    private fun parsePair(): Expr {
        val term = parseArithmetic()
        return if (isToken(Token.Comma)) {
            advance()
            Expr.Pair(term, parsePair())
        } else {
            term
        }
    }

    /**
     * Parses an arithmetic expression: an operand and the arithmetic operators
     * over it, left-associatively.
     */
    private fun parseArithmetic(): Expr {
        var term = parseOperand()
        while (true) {
            if (isToken(Token.Plus)) {
                advance()
                term = Expr.Plus(term, parseOperand())
            } else if (isToken(Token.Minus)) {
                advance()
                term = Expr.Minus(term, parseOperand())
            } else {
                break
            }
        }
        return term
    }

    /**
     * Parses an operand of an arithmetic expression: a primary, or the
     * application of a named function to an argument.
     */
    private fun parseOperand(): Expr {
        val primary = parsePrimary()
        // Function application: f(arg), only for a named function symbol,
        // which is unambiguous (a grouped/pair expression is never followed
        // by '(' in Quiver grammar).
        return if (isToken(Token.Lparen) && primary is Expr.Sym) {
            advance()
            val arg = parseExpr()
            expect(Token.Rparen)
            Expr.App(primary, arg)
        } else {
            primary
        }
    }

    /** Parses an atomic expression: literals, calls, groups, binds, and keywords. */
    private fun parsePrimary(): Expr = when (val t = peek()) {
        is Token.Fn -> parseFnLiteral()

        is Token.StateRef -> {
            advance() // $state
            Expr.StateRef
        }

        is Token.TransitionsFrom -> parseTransitionsFrom()

        is Token.TargetsFrom -> parseTargetsFrom()

        is Token.SymbolsFrom -> parseSymbolsFrom()

        is Token.IsEmpty -> parseIsEmpty()

        is Token.Size -> parseSize()

        is Token.Set -> parseSet()

        is Token.Union -> parseUnion()

        is Token.Intersection -> parseIntersection()

        is Token.Difference -> parseDifference()

        is Token.Contains -> parseContains()

        is Token.CloneAutomaton -> parseCloneAutomaton()

        is Token.FreshState -> parseFreshState()

        is Token.Epsilon -> {
            advance() // epsilon
            Expr.Epsilon
        }

        is Token.StringLit -> {
            advance() // "..."
            Expr.Sym(t.text)
        }

        is Token.Emit -> parseEmit()

        is Token.Lparen -> parseGroup()

        is Token.LBracket -> parseBracketSeq()

        is Token.Underscore -> {
            advance()
            Expr.Wildcard
        }

        is Token.DollarSeq -> parseDollarSeq()

        is Token.Plus -> {
            advance()
            Expr.Plus(Expr.IntLit(BigInteger.ZERO), parseOperand()) // unary plus, treat as identity
        }

        is Token.Minus -> {
            advance()
            Expr.Minus(Expr.IntLit(BigInteger.ZERO), parseOperand()) // unary minus
        }

        is Token.IntLit -> {
            advance()
            Expr.IntLit(t.n)
        }

        is Token.BoolTrue -> {
            advance()
            Expr.BoolLit(true)
        }

        is Token.BoolFalse -> {
            advance()
            Expr.BoolLit(false)
        }

        is Token.Ident -> parseIdent(t)

        null -> throw QuiverException(currentLine, currentColumn, UNEXPECTED_END)

        else -> throw QuiverException(t.line, t.column, "Unexpected token $t")
    }

    /** Parses `fn p1, p2 => body`, consuming the `fn` keyword. */
    private fun parseFnLiteral(): Expr {
        advance() // fn
        val params = mutableListOf<String>()
        while (!isToken(Token.DoubleArrow)) {
            val p = peek()
            if (p is Token.Ident) {
                params.add(p.name)
                advance()
            } else {
                val line = p?.line ?: currentLine
                val column = p?.column ?: currentColumn
                throw QuiverException(line, column, "Expected parameter name in 'fn'")
            }
            if (isToken(Token.Comma)) advance()
        }
        expect(Token.DoubleArrow) // =>
        val body = parseExpr()
        return Expr.Fun(params, body)
    }

    /** Parses `transitions-from(state)`, consuming the keyword. */
    private fun parseTransitionsFrom(): Expr {
        advance() // transitions-from
        expect(Token.Lparen) // (
        val inner = parseExpr()
        expect(Token.Rparen) // )
        return Expr.TransitionsFrom(inner)
    }

    /** Parses `targets-from(state)`, consuming the keyword. */
    private fun parseTargetsFrom(): Expr {
        advance() // targets-from
        expect(Token.Lparen) // (
        val inner = parseExpr()
        expect(Token.Rparen) // )
        return Expr.TargetsFrom(inner)
    }

    /** Parses `symbols-from(state)`, consuming the keyword. */
    private fun parseSymbolsFrom(): Expr {
        advance() // symbols-from
        expect(Token.Lparen) // (
        val inner = parseExpr()
        expect(Token.Rparen) // )
        return Expr.SymbolsFrom(inner)
    }

    /** Parses `is-empty(expr)`, consuming the keyword. */
    private fun parseIsEmpty(): Expr {
        advance() // is-empty
        expect(Token.Lparen) // (
        val inner = parseExpr()
        expect(Token.Rparen) // )
        return Expr.IsEmpty(inner)
    }

    /** Parses `size(expr)`, consuming the keyword. */
    private fun parseSize(): Expr {
        advance() // size
        expect(Token.Lparen) // (
        val inner = parseExpr()
        expect(Token.Rparen) // )
        return Expr.Size(inner)
    }

    /** Parses `set(seq)`, consuming the keyword. */
    private fun parseSet(): Expr {
        advance() // set
        expect(Token.Lparen) // (
        val inner = parseExpr()
        expect(Token.Rparen) // )
        return Expr.Set(inner)
    }

    /** Parses `union(left, right)`, consuming the keyword. */
    private fun parseUnion(): Expr {
        advance() // union
        val (left, right) = parseSetArguments()
        return Expr.Union(left, right)
    }

    /** Parses `intersection(left, right)`, consuming the keyword. */
    private fun parseIntersection(): Expr {
        advance() // intersection
        val (left, right) = parseSetArguments()
        return Expr.Intersection(left, right)
    }

    /** Parses `difference(left, right)`, consuming the keyword. */
    private fun parseDifference(): Expr {
        advance() // difference
        val (left, right) = parseSetArguments()
        return Expr.Difference(left, right)
    }

    /** Parses `contains(set, element)`, consuming the keyword. */
    private fun parseContains(): Expr {
        advance() // contains
        val (set, element) = parseSetArguments()
        return Expr.Contains(set, element)
    }

    /**
     * Parses the two arguments of a binary set primitive: `left, right`.
     *
     * The arguments are terms, and not full expressions: the comma separates
     * the two arguments, and not the elements of a pair.
     *
     * @return the two arguments.
     */
    private fun parseSetArguments(): Pair<Expr, Expr> {
        expect(Token.Lparen) // (
        val left = parseArithmetic()
        expect(Token.Comma) // ,
        val right = parseArithmetic()
        expect(Token.Rparen) // )
        return Pair(left, right)
    }

    /** Parses `clone-automaton()`, consuming the keyword. */
    private fun parseCloneAutomaton(): Expr {
        advance() // clone-automaton
        expect(Token.Lparen) // (
        expect(Token.Rparen) // )
        return Expr.CloneAutomaton
    }

    /** Parses `fresh-state()`, consuming the keyword. */
    private fun parseFreshState(): Expr {
        advance() // fresh-state
        expect(Token.Lparen) // (
        expect(Token.Rparen) // )
        return Expr.FreshState
    }

    /** Parses `emit(expr)`, consuming the keyword. */
    private fun parseEmit(): Expr {
        advance() // emit
        expect(Token.Lparen) // (
        val inner = parseExpr()
        expect(Token.Rparen) // )
        return Expr.Emit(inner)
    }

    /** Parses a parenthesized expression, consuming both parentheses. */
    private fun parseGroup(): Expr {
        advance()
        val e = parseExpr()
        expect(Token.Rparen)
        return e
    }

    /** Parses a `[a, b, c]` sequence literal, consuming both brackets. */
    private fun parseBracketSeq(): Expr {
        advance()
        val items = parseSeqItems()
        expect(Token.RBracket)
        return Expr.Seq(items)
    }

    /**
     * Parses the items of a bracketed sequence until the closing `]`,
     * which is not consumed.
     *
     * The items are atoms: the comma separates them, and a compound item
     * (a pair or an arrow) is therefore written between parentheses.
     */
    private fun parseSeqItems(): List<Expr> {
        val items = mutableListOf<Expr>()
        if (!isToken(Token.RBracket)) {
            while (true) {
                items += parsePrimary()
                if (isToken(Token.Comma)) advance() else break
            }
        }
        return items
    }

    /** Parses `$seq [ ... ]`, including the `|$seq|` special form. */
    private fun parseDollarSeq(): Expr {
        advance() // $seq
        expect(Token.LBracket)
        // The special form `|$seq|` denotes the symbol of the same name.
        if (isToken(Token.Pipe)) {
            advance() // |
            expect(Token.DollarSeq) // $seq
            expect(Token.Pipe) // |
            expect(Token.RBracket)
            return Expr.Sym("|\$seq|")
        }
        val items = parseSeqItems()
        expect(Token.RBracket)
        return Expr.Seq(items)
    }

    /**
     * Parses an identifier, optionally as a binding `name <- rhs`.
     *
     * The right-hand side of a binding is a full expression, so that a whole
     * transition can be bound: `t <- (q0, a) -> (q1, 0)`.
     */
    private fun parseIdent(t: Token.Ident): Expr {
        val name = t.name
        advance()
        if (peek() is Token.Assign) {
            advance() // <-
            val rhs = parseExpr()
            return Expr.Bind(name, rhs)
        }
        return Expr.Sym(name)
    }

    /**
     * Consumes an identifier token and wraps it as an [Expr.Sym].
     *
     * The reserved word `epsilon` is accepted here: the atom is a name, and
     * a state may be named `epsilon`. The walk over the parsed program
     * rejects the word where it may not appear.
     */
    private fun expectAtom(): Expr.Sym {
        val t = peek()
        if (t is Token.Epsilon) {
            advance()
            return Expr.Sym(Label.INTERNAL_NAME)
        }
        val token = expect(IDENT_PROBE)
        return Expr.Sym((token as Token.Ident).name)
    }
}

/** Convenience entry point: lexes and parses [source] into a list of [Decl]. */
fun parseSource(source: String): List<Decl> = Parser(lex(source)).parseDecls().let(::checkProgram)

/**
 * The message reported when the reserved word `epsilon` appears where it may
 * not.
 */
private const val RESERVED_EPSILON =
    "epsilon is reserved: it denotes the internal transition; write \"epsilon\" for the symbol"

/**
 * The message reported when the generator `fresh-state()` appears where it may
 * not.
 */
private const val RESERVED_FRESH_STATE =
    "fresh-state() is a generator: it mints a new state, so it may not be used as a pattern"

/**
 * The role a term plays in a program, which decides what the reserved word
 * `epsilon` may denote there.
 *
 * The marker is legal in the symbol slot of a transition, and nowhere else;
 * a state may be named `epsilon`, since a state name is never a symbol.
 */
private enum class Role {
    /** The state name of a transition. */
    STATE,

    /** The symbol of a transition: the slot where the internal marker lives. */
    SYMBOL,

    /** The value of a transition. */
    VALUE,

    /** The key of a transition: the pair `(state, symbol)`. */
    KEY,

    /** The target of a transition: the pair `(state, value)`. */
    TARGET,

    /** A pattern: the shape of a rule or of a clause. */
    PATTERN,

    /** Any other term. */
    TERM,
}

/**
 * Walks over the declarations of a program, checking the terms whose meaning
 * is restricted, and rewriting the places where the reserved word denotes a
 * state.
 *
 * Two terms have a restricted meaning: the reserved word `epsilon`, which
 * denotes the internal marker or a state, and the generator `fresh-state()`,
 * which mints a new state and is therefore no pattern.
 *
 * @param decls the declarations of the program.
 * @return the declarations, with the state names normalized.
 * @throws QuiverException when a restricted term appears where it may not.
 */
private fun checkProgram(decls: List<Decl>): List<Decl> = decls.map { decl ->
    when (decl) {
        is Decl.Fsm -> decl.copy(mapping = decl.mapping.map { walkTerms(it, Role.TERM) as Expr.Arrow })

        is Decl.RuleDecl -> decl.copy(rule = walkRuleTerms(decl.rule))

        is Decl.StrategyDecl ->
            decl.copy(strategy = decl.strategy.copy(rules = decl.strategy.rules.map(::walkRuleTerms)))

        is Decl.IncludeDecl -> decl
    }
}

/**
 * Walks over the terms of a rule, checking the restricted terms.
 *
 * @param rule the rule to walk over.
 * @return the rule, with the state names normalized.
 */
private fun walkRuleTerms(rule: Rule): Rule = rule.copy(
    pattern = walkTerms(rule.pattern, Role.PATTERN, inPattern = true),
    guard = rule.guard?.let { walkTerms(it, Role.TERM) },
    clauses = rule.clauses.map(::walkClauseTerms),
)

/**
 * Walks over the terms of a clause, checking the restricted terms.
 *
 * @param clause the clause to walk over.
 * @return the clause, with the state names normalized.
 */
private fun walkClauseTerms(clause: Clause): Clause = clause.copy(
    left = walkTerms(clause.left, Role.PATTERN, inPattern = true),
    right = walkClauseRightTerms(clause.right),
)

/**
 * Walks over the right-hand side of a clause, checking the restricted terms.
 *
 * @param right the right-hand side to walk over.
 * @return the right-hand side, with the state names normalized.
 */
private fun walkClauseRightTerms(right: ClauseRight): ClauseRight = when (right) {
    is ClauseRight.Rhs -> ClauseRight.Rhs(walkTerms(right.expr, Role.TERM))
    is ClauseRight.Block -> ClauseRight.Block(right.clauses.map(::walkClauseTerms))
    is ClauseRight.Sweep -> ClauseRight.Sweep(right.clauses.map(::walkClauseTerms))
    ClauseRight.Wildcard -> right
}

/**
 * The role the children of a term inherit from the role of the term itself.
 *
 * @param role the role of the term.
 * @return the role of its children.
 */
private fun childRole(role: Role): Role = if (role == Role.VALUE) Role.VALUE else Role.TERM

/**
 * Walks over a term, checking the terms whose meaning is restricted.
 *
 * An arrow is a transition, and its sides are the key and the target of that
 * transition: the key is the pair `(state, symbol)`, and the target is the
 * pair `(state, value)`. The reserved word denotes the internal marker in the
 * symbol slot, a state named `epsilon` in the state slot, and it is an error
 * everywhere else. The generator mints a new state, so it is an error anywhere
 * in a pattern: the flag keeps the context of the pattern alive through the
 * shapes a pattern may take, and the shapes that are evaluated rather than
 * matched, such as an arithmetic term or the body of a function, drop it.
 *
 * @param expr the term to walk over.
 * @param role the role the term plays.
 * @param inPattern whether the term is matched, that is, whether it sits in
 * the pattern of a rule or of a clause.
 * @return the term, with the state names normalized.
 * @throws QuiverException when a restricted term appears where it may not.
 */
private fun walkTerms(expr: Expr, role: Role, inPattern: Boolean = false): Expr = when (expr) {
    is Expr.Epsilon ->
        when (role) {
            Role.STATE -> Expr.Sym(Label.INTERNAL_NAME)
            Role.SYMBOL -> expr
            else -> throw QuiverException(0, 0, RESERVED_EPSILON)
        }

    is Expr.FreshState ->
        if (role == Role.PATTERN || inPattern) {
            throw QuiverException(0, 0, RESERVED_FRESH_STATE)
        } else {
            expr
        }

    is Expr.Pair -> walkPairTerms(expr, role, inPattern)

    is Expr.Arrow -> Expr.Arrow(
        walkTerms(expr.left, Role.KEY, inPattern),
        walkTerms(expr.right, Role.TARGET, inPattern),
    )

    is Expr.Bind -> Expr.Bind(expr.varName, walkTerms(expr.expr, role, inPattern))

    is Expr.Seq -> Expr.Seq(expr.items.map { walkTerms(it, childRole(role), inPattern) })

    is Expr.Plus -> Expr.Plus(
        walkTerms(expr.left, childRole(role)),
        walkTerms(expr.right, childRole(role)),
    )

    is Expr.Minus -> Expr.Minus(
        walkTerms(expr.left, childRole(role)),
        walkTerms(expr.right, childRole(role)),
    )

    is Expr.App -> Expr.App(
        walkTerms(expr.fn, childRole(role)),
        walkTerms(expr.arg, childRole(role)),
    )

    is Expr.Emit -> Expr.Emit(walkTerms(expr.expr, childRole(role)))

    is Expr.Fun -> Expr.Fun(expr.params, walkTerms(expr.body, Role.VALUE))

    else -> expr
}

/**
 * Walks over a pair, checking the terms whose meaning is restricted.
 *
 * The pair of pairs is the shape of a transition, and a pair in a pattern is
 * the key of a transition: the walk reads the shape the term has in the
 * place it occupies.
 *
 * @param expr the pair to walk over.
 * @param role the role the pair plays.
 * @param inPattern whether the pair is matched, that is, whether it sits in
 * the pattern of a rule or of a clause.
 * @return the pair, with the state names normalized.
 * @throws QuiverException when a restricted term appears where it may not.
 */
private fun walkPairTerms(expr: Expr.Pair, role: Role, inPattern: Boolean): Expr = when (role) {
    Role.KEY -> Expr.Pair(
        walkTerms(expr.left, Role.STATE, inPattern),
        walkTerms(expr.right, Role.SYMBOL, inPattern),
    )

    Role.TARGET -> Expr.Pair(
        walkTerms(expr.left, Role.STATE, inPattern),
        walkTerms(expr.right, Role.VALUE, inPattern),
    )

    Role.VALUE -> Expr.Pair(
        walkTerms(expr.left, Role.VALUE),
        walkTerms(expr.right, Role.VALUE),
    )

    Role.PATTERN ->
        if (expr.left is Expr.Pair && expr.right is Expr.Pair) {
            // The pair of pairs is the shape of a transition.
            Expr.Pair(
                walkTerms(expr.left, Role.KEY, true),
                walkTerms(expr.right, Role.TARGET, true),
            )
        } else {
            // A key of a transition: `(state, symbol)`.
            Expr.Pair(
                walkTerms(expr.left, Role.STATE, true),
                walkTerms(expr.right, Role.SYMBOL, true),
            )
        }

    else ->
        if (expr.left is Expr.Pair && expr.right is Expr.Pair) {
            // The pair of pairs is the shape of a transition.
            Expr.Pair(
                walkTerms(expr.left, Role.KEY, inPattern),
                walkTerms(expr.right, Role.TARGET, inPattern),
            )
        } else {
            Expr.Pair(
                walkTerms(expr.left, childRole(role), inPattern),
                walkTerms(expr.right, childRole(role), inPattern),
            )
        }
}
