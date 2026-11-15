# The Quiver examples

This document walks through every example program that ships with the
interpreter. Each section states what the program does, shows its source, and
traces the recognition step by step.

The examples live in [`src/main/resources`](../src/main/resources) and are
copied verbatim into [`src/test/resources`](../src/test/resources), where the
test suite exercises them.

## Table of contents

1. [Running an example](#running-an-example)
2. [The vocabulary of the traces](#the-vocabulary-of-the-traces)
3. [`simple.quiver`: a plain automaton](#simplequiver-a-plain-automaton)
4. [`rec.quiver`: the phase recognizer](#recquiver-the-phase-recognizer)
5. [`abn.quiver`: an automaton that reads its own counter](#abnquiver-an-automaton-that-reads-its-own-counter)
6. [`guard.quiver`: guards](#guardquiver-guards)
7. [`guard-literal.quiver`: the boolean literals](#guard-literalquiver-the-boolean-literals)
8. [`emit.quiver`: output](#emitquiver-output)
9. [`primitives.quiver`: the adaptive primitives](#primitivesquiver-the-adaptive-primitives)
10. [`sweep.quiver`: a sweep over the transitions](#sweepquiver-a-sweep-over-the-transitions)
11. [`inspect.quiver`: the inspection primitives](#inspectquiver-the-inspection-primitives)
12. [`closure.quiver`: closures as transition values](#closurequiver-closures-as-transition-values)
13. [`strategy.quiver`: the rewriting strategies](#strategyquiver-the-rewriting-strategies)
14. [`seq.quiver`: sequences](#seqquiver-sequences)
15. [`binding.quiver`: bindings](#bindingquiver-bindings)
16. [`adaptive-insert.quiver`: inserting a transition](#adaptive-insertquiver-inserting-a-transition)
17. [`adaptive-threshold.quiver`: retargeting a transition](#adaptive-thresholdquiver-retargeting-a-transition)
18. [`adaptive-remove.quiver`: removing a transition](#adaptive-removequiver-removing-a-transition)
19. [`include-main.quiver` and `include-sub.quiver`: modularity](#include-mainquiver-modularity)
20. [`nondet.quiver`: non-determinism without rules](#nondetquiver-non-determinism-without-rules)
21. [`nondet-adaptive.quiver`: per-path adaptive actions](#nondet-adaptivequiver-per-path-adaptive-actions)
22. [`nondet-strategy.quiver`: non-determinism with the `all` strategy](#nondet-strategyquiver-non-determinism-with-the-all-strategy)
23. [`nondet-repeat.quiver`: non-determinism with the `repeat` strategy](#nondet-repeatquiver-non-determinism-with-the-repeat-strategy)

## Running an example

Every example is a Quiver source file. The interpreter recognizes one input
string against it:

```bash
java -jar build/libs/quiver-1.1.0.jar -i src/main/resources/abn.quiver aabb
```

The command prints `true` when the input is recognized and `false` otherwise.
The `--trace` option prints what happens at every step on the standard error
stream, which is how the traces below were produced:

```bash
java -jar build/libs/quiver-1.1.0.jar -i src/main/resources/abn.quiver aabb --trace
```

The interactive REPL loads the same files and recognizes as many inputs as you
like:

```bash
java -jar build/libs/quiver-1.1.0.jar
```

```
quiver> :load src/main/resources/abn.quiver
quiver> :recognize aabb
true
```

## The vocabulary of the traces

Three ideas are enough to read every trace.

**The subject.** At every step the automaton looks up the transition that
leaves the current state on the next symbol of the input. That transition
becomes the *subject* of the step, written as a term:

```
((from, symbol), (to, value))
```

The left half is the key of the transition, the right half is its target and
its value. A rule's pattern is matched against this term, so the pattern

```quiver
(q0, a) -> ($x, $n)
```

matches the transition out of `q0` on the symbol `a`, whatever its target
(`$x`) and whatever its value (`$n`). A literal matches itself, a variable
matches anything and binds it, and `_` matches anything without binding it.

**The clauses.** The body of a rule is a list of clauses, and each clause is
`left => right`:

| Form | Action |
|---|---|
| `pat => _` | **remove** the transition denoted by `pat` |
| `_ => pat` | **insert** the transition denoted by `pat` |
| `pat => { ... }` | run the nested clauses, with the bindings of `pat` |

A clause whose left-hand side does not match the subject is **skipped**. The
body is a list of guarded equations, not a list of unconditional actions.

**The re-resolution.** After the clauses have run, the automaton looks the
transition up again, because the rules may have replaced it. If a rule
inserted a new transition with the same key, the new target is the one the
automaton moves to. If the transition was removed, the original target is
used.

The default strategy is `first`: the rules are tried in source order, and the
first one that matches fires, once.

## `simple.quiver`: a plain automaton

The smallest program that recognizes something. It has no rules at all:

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q2 ]
  mapping => {
    (q0, a) -> (q1, 0)
    (q1, b) -> (q2, 0)
  }
}
```

It recognizes exactly the word `ab`:

| Input | Result |
|---|---|
| `ab` | `true` |
| `a` | `false` (the input ends in `q1`, which is not accepting) |
| `abc` | `false` (there is no transition out of `q2` on `c`) |
| `b` | `false` (there is no transition out of `q0` on `b`) |
| the empty input | `false` (`q0` is not accepting) |

## `rec.quiver`: the phase recognizer

The program recognizes `a+ b+ c+`: one or more `a`s, then one or more `b`s,
then one or more `c`s.

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q2 ]
  mapping => {
    (q0, a) -> (q0, 0)
    (q0, b) -> (q1, 0)
    (q1, b) -> (q1, 0)
    (q1, c) -> (q2, 0)
    (q2, c) -> (q2, 0)
  }
}
```

The program is nothing but the mapping. The three states are the three
phases, and the transitions between them enforce the order: `q0` reads the
`a`s, `q1` reads the `b`s, `q2` reads the `c`s and accepts.

| Input | Result |
|---|---|
| `aabbcc` | `true` |
| `abc` | `true` |
| `aaabbbccc` | `true` |
| `a` | `false` (the input ends in `q0`) |
| `aabb` | `false` (the input ends in `q1`) |
| `ac` | `false` (there is no transition out of `q0` on `c`) |
| `aabbccd` | `false` (there is no transition out of `q2` on `d`) |

**This program is not adaptive, and that is the point.** An earlier version
of the program also carried rules that incremented a counter on every
transition, but no rule ever read that counter back, so the rules changed
nothing about the language the automaton accepted. The version that ships
here keeps only the part that does the work. The next example is the one
where the counter matters.

## `abn.quiver`: an automaton that reads its own counter

The program recognizes `a^n b^n`: the same number of `a`s and `b`s. It is
the first example that is genuinely adaptive, because it **reads a value it
computed earlier** and lets that value decide what happens next.

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q2 ]
  mapping => {
    (q0, a) -> (q0, 0)
    (q0, b) -> (q1, 0)
    (q1, b) -> (q1, 0)
  }
}

# Count a's up; carry the same counter on (q0,b) for the b-phase
(q0, a) -> ($x, $n) => {
  (q0, a) -> ($x, $n) => _
  _ => (q0, a) -> ($x, $n + 1)
  _ => (q0, b) -> (q1, $n + 1)
}

# b-phase entry with counter == 1: last b, move to accepting q2
(q0, b) -> ($x, 1) => {
  _ => (q0, b) -> (q2, 0)
}

# b-phase entry with counter > 1: switch to q1, consuming one b (decrement)
(q0, b) -> ($x, $n) => {
  _ => (q0, b) -> (q1, $n - 1)
  _ => (q1, b) -> (q1, $n - 1)
}

# In q1 with counter == 1: last b, move to accepting q2
(q1, b) -> ($x, 1) => {
  _ => (q1, b) -> (q2, 0)
}

# In q1 with counter > 1: decrement counter, stay in q1
(q1, b) -> ($x, $n) => {
  _ => (q1, b) -> ($x, $n - 1)
}
```

Two things are worth noticing.

The counter is **carried across two transitions**. The rule on `(q0, a)`
writes the count of the `a`s onto the `b`-transition as well, so the number is
available at the moment the automaton switches to the `b`-phase.

The counter is **read back**. The pattern `($x, 1)` matches only when the
value is exactly one, and the pattern `($x, $n)` matches anything else. The
two rules are tried in source order, and the `first` strategy fires the first
one that matches, so the special case must come first.

The counter is **unbounded**. The integers of Quiver are arbitrary-precision,
so the count of the `a`s grows for as long as the input does, and no overflow
is possible.

The trace of `aabb`:

```
trace step state=q0 symbol=a transition=(q0,a)->(q0,0)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q0,1)
trace   insert transition (q0,b)->(q1,1)
trace   rule fired: (q0,a) -> ($x,$n)
trace step state=q0 symbol=a transition=(q0,a)->(q0,1)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q0,2)
trace   insert transition (q0,b)->(q1,2)
trace   rule fired: (q0,a) -> ($x,$n)
trace step state=q0 symbol=b transition=(q0,b)->(q1,2)
trace   insert transition (q0,b)->(q1,1)
trace   insert transition (q1,b)->(q1,1)
trace   rule fired: (q0,b) -> ($x,$n)
trace step state=q1 symbol=b transition=(q1,b)->(q1,1)
trace   insert transition (q1,b)->(q2,0)
trace   rule fired: (q1,b) -> ($x,1)
true
```

Step by step:

| Step | Symbol | Counter | What the rule does | State after |
|---|---|---|---|---|
| 1 | `a` | 0 → 1 | count the `a`, carry the count onto `(q0,b)` | `q0` |
| 2 | `a` | 1 → 2 | count the `a`, carry the count onto `(q0,b)` | `q0` |
| 3 | `b` | 2 → 1 | the counter is not one, so switch to `q1` and decrement | `q1` |
| 4 | `b` | 1 | the counter is one, so this is the last `b`: move to `q2` | `q2` |

The input ends in `q2`, which is accepting, so the answer is `true`. The input
`aab` stops one step earlier, in `q1`, which is not accepting, so the answer
is `false`.

| Input | Result |
|---|---|
| `ab` | `true` |
| `aabb` | `true` |
| `aaabbb` | `true` |
| `aab` | `false` (one `b` too few) |
| `abb` | `false` (one `b` too many) |
| `aaabbbb` | `false` |
| `a` | `false` |
| `b` | `false` |

## `guard.quiver`: guards

A rule may carry a `when` clause that decides whether the rule fires at all.
This program accepts a `b` only after at least one `a`:

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q2 ]
  mapping => {
    (q0, a) -> (q0, 0)
    (q0, b) -> (q1, 0)
  }
}

# Count a's up; carry the counter on (q0,b) too
(q0, a) -> ($x, $n) => {
  (q0, a) -> ($x, $n) => _
  _ => (q0, a) -> ($x, $n + 1)
  _ => (q0, b) -> (q1, $n + 1)
}

# Accept b only if counter > 0 (guard: $n is truthy when non-zero)
(q0, b) -> ($x, $n) when $n => {
  _ => (q0, b) -> (q2, 0)
}
```

The guard `when $n` is evaluated with the bindings of the pattern. An integer
is true when it is not zero, so the rule fires only when the counter is
positive.

| Input | Result |
|---|---|
| `ab` | `true` |
| `aab` | `true` |
| `aaab` | `true` |
| `b` | `false` (the counter is zero, so the guard fails) |
| the empty input | `false` |

## `guard-literal.quiver`: the boolean literals

The same mechanism with the two literals, to show that a guard is a value and
not a special form:

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q1 ]
  mapping => {
    (q0, a) -> (q1, 0)
  }
}

# Guard true: always fires, transitions to q1
(q0, a) -> ($x, $n) when true => {
  _ => (q1, a) -> (q1, 0)
}

# Guard false: never fires
(q0, a) -> ($x, $n) when false => {
  _ => (q1, a) -> (q2, 0)
}
```

The first rule always fires and keeps the automaton in the accepting state
`q1`. The second one never fires, so `q2` is never reached.

| Input | Result |
|---|---|
| `a` | `true` |
| the empty input | `false` |

## `emit.quiver`: output

`emit(expr)` prints the value of `expr` on the standard output and evaluates
to it. This program counts the `a`s and prints the count as it goes:

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q1 ]
  mapping => {
    (q0, a) -> (q0, 0)
    (q0, b) -> (q1, 0)
  }
}

# Count a's up and emit the counter
(q0, a) -> ($x, $n) => {
  (q0, a) -> ($x, $n) => _
  _ => (q0, a) -> ($x, $n + 1)
  _ => emit($n + 1)
}

# Accept b and move to q1
(q0, b) -> ($x, $n) => {
  _ => (q0, b) -> (q1, 0)
}
```

The input `aab` prints:

```
1
2
true
```

The printed numbers are the counter after each `a`, and the last line is the
answer of the recognition itself.

A clause whose right-hand side is an `emit` inserts nothing: the value that
was printed is not a transition, so there is nothing to insert. A clause
whose right-hand side *is* a transition inserts it, as the other examples
show.

## `primitives.quiver`: the adaptive primitives

Three constructs let a rule look at the automaton from the inside:

| Primitive | Meaning |
|---|---|
| `$state` | the current state, as a symbol |
| `transitions-from(state)` | the transitions leaving `state`, as a sequence of `(symbol, to)` pairs |
| `clone-automaton()` | a copy of the current automaton |

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q2 ]
  mapping => {
    (q0, a) -> (q0, 0)
    (q0, b) -> (q1, 0)
    (q1, b) -> (q2, 0)
    (q1, c) -> (q2, 0)
  }
}

# Emit current state on 'a'
(q0, a) -> ($x, $n) => {
  _ => emit($state)
  _ => (q0, a) -> ($x, $n + 1)
}

# Emit transitions from q0 on 'b'
(q0, b) -> ($x, $n) => {
  _ => emit(transitions-from(q0))
  _ => (q0, b) -> ($x, $n + 1)
}

# Clone automaton on 'c'
(q1, c) -> ($x, $n) => {
  _ => emit(clone-automaton())
  _ => (q2, c) -> ($x, 0)
}
```

The input `abc` prints:

```
q0
[seq (b,q1),(a,q0)]
<automaton states=3 transitions=4>
true
```

The first line is the state the automaton was in. The second is the sequence
of the transitions leaving `q0`, each of them a pair of the symbol and the
target; the order is the order of the transitions in the automaton, and the
`a`-transition is last because the rule on it removed it and inserted it
again. The third line is the copy of the automaton.

Note what `transitions-from` is **not**: it is not a query for one specific
transition. It returns everything that leaves a state, and the program has to
pick what it wants from the sequence.

## `sweep.quiver`: a sweep over the transitions

A **sweep** is a clause inside a rule body, written `each <pattern> => { ... }`.
The pattern is matched against every transition of the automaton, and the block
runs once per matching transition, with the pattern's variables bound per
firing.

This program retargets, in one shot, every transition that leaves `q0`:

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q2 ]
  mapping => {
    (q0, a) -> (q0, 0)
    (q0, b) -> (q1, 0)
    (q0, c) -> (q1, 0)
    (q1, z) -> (q2, 0)
  }
}

(q1, z) -> ($to, $n) => {
  each (q0, $s) -> ($t, $v) => {
    _ => emit($s)
    _ => (q0, $s) -> (q2, $v)
  }
}
```

The rule fires when the automaton takes the `z`-transition out of `q1`. Its
body sweeps the transitions that leave `q0`, and each firing prints the symbol
of the transition it swept and retargets it to `q2`.

The trace of `bz`:

```
trace step state=q0 symbol=b transition=(q0,b)->(q1,0)
trace   no rule fired
trace step state=q1 symbol=z transition=(q1,z)->(q2,0)
trace   sweep: (q0,$s) -> ($t,$v) on (q0,a)->(q0,0)
a
trace   insert transition (q0,a)->(q2,0)
trace   sweep: (q0,$s) -> ($t,$v) on (q0,b)->(q1,0)
b
trace   insert transition (q0,b)->(q2,0)
trace   sweep: (q0,$s) -> ($t,$v) on (q0,c)->(q1,0)
c
trace   insert transition (q0,c)->(q2,0)
trace   rule fired: (q1,z) -> ($to,$n)
true
```

The `sweep` line names the pattern and the transition the firing matched; the
letter that follows is the symbol the pattern bound to `$s` in that firing. The
three firings are independent: each one sees its own binding.

| Input | Result |
|---|---|
| `bz` | `true` |
| `b` | `false` (the input ends in `q1`, which is not accepting) |
| `bzz` | `false` (there is no transition out of `q2` on `z`) |

Two details are worth noticing. The sweep matches the transitions that were
there when it started, so the transitions it inserts are not swept by the same
sweep: without that, the program would never end. And the actions of the
firings apply to the live automaton, so a transition that one firing removed is
not there for the next one to act upon.

## `inspect.quiver`: the inspection primitives

Four primitives inspect a sequence, or the transitions of a state:

| Primitive | Meaning |
|---|---|
| `targets-from(state)` | the distinct targets of the transitions leaving `state` |
| `symbols-from(state)` | the distinct symbols of the transitions leaving `state` |
| `size(expr)` | the number of elements of a sequence |
| `is-empty(expr)` | whether a sequence has no elements |

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q2 ]
  mapping => {
    (q0, a) -> (q0, 0)
    (q0, b) -> (q1, 0)
    (q1, b) -> (q2, 0)
  }
}

# Inspect the automaton on the first `a`.
(q0, a) -> ($x, $n) => {
  _ => emit(targets-from(q0))
  _ => emit(symbols-from(q0))
  _ => emit(size(targets-from(q0)))
  _ => emit(is-empty(targets-from(q2)))
  _ => (q0, a) -> ($x, $n + 1)
}

# A guard that reads the automaton: the rule fires only when q0 still has at
# least one target, that is, when the automaton is not stuck.
(q0, b) -> ($x, $n) when size(targets-from(q0)) => {
  _ => (q0, b) -> (q2, 0)
}
```

The trace of `ab`:

```
trace step state=q0 symbol=a transition=(q0,a)->(q0,0)
[seq q0,q1]
[seq a,b]
2
true
trace   insert transition (q0,a)->(q0,1)
trace   rule fired: (q0,a) -> ($x,$n)
trace step state=q0 symbol=b transition=(q0,b)->(q1,0)
trace   insert transition (q0,b)->(q2,0)
trace   rule fired: (q0,b) -> ($x,$n)
true
```

The four lines are the four primitives. The targets of `q0` are `q0` and `q1`,
its symbols are `a` and `b`, the size of the sequence is two, and `q2` has no
outgoing transition at all, so the sequence of its targets is empty.

The guard of the second rule is an integer, and an integer is true when it is
not zero: the rule fires because `q0` still has two targets. A sequence used
directly as a guard follows the same rule, and is false when it is empty.

| Input | Result |
|---|---|
| `ab` | `true` |
| `a` | `false` (the input ends in `q0`, which is not accepting) |
| `b` | `true` (the guard passes and the `b`-transition is retargeted to `q2`) |

## `closure.quiver`: closures as transition values

A transition's value is an arbitrary term, and a term may be a function. This
program stores a closure on a transition and applies it later:

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q2 ]
  mapping => {
    (q0, a) -> (q1, 5)
  }
}

(q0, a) -> (q1, $n) => {
  (q0, a) -> (q1, $n) => _
  _ => (q1, b) -> (q1, fn $x => $x + $x)
}

(q1, b) -> (q1, $f) => {
  (q1, b) -> (q1, $f) => _
  _ => (q1, c) -> (q2, $f(21))
}
```

On the `a`, the rule stores the closure `fn $x => $x + $x` as the value of
the transition that the next symbol, `b`, will take. On the `b`, the rule
applies that closure to `21` and stores the result, `42`, on the transition
that the `c` will take.

| Input | Result |
|---|---|
| `abc` | `true` |
| `ab` | `false` (the input ends before the `c`) |
| `a` | `false` |
| `abcd` | `false` (there is no transition out of `q2` on `d`) |

## `strategy.quiver`: the rewriting strategies

The `first` strategy fires the first matching rule, once. The `repeat`
strategy fires it again and again, until no rule matches or the iteration
limit is reached:

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q1 ]
  strategy => repeatTest
  mapping => {
    (q0, a) -> (q1, 0)
  }
}

strategy repeatTest repeat max 3 => {
  (q0, a) -> ($x, $n) => {
    (q0, a) -> ($x, $n) => _
    _ => (q0, a) -> ($x, $n + 1)
  }
}
```

The FSM header selects the active strategy by label. The rule here is the
same counter rule of `rec.quiver`, and under `repeat max 3` it fires three times
in the same step:

```
trace step state=q0 symbol=a transition=(q0,a)->(q1,0)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q1,1)
trace   rule fired: (q0,a) -> ($x,$n)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q1,1)
trace   rule fired: (q0,a) -> ($x,$n)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q1,1)
trace   rule fired: (q0,a) -> ($x,$n)
true
```

The subject of a step is fixed, so the rule sees the same transition on
every repetition, and the counter is read from that same transition each
time. Under the `first` strategy the trace would show a single firing.

| Input | Result |
|---|---|
| `a` | `true` |
| the empty input | `false` |
| `b` | `false` (there is no transition out of `q0` on `b`) |

The three strategies are `first` (the default), `all` (every matching rule
fires) and `repeat [max N]`.

## `seq.quiver`: sequences

A sequence is a list of terms, written `$seq [ ... ]`. It appears in the
automaton as the list of accepting states, it is what `transitions-from`
returns, and it can be built and matched like any other term.

This program stores a sequence as the value of a transition and matches it
back:

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q2 ]
  mapping => {
    (q0, a) -> (q1, 0)
  }
}

# On 'a', create the (q1, b) transition and label it with a sequence.
(q0, a) -> ($x, $n) => {
  _ => (q1, b) -> (q2, $seq [ $x, $n ])
}

# On 'b', match the sequence that the first rule stored and emit it.
(q1, b) -> ($to, $seq [ $from, $n ]) => {
  _ => emit($seq [ $from, $n ])
}
```

The first rule creates a transition that the mapping did not have, and labels
it with the sequence `[ $x, $n ]`: the target state and the value of the
transition that was consulted. The second rule matches that sequence, item by
item, which is what binds `$from` and `$n`.

The trace of `ab`:

```
trace step state=q0 symbol=a transition=(q0,a)->(q1,0)
trace   insert transition (q1,b)->(q2,[seq q1,0])
trace   rule fired: (q0,a) -> ($x,$n)
trace step state=q1 symbol=b transition=(q1,b)->(q2,[seq q1,0])
[seq q1,0]
trace   rule fired: (q1,b) -> ($to,[seq $from,$n])
true
```

The stored sequence is `[q1, 0]`: the target of the `a`-transition was `q1`
and its value was `0`.

| Input | Result |
|---|---|
| `ab` | `true` (and it prints `[seq q1,0]`) |
| `a` | `false` (the input ends in `q1`) |
| `b` | `false` (there is no transition out of `q0` on `b`) |
| `aba` | `false` (there is no transition out of `q2` on `a`) |

The items of a sequence are atoms, so a compound item is written between
parentheses: `$seq [ (a, b), c ]`.

## `binding.quiver`: bindings

A binding `x <- t` matches the term `t` and names it: the matched term is
bound to the variable `$x`. A binding is meaningful in a pattern, that is, on
the left-hand side of a rule or of a clause.

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q1 ]
  mapping => {
    (q0, a) -> (q0, 0)
  }
}

(q0, a) -> ($x, $n) => {
  t <- (q0, a) -> ($x, $n) => {
    $t => _
    _ => (q0, b) -> (q1, $n + 1)
  }
}
```

The clause `t <- (q0, a) -> ($x, $n) => { ... }` binds the subject of the
step, which is the whole transition, to `$t`. The nested block then uses the
name: the clause `$t => _` removes exactly the transition the binding
captured, and the clause `_ => (q0, b) -> (q1, $n + 1)` creates the one that
accepts the rest of the input, carrying the counter over.

The trace of `ab`:

```
trace step state=q0 symbol=a transition=(q0,a)->(q0,0)
trace   remove transition (q0,a)->*
trace   insert transition (q0,b)->(q1,1)
trace   rule fired: (q0,a) -> ($x,$n)
trace step state=q0 symbol=b transition=(q0,b)->(q1,1)
trace   no rule fired
true
```

| Input | Result |
|---|---|
| `ab` | `true` |
| `a` | `false` (the input ends in `q0`) |
| `aa` | `false` (the `a`-transition was removed) |
| `b` | `false` (there is no transition out of `q0` on `b`) |

A binding in a value position is an error: the evaluator cannot extend an
environment.

## `adaptive-insert.quiver`: inserting a transition

The first of the three adaptive actions. A rule creates a transition that the
automaton did not have, so an input that was impossible becomes possible.

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q1 ]
  mapping => {
    (q0, a) -> (q0, 0)
  }
}

# On 'a', insert the (q0, b) transition that leads to the accepting state.
(q0, a) -> ($x, $n) => {
  _ => (q0, b) -> (q1, 0)
}
```

The mapping has no `(q0, b)` transition. The rule creates one when the
automaton takes the `a`-transition out of `q0`, so the `b` becomes readable
only after an `a`.

The trace of `ab`:

```
trace step state=q0 symbol=a transition=(q0,a)->(q0,0)
trace   insert transition (q0,b)->(q1,0)
trace   rule fired: (q0,a) -> ($x,$n)
trace step state=q0 symbol=b transition=(q0,b)->(q1,0)
trace   no rule fired
true
```

| Input | Result |
|---|---|
| `ab` | `true` |
| `b` | `false` (the transition does not exist yet) |
| `a` | `false` (the input ends in `q0`) |

The rule is what makes the difference: without it, the mapping alone rejects
`ab`, because there is no transition out of `q0` on `b`.

## `adaptive-threshold.quiver`: retargeting a transition

The second adaptive action. A rule changes **where** a transition leads, and
the decision depends on a value the automaton computed earlier.

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q2 ]
  mapping => {
    (q0, a) -> (q0, 0)
  }
}

# The counter reached the threshold: retarget the transition to q2.
(q0, a) -> ($x, 3) => {
  (q0, a) -> ($x, 3) => _
  _ => (q0, a) -> (q2, 0)
}

# Any other counter: count one more a.
(q0, a) -> ($x, $n) => {
  (q0, a) -> ($x, $n) => _
  _ => (q0, a) -> ($x, $n + 1)
}
```

The counter on the `a`-transition counts the `a`s. The first rule matches
only when the counter is exactly three, and it retargets the transition to the
accepting state `q2`. The second rule handles every other counter. Since the
rules are tried in source order and the `first` strategy fires the first one
that matches, the threshold wins when it applies.

The trace of `aaaa`:

```
trace step state=q0 symbol=a transition=(q0,a)->(q0,0)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q0,1)
trace   rule fired: (q0,a) -> ($x,$n)
trace step state=q0 symbol=a transition=(q0,a)->(q0,1)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q0,2)
trace   rule fired: (q0,a) -> ($x,$n)
trace step state=q0 symbol=a transition=(q0,a)->(q0,2)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q0,3)
trace   rule fired: (q0,a) -> ($x,$n)
trace step state=q0 symbol=a transition=(q0,a)->(q0,3)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q2,0)
trace   rule fired: (q0,a) -> ($x,3)
true
```

The first three `a`s raise the counter to three. The fourth one matches the
threshold rule, which retargets the transition to `q2`, and the automaton
moves to the accepting state.

| Input | Result |
|---|---|
| `aaaa` | `true` |
| `aaa` | `false` (the counter never reached the threshold) |
| `aaaaa` | `false` (there is no transition out of `q2` on `a`) |
| `a` | `false` |

## `adaptive-remove.quiver`: removing a transition

The third adaptive action. A rule deletes a transition, and the deletion is
permanent: the symbol it reacted to can no longer be read from that state.

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q1 ]
  mapping => {
    (q0, a) -> (q1, 0)
    (q1, a) -> (q1, 0)
  }
}

# On 'a' out of q1, remove the (q1, a) transition and do not insert it again.
(q1, a) -> ($x, $n) => {
  (q1, a) -> ($x, $n) => _
}
```

The mapping loops on `a` in the accepting state `q1`, so an input of any
number of `a`s would be accepted. The rule removes the `a`-transition out of
`q1` the first time it is taken, so a third `a` can never be read.

The trace of `aaa`:

```
trace step state=q0 symbol=a transition=(q0,a)->(q1,0)
trace   no rule fired
trace step state=q1 symbol=a transition=(q1,a)->(q1,0)
trace   remove transition (q1,a)->*
trace   rule fired: (q1,a) -> ($x,$n)
trace step state=q1 symbol=a transition=NULL (no transition)
false
```

The second `a` still leads to `q1`, because the rule removed the transition
but the automaton falls back to the original target when the transition is
gone. The third `a` has no transition to take at all, so the input is
rejected.

| Input | Result |
|---|---|
| `a` | `true` |
| `aa` | `true` |
| `aaa` | `false` (the transition was removed) |
| the empty input | `false` |

The rule is what makes the difference: without it, the mapping alone accepts
`aaa`.

## `include-main.quiver`: modularity

A source file may pull in another one with `include`. The main file declares
the automaton and pulls in the rules:

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q1 ]
  mapping => {
    (q0, a) -> (q0, 0)
    (q0, b) -> (q1, 0)
  }
}
include "include-sub.quiver"
```

The included file holds the rule that counts the `a`s:

```quiver
# Included rule file: counts a's up.
(q0, a) -> ($x, $n) => {
  (q0, a) -> ($x, $n) => _
  _ => (q0, a) -> ($x, $n + 1)
}
```

The declarations of the included file are merged into the program, so the
program has the same automaton and the same rule as if the rule had been
written in the main file. The trace of `aa` shows the counter growing:

```
trace step state=q0 symbol=a transition=(q0,a)->(q0,0)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q0,1)
trace   rule fired: (q0,a) -> ($x,$n)
trace step state=q0 symbol=a transition=(q0,a)->(q0,1)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q0,2)
trace   rule fired: (q0,a) -> ($x,$n)
false
```

| Input | Result |
|---|---|
| `a` | `false` (the run ends in `q0`) |
| `aa` | `false` (the run ends in `q0`) |
| `b` | `true` (the run ends in `q1`) |
| `ab` | `true` (the run ends in `q1`) |
| `ba` | `false` (`q1` has no `a`-transition) |
| the empty input | `false` |

The path is resolved relative to the directory of the including file, and a
file that includes itself, directly or indirectly, is reported as a cycle.

## `nondet.quiver`: non-determinism without rules

The simplest non-deterministic program: two `(q0, a)` transitions with no
rewrite rules at all.

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q2 ]
  mapping => {
    (q0, a) -> (q1, 0)
    (q0, a) -> (q2, 0)
    (q1, b) -> (q1, 0)
  }
}
```

The mapping has two transitions out of `q0` on `a`. With `--nondet`, both are
followed in parallel:

* Path via `q1`: ends in `q1`, which is **not** accepting.
* Path via `q2`: ends in `q2`, which **is** accepting.

Since at least one path accepts, the input is accepted. The trace of `a`:

```
trace step state=q0 symbol=a transition=(q0,a)->(q1,0)
trace   no rule fired
trace step state=q0 symbol=a transition=(q0,a)->(q2,0)
trace   no rule fired
true
```

| Input | Deterministic | Non-deterministic |
|---|---|---|
| `a` | `false` (only the first transition is used, and `q1` is not accepting) | `true` (the `q2` path accepts) |
| `ab` | `false` (the `q1` path ends in `q1`, which is not accepting) | `false` (the `q1` path ends in `q1`, the `q2` path dies) |
| `ac` | `false` | `false` (neither path has a `c`-transition) |
| `b` | `false` (there is no transition out of `q0` on `b`) | `false` (the same) |

Run with:

```bash
java -jar build/libs/quiver-1.1.0.jar -i src/main/resources/nondet.quiver a --nondet
```

## `nondet-adaptive.quiver`: per-path adaptive actions

Two `(q0, a)` transitions, each leading to a state with its own adaptive rule.
Each path independently rewrites its own transition, and neither path's
insert/remove leaks into the other.

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q3 ]
  mapping => {
    (q0, a) -> (q1, 0)
    (q0, a) -> (q2, 0)
    (q1, b) -> (q1, 0)
    (q2, c) -> (q2, 0)
  }
}

# Path via q1: on 'b', retarget (q1,b) to accepting q3.
(q1, b) -> ($x, $n) => {
  (q1, b) -> ($x, $n) => _
  _ => (q1, b) -> (q3, 0)
}

# Path via q2: on 'c', retarget (q2,c) to accepting q3.
(q2, c) -> ($x, $n) => {
  (q2, c) -> ($x, $n) => _
  _ => (q2, c) -> (q3, 0)
}
```

The trace of `ab`:

```
trace step state=q0 symbol=a transition=(q0,a)->(q1,0)
trace   no rule fired
trace step state=q0 symbol=a transition=(q0,a)->(q2,0)
trace   no rule fired
trace step state=q1 symbol=b transition=(q1,b)->(q1,0)
trace   remove transition (q1,b)->*
trace   insert transition (q1,b)->(q3,0)
trace   rule fired: (q1,b) -> ($x,$n)
trace step state=q2 symbol=b transition=NULL (no transition)
true
```

The first symbol forks the run into two paths, one through `q1` and one
through `q2`. The second symbol kills the `q2` path, which has no `b`
transition, and the `q1` path rewrites its own transition to target `q3`,
which is accepting.

| Input | Deterministic | Non-deterministic |
|---|---|---|
| `ab` | `true` (only the `q1` path is taken, and its rule reaches `q3`) | `true` (the `q1` path accepts) |
| `ac` | `false` (only the `q1` path is taken, and it has no `c`-transition) | `true` (the `q2` path reaches `q3`) |
| `a` | `false` (the run ends in `q1`) | `false` (the run ends in `q1` or `q2`, neither accepting) |

The key point: path q1's retargeting of `(q1,b)` does not affect path q2, and vice versa. Each path has its own automaton clone.

Run with:

```bash
java -jar build/libs/quiver-1.1.0.jar -i src/main/resources/nondet-adaptive.quiver ab --nondet
java -jar build/libs/quiver-1.1.0.jar -i src/main/resources/nondet-adaptive.quiver ac --nondet
```

## `nondet-strategy.quiver`: non-determinism with the `all` strategy

The `all` strategy fires every matching rule on each path's subject:

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q1, q2 ]
  strategy => allTest
  mapping => {
    (q0, a) -> (q1, 0)
    (q0, a) -> (q2, 0)
  }
}

strategy allTest all => {
  (q0, a) -> ($x, $n) => {
    _ => emit($n + 1)
  }
}
```

On input `a`, two paths are created (q1 and q2), each firing the rule on its
own automaton clone. Both q1 and q2 are accepting, so the input is accepted.
The trace shows the rule firing once per path:

```
trace step state=q0 symbol=a transition=(q0,a)->(q1,0)
1
trace   rule fired: (q0,a) -> ($x,$n)
trace step state=q0 symbol=a transition=(q0,a)->(q2,0)
1
trace   rule fired: (q0,a) -> ($x,$n)
true
```

The deterministic run follows only the first transition, so it prints the
value once:

```
trace step state=q0 symbol=a transition=(q0,a)->(q1,0)
1
trace   rule fired: (q0,a) -> ($x,$n)
true
```

| Input | Deterministic | Non-deterministic |
|---|---|---|
| `a` | `true` (the value is printed once) | `true` (the value is printed once per path) |
| `b` | `false` (there is no transition out of `q0` on `b`) | `false` (the same) |

## `nondet-repeat.quiver`: non-determinism with the `repeat` strategy

The `repeat` strategy re-applies the first matching rule up to `max` times on
each path:

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q1, q2 ]
  strategy => repeatTest
  mapping => {
    (q0, a) -> (q1, 0)
    (q0, a) -> (q2, 0)
  }
}

strategy repeatTest repeat max 3 => {
  (q0, a) -> ($x, $n) => {
    (q0, a) -> ($x, $n) => _
    _ => (q0, a) -> ($x, $n + 1)
  }
}
```

The subject of a step is fixed, so every repetition rewrites the same
transition, and the value of the transition stays `1`: every repetition reads
the value of the transition the run consulted, which is `0`, and writes
`0 + 1`. The trace of `a` shows the three repetitions of each path:

```
trace step state=q0 symbol=a transition=(q0,a)->(q1,0)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q1,1)
trace   rule fired: (q0,a) -> ($x,$n)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q1,1)
trace   rule fired: (q0,a) -> ($x,$n)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q1,1)
trace   rule fired: (q0,a) -> ($x,$n)
trace step state=q0 symbol=a transition=(q0,a)->(q2,0)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q2,1)
trace   rule fired: (q0,a) -> ($x,$n)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q2,1)
trace   rule fired: (q0,a) -> ($x,$n)
trace   remove transition (q0,a)->*
trace   insert transition (q0,a)->(q2,1)
trace   rule fired: (q0,a) -> ($x,$n)
true
```

| Input | Deterministic | Non-deterministic |
|---|---|---|
| `a` | `true` (three repetitions on the first path) | `true` (three repetitions on each path) |
| `b` | `false` (there is no transition out of `q0` on `b`) | `false` (the same) |

Run with:

```bash
java -jar build/libs/quiver-1.1.0.jar -i src/main/resources/nondet-strategy.quiver a --nondet
java -jar build/libs/quiver-1.1.0.jar -i src/main/resources/nondet-repeat.quiver a --nondet
```
