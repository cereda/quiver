// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import java.math.BigInteger

/**
 * A value manipulated by the Quiver term-rewriting interpreter.
 *
 * Quiver is a minimal, type-safe term rewriting language.
 *
 * Values:
 *   - Symbol: an atomic symbol such as `a`, `b`, `c`, `q0`, `x`, `_`.
 *   - Pair:   a binary tuple `(lhs, rhs)` used to denote transitions
 *             such as `(q0, a) -> (q0, 1)`.
 *   - List:   a sequence of values, used for the `$seq [ ... ]` construct.
 *   - Int:    an integer literal used in arithmetic such as `y + 1`.
 *   - Bool:   a boolean literal, result of recognition (`true` / `false`).
 */
sealed class Value {
    /**
     * The static type tags used by Quiver's type-safe rule application.
     *
     * Each tag corresponds to exactly one [Value] subtype; a rule pattern only
     * fires on subjects whose type is compatible with the pattern's head.
     */
    enum class Type { SYMBOL, INT, BOOL, PAIR, SEQ, FUNC, AUTOMATON }

    /** The static type of this value, used for type-safe rule application. */
    open val type: Type
        get() = when (this) {
            is Symbol -> Type.SYMBOL
            is IntVal -> Type.INT
            is BoolVal -> Type.BOOL
            is Pair -> Type.PAIR
            is Seq -> Type.SEQ
            is Func -> Type.FUNC
            is Automaton -> Type.AUTOMATON
        }

    /**
     * An atomic symbol such as `a`, `b`, `q0` or a state name.
     *
     * @property name the symbol's name.
     */
    data class Symbol(val name: String) : Value()

    /**
     * A binary tuple `(left, right)`, used to denote transitions such as `(q0, a)`.
     *
     * @property left the tuple's first component.
     * @property right the tuple's second component.
     */
    data class Pair(val left: Value, val right: Value) : Value()

    /**
     * A sequence of values, produced by the `$seq [ ... ]` construct.
     *
     * @property items the sequence's elements, in order.
     */
    data class Seq(val items: List<Value>) : Value()

    /**
     * An integer value, produced by integer literals and arithmetic.
     *
     * The value is an arbitrary-precision integer: the counters a program
     * carries are unbounded, and a run may grow them as far as it needs.
     *
     * @property n the integer's value.
     */
    data class IntVal(val n: BigInteger) : Value() {
        /**
         * Builds an integer value from a machine integer.
         *
         * @param n the integer's value.
         */
        constructor(n: Int) : this(BigInteger.valueOf(n.toLong()))
    }

    /**
     * A boolean value (`true` or `false`), typically the result of recognition.
     *
     * @property b the boolean's value.
     */
    data class BoolVal(val b: Boolean) : Value()

    /**
     * A closure: parameters, body, and the environment captured at definition.
     *
     * @property params the names of the closure's parameters, in order.
     * @property body the expression evaluated when the closure is applied.
     * @property env the environment captured when the closure was defined.
     */
    data class Func(val params: List<String>, val body: Expr, val env: Env) : Value()

    /**
     * A clone of the current automaton, produced by `clone-automaton()`.
     *
     * @property automaton the cloned automaton's state.
     */
    data class Automaton(val automaton: br.usp.poli.lta.cereda.quiver.Automaton) : Value()

    /**
     * True when this value is a [Symbol].
     */
    fun isSymbol(): Boolean = this is Symbol

    /**
     * Returns this value as a [Symbol].
     *
     * @throws QuiverException when the value is not a symbol.
     */
    fun symbol(): Symbol = this as? Symbol ?: throw typeError("symbol")

    /**
     * Returns this value as a [Pair].
     *
     * @throws QuiverException when the value is not a pair.
     */
    fun asPair(): Pair = this as? Pair ?: throw typeError("pair")

    /**
     * Returns this value's items as a [Seq].
     *
     * @throws QuiverException when the value is not a sequence.
     */
    fun asSeq(): List<Value> = (this as? Seq)?.items ?: throw typeError("sequence")

    /**
     * Returns this value as an [IntVal].
     *
     * @throws QuiverException when the value is not an integer.
     */
    fun asInt(): BigInteger = (this as? IntVal)?.n ?: throw typeError("integer")

    /**
     * Returns this value as a [BoolVal].
     *
     * @throws QuiverException when the value is not a boolean.
     */
    fun asBool(): Boolean = (this as? BoolVal)?.b ?: throw typeError("boolean")

    /**
     * Returns the wrapped automaton.
     *
     * @throws QuiverException when the value is not an automaton.
     */
    fun asAutomaton(): br.usp.poli.lta.cereda.quiver.Automaton =
        (this as? Automaton)?.automaton ?: throw typeError("automaton")

    /** Builds the error reported when a value has an unexpected type. */
    private fun typeError(expected: String): QuiverException =
        QuiverException(0, 0, "Expected a $expected value, got ${type.name.lowercase()}")
}

/**
 * Human-readable rendering of a [Value], used for the output of `emit`.
 *
 * @param v the value to render.
 */
internal fun valueToString(v: Value): String = when (v) {
    is Value.Symbol -> v.name

    is Value.Pair -> "(${valueToString(v.left)},${valueToString(v.right)})"

    is Value.Seq -> {
        val items = v.items.joinToString(",") { valueToString(it) }
        if (items.isEmpty()) "[seq]" else "[seq $items]"
    }

    is Value.IntVal -> v.n.toString()

    is Value.BoolVal -> if (v.b) "true" else "false"

    is Value.Func -> "<fn ${v.params.joinToString(",")} => ...>"

    is Value.Automaton -> "<automaton states=${v.automaton.states.size} transitions=${v.automaton.transitions.size}>"
}

/**
 * A Quiver rule: `pattern [when guard] => { clauses }`.
 *
 * The pattern is matched against the current term. On a successful match,
 * variables introduced in the pattern are bound. If a guard is present, it is
 * evaluated with those bindings; the rule fires only if the guard evaluates to
 * true. Clauses are executed in order. Each clause is either `pat => _`
 * (a removal/negative clause) or `_ => pat` (an insertion/positive clause).
 *
 * @property pattern the pattern matched against the current subject term.
 * @property guard the optional `when` guard evaluated after a successful match.
 * @property clauses the clauses executed in order when the rule fires.
 */
data class Rule(val pattern: Expr, val guard: Expr?, val clauses: List<Clause>)

/**
 * A clause within a rule body: `left => right`.
 *
 * Either side may be `_` (the wildcard, meaning "no term"). The right side may
 * also be a nested block of clauses `{ ... }`, introduced by `=> { ... }`.
 *
 * A clause may also be a **sweep**, `each <pattern> => { ... }`: its left side
 * is the pattern the sweep matches, and its right side is the block of clauses
 * the sweep runs once per matching transition.
 */
sealed class ClauseRight {
    /**
     * A term to insert or match, appearing on the right of `=>`.
     *
     * @property expr the term.
     */
    data class Rhs(val expr: Expr) : ClauseRight()

    /** The wildcard `_`, meaning "no term". */
    object Wildcard : ClauseRight()

    /**
     * A nested block of clauses, written `{ ... }`.
     *
     * @property clauses the nested clauses, executed in order.
     */
    data class Block(val clauses: List<Clause>) : ClauseRight()

    /**
     * A sweep, written `each <pattern> => { ... }`.
     *
     * The sweep matches the pattern of its clause against every transition of
     * the automaton, and runs the nested clauses once per matching transition.
     *
     * @property clauses the nested clauses, executed once per matching
     *   transition.
     */
    data class Sweep(val clauses: List<Clause>) : ClauseRight()
}

/**
 * A single clause `left => right` inside a rule body or nested block.
 *
 * @property left the term matched on the left of `=>`; for a sweep, the
 *   pattern the sweep matches against the transitions of the automaton.
 * @property right the action performed on the right of `=>`.
 */
data class Clause(val left: Expr, val right: ClauseRight)

/**
 * The kind of rewriting strategy used to select which rule fires on a subject.
 *
 * - FIRST:    try rules in source order; the first match fires once (default).
 * - ALL:      fire every rule whose pattern matches the subject.
 * - REPEAT:   keep re-applying the first matching rule until no rule matches or
 *             maxIterations is reached (a fixed-point / normal-form loop).
 */
enum class StrategyKind { FIRST, ALL, REPEAT }

/**
 * A named group of rewrite rules with an associated strategy kind.
 *
 * `strategy <label> <kind> [max <n>] => { rule1 rule2 ... }`
 *
 * @property label the strategy name, referenced by the FSM header.
 * @property kind the rule selection strategy.
 * @property maxIterations the iteration cap used by [StrategyKind.REPEAT].
 * @property rules the rules that this strategy may fire, in source order.
 */
data class Strategy(val label: String, val kind: StrategyKind, val maxIterations: Int, val rules: List<Rule>)

/**
 * An expression in the Quiver language.
 *
 * Expressions are terms that can be matched, rewritten, or evaluated.
 */
sealed class Expr {
    /**
     * A symbol reference: a literal name, a variable (`$x`), or `_`.
     *
     * @property name the symbol's name, without the `$` of a variable.
     */
    data class Sym(val name: String) : Expr()

    /**
     * A binary tuple `(left, right)`.
     *
     * @property left the tuple's first component.
     * @property right the tuple's second component.
     */
    data class Pair(val left: Expr, val right: Expr) : Expr()

    /**
     * A sequence literal `[a, b, c]` (also written `$seq [ ... ]`).
     *
     * @property items the sequence's elements, in order.
     */
    data class Seq(val items: List<Expr>) : Expr()

    /**
     * A transition arrow `(a, b) -> (c, d)`, matched structurally as a pair of pairs.
     *
     * @property left the arrow's source, the pair `(a, b)`.
     * @property right the arrow's target, the pair `(c, d)`.
     */
    data class Arrow(val left: Expr, val right: Expr) : Expr()

    /**
     * Integer addition, e.g. `y + 1`; evaluated rather than matched.
     *
     * @property left the left-hand operand.
     * @property right the right-hand operand.
     */
    data class Plus(val left: Expr, val right: Expr) : Expr()

    /**
     * An integer literal such as `1`.
     *
     * The literal is an arbitrary-precision integer, so a program may write a
     * counter's initial value with as many digits as it needs.
     *
     * @property n the literal's value.
     */
    data class IntLit(val n: BigInteger) : Expr() {
        /**
         * Builds an integer literal from a machine integer.
         *
         * @param n the literal's value.
         */
        constructor(n: Int) : this(BigInteger.valueOf(n.toLong()))
    }

    /**
     * Integer subtraction, e.g. `y - 1`; evaluated rather than matched.
     *
     * @property left the left-hand operand.
     * @property right the right-hand operand.
     */
    data class Minus(val left: Expr, val right: Expr) : Expr()

    /**
     * A binding `x <- y`, evaluating `y` and making it available as `x`.
     *
     * @property varName the name the value is bound to.
     * @property expr the expression whose value is bound.
     */
    data class Bind(val varName: String, val expr: Expr) : Expr()

    /** The wildcard `_`, matching any term. */
    object Wildcard : Expr()

    /**
     * Boolean literal: `true` or `false`.
     *
     * @property b the literal's value.
     */
    data class BoolLit(val b: Boolean) : Expr()

    /**
     * Function abstraction: `fn p1, p2 => body`.
     *
     * @property params the names of the function's parameters, in order.
     * @property body the expression evaluated when the function is applied.
     */
    data class Fun(val params: List<String>, val body: Expr) : Expr()

    /**
     * Function application: `fn(arg)`.
     *
     * @property fn the function being applied.
     * @property arg the argument of the application.
     */
    data class App(val fn: Expr, val arg: Expr) : Expr()

    /**
     * Output: `emit(expr)` prints the value of expr to stdout during evaluation.
     *
     * @property expr the expression whose value is printed.
     */
    data class Emit(val expr: Expr) : Expr()

    /** Current state symbol: `$state`. */
    object StateRef : Expr()

    /**
     * All transitions from a state: `transitions-from(state)` returns a Seq of
     * (symbol, to) pairs.
     *
     * @property state the expression denoting the state whose transitions are
     *   returned.
     */
    data class TransitionsFrom(val state: Expr) : Expr()

    /**
     * The distinct targets of the transitions leaving a state:
     * `targets-from(state)` returns a Seq of symbols.
     *
     * @property state the expression denoting the state whose targets are
     *   returned.
     */
    data class TargetsFrom(val state: Expr) : Expr()

    /**
     * The distinct symbols of the transitions leaving a state:
     * `symbols-from(state)` returns a Seq of symbols.
     *
     * @property state the expression denoting the state whose symbols are
     *   returned.
     */
    data class SymbolsFrom(val state: Expr) : Expr()

    /**
     * The emptiness test: `is-empty(expr)` returns whether the sequence
     * denoted by `expr` has no elements.
     *
     * @property expr the expression denoting the sequence.
     */
    data class IsEmpty(val expr: Expr) : Expr()

    /**
     * The size of a sequence: `size(expr)` returns the number of elements of
     * the sequence denoted by `expr`.
     *
     * @property expr the expression denoting the sequence.
     */
    data class Size(val expr: Expr) : Expr()

    /** Clone the current automaton: `clone-automaton()`. */
    object CloneAutomaton : Expr()
}

/**
 * The runtime environment: a mapping from variable names to values.
 */
@Suppress("ktlint:standard:kdoc")
typealias Env = Map<String, Value>

/**
 * The adaptive automaton state: set of states, final states and transitions.
 *
 * Transitions are stored as a flat list; [addTransition] appends a new
 * transition, so several transitions may leave the same (from, symbol) pair
 * and the automaton can be non-deterministic.
 *
 * @property start the initial state name.
 * @property states the set of all known state names (grown by [addTransition]).
 * @property accepting the set of accepting (final) state names.
 * @property transitions the current transition list, in insertion order.
 */
data class Automaton(
    val start: String,
    val states: MutableSet<String>,
    val accepting: MutableSet<String>,
    val transitions: MutableList<Transition>,
) {
    /**
     * A single transition `(from, symbol) -> (to, value)`.
     *
     * @property from the source state name.
     * @property symbol the input symbol consumed by this transition.
     * @property to the target state name.
     * @property value an auxiliary payload carried by the transition
     *   (defaults to 0); clauses may inspect and rewrite it.
     */
    data class Transition(val from: String, val symbol: String, val to: String, val value: Value = Value.IntVal(0))

    /**
     * Appends a transition, registering both endpoints as states. Multiple
     * transitions may share the same (from, symbol) key, which makes the
     * automaton non-deterministic.
     */
    fun addTransition(from: String, symbol: String, to: String, value: Value = Value.IntVal(0)) {
        states += from
        states += to
        transitions += Transition(from, symbol, to, value)
    }

    /** Deep-copies this automaton: new state sets and a new transition list. */
    fun clone(): Automaton = Automaton(
        start,
        states.toMutableSet(),
        accepting.toMutableSet(),
        transitions.toMutableList(),
    )

    /** Renders the automaton in a debug-friendly notation. */
    override fun toString(): String {
        val acc = accepting.sorted().joinToString(",", prefix = "{", postfix = "}")
        val tr = transitions.joinToString("; ") { "(${it.from}, ${it.symbol}) -> ${it.to}" }
        return "Automaton(states=$states, accepting=$acc, transitions=[$tr])"
    }
}

/**
 * Parse error with source location information.
 *
 * @property line the 1-based source line where the error occurred (0 when unknown).
 * @property column the 1-based source column where the error occurred (0 when unknown).
 */
class QuiverException(val line: Int = 0, val column: Int = 0, message: String) :
    Exception("line $line:col $column: $message")

/**
 * Thrown when the rewrite step counter exceeds the configured fuel budget,
 * preventing non-termination in Turing-complete Quiver programs.
 */
class FuelExhaustedException(message: String) : Exception(message)
