// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import java.io.File
import java.io.IOException

/**
 * The default fuel budget: the maximum number of steps a run may take before
 * a [FuelExhaustedException] aborts it.
 */
const val DEFAULT_MAX_STEPS = 10_000

/** The default iteration cap of a [StrategyKind.REPEAT] strategy. */
const val DEFAULT_MAX_ITERATIONS = 100

/**
 * Matches a pattern [expr] against a [subject], producing an [Env] of variable
 * bindings, or null when the match fails.
 *
 * Literal symbols match exactly; `$`-prefixed names are variables (bound on
 * first occurrence, occurrence-checked afterwards); `_` and [Expr.Wildcard]
 * match anything. Pairs, sequences and arrows match structurally; arithmetic
 * and other evaluated forms never match.
 *
 * @param expr the pattern expression.
 * @param subject the value being matched.
 * @param env the bindings accumulated so far.
 * @return the extended environment, or null when the match fails.
 */
fun matchTerm(expr: Expr, subject: Value, env: Env): Env? = when (expr) {
    is Expr.Sym -> matchSym(expr.name, subject, env)

    is Expr.Pair -> {
        if (subject !is Value.Pair) {
            null
        } else {
            matchTerm(expr.left, subject.left, env)?.let { env2 ->
                matchTerm(expr.right, subject.right, env2)
            }
        }
    }

    is Expr.Seq -> {
        if (subject !is Value.Seq) {
            null
        } else {
            matchList(expr.items, subject.asSeq(), env)
        }
    }

    is Expr.Arrow -> {
        if (subject !is Value.Pair) {
            null
        } else {
            matchTerm(expr.left, subject.left, env)?.let { env2 ->
                matchTerm(expr.right, subject.right, env2)
            }
        }
    }

    is Expr.Plus -> {
        // Arithmetic expressions are not matched structurally; they evaluate.
        null
    }

    is Expr.Minus -> null

    is Expr.Bind -> {
        // A binding `x <- t` matches when `t` matches, and binds the matched
        // value to the variable `$x`.
        matchTerm(expr.expr, subject, env)?.let { env2 ->
            env2 + (bindingKey(expr.varName) to subject)
        }
    }

    is Expr.IntLit -> {
        if (subject is Value.IntVal && subject.n == expr.n) env else null
    }

    is Expr.BoolLit -> {
        if (subject is Value.BoolVal && subject.b == expr.b) env else null
    }

    is Expr.Fun -> null

    is Expr.App -> null

    is Expr.Emit -> null

    is Expr.StateRef -> null

    is Expr.TransitionsFrom -> null

    is Expr.CloneAutomaton -> null

    is Expr.Wildcard -> env
}

/**
 * The name a binding introduces in the environment.
 *
 * Variables are the `$`-prefixed names, so the binding `x <- t` (as well as
 * the explicit `$x <- t`) introduces the variable `$x`.
 */
private fun bindingKey(varName: String): String = if (varName.startsWith("$")) varName else "$$varName"

/**
 * Matches a symbol-name pattern against a subject.
 *
 * `_` is the wildcard; `$`-prefixed names are variables (bound on first
 * occurrence, occurrence-checked afterwards); any other name must match a
 * literal symbol exactly.
 *
 * @return the extended environment, or null when the match fails.
 */
private fun matchSym(name: String, subject: Value, env: Env): Env? = when {
    name == "_" -> env

    name.startsWith("$") -> {
        // variable: bind / occurs-check
        val bound = env[name]
        if (bound == null) {
            env + (name to subject)
        } else if (bound == subject) {
            env
        } else {
            null
        }
    }

    // literal symbol: match exactly
    else -> if (subject is Value.Symbol && subject.name == name) env else null
}

/** Matches [patterns] elementwise against [subjects]; see [matchTerm]. */
private fun matchList(patterns: List<Expr>, subjects: List<Value>, env: Env): Env? {
    if (patterns.size != subjects.size) return null
    var acc = env
    for (i in patterns.indices) {
        acc = matchTerm(patterns[i], subjects[i], acc) ?: return null
    }
    return acc
}

/**
 * Evaluates an expression in an environment to a [Value].
 * Arithmetic and variable references are resolved here.
 *
 * @param expr the expression to evaluate.
 * @param env the variable bindings in scope.
 * @param currentState the automaton's current state, returned by `$state`.
 * @param automaton the automaton being rewritten, consulted by the adaptive
 *   primitives (`transitions-from`, `clone-automaton`).
 */
fun evalExpr(expr: Expr, env: Env, currentState: String, automaton: Automaton): Value = when (expr) {
    is Expr.Sym -> {
        val name = expr.name
        env[name] ?: Value.Symbol(name)
    }

    is Expr.Pair -> Value.Pair(
        evalExpr(expr.left, env, currentState, automaton),
        evalExpr(expr.right, env, currentState, automaton),
    )

    is Expr.Seq -> Value.Seq(expr.items.map { e -> evalExpr(e, env, currentState, automaton) })

    is Expr.Arrow -> Value.Pair(
        evalExpr(expr.left, env, currentState, automaton),
        evalExpr(expr.right, env, currentState, automaton),
    )

    is Expr.IntLit -> Value.IntVal(expr.n)

    is Expr.BoolLit -> Value.BoolVal(expr.b)

    is Expr.Plus -> {
        val l = evalExpr(expr.left, env, currentState, automaton)
        val r = evalExpr(expr.right, env, currentState, automaton)
        Value.IntVal(l.asInt() + r.asInt())
    }

    is Expr.Minus -> {
        val l = evalExpr(expr.left, env, currentState, automaton)
        val r = evalExpr(expr.right, env, currentState, automaton)
        Value.IntVal(l.asInt() - r.asInt())
    }

    is Expr.Fun -> Value.Func(expr.params, expr.body, env)

    is Expr.App -> {
        val f = evalExpr(expr.fn, env, currentState, automaton)
        if (f !is Value.Func) throw QuiverException(0, 0, "Cannot apply a non-function value")
        evalApplication(f, expr.arg, env, currentState, automaton)
    }

    is Expr.Emit -> {
        val v = evalExpr(expr.expr, env, currentState, automaton)
        System.out.println(valueToString(v))
        v
    }

    is Expr.Bind ->
        // A binding introduces a variable in a pattern; the evaluator has no
        // environment to extend, so a binding in a value position is an error.
        throw QuiverException(0, 0, "A binding is only allowed in a pattern")

    is Expr.Wildcard -> Value.Symbol("_")

    is Expr.StateRef -> Value.Symbol(currentState)

    is Expr.TransitionsFrom -> {
        val stateVal = evalExpr(expr.state, env, currentState, automaton)
        if (stateVal !is Value.Symbol) throw QuiverException(0, 0, "transitions-from expects a state symbol")
        val stateName = stateVal.name
        val trans = automaton.transitions.filter { it.from == stateName }
        Value.Seq(
            trans.map { t ->
                Value.Pair(Value.Symbol(t.symbol), Value.Symbol(t.to))
            },
        )
    }

    is Expr.CloneAutomaton -> {
        Value.Automaton(automaton.clone())
    }
}

/** Applies a [Value.Func] to one argument: binds the parameter and evaluates the body. */
private fun evalApplication(
    f: Value.Func,
    argExpr: Expr,
    env: Env,
    currentState: String,
    automaton: Automaton,
): Value {
    val arg = evalExpr(argExpr, env, currentState, automaton)
    val callEnv = f.env + f.params.zip(listOf(arg)).toMap()
    return evalExpr(f.body, callEnv, currentState, automaton)
}

/**
 * The outcome of a single [step] of the adaptive automaton.
 *
 * @property nextState the state reached after this step, or null when no
 *   transition exists for the (state, symbol) pair (the input is rejected).
 * @property accepted true when recognition has finished and accepted the
 *   input; reserved for interactive stepping.
 */
class StepResult(val nextState: String?, val accepted: Boolean = false)

/**
 * The fuel budget of a run: the number of steps it may still take.
 *
 * A step is a transition the run follows: the deterministic recognizer
 * follows at most one transition per input symbol, and the non-deterministic
 * one follows one per path, so a symbol may account for several steps there.
 *
 * The counter is shared by every step of a run, so that the budget spans the
 * whole recognition instead of a single step.
 *
 * @property budget the total number of steps the run may take.
 */
class Fuel(val budget: Int) {
    /** The number of steps still available. */
    var remaining: Int = budget
        private set

    /** The number of steps taken so far. */
    var taken: Int = 0
        private set

    /**
     * Consumes one unit of fuel.
     *
     * @throws FuelExhaustedException when the budget is exhausted.
     */
    fun consume() {
        if (remaining <= 0) {
            throw FuelExhaustedException(
                "Step budget exhausted after $taken steps; " +
                    "possible non-termination. Increase --max-steps or check for infinite loops.",
            )
        }
        remaining--
        taken++
    }
}

/**
 * A single step of the adaptive automaton: given the current state, the next
 * input symbol, and the automaton, finds a matching transition, fires any
 * applicable rewrite rule, applies its clauses (insert/remove transitions),
 * and returns the next state (or null if no transition exists).
 *
 * @param state the current automaton state.
 * @param symbol the next input symbol to consume.
 * @param automaton the automaton to rewrite (mutated by fired rules).
 * @param strategy the active rewriting strategy, or null for no rules.
 * @param trace when true, prints a step trace to stderr.
 * @param fuel the run's fuel budget; every step consumes one unit, and an
 *   exhausted budget raises a [FuelExhaustedException].
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 */
fun step(
    state: String,
    symbol: String,
    automaton: Automaton,
    strategy: Strategy?,
    trace: Boolean = false,
    fuel: Fuel = Fuel(Int.MAX_VALUE),
    ruleIndex: Map<String, List<Rule>>? = null,
): StepResult {
    // Find a transition (state, symbol) -> (to, value)
    val t = automaton.transitions.find { it.from == state && it.symbol == symbol }
    if (t == null) {
        if (trace) System.err.println("trace step state=$state symbol=$symbol transition=NULL (no transition)")
        return StepResult(null)
    }

    // The subject is the full transition: ((from, symbol), (to, value))
    val subject = Value.Pair(
        Value.Pair(Value.Symbol(t.from), Value.Symbol(t.symbol)),
        Value.Pair(Value.Symbol(t.to), t.value),
    )

    if (trace) {
        System.err.println(
            "trace step state=$state symbol=$symbol " +
                "transition=(${t.from},${t.symbol})->(${t.to},${valueToString(t.value)})",
        )
    }

    val allRules = strategy?.rules ?: emptyList()
    // Use rule index to only try rules whose pattern head type matches the subject head type.
    val subjectKey = subjectHeadKey(subject)
    val candidateRules = ruleIndex?.get(subjectKey) ?: allRules
    val kind = strategy?.kind ?: StrategyKind.FIRST

    // Consume one unit of fuel per step (per input symbol processed). The
    // repetitions of a `repeat` strategy are bounded by the strategy's own
    // iteration limit, not by the fuel.
    fuel.consume()

    // Each strategy kind resolves the (possibly clause-rewritten) target state;
    // null means no rule fired and the original transition applies.
    val firedTo: String? =
        when (kind) {
            StrategyKind.FIRST ->
                // Try rules in source order; the first match fires once.
                candidateRules
                    .firstNotNullOfOrNull { tryFireRule(it, subject, state, automaton, trace) }
                    ?.let { reresolveTo(automaton, state, symbol, t.to) }

            StrategyKind.ALL -> {
                // Fire every rule whose pattern matches the subject.
                val fired = candidateRules.mapNotNull { tryFireRule(it, subject, state, automaton, trace) }
                if (fired.isNotEmpty()) reresolveTo(automaton, state, symbol, t.to) else null
            }

            StrategyKind.REPEAT -> {
                // Re-apply the first matching rule until no rule matches or the
                // iteration limit is reached (a fixed-point / normal-form loop).
                repeatFire(
                    candidateRules,
                    subject,
                    state,
                    automaton,
                    trace,
                    strategy?.maxIterations ?: DEFAULT_MAX_ITERATIONS,
                )
                reresolveTo(automaton, state, symbol, t.to)
            }
        }

    // No rule fired; still take the original transition.
    if (firedTo == null && trace) System.err.println("trace   no rule fired")
    return StepResult(firedTo ?: t.to)
}

/**
 * Re-applies the first matching rule until no rule matches or the iteration
 * limit is reached: a fixed-point / normal-form loop.
 */
private fun repeatFire(
    candidateRules: List<Rule>,
    subject: Value,
    state: String,
    automaton: Automaton,
    trace: Boolean,
    limit: Int,
) {
    var iterations = 0
    while (iterations < limit) {
        val fired = candidateRules.firstNotNullOfOrNull { tryFireRule(it, subject, state, automaton, trace) }
        if (fired == null) break
        iterations++
    }
}

/**
 * Attempts to fire one rule against a subject: checks the pattern's type,
 * matches it, evaluates the guard, and applies the clauses on success.
 *
 * @param rule the rule candidate.
 * @param subject the subject term the rule is tried against.
 * @param state the automaton's current state, for `$state` and the trace.
 * @param automaton the automaton the clauses mutate.
 * @param trace when true, prints the firing to stderr.
 * @return the rule's bindings when the rule fired, null otherwise.
 */
private fun tryFireRule(rule: Rule, subject: Value, state: String, automaton: Automaton, trace: Boolean): Env? {
    if (!typeCompatible(rule.pattern, subject)) return null
    val env = matchTerm(rule.pattern, subject, emptyMap()) ?: return null
    if (!guardPasses(rule.guard, env, state, automaton)) return null
    applyClauses(rule.clauses, subject, env, automaton, state, trace)
    if (trace) System.err.println("trace   rule fired: ${exprToString(rule.pattern)}")
    return env
}

/**
 * Re-resolves the transition leaving (state, symbol): the fired clauses may
 * have replaced or removed it.
 *
 * @return the transition's target state, or [fallback] when none exists.
 */
private fun reresolveTo(automaton: Automaton, state: String, symbol: String, fallback: String): String =
    automaton.transitions.find { it.from == state && it.symbol == symbol }?.to ?: fallback

/**
 * Evaluates an optional guard expression with the given environment.
 * Returns true if there is no guard. Otherwise, evaluates the guard and
 * returns its truthiness: [Value.BoolVal] follows its boolean, [Value.IntVal]
 * is false only when zero, and all other values are truthy.
 *
 * @param guard the guard expression, or null for "always passes".
 * @param env the bindings from the rule's pattern match.
 * @param currentState the automaton's current state, for `$state`.
 * @param automaton the automaton, for the adaptive primitives.
 */
private fun guardPasses(guard: Expr?, env: Env, currentState: String, automaton: Automaton): Boolean {
    if (guard == null) return true
    val result = evalExpr(guard, env, currentState, automaton)
    return when (result) {
        is Value.BoolVal -> result.b
        is Value.IntVal -> result.n != 0
        else -> true
    }
}

/**
 * Executes a list of clauses against a subject, threading an environment and
 * mutating the automaton (insert/remove transitions) as a side effect.
 *
 * @param clauses the clauses to execute, in order.
 * @param subject the subject term the rule matched.
 * @param env the bindings from the rule's pattern match.
 * @param automaton the automaton to mutate.
 * @param currentState the automaton's current state, for `$state`.
 * @param trace when true, prints each insert/remove action to stderr.
 */
private fun applyClauses(
    clauses: List<Clause>,
    subject: Value,
    env: Env,
    automaton: Automaton,
    currentState: String,
    trace: Boolean,
) {
    var currentEnv = env
    for (clause in clauses) {
        currentEnv = applyClause(clause, subject, currentEnv, automaton, currentState, trace)
    }
}

/**
 * Executes one clause: refines the environment by matching its left pattern,
 * then performs its right-hand side action against the automaton.
 *
 * A clause whose left pattern does not match the subject is skipped, and the
 * environment is left untouched: this is the discipline of term rewriting,
 * where a rule body is a sequence of guarded equations.
 *
 * @return the environment refined by the clause's left pattern.
 */
private fun applyClause(
    clause: Clause,
    subject: Value,
    env: Env,
    automaton: Automaton,
    currentState: String,
    trace: Boolean,
): Env {
    // Refine the environment by matching the clause's left pattern; a clause
    // that does not match the subject does not apply.
    val currentEnv = matchTerm(clause.left, subject, env) ?: return env
    when (val cr = clause.right) {
        is ClauseRight.Wildcard ->
            // left => _ : remove the transition described by the left side.
            removeTransition(
                transitionKey(evalExpr(clause.left, currentEnv, currentState, automaton)),
                automaton,
                trace,
            )

        is ClauseRight.Rhs ->
            // => expr or left => expr : insert/update a transition.
            insertTransition(
                transition(evalExpr(cr.expr, currentEnv, currentState, automaton)),
                automaton,
                trace,
            )

        is ClauseRight.Block ->
            // Nested block: execute its clauses with the current environment.
            applyClauses(cr.clauses, subject, currentEnv, automaton, currentState, trace)
    }
    return currentEnv
}

/** Removes every transition with the given (from, symbol) key, when present. */
private fun removeTransition(key: Pair<String, String>?, automaton: Automaton, trace: Boolean) {
    if (key == null) return
    if (trace) System.err.println("trace   remove transition (${key.first},${key.second})->*")
    automaton.transitions.removeAll {
        it.from == key.first && it.symbol == key.second
    }
}

/** Inserts a transition, when the evaluated value denotes one. */
private fun insertTransition(t: TransKey?, automaton: Automaton, trace: Boolean) {
    if (t == null) return
    if (trace) {
        System.err.println(
            "trace   insert transition (${t.from},${t.sym})->(${t.to},${valueToString(t.value)})",
        )
    }
    // Replace any existing transition with the same key within this path's
    // automaton, so a rule-driven insert keeps the path a proper function.
    automaton.transitions.removeAll { it.from == t.from && it.symbol == t.sym }
    automaton.addTransition(t.from, t.sym, t.to, t.value)
}

/**
 * Internal representation of a full transition produced by evaluating a
 * clause's right-hand side, ready to be inserted via [Automaton.addTransition].
 */
private data class TransKey(val from: String, val sym: String, val to: String, val value: Value)

/** True when [v] is a pair whose both sides are symbols. */
private fun isSymbolPair(v: Value): Boolean = v is Value.Pair && v.left is Value.Symbol && v.right is Value.Symbol

/**
 * Extracts the (from, symbol) key from a value that denotes a transition.
 *
 * The value is the shape of the subject of a step, that is the arrow
 * `((from, symbol), (to, value))`: the left-hand side of a clause is matched
 * against that subject, so it evaluates to that shape when it applies.
 */
private fun transitionKey(v: Value): Pair<String, String>? = when {
    v is Value.Pair && isSymbolPair(v.left) -> {
        val l = v.left as Value.Pair
        Pair((l.left as Value.Symbol).name, (l.right as Value.Symbol).name)
    }

    else -> null
}

/**
 * Extracts a full transition `(from, symbol) -> (to, value)` from a value.
 */
private fun transition(v: Value): TransKey? = when {
    v is Value.Pair && isSymbolPair(v.left) && v.right is Value.Pair -> {
        val l = v.left as Value.Pair
        val r = v.right
        if (r.left is Value.Symbol) {
            TransKey(
                (l.left as Value.Symbol).name,
                (l.right as Value.Symbol).name,
                r.left.name,
                r.right,
            )
        } else {
            null
        }
    }

    else -> null
}

/**
 * Returns the top-level [Value.Type] that a pattern expects to match, or null
 * when the pattern is a wildcard (compatible with any subject type).
 */
private fun patternType(expr: Expr): Value.Type? = when (expr) {
    is Expr.Sym -> if (expr.name == "_" || expr.name.startsWith("$")) null else Value.Type.SYMBOL

    is Expr.Pair -> Value.Type.PAIR

    is Expr.Seq -> Value.Type.SEQ

    is Expr.Arrow -> Value.Type.PAIR

    // (a, b) -> (c, d) is a pair of pairs
    is Expr.Plus -> Value.Type.INT

    is Expr.Minus -> Value.Type.INT

    is Expr.IntLit -> Value.Type.INT

    is Expr.BoolLit -> Value.Type.BOOL

    is Expr.Wildcard -> null

    // matches anything
    is Expr.Bind -> patternType(expr.expr)

    is Expr.Fun -> Value.Type.FUNC

    is Expr.App -> Value.Type.FUNC

    is Expr.Emit -> null

    else -> null
}

/**
 * True when [pattern] is type-compatible with [subject] at the root level.
 * A wildcard pattern is compatible with every subject type.
 */
private fun typeCompatible(pattern: Expr, subject: Value): Boolean =
    patternType(pattern)?.let { it == subject.type } ?: true

/**
 * The head-key categories of the rule index: the root type a pattern can match
 * (see [patternHeadKey]) or a subject can present (see [subjectHeadKey]).
 */
internal object HeadKeys {
    /** Key of symbol-shaped patterns and subjects. */
    const val SYMBOL = "Symbol"

    /** Key of pair-shaped patterns and subjects (arrows are pairs of pairs). */
    const val PAIR = "Pair"

    /** Key of sequence-shaped patterns and subjects. */
    const val SEQ = "Seq"

    /** Key of integer-valued patterns and subjects. */
    const val INT = "IntVal"

    /** Key of boolean-valued patterns and subjects. */
    const val BOOL = "BoolVal"

    /** Key of function-valued patterns and subjects (automata map here too). */
    const val FUNC = "Func"

    /** Key of patterns that match any subject type (wildcards, variables). */
    const val ANY = "Any"

    /**
     * The head keys a subject may carry: every one of them has a bucket in
     * the rule index.
     */
    val SUBJECT_KEYS = listOf(SYMBOL, PAIR, SEQ, INT, BOOL, FUNC)
}

/**
 * Returns the "head key" of a pattern expression - the root type category
 * that this pattern can match at the top level. Used for rule indexing to
 * avoid trying rules whose patterns can't possibly match a given subject.
 *
 * The keys are the [HeadKeys] constants; [HeadKeys.ANY] matches any subject
 * type (wildcards, variables, binds, etc.). A binding matches the term it
 * binds, so its head key is the head key of that term.
 */
internal fun patternHeadKey(pattern: Expr): String = when (pattern) {
    is Expr.Sym -> if (pattern.name == "_" || pattern.name.startsWith("$")) HeadKeys.ANY else HeadKeys.SYMBOL
    is Expr.Pair -> HeadKeys.PAIR
    is Expr.Seq -> HeadKeys.SEQ
    is Expr.Arrow -> HeadKeys.PAIR
    is Expr.Plus -> HeadKeys.INT
    is Expr.Minus -> HeadKeys.INT
    is Expr.IntLit -> HeadKeys.INT
    is Expr.BoolLit -> HeadKeys.BOOL
    is Expr.Wildcard -> HeadKeys.ANY
    is Expr.Bind -> patternHeadKey(pattern.expr)
    is Expr.Fun -> HeadKeys.FUNC
    is Expr.App -> HeadKeys.FUNC
    is Expr.Emit -> HeadKeys.ANY
    else -> HeadKeys.ANY
}

/**
 * Returns the head key of a subject value - the root type category.
 */
internal fun subjectHeadKey(subject: Value): String = when (subject) {
    is Value.Symbol -> HeadKeys.SYMBOL
    is Value.Pair -> HeadKeys.PAIR
    is Value.Seq -> HeadKeys.SEQ
    is Value.IntVal -> HeadKeys.INT
    is Value.BoolVal -> HeadKeys.BOOL
    is Value.Func -> HeadKeys.FUNC
    is Value.Automaton -> HeadKeys.FUNC
}

/**
 * Builds the rule index: the rules that may fire on a subject of a given head
 * key, in the order they were declared.
 *
 * A rule whose pattern matches a subject of any type - a wildcard, a variable,
 * or a binding over one - belongs to every bucket, so the index returns the
 * rules of the bucket together with those, in source order. The source order
 * matters: the `first` strategy fires the first rule that matches, so the
 * index must not reorder the rules it returns.
 *
 * @param rules the rules of the active strategy, in source order.
 * @return the index, mapping each subject head key to its candidate rules.
 */
internal fun buildRuleIndex(rules: List<Rule>): Map<String, List<Rule>> {
    val numbered = rules.mapIndexed { ordinal, rule -> ordinal to rule }
    val byKey = numbered.groupBy { (_, rule) -> patternHeadKey(rule.pattern) }
    val universal = byKey[HeadKeys.ANY].orEmpty()
    val index = mutableMapOf<String, List<Rule>>()
    for (key in HeadKeys.SUBJECT_KEYS) {
        val group = byKey[key].orEmpty()
        index[key] = (group + universal).sortedBy { it.first }.map { it.second }
    }
    return index
}

/**
 * Human-readable rendering of a pattern expression, used for trace output.
 */
internal fun exprToString(expr: Expr): String = when (expr) {
    is Expr.Sym -> expr.name

    is Expr.Pair -> "(${exprToString(expr.left)},${exprToString(expr.right)})"

    is Expr.Seq -> {
        val items = expr.items.joinToString(",") { exprToString(it) }
        if (items.isEmpty()) "[seq]" else "[seq $items]"
    }

    is Expr.Arrow -> "${exprToString(expr.left)} -> ${exprToString(expr.right)}"

    is Expr.Plus -> "${exprToString(expr.left)} + ${exprToString(expr.right)}"

    is Expr.Minus -> "${exprToString(expr.left)} - ${exprToString(expr.right)}"

    is Expr.IntLit -> expr.n.toString()

    is Expr.BoolLit -> expr.b.toString()

    is Expr.Bind -> "<bind>"

    is Expr.Wildcard -> "_"

    is Expr.Fun -> "fn ${expr.params.joinToString(",")} => ${exprToString(expr.body)}"

    is Expr.App -> "${exprToString(expr.fn)}(${exprToString(expr.arg)})"

    is Expr.Emit -> "emit(${exprToString(expr.expr)})"

    else -> expr.toString()
}

/**
 * Recognizes an input string using the automaton and rules.
 * Returns true if the input is fully consumed and ends in an accepting state.
 *
 * @param maxSteps maximum number of steps before throwing
 *   [FuelExhaustedException] (default: 10000).
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 * @return true when the input is fully consumed and ends in an accepting state.
 */
fun recognize(
    input: String,
    automaton: Automaton,
    strategy: Strategy?,
    trace: Boolean = false,
    maxSteps: Int = DEFAULT_MAX_STEPS,
    ruleIndex: Map<String, List<Rule>>? = null,
): Boolean {
    val fuel = Fuel(maxSteps)
    var state = automaton.start
    for (symbol in input) {
        val result = step(state, symbol.toString(), automaton, strategy, trace, fuel, ruleIndex)
        if (result.nextState == null) return false
        state = result.nextState
    }
    return automaton.accepting.contains(state)
}

/**
 * One element of a non-deterministic frontier: the current state together with
 * that path's own automaton clone, so that insert/remove actions on one path
 * never leak into another path.
 *
 * @property state the automaton's current state for this path.
 * @property automaton this path's private automaton copy.
 */
data class FrontierState(val state: String, val automaton: Automaton)

/**
 * A single non-deterministic step: for every frontier state, finds every
 * transition leaving that state on the given symbol, clones the automaton for
 * each path, fires the applicable rewrite rules on each clone, and collects
 * all resulting (state, automaton) pairs into the next frontier.
 *
 * Paths die when no transition exists for (state, symbol). Each surviving
 * path carries its own mutated automaton, so adaptive actions on one path
 * never affect another path.
 *
 * Every transition the run follows is a step of its own, and consumes one
 * unit of fuel, so the budget bounds the total work a non-deterministic run
 * may do, and not only the number of symbols it reads.
 *
 * @param frontier the current frontier: one (state, automaton) pair per path.
 * @param symbol the next input symbol to consume.
 * @param strategy the active rewriting strategy, or null for no rules.
 * @param trace when true, prints a step trace to stderr.
 * @param fuel the run's fuel budget.
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 * @return the next frontier: one (state, automaton) pair per surviving path.
 */
fun stepNondet(
    frontier: List<FrontierState>,
    symbol: String,
    strategy: Strategy?,
    trace: Boolean,
    fuel: Fuel,
    ruleIndex: Map<String, List<Rule>>? = null,
): List<FrontierState> {
    val nextFrontier = mutableListOf<FrontierState>()

    for (fs in frontier) {
        val transitions = fs.automaton.transitions.filter { it.from == fs.state && it.symbol == symbol }
        if (transitions.isEmpty()) {
            if (trace) {
                System.err.println(
                    "trace step state=${fs.state} symbol=$symbol transition=NULL (no transition)",
                )
            }
            continue // this path dies
        }

        for (t in transitions) {
            // One unit of fuel per transition followed.
            fuel.consume()
            val result = processTransitionNondet(t, fs.automaton, strategy, trace, ruleIndex)
            nextFrontier += FrontierState(result.nextState, result.automaton)
        }
    }

    return nextFrontier
}

/**
 * The outcome of processing one transition of one path.
 *
 * @property nextState the state the path reaches.
 * @property automaton this path's automaton after the fired rules' clauses
 *   have been applied (insert/remove transitions).
 */
private data class TransitionResult(val nextState: String, val automaton: Automaton)

/**
 * Processes one transition of one path: clones the automaton, fires the
 * applicable rewrite rules on the clone, and returns the next state plus the
 * mutated automaton.
 *
 * @param transition the transition being taken; its source state and symbol
 *   are the ones the path is at.
 * @param automaton this path's automaton (will be cloned before mutation).
 * @param strategy the active rewriting strategy, or null for no rules.
 * @param trace when true, prints a step trace to stderr.
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 */
private fun processTransitionNondet(
    transition: Automaton.Transition,
    automaton: Automaton,
    strategy: Strategy?,
    trace: Boolean,
    ruleIndex: Map<String, List<Rule>>?,
): TransitionResult {
    val state = transition.from
    val symbol = transition.symbol

    // Each path gets its own automaton clone: insert/remove actions fired by
    // this path's rules cannot leak into another path.
    val pathAutomaton = automaton.clone()

    // The subject is the full transition: ((from, symbol), (to, value))
    val subject = Value.Pair(
        Value.Pair(Value.Symbol(transition.from), Value.Symbol(transition.symbol)),
        Value.Pair(Value.Symbol(transition.to), transition.value),
    )

    if (trace) {
        System.err.println(
            "trace step state=$state symbol=$symbol " +
                "transition=(${transition.from},${transition.symbol})->(" +
                "${transition.to},${valueToString(transition.value)})",
        )
    }

    val allRules = strategy?.rules ?: emptyList()
    val kind = strategy?.kind ?: StrategyKind.FIRST
    // Use the rule index to only try rules whose pattern head type matches the
    // subject head type.
    val candidateRules = ruleIndex?.get(subjectHeadKey(subject)) ?: allRules

    // Each strategy kind resolves the (possibly clause-rewritten)
    // target state; null means no rule fired and the original
    // transition applies.
    val firedTo: String? = when (kind) {
        StrategyKind.FIRST ->
            // Try rules in source order; the first match fires once.
            candidateRules
                .firstNotNullOfOrNull { tryFireRule(it, subject, state, pathAutomaton, trace) }
                ?.let { reresolveTo(pathAutomaton, state, symbol, transition.to) }

        StrategyKind.ALL -> {
            // Fire every rule whose pattern matches the subject.
            val fired = candidateRules.mapNotNull {
                tryFireRule(it, subject, state, pathAutomaton, trace)
            }
            if (fired.isNotEmpty()) reresolveTo(pathAutomaton, state, symbol, transition.to) else null
        }

        StrategyKind.REPEAT -> {
            // Re-apply the first matching rule until no rule matches
            // or the iteration limit is reached.
            repeatFire(
                candidateRules,
                subject,
                state,
                pathAutomaton,
                trace,
                strategy?.maxIterations ?: DEFAULT_MAX_ITERATIONS,
            )
            reresolveTo(pathAutomaton, state, symbol, transition.to)
        }
    }

    // No rule fired; still take the original transition.
    if (firedTo == null && trace) {
        System.err.println("trace   no rule fired")
    }
    return TransitionResult(firedTo ?: transition.to, pathAutomaton)
}

/**
 * Non-deterministic recognition: advances a frontier of (state, automaton)
 * pairs symbol by symbol. A path dies when no transition exists for the
 * current state and symbol. Acceptance is NFA-style: the input is accepted
 * when at least one surviving path ends in an accepting state.
 *
 * Every transition that matches a path is a path of its own, so a program
 * with two identical transitions out of a state explores two paths.
 *
 * @param input the input string to recognize.
 * @param automaton the automaton to recognize against.
 * @param strategy the active rewriting strategy, or null for no rules.
 * @param trace when true, prints a step trace to stderr.
 * @param maxSteps maximum number of steps before throwing
 *   [FuelExhaustedException] (default: 10000).
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 * @return true when at least one path accepts the input, false otherwise.
 */
fun recognizeNondet(
    input: String,
    automaton: Automaton,
    strategy: Strategy?,
    trace: Boolean = false,
    maxSteps: Int = DEFAULT_MAX_STEPS,
    ruleIndex: Map<String, List<Rule>>? = null,
): Boolean {
    val fuel = Fuel(maxSteps)
    var frontier = listOf(FrontierState(automaton.start, automaton.clone()))

    for (symbol in input) {
        frontier = stepNondet(frontier, symbol.toString(), strategy, trace, fuel, ruleIndex)
        if (frontier.isEmpty()) return false // every path died
    }

    // NFA acceptance: any surviving frontier state is accepting.
    return frontier.any { it.automaton.accepting.contains(it.state) }
}

/**
 * A step-by-step recognizer for interactive debugging.
 *
 * Holds mutable state (current state, input position) and can step one
 * input symbol at a time. Used by the REPL's debug mode.
 */
class Recognizer(
    /** The automaton being stepped (mutated by fired rules). */
    val automaton: Automaton,
    /** The active rewriting strategy. */
    val strategy: Strategy?,
    /** Whether step tracing is enabled. */
    val trace: Boolean = false,
    /** Maximum number of steps before [FuelExhaustedException]. */
    val maxSteps: Int = DEFAULT_MAX_STEPS,
    /** Rule index keyed by pattern head key, for faster rule lookup. */
    val ruleIndex: Map<String, List<Rule>>? = null,
) {
    /** The current automaton state during stepping. */
    var state: String = automaton.start

    /** The index of the next input symbol to consume. */
    var position: Int = 0

    /** The pending input string being recognized. */
    var input: String = ""

    /** True once recognition is complete (accepted or rejected). */
    var finished: Boolean = false

    /** The recognition result once [finished], or null while still running. */
    var result: Boolean? = null

    /** The run's fuel budget, shared across all steps. */
    private val fuel = Fuel(maxSteps)

    /**
     * Consumes one input symbol and advances the recognizer.
     *
     * @return the step result, or null when there is no more input or the
     *   recognizer has already finished.
     */
    fun step(): StepResult? {
        if (finished || position >= input.length) return null
        val symbol = input[position].toString()
        val r = step(state, symbol, automaton, strategy, trace, fuel, ruleIndex)
        if (r.nextState == null) {
            finished = true
            result = false
            return r
        }
        state = r.nextState
        position++
        return r
    }

    /**
     * Runs all remaining input symbols and finalizes the result.
     *
     * @return true when the final state is accepting, false otherwise.
     */
    fun runToCompletion(): Boolean {
        while (position < input.length && !finished) {
            step()
        }
        if (!finished) {
            result = automaton.accepting.contains(state)
            finished = true
        }
        return result ?: false
    }
}

/**
 * The canonical path of a file, used as the identity of an included file.
 *
 * The canonical path resolves the `.` and `..` components of the path and
 * follows symbolic links, so that the same file reached through different
 * spellings is recognized as one file and is merged only once. When the
 * canonicalization fails, the absolute path is used instead.
 *
 * @param file the file.
 * @return the canonical path of the file.
 */
internal fun canonicalPath(file: File): String = try {
    file.canonicalPath
} catch (_: IOException) {
    file.absolutePath
}

/**
 * Recursively resolves `include` directives, merging declarations from included
 * files. Returns a flat list of [Decl] with all includes resolved.
 *
 * A file is merged once: the second time it is reached (through a diamond of
 * includes, or through a different spelling of its path), its declarations are
 * skipped, so that the rules of the file are not duplicated. A file that
 * includes itself, directly or indirectly, is a cycle and is reported as an
 * error.
 *
 * @param decls the declarations of the program.
 * @param baseDir directory of the file currently being parsed (for resolving
 *   relative include paths)
 * @param included the canonical paths of the files merged so far
 * @param chain the canonical paths of the files being merged right now (the
 *   include chain, for cycle detection)
 */
internal fun resolveIncludes(
    decls: List<Decl>,
    baseDir: String,
    included: MutableSet<String>,
    chain: MutableSet<String> = mutableSetOf(),
): List<Decl> {
    val result = mutableListOf<Decl>()
    for (decl in decls) {
        when (decl) {
            is Decl.IncludeDecl -> {
                val resolvedFile = if (File(decl.path).isAbsolute) {
                    File(decl.path)
                } else {
                    File(baseDir, decl.path)
                }
                val resolvedPath = canonicalPath(resolvedFile)
                if (resolvedPath in chain) {
                    throw QuiverException(0, 0, "Circular include detected: $resolvedPath")
                }
                if (!included.add(resolvedPath)) {
                    // The file was merged already: skip it, so that its rules
                    // are not declared twice.
                    continue
                }
                val includedSource = File(resolvedPath).readText()
                val includedDecls = parseSource(includedSource)
                chain.add(resolvedPath)
                result.addAll(resolveIncludes(includedDecls, File(resolvedPath).parent ?: ".", included, chain))
                chain.remove(resolvedPath)
            }

            else -> result.add(decl)
        }
    }
    return result
}

/**
 * The top-level entry point: parses a Quiver source, builds the automaton, and
 * recognizes the given input string.
 *
 * @param source the Quiver source code.
 * @param input the input string to recognize.
 * @param trace when true, prints a step trace to stderr.
 * @param maxSteps maximum number of steps before throwing
 *   [FuelExhaustedException] (default: 10000).
 * @param baseDir base directory for resolving relative `include` paths
 *   (default: current working directory).
 * @param sourcePath the path of the source file, when it is known: the file
 *   takes part in the cycle detection of the `include` directives.
 * @param nondet when true, runs non-deterministic recognition: every transition
 *   matching (state, symbol) is followed in parallel, each on its own automaton
 *   clone, and acceptance is NFA-style (any surviving path accepts).
 * @return true when the input is recognized, false otherwise.
 */
fun runQuiver(
    source: String,
    input: String,
    trace: Boolean = false,
    maxSteps: Int = DEFAULT_MAX_STEPS,
    baseDir: String = ".",
    sourcePath: String? = null,
    nondet: Boolean = false,
): Boolean {
    val decls = parseSource(source)
    // The source file itself is part of the include chain: a file that
    // includes the source it came from is a cycle.
    val included = mutableSetOf<String>()
    val chain = mutableSetOf<String>()
    sourcePath?.let {
        val path = canonicalPath(File(it))
        included.add(path)
        chain.add(path)
    }
    val resolvedDecls = resolveIncludes(decls, baseDir, included, chain)
    val fsm = resolvedDecls.filterIsInstance<Decl.Fsm>().firstOrNull()
    val rules = resolvedDecls.filterIsInstance<Decl.RuleDecl>().map { it.rule }
    val strategies = resolvedDecls.filterIsInstance<Decl.StrategyDecl>().associateBy { it.strategy.label }

    // Select the active strategy: the one named in the FSM header, otherwise a
    // default FIRST strategy over all top-level rules.
    val activeStrategy: Strategy = fsm?.strategyLabel?.let { strategies[it]?.strategy }
        ?: Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, rules)

    // The rule index covers the rules of the active strategy: a program that
    // selects a named strategy runs that strategy's rules, and not the
    // top-level ones.
    val ruleIndex = buildRuleIndex(activeStrategy.rules)

    val automaton = buildAutomaton(fsm)

    return if (nondet) {
        recognizeNondet(input, automaton, activeStrategy, trace, maxSteps, ruleIndex)
    } else {
        recognize(input, automaton, activeStrategy, trace, maxSteps, ruleIndex)
    }
}

/**
 * Builds the automaton declared by an FSM header.
 *
 * The accepting states are registered both as accepting states and as states
 * of the automaton, so that the state set names every state the program
 * mentions.
 *
 * The value of a mapping entry is evaluated as the entry is added, so an
 * entry whose value consults the automaton (`transitions-from`,
 * `clone-automaton`) sees the entries declared before it.
 *
 * @param fsm the FSM header, or null when the program has none.
 * @return the automaton the program recognizes against.
 */
internal fun buildAutomaton(fsm: Decl.Fsm?): Automaton {
    val start = fsm?.start ?: "q0"
    val automaton = Automaton(start, mutableSetOf(start), mutableSetOf(), mutableListOf())
    fsm?.let {
        automaton.states.addAll(it.accepting)
        automaton.accepting.addAll(it.accepting)
        it.mapping.forEach { arrow ->
            val entry = mappingEntry(arrow)
            val value = evalExpr(entry.value, emptyMap(), start, automaton)
            automaton.addTransition(entry.from, entry.symbol, entry.to, value)
        }
    }
    return automaton
}

/**
 * A transition declared in the `mapping` block of an FSM header.
 *
 * @property from the source state.
 * @property symbol the input symbol the transition reacts to.
 * @property to the target state.
 * @property value the expression whose value labels the transition.
 */
internal data class MappingEntry(val from: String, val symbol: String, val to: String, val value: Expr)

/**
 * Validates a mapping entry of an FSM header.
 *
 * @param arrow the entry declared in the `mapping` block.
 * @return the validated entry.
 * @throws QuiverException when the entry is not a `(state, symbol) -> (state, value)`
 *   arrow over symbols.
 */
internal fun mappingEntry(arrow: Expr.Arrow): MappingEntry {
    val parts = mappingParts(arrow) ?: throw invalidMapping()
    return MappingEntry(parts.from, parts.symbol, parts.to, parts.value)
}

/**
 * The parts of a mapping entry, or null when the entry is malformed.
 *
 * @param arrow the entry declared in the `mapping` block.
 */
private fun mappingParts(arrow: Expr.Arrow): MappingEntry? {
    val fromPair = arrow.left as? Expr.Pair ?: return null
    val toPair = arrow.right as? Expr.Pair ?: return null
    val from = fromPair.left as? Expr.Sym ?: return null
    val symbol = fromPair.right as? Expr.Sym ?: return null
    val to = toPair.left as? Expr.Sym ?: return null
    return MappingEntry(from.name, symbol.name, to.name, toPair.right)
}

/** Builds the error reported for a malformed mapping entry. */
private fun invalidMapping(reason: String = "expected (state, symbol) -> (state, value)"): QuiverException =
    QuiverException(0, 0, "Invalid FSM mapping: $reason")
