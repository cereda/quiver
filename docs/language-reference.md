# The Quiver language reference

This document describes the Quiver language as implemented by this interpreter: the
lexical structure, the grammar, the meaning of every construct, and the
operational semantics of recognition.

Quiver is a small, type-safe term-rewriting language for **adaptive automata**, that is,
finite automata whose transition function may change while they run. A Quiver program is a set
of rewrite rules plus, optionally, a description of the automaton the rules operate
on. The rules consult the automaton's current transition, and rewrite it.

The reference is normative for this implementation: every construct and every
semantic choice is described here.

## Table of contents

1. [A first program](#a-first-program)
2. [Lexical structure](#lexical-structure)
3. [Terms](#terms)
4. [Programs](#programs)
5. [The automaton](#the-automaton)
6. [Rules and clauses](#rules-and-clauses)
7. [Recognition](#recognition)
8. [Strategies](#strategies)
9. [Adaptive primitives](#adaptive-primitives)
10. [The inspection primitives](#the-inspection-primitives)
11. [Errors](#errors)
12. [The command-line interface](#the-command-line-interface)
13. [The REPL](#the-repl)
14. [Appendix: the grammar in one block](#appendix-the-grammar-in-one-block)

## A first program

The phase recognizer, which reads one or more `a`s, then one or more
`b`s, then one or more `c`s, is the canonical example:

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

Read it as follows:

* The `@fsm` header describes the automaton: it starts at `q0`, accepts at `q2`,
  and has five transitions. Each transition maps a `(state, symbol)` pair to a
  `(state, value)` pair; the value is the transition's *label*, and the rules
  that rewrite the automaton read it and write it back.
* The states are the phases of the recognizer: `q0` reads the `a`s, `q1` reads
  the `b`s, and `q2` reads the `c`s and accepts.

This program has no rules, and it does not need any: the mapping alone
recognizes `a+ b+ c+`. The rules come in when the automaton has to consult a
value that it computed earlier, which is what
[Rules and clauses](#rules-and-clauses) describes.

Run it with:

```bash
java -jar build/libs/quiver-1.1.0.jar -i src/main/resources/rec.quiver aabc
```

The companion document [the examples](examples.md) walks through every example
program step by step, including the ones that do carry rules.

## Lexical structure

Source files are UTF-8 text. Whitespace (spaces, tabs, newlines) separates tokens
and is otherwise insignificant.

### Comments

A `#` starts a comment that runs to the end of the line.

```quiver
# The phase that counts the a's.
(q0, a) -> ($x, $n) => { ... }   # a trailing comment
```

### Identifiers and keywords

An identifier starts with a letter or with one of `$ @ . /`, and continues with
letters, digits, or one of `$ . _ /`. Identifiers are case-sensitive.

| Form | Meaning |
|---|---|
| `q0`, `a`, `true` | a literal symbol (or a keyword, see below) |
| `$x`, `$n` | a **variable** |
| `$seq` | keyword: a sequence literal |
| `$state` | keyword: the adaptive primitive that reads the current state |
| `@fsm` | keyword: the automaton header |
| `transitions-from`, `targets-from`, `symbols-from`, `clone-automaton` | keywords: the adaptive primitives |
| `is-empty`, `size` | keywords: the sequence primitives |
| `each` | keyword: the sweep clause |

The keywords `fn`, `when`, `true`, `false`, `emit` and `include` are reserved.

### Numbers

An integer literal is a sequence of digits. A leading `-` is the subtraction
operator, not part of the literal: `-1` is parsed as the unary minus applied to
`1`, and `a-1` is the subtraction of `1` from `a`. A leading `+` is the unary plus
and evaluates to its operand.

Integers are **arbitrary-precision**: a literal may have as many digits as the
program needs, and the arithmetic on transition values never overflows and never
wraps around. The counters a program carries are therefore unbounded. The single
exception is the iteration cap of the `repeat` strategy, which is a loop bound
and must fit in a machine integer; a larger literal is rejected with an error.

### Strings

A double-quoted string holds a file path, used by the `include` directive. The
escapes `\n`, `\t`, `\"` and `\\` are understood.

### Operators and punctuation

| Token | Use |
|---|---|
| `( )` | grouping, pairs, function application |
| `{ }` | rule bodies, nested clause blocks, the FSM header |
| `[ ]` | sequence literals |
| `,` | separator inside pairs, sequences, lists and parameter lists |
| `;` | optional separator inside the FSM header and inside a rule body |
| `\|` | the `\|$seq\|` special form |
| `->` | the arrow: a transition, `(from, symbol) -> (to, value)` |
| `=>` | the double arrow: the rule, clause and header separator |
| `<-` | the assignment: the binding `x <- t` |
| `+` `-` | addition and subtraction |
| `_` | the wildcard |

Whitespace is optional around the operators: `(q0,a)->(q1,0)` and `(q0, a) -> (q1, 0)`
are the same program.

## Terms

A **term** is the expression language of Quiver. Terms appear as rule patterns, as
clause left-hand sides, and as the values inserted into the automaton.

### Symbols and variables

A plain identifier denotes a literal **symbol**: `q0`, `a`, `b`. Symbols are
matched exactly.

An identifier that starts with `$` denotes a **variable**: `$x`, `$n`. A variable
matches any term and binds it; a variable that is already bound must match the
same term again. This is the consistency check of term rewriting, and it keeps the
bindings of a match unambiguous.

The wildcard `_` matches any term without binding it.

```quiver
(q0, a)    # a literal symbol
$x         # a variable
_          # the wildcard
```

### Pairs and arrows

`(a, b)` is a **pair**. `a -> b` is an **arrow**; in this implementation an arrow is
a pair of pairs, and it is the shape of a transition:

```quiver
(q0, a) -> (q1, 0)
```

### Sequences

`$seq [ a, b, c ]`, or simply `[ a, b, c ]`, is a **sequence** of terms. The
special form `$seq [ |$seq| ]` denotes the symbol `|$seq|`.

The items of a sequence are **atoms**: the comma separates them, so a compound item
(a pair or an arrow) is written between parentheses.

```quiver
$seq [ a, b ]        # a sequence of two symbols
$seq [ (a, b), c ]   # a sequence of a pair and a symbol
```

A sequence literal is a term: it matches a sequence, term by term, and it evaluates
to a sequence of values.

### Arithmetic

`a + b` and `a - b` are arithmetic expressions over integers. They are *evaluated*,
not matched: an arithmetic term never matches a pattern.

### Bindings

`x <- t` is a **binding**: it matches `t` and binds the matched term to the
variable `$x` (the explicit form `$x <- t` binds the same variable).

```quiver
t <- (q0, a) -> ($x, $n)   # binds the whole transition to $t
```

A binding is meaningful in a **pattern**, the left-hand side of a rule or of a
clause. In a value position it is an error: the evaluator cannot extend an
environment.

### Functions

`fn p1, p2 => body` is a function literal; `f(arg)` applies one. A function
captures the environment in which it was defined, and its body is evaluated in the
captured environment extended with the argument.

```quiver
fn $x => $x + $x
```

A function can be stored in a transition and applied later, which is how a
transition's value can carry a computation.

### The boolean literals

`true` and `false` are the boolean values. They are used in guards.

## Programs

A program is a sequence of declarations, in any order.

### The FSM header

```quiver
@fsm => {
  start => q0
  accepting => $seq [ q1 ]
  mapping => {
    (q0, a) -> (q0, 0)
    (q0, b) -> (q1, 0)
  }
  strategy => myStrategy
}
```

| Key | Meaning |
|---|---|
| `start` | the initial state (required) |
| `accepting` | the list of accepting states: `$seq [ q1, q2 ]` or `( q1, q2 )` |
| `mapping` | the transitions, one arrow per entry |
| `strategy` | the label of the strategy to activate (optional) |

The `mapping` block is the automaton's transition function. Each entry must be an
arrow `(state, symbol) -> (state, value)` over symbols; anything else is an error.
The entries are separated by whitespace. A comma after an entry belongs to the pair
syntax of the entry's value, not to the block.

### Strategies

A `strategy` declaration groups rules under a name and a rewriting strategy:

```quiver
strategy repeatTest repeat max 1 => {
  (q0, a) -> ($x, $n) => {
    (q0, a) -> ($x, $n) => _
    _ => (q0, a) -> ($x, $n + 1)
  }
}
```

The syntax is `strategy <label> <kind> [max <n>] => { rules }`, where the kind is
`first`, `all` or `repeat`, and `max` bounds the iterations of a `repeat` strategy.
The FSM header's `strategy` key selects the active strategy by label.

### Rules

```quiver
pattern [when guard] => { clauses }
```

The pattern is matched against the *subject* of a step (see
[Recognition](#recognition)); the guard is an optional condition; the body is a
sequence of clauses.

### Includes

```quiver
include "other-file.quiver"
```

The included file's declarations are merged into the program. The path is resolved
relative to the directory of the including file. The resolved path is canonical,
so the same file reached through different spellings is one file: it is merged
once, even when it is reached through several paths; a file that includes
itself, directly or indirectly, is a cycle and is reported as an error.

## The automaton

The automaton is a finite automaton with a *mutable* transition function:

* **states**: a set of names; the initial state, the accepting states and every
  state named by a transition are all added when the automaton is built;
* **accepting states**: the set of final states;
* **transitions**: a list of `(from, symbol) -> (to, value)` entries, in insertion
  order.

Unlike a classical deterministic automaton, several transitions may leave the
same `(state, symbol)` pair. Each such transition is a distinct path the
automaton may take. When the automaton runs deterministically (the default),
the first matching transition wins; when it runs non-deterministically
(`--nondet`), every matching transition is followed in parallel on its own
copy of the automaton, so insert/remove actions on one path never leak into
another.

The value of a mapping entry is evaluated when the automaton is built, in the
order of the entries, so an entry whose value consults the automaton
(`transitions-from`, `clone-automaton`) sees the entries declared before it.

A transition's **value** is an arbitrary term: an integer (a counter), a closure,
a sequence, a symbol. The rules read it, compute with it, and write it back. The
integers are arbitrary-precision, so a counter is unbounded.

## Rules and clauses

### The subject

Every step of the recognition has a **subject**: the transition the automaton is
about to take, as a term.

```
((from, symbol), (to, value))
```

The rule's pattern is matched against this subject. A pattern of the shape
`(q0, a) -> ($x, $n)` therefore matches the transition out of `q0` on `a`, and binds
`$x` to the source state and `$n` to the transition's value.

### Type compatibility

A rule fires only when the pattern's head type is compatible with the subject's
type: a pattern rooted at a symbol matches a symbol, a pattern rooted at a pair (or
an arrow) matches a pair, and so on. A pattern rooted at a wildcard or a variable
matches any type. This is the *type-safe* discipline of Quiver: a rule that can never
match is never consulted.

### Guards

```quiver
(q0, b) -> ($x, $n) when $n => {
  _ => (q0, b) -> (q1, 0)
}
```

The guard is evaluated with the bindings of the pattern. Its truthiness follows
these rules:

* a boolean is its own truth value;
* an integer is true when it is not zero;
* a sequence is true when it is not empty;
* any other value is true.

A rule without a guard always passes it.

### Clauses

The body of a rule is a sequence of **clauses**, each of the form `left => right`.

The **left-hand side is a pattern**, matched against the subject:

> **A clause whose left-hand side does not match the subject is skipped.** The body
> of a rule is a sequence of guarded equations, not a sequence of unconditional
> actions.

The **right-hand side** is the action:

| Form | Action |
|---|---|
| `_` | **remove** the transition denoted by the left-hand side |
| `term` | **insert** the transition denoted by `term` |
| `{ clauses }` | execute the nested clauses, in order, with the bindings of the left-hand side |

The left-hand side denotes a transition when it evaluates to the shape of the
subject, that is to an arrow `((from, symbol), (to, value))`; the transition it
denotes is the one the step consulted. A left-hand side that evaluates to
anything else denotes no transition, and the removal does nothing.

The right-hand side denotes a transition when it evaluates to an arrow over
symbols, `(from, symbol) -> (to, value)`. Inserting such a transition replaces any
transition with the same `(from, symbol)` key.

### Worked example

```quiver
(q0, a) -> ($x, $n) => {
  (q0, a) -> ($x, $n) => _
  _ => (q0, a) -> ($x, $n + 1)
}
```

When the automaton is about to take the `a`-transition out of `q0`:

1. the first clause's left-hand side, which is the very transition that was
   consulted, matches the subject, so the clause applies: the transition is
   **removed**;
2. the second clause's left-hand side is the wildcard, which matches anything, so
   the clause applies: the transition is **inserted** again, with its value
   incremented.

A clause whose left-hand side does not match is skipped. If the rule above fired
while the automaton was taking the `b`-transition out of `q0`, the first clause
would not apply, and the `b`-transition would survive untouched.

### The sweep

A clause may be a **sweep**, written `each <pattern> => { clauses }`. The sweep
matches its pattern against every transition of the automaton, and runs the
nested clauses once per matching transition:

```quiver
(q1, z) -> ($to, $n) => {
  each (q0, $s) -> ($t, $v) => {
    _ => (q0, $s) -> (q2, $v)
  }
}
```

The sweep differs from an ordinary clause in three ways:

* the pattern is matched against the **transitions of the automaton**, not
  against the subject of the rule; the subject plays no part in it;
* the pattern is matched against each transition in the shape of the subject,
  `((from, symbol), (to, value))`, so a pattern that matches every transition
  leaving a state is written `(q0, $s) -> ($t, $v)`;
* the nested clauses run **once per matching transition**, with the bindings of
  the pattern extended by the bindings of that firing.

The transitions that match are collected **before the first firing**, so the
sweep sees the automaton as it was when it started; the actions of the firings,
on the other hand, apply to the live automaton. A transition that an earlier
firing removed is simply not there when a later firing looks for it, and a
transition that a firing inserted is not swept by the same sweep.

Each firing starts from the environment of the enclosing clause, extended with
the bindings of the sweep's pattern: the bindings of one firing do not leak
into the next. A sweep that matches no transition does nothing at all.

The sweep is how a program performs a **bulk action**: one action, applied to
many transitions at once.

## Recognition

The interpreter recognizes an input string symbol by symbol:

1. the automaton starts at its start state;
2. for each symbol of the input:
   1. the transition leaving the current state on that symbol is looked up; if
      there is none, the input is **rejected**;
   2. the transition becomes the **subject**, and the rules of the active strategy
      are consulted: every rule whose pattern matches the subject, whose type is
      compatible, and whose guard passes, is a candidate; the strategy decides
      which candidates fire (see [Strategies](#strategies)), and the fired rules
      apply their clauses;
   3. the automaton moves to the target of the transition, which the rules may
      have rewritten. If the rules removed the transition, the original target
      is used;
3. when the input is exhausted, it is **accepted** if and only if the automaton is
   in an accepting state.

### Non-deterministic recognition (`--nondet`)

With the `--nondet` flag, the interpreter runs in non-deterministic mode.
Instead of tracking a single current state, it tracks a *frontier* of (state,
automaton) pairs. For each input symbol, every transition leaving the current
state on that symbol is followed in parallel:

* Each transition is a path of its own, so a program with two identical
  transitions out of a state explores two paths.
* Each path gets its own clone of the automaton, so insert/remove actions fired
  by one path's rules never affect another path.
* A path dies when no transition exists for its current state and symbol.
* Acceptance is NFA-style: the input is accepted if *any* surviving path ends in
  an accepting state.

Strategies operate per path: each path evaluates its rules independently on its
own automaton clone.

The empty input is accepted exactly when the start state is accepting.

### Fuel

Every transition the run follows is a step, and every step consumes one unit of
fuel. The deterministic recognizer follows at most one transition per input
symbol, and the non-deterministic one follows one per path, so a symbol may
account for several steps there.

The budget (`--max-steps`, 10000 by default) bounds the number of steps a run
may take; when it is exhausted, the run is aborted with an error that names the
number of steps taken. The budget protects the interpreter against a program
that rewrites the automaton forever, and against a non-deterministic program
whose frontier grows without bound.

The repetitions of a `repeat` strategy are bounded by the iteration limit of
the strategy, and not by the fuel.

### Tracing

With `--trace`, every step, every rule that fires, and every transition that is
inserted or removed are reported on the standard error stream.

## Strategies

The active strategy decides *which* rules fire on a subject.

| Strategy | Behaviour |
|---|---|
| `first` | the rules are tried in source order; the **first** one that matches fires, once |
| `all` | **every** rule that matches fires, in source order |
| `repeat` | the first matching rule fires repeatedly, until no rule matches or the iteration limit (`max N`) is reached |

`first` is the default: with no `strategy` declaration and no `strategy` key in the
FSM header, all the top-level rules form a single `first` strategy.

When the FSM header names a strategy that the program does not declare, the
interpreter falls back on that same default: the top-level rules form a single
`first` strategy.

The strategy decides which rules fire on *each subject*, where a subject is the
transition the automaton is about to take. In non-deterministic mode
(`--nondet`), every path has its own subject and its own automaton clone, so
strategies operate independently per path.

A `repeat` strategy fires the first matching rule again and again, up to the
iteration limit. The subject of a step is fixed, so every repetition matches
the same transition and sees the same bindings.

## Adaptive primitives

Four constructs read the automaton from inside a rule.

| Primitive | Meaning |
|---|---|
| `$state` | the current state, as a symbol |
| `transitions-from(state)` | the transitions leaving `state`, as a sequence of `(symbol, to)` pairs |
| `clone-automaton()` | a copy of the current automaton |
| `emit(expr)` | prints the value of `expr` on the standard output, and evaluates to it |

```quiver
(q0, a) -> ($x, $n) => {
  _ => emit($state)
  _ => (q0, a) -> ($x, $n + 1)
}
```

## The inspection primitives

Four constructs inspect a sequence, or the transitions of a state.

| Primitive | Meaning |
|---|---|
| `targets-from(state)` | the **distinct targets** of the transitions leaving `state`, as a sequence of symbols |
| `symbols-from(state)` | the **distinct symbols** of the transitions leaving `state`, as a sequence of symbols |
| `size(expr)` | the number of elements of the sequence `expr`, as an integer |
| `is-empty(expr)` | whether the sequence `expr` has no elements, as a boolean |

The order of the elements is the order of the transitions in the automaton, and
the repetitions are dropped: `targets-from` and `symbols-from` return each
target and each symbol once.

```quiver
(q0, a) -> ($x, $n) => {
  _ => emit(targets-from(q0))
  _ => emit(size(symbols-from(q0)))
  _ => (q0, a) -> ($x, $n + 1)
}
```

`size` and `is-empty` apply to sequences only; used on a value of another
type, they are an error. `is-empty` is the explicit emptiness test, while a
sequence used directly as a guard follows the truthiness rule and is false
when it is empty.

## Errors

Every error is reported with the position of the offending construct:

```
error: line 3:col 12: Unexpected character '<'
```

The interpreter reports a Quiver error, never an unchecked exception, for:

* a lexical error (an unexpected character, an unterminated string);
* a syntax error (a missing token, an unknown FSM key, an unknown strategy kind);
* a malformed FSM mapping (an entry that is not an arrow, or an arrow whose source
  or target is not a pair of symbols);
* an arithmetic operation over a value that is not an integer;
* an inspection primitive used on a value of the wrong type (`size` and
  `is-empty` over anything but a sequence, and `targets-from` and
  `symbols-from` over anything but a state symbol);
* a binding in a value position;
* a circular `include`;
* an exhausted fuel budget.

## The command-line interface

```
quiver -i <file.quiver> <input-string> [--trace] [--nondet] [--max-steps N]
```

| Option | Meaning |
|---|---|
| `-i <file.quiver>` | the Quiver source file to execute (required) |
| `<input-string>` | the input to recognize (required) |
| `--trace` | print the step trace on the standard error stream |
| `--nondet` | non-deterministic recognition (NFA-style, any path accepts) |
| `--max-steps N` | the fuel budget; 10000 by default |
| `-h`, `--help` | print the usage summary |

The command prints `true` when the input is recognized and `false` otherwise, and
exits with status 0. An error is reported on the standard error stream and the
process exits with status 1. A usage error, such as a missing argument, a
malformed option value or an extra argument, is reported on the standard error
stream, followed by the usage summary, and the process exits with status 1.

The options may appear in any order. The first argument that is none of the
options above is the input string, and a second one is a usage error. The value
of `--max-steps` must be an integer; a value that is not one is a usage error.
A budget of zero or less aborts the run at its first step.

The `include` directives of the source file are resolved relative to the directory
of that file.

The executable jar starts the interactive REPL when it is run without arguments.

## The REPL

The interactive read-eval-print loop loads a program and recognizes inputs against
it:

```bash
java -jar build/libs/quiver-1.1.0.jar
```

| Command | Meaning |
|---|---|
| `:load <file.quiver>` | load a source file: its rules, strategies and FSM header |
| `:rules` | list the rules of the active strategy, and the top-level rules it does not consult |
| `:strategy <kind>` | set the strategy: `first`, `all` or `repeat [max N]` |
| `:trace on` / `:trace off` | toggle the step trace |
| `:nondet on` / `:nondet off` | toggle the non-deterministic recognition |
| `:step <input>` | recognize an input, printing the trace |
| `:recognize <input>` | recognize an input, printing `true` or `false` |
| `:debug <input>` | step through an input, one symbol at a time |
| `:reset` | forget the loaded rules and the automaton |
| `:help` | print the summary |
| `:quit` | leave the REPL |

Any other line is treated as an input to recognize against the loaded automaton.
The `:strategy` command with no argument prints its usage instead of being taken for
an input.

The `:rules` command reports the rules of the active strategy, which are the ones
the run consults. When the program declares top-level rules that the active
strategy does not consult, they are listed separately, under a heading that says
so.

In debug mode, `:step` consumes one symbol, `:inspect` shows the current state,
the position and the automaton, `:continue` runs to completion, and `:quit` returns
to the REPL.

The `:nondet` command toggles the same non-deterministic mode as the `--nondet`
option of the command line, and the toggle applies to the inputs recognized from
then on. The `:reset` command turns it off along with the loaded program. The
interactive debug mode always follows a single path, so it runs deterministically
even when the non-deterministic mode is on.

## Appendix: the grammar in one block

```ebnf
program        ::= declaration*

declaration    ::= fsm | strategy | rule | include

fsm            ::= '@fsm' '=>' '{' fsm-entry* '}'
fsm-entry      ::= 'start' '=>' symbol
                 | 'accepting' '=>' symbol-list
                 | 'mapping' '=>' '{' arrow* '}'
                 | 'strategy' '=>' symbol
symbol-list    ::= '$seq' '[' symbol (',' symbol)* ']'
                 | '(' symbol (',' symbol)* ')'

strategy       ::= 'strategy' symbol ('first' | 'all' | 'repeat') ['max' integer]
                   '=>' '{' rule* '}'

rule           ::= pattern ['when' term] '=>' '{' clause* '}'
clause         ::= pattern '=>' (term | '_' | '{' clause* '}')
                 | 'each' pattern '=>' '{' clause* '}'

include        ::= 'include' string

pattern        ::= term
term           ::= symbol
                 | variable
                 | '_'
                 | '(' term ',' term ')'
                 | '$seq' '[' [ atom (',' atom)* ] ']'
                 | '$seq' '[' '|' '$seq' '|' ']'
                 | '[' [ atom (',' atom)* ] ']'
                 | term '->' term
                 | term '+' term
                 | term '-' term
                 | binding
                 | 'fn' identifier (',' identifier)* '=>' term
                 | term '(' term ')'
                 | 'emit' '(' term ')'
                 | '$state'
                 | 'transitions-from' '(' term ')'
                 | 'targets-from' '(' term ')'
                 | 'symbols-from' '(' term ')'
                 | 'is-empty' '(' term ')'
                 | 'size' '(' term ')'
                 | 'clone-automaton' '(' ')'
                 | integer

atom           ::= symbol
                 | variable
                 | '_'
                 | integer
                 | '(' term ')'

binding        ::= identifier '<-' term
symbol         ::= identifier | 'true' | 'false'
variable       ::= '$' identifier
identifier     ::= letter (letter | digit | '$' | '.' | '_' | '/')*
                 | ('$' | '@' | '.' | '/') (letter | digit | '$' | '.' | '_' | '/')*
```
