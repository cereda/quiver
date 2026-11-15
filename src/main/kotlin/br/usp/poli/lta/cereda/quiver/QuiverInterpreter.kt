// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import java.io.File
import java.io.IOException
import java.math.BigInteger

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
 * match anything. The reserved word `epsilon` matches the internal marker
 * and nothing else. Pairs, sequences and arrows match structurally;
 * arithmetic and other evaluated forms never match.
 *
 * @param expr the pattern expression.
 * @param subject the value being matched.
 * @param env the bindings accumulated so far.
 * @return the extended environment, or null when the match fails.
 */
fun matchTerm(expr: Expr, subject: Value, env: Env): Env? = when (expr) {
    is Expr.Sym -> matchSym(expr.name, subject, env)

    is Expr.Epsilon ->
        if (subject is Value.Symbol && subject.label == Label.Internal) env else null

    is Expr.Pair -> matchPair(expr.left, expr.right, subject, env)

    is Expr.Seq -> {
        if (subject !is Value.Seq) {
            null
        } else {
            matchList(expr.items, subject.asSeq(), env)
        }
    }

    is Expr.Arrow -> matchPair(expr.left, expr.right, subject, env)

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
        if (subject is Value.IntVal && subject.n.compareTo(expr.n) == 0) env else null
    }

    is Expr.BoolLit -> {
        if (subject is Value.BoolVal && subject.b == expr.b) env else null
    }

    is Expr.Fun -> null

    is Expr.App -> null

    is Expr.Emit -> null

    is Expr.StateRef -> null

    is Expr.TransitionsFrom -> null

    is Expr.TargetsFrom -> null

    is Expr.SymbolsFrom -> null

    is Expr.IsEmpty -> null

    is Expr.Size -> null

    is Expr.Set -> null

    is Expr.Union -> null

    is Expr.Intersection -> null

    is Expr.Difference -> null

    is Expr.Contains -> null

    is Expr.CloneAutomaton -> null

    is Expr.FreshState -> null

    is Expr.Wildcard -> env
}

/**
 * The name a binding introduces in the environment.
 *
 * Variables are the `$`-prefixed names and the set variables are the
 * `%`-prefixed ones, so the binding `x <- t` (as well as the explicit
 * `$x <- t`) introduces the variable `$x`, and the binding `%x <- t`
 * introduces the set variable `%x`.
 */
private fun bindingKey(varName: String): String =
    if (varName.startsWith("$") || varName.startsWith("%")) varName else "$$varName"

/**
 * Matches a pair-like pattern against a subject.
 *
 * A pair and an arrow have the same shape: both are matched as a pair of
 * terms, and the arrow has no arrow of its own to match.
 *
 * @param left the left-hand term of the pattern.
 * @param right the right-hand term of the pattern.
 * @param subject the value being matched.
 * @param env the bindings accumulated so far.
 * @return the extended environment, or null when the match fails.
 */
private fun matchPair(left: Expr, right: Expr, subject: Value, env: Env): Env? {
    if (subject !is Value.Pair) return null
    return matchTerm(left, subject.left, env)?.let { env2 ->
        matchTerm(right, subject.right, env2)
    }
}

/**
 * Matches a symbol-name pattern against a subject.
 *
 * `_` is the wildcard; `$`-prefixed names are variables (bound on first
 * occurrence, occurrence-checked afterwards); `%`-prefixed names are set
 * variables (see [matchSetVariable]); any other name must match a literal
 * symbol exactly.
 *
 * The name is compared against the label of the subject, and not against its
 * text: the symbol named `epsilon` and the internal marker share a text, and
 * they are different symbols.
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

    name.startsWith("%") -> matchSetVariable(name, subject, env)

    // literal symbol: match exactly
    else -> if (subject is Value.Symbol && subject.label == Label.Symbol(name)) env else null
}

/**
 * Matches a set variable against a subject.
 *
 * A set variable that is not bound yet matches a **set** and binds it: the
 * set variables of a rule pattern name the set values a program manipulates.
 *
 * A set variable that is already bound matches the **elements** of the set it
 * is bound to: the clause of a rule may then denote a set of transitions, and
 * the action it performs applies to every one of them.
 *
 * @param name the name of the set variable.
 * @param subject the value being matched.
 * @param env the bindings accumulated so far.
 * @return the extended environment, or null when the match fails.
 */
private fun matchSetVariable(name: String, subject: Value, env: Env): Env? {
    val bound = env[name]
    return when {
        bound == null -> if (subject is Value.Set) env + (name to subject) else null

        else -> {
            val members = (bound as? Value.Set)?.items
            if (members != null && members.contains(subject)) env else null
        }
    }
}

/** True when a name is the name of a set variable. */
private fun isSetVariable(name: String): Boolean = name.startsWith("%")

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

    is Expr.Epsilon -> Value.Symbol(Label.Internal)

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

    is Expr.TransitionsFrom -> evalTransitionsFrom(expr.state, env, currentState, automaton)

    is Expr.TargetsFrom, is Expr.SymbolsFrom, is Expr.IsEmpty, is Expr.Size ->
        evalInspection(expr, env, currentState, automaton)

    is Expr.Set, is Expr.Union, is Expr.Intersection, is Expr.Difference, is Expr.Contains ->
        evalSetPrimitive(expr, env, currentState, automaton)

    is Expr.CloneAutomaton -> {
        Value.Automaton(automaton.clone())
    }

    is Expr.FreshState -> Value.Symbol(automaton.freshState())
}

/**
 * Evaluates the `transitions-from` primitive, which reads the transitions
 * that leave a state.
 *
 * @param state the expression denoting the state.
 * @param env the variable bindings in scope.
 * @param currentState the automaton's current state.
 * @param automaton the automaton being rewritten.
 * @return the transitions of the state, as a sequence of (symbol, target) pairs.
 * @throws QuiverException when the expression does not denote a state symbol.
 */
private fun evalTransitionsFrom(state: Expr, env: Env, currentState: String, automaton: Automaton): Value {
    val stateVal = evalExpr(state, env, currentState, automaton)
    if (stateVal !is Value.Symbol) throw QuiverException(0, 0, "transitions-from expects a state symbol")
    val stateName = stateVal.name
    val trans = automaton.transitions.filter { it.from == stateName }
    return Value.Seq(
        trans.map { t ->
            Value.Pair(Value.Symbol(t.label), Value.Symbol(t.to))
        },
    )
}

/**
 * Evaluates the set primitives, which build and combine sets of values.
 *
 * @param expr the primitive to evaluate.
 * @param env the variable bindings in scope.
 * @param currentState the automaton's current state.
 * @param automaton the automaton being rewritten.
 * @return the value of the primitive.
 * @throws QuiverException when the primitive is applied to a value of the
 *   wrong type.
 */
private fun evalSetPrimitive(expr: Expr, env: Env, currentState: String, automaton: Automaton): Value = when (expr) {
    is Expr.Set -> {
        val value = evalExpr(expr.expr, env, currentState, automaton)
        when (value) {
            is Value.Set -> value
            is Value.Seq -> Value.Set(value.items.toCollection(LinkedHashSet()))
            else -> throw QuiverException(0, 0, "set expects a sequence or a set value")
        }
    }

    is Expr.Union -> {
        val left = evalSet(expr.left, env, currentState, automaton, "union")
        val right = evalSet(expr.right, env, currentState, automaton, "union")
        Value.Set(LinkedHashSet(left).apply { addAll(right) })
    }

    is Expr.Intersection -> {
        val left = evalSet(expr.left, env, currentState, automaton, "intersection")
        val right = evalSet(expr.right, env, currentState, automaton, "intersection")
        Value.Set(left.filter { it in right }.toCollection(LinkedHashSet()))
    }

    is Expr.Difference -> {
        val left = evalSet(expr.left, env, currentState, automaton, "difference")
        val right = evalSet(expr.right, env, currentState, automaton, "difference")
        Value.Set(left.filter { it !in right }.toCollection(LinkedHashSet()))
    }

    is Expr.Contains -> {
        val members = evalSet(expr.set, env, currentState, automaton, "contains")
        val element = evalExpr(expr.element, env, currentState, automaton)
        Value.BoolVal(members.contains(element))
    }

    else -> throw QuiverException(0, 0, "Not a set primitive")
}

/**
 * Evaluates an expression that must denote a set.
 *
 * @param expr the expression to evaluate.
 * @param env the variable bindings in scope.
 * @param currentState the automaton's current state.
 * @param automaton the automaton being rewritten.
 * @param primitive the name of the primitive, for the error message.
 * @return the elements of the set.
 * @throws QuiverException when the expression does not denote a set.
 */
private fun evalSet(
    expr: Expr,
    env: Env,
    currentState: String,
    automaton: Automaton,
    primitive: String,
): kotlin.collections.Set<Value> {
    val value = evalExpr(expr, env, currentState, automaton)
    if (value !is Value.Set) throw QuiverException(0, 0, "$primitive expects a set value")
    return value.items
}

/**
 * Evaluates the inspection primitives, which read the transitions of a state
 * or the elements of a sequence.
 *
 * @param expr the primitive to evaluate.
 * @param env the variable bindings in scope.
 * @param currentState the automaton's current state.
 * @param automaton the automaton being rewritten.
 * @return the value of the primitive.
 * @throws QuiverException when the primitive is applied to a value of the
 *   wrong type.
 */
private fun evalInspection(expr: Expr, env: Env, currentState: String, automaton: Automaton): Value = when (expr) {
    is Expr.TargetsFrom -> {
        val stateName = stateNameOf(expr.state, env, currentState, automaton, "targets-from")
        val targets = automaton.transitions.filter { it.from == stateName }.map { it.to }.distinct()
        Value.Seq(targets.map { Value.Symbol(it) })
    }

    is Expr.SymbolsFrom -> {
        val stateName = stateNameOf(expr.state, env, currentState, automaton, "symbols-from")
        val symbols = automaton.transitions.filter { it.from == stateName }.map { it.label }.distinct()
        Value.Seq(symbols.map { Value.Symbol(it) })
    }

    is Expr.IsEmpty -> {
        val value = evalExpr(expr.expr, env, currentState, automaton)
        val empty = when (value) {
            is Value.Seq -> value.items.isEmpty()
            is Value.Set -> value.items.isEmpty()
            else -> throw QuiverException(0, 0, "is-empty expects a sequence or a set value")
        }
        Value.BoolVal(empty)
    }

    is Expr.Size -> {
        val value = evalExpr(expr.expr, env, currentState, automaton)
        val count = when (value) {
            is Value.Seq -> value.items.size
            is Value.Set -> value.items.size
            else -> throw QuiverException(0, 0, "size expects a sequence or a set value")
        }
        Value.IntVal(BigInteger.valueOf(count.toLong()))
    }

    else -> throw QuiverException(0, 0, "Not an inspection primitive")
}

/**
 * Evaluates the state an inspection primitive reads.
 *
 * @param expr the expression denoting the state.
 * @param env the variable bindings in scope.
 * @param currentState the automaton's current state.
 * @param automaton the automaton being rewritten.
 * @param primitive the name of the primitive, for the error message.
 * @return the name of the state.
 * @throws QuiverException when the expression does not denote a state symbol.
 */
private fun stateNameOf(expr: Expr, env: Env, currentState: String, automaton: Automaton, primitive: String): String {
    val value = evalExpr(expr, env, currentState, automaton)
    if (value !is Value.Symbol) throw QuiverException(0, 0, "$primitive expects a state symbol")
    return value.name
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
 * input label, and the automaton, finds a matching transition, fires any
 * applicable rewrite rule, applies its clauses (insert/remove transitions),
 * and returns the next state (or null if no transition exists).
 *
 * @param state the current automaton state.
 * @param label the label of the transition to take: the symbol the run reads,
 *   or the internal marker.
 * @param automaton the automaton to rewrite (mutated by fired rules).
 * @param strategy the active rewriting strategy, or null for no rules.
 * @param trace when true, prints a step trace to stderr.
 * @param fuel the run's fuel budget; every step consumes one unit, and an
 *   exhausted budget raises a [FuelExhaustedException].
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 */
fun step(
    state: String,
    label: Label,
    automaton: Automaton,
    strategy: Strategy?,
    trace: Boolean = false,
    fuel: Fuel = Fuel(Int.MAX_VALUE),
    ruleIndex: Map<String, List<Rule>>? = null,
): StepResult {
    // Find a transition (state, label) -> (to, value)
    val t = automaton.transitions.find { it.from == state && it.label == label }
    if (t == null) {
        if (trace) {
            System.err.println("trace step state=$state symbol=${label.render()} transition=NULL (no transition)")
        }
        return StepResult(null)
    }

    // The subject is the full transition: ((from, label), (to, value))
    val subject = Value.Pair(
        Value.Pair(Value.Symbol(t.from), Value.Symbol(t.label)),
        Value.Pair(Value.Symbol(t.to), t.value),
    )

    if (trace) {
        System.err.println(
            "trace step state=$state symbol=${label.render()} " +
                "transition=(${t.from},${label.render()})->(${t.to},${valueToString(t.value)})",
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
                    ?.let { reresolveTo(automaton, state, label, t.to) }

            StrategyKind.ALL -> {
                // Fire every rule whose pattern matches the subject.
                val fired = candidateRules.mapNotNull { tryFireRule(it, subject, state, automaton, trace) }
                if (fired.isNotEmpty()) reresolveTo(automaton, state, label, t.to) else null
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
                reresolveTo(automaton, state, label, t.to)
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
 * Re-resolves the transition leaving (state, label): the fired clauses may
 * have replaced or removed it.
 *
 * @return the transition's target state, or [fallback] when none exists.
 */
private fun reresolveTo(automaton: Automaton, state: String, label: Label, fallback: String): String =
    automaton.transitions.find { it.from == state && it.label == label }?.to ?: fallback

/**
 * Evaluates an optional guard expression with the given environment.
 * Returns true if there is no guard. Otherwise, evaluates the guard and
 * returns its truthiness: [Value.BoolVal] follows its boolean, [Value.IntVal]
 * is false only when zero, [Value.Seq] and [Value.Set] are false only when
 * empty, and all other values are truthy.
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
        is Value.IntVal -> result.n.signum() != 0
        is Value.Seq -> result.items.isNotEmpty()
        is Value.Set -> result.items.isNotEmpty()
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
 * A sweep is the exception: its left pattern is matched against the
 * transitions of the automaton, not against the subject of the rule, and its
 * body runs once per matching transition.
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
    val cr = clause.right
    val result =
        if (cr is ClauseRight.Sweep) {
            // The sweep's pattern is matched against the transitions of the
            // automaton, so the subject of the rule plays no part in it.
            applySweep(clause.left, cr.clauses, env, automaton, currentState, trace)
        } else {
            // Refine the environment by matching the clause's left pattern; a
            // clause that does not match the subject does not apply.
            val currentEnv = matchTerm(clause.left, subject, env)
            if (currentEnv == null) {
                env
            } else {
                when (cr) {
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
                            transitions(evalExpr(cr.expr, currentEnv, currentState, automaton)),
                            automaton,
                            trace,
                        )

                    is ClauseRight.Block ->
                        // Nested block: execute its clauses with the current environment.
                        applyClauses(cr.clauses, subject, currentEnv, automaton, currentState, trace)
                }
                currentEnv
            }
        }
    return result
}

/**
 * Runs a sweep: matches its pattern against every transition of the automaton
 * and runs the nested clauses once per matching transition.
 *
 * The transitions that match are collected before the first firing, so the
 * sweep sees the automaton as it was when it started; the actions of the
 * firings, on the other hand, apply to the live automaton. A transition that
 * an earlier firing removed is simply not there when a later firing looks for
 * it.
 *
 * Each firing starts from the environment of the enclosing clause, extended
 * with the bindings of the sweep's pattern: the bindings of one firing do not
 * leak into the next.
 *
 * A **set variable** of the pattern is the exception: it collects the values
 * it matches across the firings, and the collection is a set. The pattern is
 * rewritten before it is matched, so that the set variable matches the value
 * at its position instead of a set. Inside a firing, the set variable is
 * bound to the values collected so far; after the sweep, it is bound to the
 * whole collection, which the clauses that follow the sweep may use.
 *
 * @param pattern the pattern the sweep matches against the transitions.
 * @param clauses the clauses the sweep runs once per matching transition.
 * @param env the bindings in scope where the sweep appears.
 * @param automaton the automaton the sweep reads and mutates.
 * @param currentState the automaton's current state, for `$state`.
 * @param trace when true, prints each firing to stderr.
 * @return the environment extended with the sets the sweep collected.
 */
private fun applySweep(
    pattern: Expr,
    clauses: List<Clause>,
    env: Env,
    automaton: Automaton,
    currentState: String,
    trace: Boolean,
): Env {
    val variables = mutableMapOf<String, String>()
    val rewritten = rewriteSetVariables(pattern, env, variables)
    val firings =
        automaton.transitions.mapNotNull { transition ->
            val term = transitionSubject(transition)
            matchTerm(rewritten, term, env)?.let { firingEnv -> firingEnv to term }
        }
    val collected = variables.keys.associateWith { LinkedHashSet<Value>() }.toMutableMap()
    for ((firingEnv, term) in firings) {
        if (trace) {
            System.err.println("trace   sweep: ${exprToString(pattern)} on ${subjectToString(term)}")
        }
        for ((setVar, freshVar) in variables) {
            firingEnv[freshVar]?.let { value -> collected.getValue(setVar).add(value) }
        }
        // The set variables are bound to the values collected so far.
        val firingScope = env + collected.mapValues { (_, items) -> Value.Set(items) } + firingEnv
        applyClauses(clauses, term, firingScope, automaton, currentState, trace)
    }
    return env + collected.mapValues { (_, items) -> Value.Set(items) }
}

/**
 * Rewrites the set variables of a sweep pattern, so that the sweep collects
 * the values they match instead of binding them.
 *
 * A set variable that is already bound is left alone: it matches the elements
 * of the set it is bound to, as any other bound set variable does.
 *
 * @param expr the expression to rewrite.
 * @param env the bindings in scope, which the rewrite consults.
 * @param variables the fresh variables that stand for the set variables,
 *   filled by the rewrite.
 * @return the rewritten expression.
 */
private fun rewriteSetVariables(expr: Expr, env: Env, variables: MutableMap<String, String>): Expr = when (expr) {
    is Expr.Sym -> {
        if (isSetVariable(expr.name) && expr.name !in env) {
            val fresh = variables.getOrPut(expr.name) { freshVariable(expr.name, variables.size) }
            Expr.Sym(fresh)
        } else {
            expr
        }
    }

    is Expr.Bind -> {
        val inner = rewriteSetVariables(expr.expr, env, variables)
        if (isSetVariable(expr.varName) && expr.varName !in env) {
            val fresh = variables.getOrPut(expr.varName) { freshVariable(expr.varName, variables.size) }
            Expr.Bind(fresh, inner)
        } else {
            Expr.Bind(expr.varName, inner)
        }
    }

    is Expr.Pair ->
        Expr.Pair(
            rewriteSetVariables(expr.left, env, variables),
            rewriteSetVariables(expr.right, env, variables),
        )

    is Expr.Seq -> Expr.Seq(expr.items.map { item -> rewriteSetVariables(item, env, variables) })

    is Expr.Arrow ->
        Expr.Arrow(
            rewriteSetVariables(expr.left, env, variables),
            rewriteSetVariables(expr.right, env, variables),
        )

    else -> expr
}

/**
 * The name of the fresh variable that stands for a set variable in a sweep.
 *
 * The name carries a character an identifier may not contain, so it cannot
 * clash with a variable of the program.
 *
 * @param name the name of the set variable.
 * @param index the number of the set variables collected so far.
 * @return the name of the fresh variable.
 */
private fun freshVariable(name: String, index: Int): String = "$${name.removePrefix("%")}#$index"

/**
 * The subject term of a transition: `((from, label), (to, value))`, the shape
 * a rule pattern and a sweep pattern are matched against.
 *
 * The label of the transition is carried as it is, so that a pattern may
 * single out the internal marker.
 *
 * @param transition the transition to denote.
 * @return the term that denotes the transition.
 */
private fun transitionSubject(transition: Automaton.Transition): Value = Value.Pair(
    Value.Pair(Value.Symbol(transition.from), Value.Symbol(transition.label)),
    Value.Pair(Value.Symbol(transition.to), transition.value),
)

/**
 * Renders a subject term as a transition, for the trace.
 *
 * @param subject the subject term to render.
 * @return the rendering of the term.
 */
private fun subjectToString(subject: Value): String =
    if (subject is Value.Pair && subject.left is Value.Pair && subject.right is Value.Pair) {
        val key = subject.left
        val target = subject.right
        val symbol = valueToString(key.right)
        val from = if (key.left is Value.Symbol) key.left.name else "?"
        val to = if (target.left is Value.Symbol) target.left.name else "?"
        "($from,$symbol)->($to,${valueToString(target.right)})"
    } else {
        valueToString(subject)
    }

/** Removes every transition with the given (from, label) key, when present. */
private fun removeTransition(key: RemovalKey?, automaton: Automaton, trace: Boolean) {
    if (key == null) return
    if (trace) {
        System.err.println("trace   remove transition (${key.from},${labelsToString(key.labels)})->*")
    }
    automaton.transitions.removeAll {
        it.from == key.from && it.label in key.labels
    }
}

/**
 * Inserts the transitions, when the evaluated value denotes them.
 *
 * The transitions that share a key with the ones being inserted are removed
 * first, so that a rule-driven insert keeps the path a proper function. When
 * the insertion denotes several transitions with the same key, the bulk
 * action of the clause keeps them all.
 */
private fun insertTransition(ts: List<TransKey>, automaton: Automaton, trace: Boolean) {
    if (ts.isEmpty()) return
    if (trace) {
        for (t in ts) {
            System.err.println(
                "trace   insert transition (${t.from},${t.label.render()})->" +
                    "(${t.to},${valueToString(t.value)})",
            )
        }
    }
    val keys = ts.map { it.from to it.label }.toSet()
    automaton.transitions.removeAll { it.from to it.label in keys }
    for (t in ts) {
        automaton.addTransition(t.from, t.label, t.to, t.value)
    }
}

/**
 * Internal representation of a full transition produced by evaluating a
 * clause's right-hand side, ready to be inserted via [Automaton.addTransition].
 *
 * @property from the source state.
 * @property label the label of the transition: the symbol it reacts to, or
 *   the internal marker.
 * @property to the target state.
 * @property value the payload the transition carries.
 */
private data class TransKey(val from: String, val label: Label, val to: String, val value: Value)

/**
 * The key of the transitions a removal clause denotes: the source state and
 * the labels of the transitions, which are several when the clause denotes a
 * set of symbols.
 *
 * @property from the source state name.
 * @property labels the labels of the transitions to remove.
 */
private data class RemovalKey(val from: String, val labels: List<Label>)

/**
 * Renders a list of labels, as the trace does: a single label is written
 * alone, and several labels are written as a set.
 *
 * @param labels the labels to render.
 * @return the rendering of the labels.
 */
private fun labelsToString(labels: List<Label>): String = if (labels.size == 1) {
    labels.first().render()
} else {
    labels.joinToString(",", prefix = "{", postfix = "}") { it.render() }
}

/**
 * The labels a value denotes in a symbol position: the label of the symbol
 * itself, or the labels of the elements of a set.
 *
 * @param v the value in the symbol position.
 * @return the labels, or null when the value is neither a symbol nor a set of
 *   symbols.
 */
private fun symbolLabels(v: Value): List<Label>? {
    val elements: List<Value> = when (v) {
        is Value.Symbol -> listOf(v)
        is Value.Set -> v.items.toList()
        else -> return null
    }
    val labels = elements.mapNotNull { element -> (element as? Value.Symbol)?.label }
    return if (labels.size == elements.size) labels else null
}

/**
 * The names a value denotes in a state position: the name of the symbol
 * itself, or the names of the elements of a set.
 *
 * @param v the value in the state position.
 * @return the names, or null when the value is neither a symbol nor a set of
 *   symbols.
 */
private fun symbolNames(v: Value): List<String>? {
    val elements: List<Value> = when (v) {
        is Value.Symbol -> listOf(v)
        is Value.Set -> v.items.toList()
        else -> return null
    }
    val names = elements.mapNotNull { element -> (element as? Value.Symbol)?.name }
    return if (names.size == elements.size) names else null
}

/**
 * Extracts the (from, label) key from a value that denotes a transition.
 *
 * The value is the shape of the subject of a step, that is the arrow
 * `((from, label), (to, value))`: the left-hand side of a clause is matched
 * against that subject, so it evaluates to that shape when it applies.
 *
 * The label may be a set, in which case the key denotes every transition
 * whose label belongs to the set: this is the bulk removal of a clause.
 */
private fun transitionKey(v: Value): RemovalKey? {
    val key = (v as? Value.Pair)?.left
    val from = ((key as? Value.Pair)?.left as? Value.Symbol)?.name
    val labels = (key as? Value.Pair)?.right?.let { symbolLabels(it) }
    return if (from != null && labels != null) RemovalKey(from, labels) else null
}

/**
 * Extracts the full transitions `(from, label) -> (to, value)` from a value.
 *
 * The label and the target state may be sets, in which case the value
 * denotes several transitions: one per element of the set, and one per
 * combination of the two sets when both of them are sets. This is the bulk
 * insertion of a clause.
 */
private fun transitions(v: Value): List<TransKey> {
    val key = (v as? Value.Pair)?.left
    val target = (v as? Value.Pair)?.right
    val from = ((key as? Value.Pair)?.left as? Value.Symbol)?.name
    val labels = (key as? Value.Pair)?.right?.let { symbolLabels(it) }
    val targets = (target as? Value.Pair)?.left?.let { symbolNames(it) }
    if (from == null || labels == null || targets == null) return emptyList()
    return labels.flatMap { label ->
        targets.map { state -> TransKey(from, label, state, target.right) }
    }
}

/**
 * Returns the top-level [Value.Type] that a pattern expects to match, or null
 * when the pattern is a wildcard (compatible with any subject type).
 */
internal fun patternType(expr: Expr): Value.Type? = when (expr) {
    is Expr.Sym ->
        when {
            expr.name == "_" || expr.name.startsWith("$") -> null
            expr.name.startsWith("%") -> Value.Type.SET
            else -> Value.Type.SYMBOL
        }

    // The internal marker is a symbol value of its own kind.
    is Expr.Epsilon -> Value.Type.SYMBOL

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

    is Expr.TargetsFrom -> null

    is Expr.SymbolsFrom -> null

    is Expr.IsEmpty -> null

    is Expr.Size -> null

    is Expr.Set -> null

    is Expr.Union -> null

    is Expr.Intersection -> null

    is Expr.Difference -> null

    is Expr.Contains -> null

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

    /** Key of set-valued patterns and subjects. */
    const val SET = "Set"

    /** Key of patterns that match any subject type (wildcards, variables). */
    const val ANY = "Any"

    /**
     * The head keys a subject may carry: every one of them has a bucket in
     * the rule index.
     */
    val SUBJECT_KEYS = listOf(SYMBOL, PAIR, SEQ, INT, BOOL, FUNC, SET)
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
    is Expr.Sym ->
        when {
            pattern.name == "_" || pattern.name.startsWith("$") -> HeadKeys.ANY
            pattern.name.startsWith("%") -> HeadKeys.SET
            else -> HeadKeys.SYMBOL
        }

    // The internal marker is a symbol value.
    is Expr.Epsilon -> HeadKeys.SYMBOL

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

    is Expr.TargetsFrom -> HeadKeys.ANY

    is Expr.SymbolsFrom -> HeadKeys.ANY

    is Expr.IsEmpty -> HeadKeys.ANY

    is Expr.Size -> HeadKeys.ANY

    is Expr.Set -> HeadKeys.ANY

    is Expr.Union -> HeadKeys.ANY

    is Expr.Intersection -> HeadKeys.ANY

    is Expr.Difference -> HeadKeys.ANY

    is Expr.Contains -> HeadKeys.ANY

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
    is Value.Set -> HeadKeys.SET
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

    is Expr.Epsilon -> Label.INTERNAL_NAME

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

    is Expr.TargetsFrom -> "targets-from(${exprToString(expr.state)})"

    is Expr.SymbolsFrom -> "symbols-from(${exprToString(expr.state)})"

    is Expr.IsEmpty -> "is-empty(${exprToString(expr.expr)})"

    is Expr.Size -> "size(${exprToString(expr.expr)})"

    is Expr.Set -> "set(${exprToString(expr.expr)})"

    is Expr.Union -> "union(${exprToString(expr.left)},${exprToString(expr.right)})"

    is Expr.Intersection -> "intersection(${exprToString(expr.left)},${exprToString(expr.right)})"

    is Expr.Difference -> "difference(${exprToString(expr.left)},${exprToString(expr.right)})"

    is Expr.Contains -> "contains(${exprToString(expr.set)},${exprToString(expr.element)})"

    is Expr.FreshState -> "fresh-state()"

    else -> expr.toString()
}

/**
 * Recognizes an input using the automaton and rules.
 * Returns true if the input is fully consumed and ends in an accepting state.
 *
 * The run is a sequence of closures and input-driven steps: the automaton
 * starts at its start state, takes the internal transitions that leave it, and
 * then consumes the input one symbol at a time, taking the internal
 * transitions again after every symbol. The acceptance is decided on the
 * state the automaton rests in after the final closure.
 *
 * @param input the input to recognize: the names of the input symbols, in
 *   order.
 * @param automaton the automaton to recognize against.
 * @param strategy the active rewriting strategy, or null for no rules.
 * @param trace when true, prints a step trace to stderr.
 * @param maxSteps maximum number of steps before throwing
 *   [FuelExhaustedException] (default: 10000).
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 * @return true when the input is fully consumed and ends in an accepting state.
 */
fun recognize(
    input: List<String>,
    automaton: Automaton,
    strategy: Strategy?,
    trace: Boolean = false,
    maxSteps: Int = DEFAULT_MAX_STEPS,
    ruleIndex: Map<String, List<Rule>>? = null,
): Boolean {
    val fuel = Fuel(maxSteps)
    var state = automaton.start
    // The closure of the start state, before the first symbol.
    state = closeInternally(state, automaton, strategy, trace, fuel, ruleIndex)
    for (symbol in input) {
        val result = step(state, Label.Symbol(symbol), automaton, strategy, trace, fuel, ruleIndex)
        if (result.nextState == null) return false
        state = result.nextState
        // The closure again, after every symbol.
        state = closeInternally(state, automaton, strategy, trace, fuel, ruleIndex)
    }
    return automaton.accepting.contains(state)
}

/**
 * Takes the internal transitions that leave a state, until there is none left.
 *
 * The internal moves are compulsory: a state that has an internal transition
 * does not come to rest before the chain of internal transitions ends. Each
 * internal transition is a step of its own, so the rules are consulted on it
 * and may rewrite it, and each one consumes a unit of the budget.
 *
 * @param state the state the closure starts from.
 * @param automaton the automaton being rewritten.
 * @param strategy the active rewriting strategy, or null for no rules.
 * @param trace when true, prints a step trace to stderr.
 * @param fuel the run's fuel budget; every internal step consumes one unit.
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 * @return the state the closure ends in.
 * @throws EpsilonClosureException when the budget is exhausted inside the
 *   closure, that is, when the internal transitions do not come to an end.
 */
private fun closeInternally(
    state: String,
    automaton: Automaton,
    strategy: Strategy?,
    trace: Boolean,
    fuel: Fuel,
    ruleIndex: Map<String, List<Rule>>?,
): String {
    // The closure counts its own steps, so that the failure it reports names
    // the internal steps and not the steps of the whole run.
    val start = fuel.taken
    var current = state
    while (true) {
        val internal = automaton.transitions.find { it.from == current && it.label == Label.Internal }
        if (internal == null) return current
        val result =
            try {
                step(current, Label.Internal, automaton, strategy, trace, fuel, ruleIndex)
            } catch (exhausted: FuelExhaustedException) {
                throw EpsilonClosureException(
                    "Epsilon closure did not terminate: the budget was exhausted " +
                        "after ${fuel.taken - start} internal steps",
                    exhausted,
                )
            }
        current = result.nextState ?: return current
    }
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
 * @param label the label of the transition to take: the symbol the run reads,
 *   or the internal marker.
 * @param strategy the active rewriting strategy, or null for no rules.
 * @param trace when true, prints a step trace to stderr.
 * @param fuel the run's fuel budget.
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 * @return the next frontier: one (state, automaton) pair per surviving path.
 */
fun stepNondet(
    frontier: List<FrontierState>,
    label: Label,
    strategy: Strategy?,
    trace: Boolean,
    fuel: Fuel,
    ruleIndex: Map<String, List<Rule>>? = null,
): List<FrontierState> {
    val nextFrontier = mutableListOf<FrontierState>()

    for (fs in frontier) {
        val transitions = fs.automaton.transitions.filter { it.from == fs.state && it.label == label }
        if (transitions.isEmpty()) {
            if (trace) {
                System.err.println(
                    "trace step state=${fs.state} symbol=${label.render()} transition=NULL (no transition)",
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
    val label = transition.label

    // Each path gets its own automaton clone: insert/remove actions fired by
    // this path's rules cannot leak into another path.
    val pathAutomaton = automaton.clone()

    // The subject is the full transition: ((from, label), (to, value))
    val subject = Value.Pair(
        Value.Pair(Value.Symbol(transition.from), Value.Symbol(transition.label)),
        Value.Pair(Value.Symbol(transition.to), transition.value),
    )

    if (trace) {
        System.err.println(
            "trace step state=$state symbol=${label.render()} " +
                "transition=(${transition.from},${label.render()})->(" +
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
                ?.let { reresolveTo(pathAutomaton, state, label, transition.to) }

        StrategyKind.ALL -> {
            // Fire every rule whose pattern matches the subject.
            val fired = candidateRules.mapNotNull {
                tryFireRule(it, subject, state, pathAutomaton, trace)
            }
            if (fired.isNotEmpty()) reresolveTo(pathAutomaton, state, label, transition.to) else null
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
            reresolveTo(pathAutomaton, state, label, transition.to)
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
 * The run is a sequence of closures and input-driven steps, exactly as in the
 * deterministic mode: the internal transitions are taken before the first
 * symbol and after every symbol, and the acceptance is decided on the closed
 * frontier.
 *
 * @param input the input to recognize: the names of the input symbols, in
 *   order.
 * @param automaton the automaton to recognize against.
 * @param strategy the active rewriting strategy, or null for no rules.
 * @param trace when true, prints a step trace to stderr.
 * @param maxSteps maximum number of steps before throwing
 *   [FuelExhaustedException] (default: 10000).
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 * @return true when at least one path accepts the input, false otherwise.
 */
fun recognizeNondet(
    input: List<String>,
    automaton: Automaton,
    strategy: Strategy?,
    trace: Boolean = false,
    maxSteps: Int = DEFAULT_MAX_STEPS,
    ruleIndex: Map<String, List<Rule>>? = null,
): Boolean {
    val fuel = Fuel(maxSteps)
    var frontier = listOf(FrontierState(automaton.start, automaton.clone()))
    // The closure of the start state, before the first symbol.
    frontier = closeFrontierInternally(frontier, strategy, trace, fuel, ruleIndex)

    for (symbol in input) {
        frontier = stepNondet(frontier, Label.Symbol(symbol), strategy, trace, fuel, ruleIndex)
        if (frontier.isNotEmpty()) {
            // The closure again, after every symbol.
            frontier = closeFrontierInternally(frontier, strategy, trace, fuel, ruleIndex)
        }
        if (frontier.isEmpty()) break // every path died
    }

    // NFA acceptance: any surviving frontier state is accepting.
    return frontier.any { it.automaton.accepting.contains(it.state) }
}

/**
 * Takes the internal transitions that leave the states of a frontier, until
 * none of them has one left.
 *
 * The internal moves are compulsory, and every internal transition out of a
 * state is an alternative path, as any other transition is in the
 * non-deterministic mode. Each path keeps its own automaton clone, so an
 * internal step that rewrites the transition function affects only its own
 * branch.
 *
 * @param frontier the frontier to close: one (state, automaton) pair per path.
 * @param strategy the active rewriting strategy, or null for no rules.
 * @param trace when true, prints a step trace to stderr.
 * @param fuel the run's fuel budget; every internal transition consumes a unit.
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 * @return the closed frontier: one (state, automaton) pair per surviving path.
 * @throws EpsilonClosureException when the budget is exhausted inside the
 *   closure, that is, when the internal transitions do not come to an end.
 */
private fun closeFrontierInternally(
    frontier: List<FrontierState>,
    strategy: Strategy?,
    trace: Boolean,
    fuel: Fuel,
    ruleIndex: Map<String, List<Rule>>?,
): List<FrontierState> {
    // The closure counts its own steps, so that the failure it reports names
    // the internal steps and not the steps of the whole run.
    val start = fuel.taken
    var current = frontier
    while (true) {
        val moved = current.any { hasInternals(it) }
        try {
            current = current.flatMap { fs -> expandInternally(fs, strategy, trace, fuel, ruleIndex) }
        } catch (exhausted: FuelExhaustedException) {
            throw EpsilonClosureException(
                "Epsilon closure did not terminate: the budget was exhausted " +
                    "after ${fuel.taken - start} internal steps",
                exhausted,
            )
        }
        if (!moved) return current
    }
}

/**
 * True when a path has an internal transition to follow.
 *
 * @param fs the path.
 * @return true when the path has an internal transition.
 */
private fun hasInternals(fs: FrontierState): Boolean =
    fs.automaton.transitions.any { it.from == fs.state && it.label == Label.Internal }

/**
 * Expands a path into the paths that follow its internal transitions: one
 * path for the path itself when it has no internal transition, and one path
 * per internal transition otherwise.
 *
 * @param fs the path to expand.
 * @param strategy the active rewriting strategy, or null for no rules.
 * @param trace when true, prints a step trace to stderr.
 * @param fuel the run's fuel budget; every internal transition consumes a unit.
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 * @return the paths that follow the internal transitions of the path.
 */
private fun expandInternally(
    fs: FrontierState,
    strategy: Strategy?,
    trace: Boolean,
    fuel: Fuel,
    ruleIndex: Map<String, List<Rule>>?,
): List<FrontierState> {
    val internals = fs.automaton.transitions.filter { it.from == fs.state && it.label == Label.Internal }
    if (internals.isEmpty()) return listOf(fs)
    return internals.map { internal -> followInternal(internal, fs, strategy, trace, fuel, ruleIndex) }
}

/**
 * Follows one internal transition of a path, consuming a unit of the budget.
 *
 * @param internal the internal transition to follow.
 * @param fs the path the transition belongs to.
 * @param strategy the active rewriting strategy, or null for no rules.
 * @param trace when true, prints a step trace to stderr.
 * @param fuel the run's fuel budget.
 * @param ruleIndex optional index mapping head keys to rules, for faster lookup.
 * @return the path that follows the transition.
 * @throws FuelExhaustedException when the budget is exhausted.
 */
private fun followInternal(
    internal: Automaton.Transition,
    fs: FrontierState,
    strategy: Strategy?,
    trace: Boolean,
    fuel: Fuel,
    ruleIndex: Map<String, List<Rule>>?,
): FrontierState {
    fuel.consume()
    val result = processTransitionNondet(internal, fs.automaton, strategy, trace, ruleIndex)
    return FrontierState(result.nextState, result.automaton)
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

    /** The pending input being recognized: the names of the input symbols. */
    var input: List<String> = emptyList()

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
        if (finished || position >= input.size) return null
        val symbol = input[position]
        val r = step(state, Label.Symbol(symbol), automaton, strategy, trace, fuel, ruleIndex)
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
        while (position < input.size && !finished) {
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
 * recognizes the given input.
 *
 * @param source the Quiver source code.
 * @param input the input to recognize: the names of the input symbols, in
 *   order. When it is null, the input the program declares in its header is
 *   used, and the run fails when the program declares none.
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
 * @throws NoInputException when neither the command line nor the program
 *   declares an input.
 */
fun runQuiver(
    source: String,
    input: List<String>? = null,
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

    // A cycle among the internal transitions that the rules cannot break is a
    // program that never finishes: it is refused before the run starts.
    checkEpsilonCycles(automaton, activeStrategy.rules)

    // The command line wins over the input the program declares; when neither
    // gives one, the run has nothing to recognize.
    val symbols = input ?: fsm?.input ?: throw NoInputException()

    return if (nondet) {
        recognizeNondet(symbols, automaton, activeStrategy, trace, maxSteps, ruleIndex)
    } else {
        recognize(symbols, automaton, activeStrategy, trace, maxSteps, ruleIndex)
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
            automaton.addTransition(entry.from, entry.label, entry.to, value)
        }
    }
    return automaton
}

/**
 * Checks the automaton for an epsilon cycle, when the rules of the program
 * cannot break one.
 *
 * A cycle among the internal transitions of the automaton is a program that
 * never finishes: the closure of a state on the cycle never ends. When the
 * rules of the program may insert or remove an internal transition, the cycle
 * proves nothing, because a rule may break it; the check is then skipped. It
 * applies, in particular, to the programs that declare no rules at all, whose
 * transition function is static.
 *
 * Only the states that are reachable from the start state count: a cycle in a
 * part of the automaton that no run ever reaches is harmless.
 *
 * @param automaton the automaton to check.
 * @param rules the rules of the active strategy.
 * @throws QuiverException when the internal transitions of the automaton
 *   contain a cycle that the rules cannot break.
 */
internal fun checkEpsilonCycles(automaton: Automaton, rules: List<Rule>) {
    if (mayRewriteInternalTransition(rules)) return
    val cycle = findEpsilonCycle(automaton) ?: return
    throw QuiverException(0, 0, "Epsilon cycle detected: ${cycle.joinToString(" -> ")}")
}

/**
 * True when the rules may insert or remove an internal transition, in which
 * case an epsilon cycle of the declared automaton proves nothing.
 *
 * The analysis is conservative: it errs only in the safe direction, so a
 * program that is rejected is always a program whose cycle cannot be broken.
 *
 * @param rules the rules of the active strategy.
 * @return true when a rule may insert or remove an internal transition.
 */
internal fun mayRewriteInternalTransition(rules: List<Rule>): Boolean =
    rules.any { rule -> rule.clauses.any { clause -> clauseTouchesInternal(clause) } }

/**
 * True when a clause may insert or remove an internal transition.
 *
 * @param clause the clause to analyse.
 * @return true when the clause may act on an internal transition.
 */
private fun clauseTouchesInternal(clause: Clause): Boolean {
    val right = clause.right
    val touches =
        when (right) {
            // The sweep matches its pattern against the transitions, so a
            // pattern that may match an internal one lets the nested clauses
            // act on it.
            is ClauseRight.Sweep ->
                mayDenoteInternalTransition(clause.left) ||
                    right.clauses.any { nested -> clauseTouchesInternal(nested) }

            // A nested block may act on an internal transition.
            is ClauseRight.Block -> right.clauses.any { nested -> clauseTouchesInternal(nested) }

            // The left-hand side of a clause denotes the transition it
            // removes, and the right-hand side the transition it inserts.
            is ClauseRight.Rhs ->
                mayDenoteInternalTransition(clause.left) || mayDenoteInternalTransition(right.expr)

            is ClauseRight.Wildcard -> mayDenoteInternalTransition(clause.left)
        }
    return touches
}

/**
 * True when an expression may denote a transition whose symbol is the
 * internal one.
 *
 * @param expr the expression to analyse; null denotes no transition.
 * @return true when the expression may denote an internal transition.
 */
private fun mayDenoteInternalTransition(expr: Expr?): Boolean = when (expr) {
    is Expr.Arrow -> mayBeInternalKey(expr.left)
    else -> false
}

/**
 * True when the symbol position of a transition key may hold the internal
 * symbol.
 *
 * @param expr the expression in the key position.
 * @return true when the key may be an internal one.
 */
private fun mayBeInternalKey(expr: Expr): Boolean = when (expr) {
    is Expr.Pair -> mayBeInternalSymbol(expr.right)
    else -> false
}

/**
 * True when an expression in the symbol position may denote the internal
 * symbol.
 *
 * The reserved word `epsilon` denotes the internal marker; a literal symbol
 * rules the transition out; a variable or a wildcard may be bound to the
 * internal marker, and so may the result of any other form of expression,
 * which the analysis does not understand.
 *
 * @param expr the expression in the symbol position.
 * @return true when the symbol may be the internal one.
 */
private fun mayBeInternalSymbol(expr: Expr): Boolean = when (expr) {
    is Expr.Epsilon -> true
    is Expr.Sym -> expr.name.startsWith("$")
    is Expr.Wildcard -> true
    is Expr.Bind -> mayBeInternalSymbol(expr.expr)
    else -> true
}

/**
 * Finds a cycle among the internal transitions of the automaton, if there is
 * one.
 *
 * The cycle is sought among the states that are reachable from the start
 * state through any transition, and it is returned as the list of the states
 * that form it, with the first state repeated at the end.
 *
 * @param automaton the automaton to analyse.
 * @return the cycle, or null when the internal transitions are acyclic.
 */
internal fun findEpsilonCycle(automaton: Automaton): List<String>? {
    val reachable = reachableStates(automaton)
    val edges = automaton.transitions
        .filter { it.label == Label.Internal && it.from in reachable && it.to in reachable }
        .groupBy({ it.from }, { it.to })
    val visited = mutableSetOf<String>()
    val stack = mutableListOf<String>()
    for (state in reachable) {
        if (state !in visited) {
            val cycle = findCycleFrom(state, edges, visited, stack)
            if (cycle != null) return cycle
        }
    }
    return null
}

/**
 * The states that are reachable from the start state, through any transition.
 *
 * @param automaton the automaton to analyse.
 * @return the reachable states.
 */
private fun reachableStates(automaton: Automaton): Set<String> {
    val reachable = mutableSetOf(automaton.start)
    val pending = ArrayDeque<String>()
    pending.add(automaton.start)
    while (pending.isNotEmpty()) {
        val state = pending.removeFirst()
        for (transition in automaton.transitions.filter { it.from == state }) {
            if (reachable.add(transition.to)) pending.add(transition.to)
        }
    }
    return reachable
}

/**
 * Searches a cycle of internal transitions, depth first.
 *
 * @param state the state to explore.
 * @param edges the internal transitions, grouped by their source state.
 * @param visited the states already explored.
 * @param stack the states on the path currently being explored.
 * @return the cycle, or null when there is none from this state.
 */
private fun findCycleFrom(
    state: String,
    edges: Map<String, List<String>>,
    visited: MutableSet<String>,
    stack: MutableList<String>,
): List<String>? {
    visited.add(state)
    stack.add(state)
    var cycle: List<String>? = null
    for (next in edges[state].orEmpty()) {
        if (cycle != null) break
        val index = stack.indexOf(next)
        if (index >= 0) {
            // The state is on the path being explored: the cycle runs from it
            // back to it.
            cycle = stack.subList(index, stack.size).toList() + next
        } else if (next !in visited) {
            cycle = findCycleFrom(next, edges, visited, stack)
        }
    }
    stack.removeAt(stack.size - 1)
    return cycle
}

/**
 * A transition declared in the `mapping` block of an FSM header.
 *
 * @property from the source state.
 * @property label the label of the transition: the symbol it reacts to, or
 *   the internal marker.
 * @property to the target state.
 * @property value the expression whose value labels the transition.
 */
internal data class MappingEntry(val from: String, val label: Label, val to: String, val value: Expr)

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
    return MappingEntry(parts.from, parts.label, parts.to, parts.value)
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
    val label = labelOf(fromPair.right) ?: return null
    val to = toPair.left as? Expr.Sym ?: return null
    return MappingEntry(from.name, label, to.name, toPair.right)
}

/**
 * The label an expression in the symbol position denotes: the internal
 * marker for the reserved word `epsilon`, and the literal symbol for a name.
 *
 * @param expr the expression in the symbol position.
 * @return the label, or null when the expression is not a symbol.
 */
private fun labelOf(expr: Expr): Label? = when (expr) {
    is Expr.Epsilon -> Label.Internal
    is Expr.Sym -> Label.Symbol(expr.name)
    else -> null
}

/** Builds the error reported for a malformed mapping entry. */
private fun invalidMapping(reason: String = "expected (state, symbol) -> (state, value)"): QuiverException =
    QuiverException(0, 0, "Invalid FSM mapping: $reason")
