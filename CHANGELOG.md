# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Every release
is a `## [x.y.z] - yyyy-mm-dd` heading followed by one list of changes per category, and
the categories are `### Added`, `### Changed`, `### Removed` and `### Fixed`.

The **version of the build** is derived from this file: the spotless-changelog plugin
reads the section below and computes the version of the release it describes. A release
is declared by promoting the `[Unreleased]` section to a version heading.

## [Unreleased]

## [1.3.0] - 2026-11-15

### Added

- The **set algebra**: a set is now a value that a program manipulates. The
  primitive `set(seq)` builds a set out of a sequence, dropping the
  repetitions and keeping the order of the elements; `union(left, right)`,
  `intersection(left, right)` and `difference(left, right)` combine two sets,
  and `contains(set, element)` tells whether an element belongs to a set. The
  primitives `size` and `is-empty` now accept a set as well as a sequence.
- The **set variable**, written `%x`: an unbound set variable matches a set
  and binds it, and a bound set variable matches the elements of the set it
  is bound to. The set variable of a sweep collects the values it matches
  across the firings, so that a sweep may gather a collection.
- The **bulk actions**: an insertion whose symbol (or whose target state) is a
  set inserts one transition per element of the set, and one per combination
  of the two sets when both of them are sets; a removal whose symbol is a set
  removes every transition under those symbols.
- The example program `set.quiver`, which loads an alphabet as a set, builds
  the transitions of the alphabet with a bulk action, and collects the symbols
  it reads with a sweep, and its walkthrough in the examples document.

### Changed

- A set used directly as a guard is false when it is empty, like a sequence.
- The error message of the `set` primitive names the sequence and the set it
  accepts: `set expects a sequence or a set value`.

## [1.2.0] - 2026-11-15

### Added

- The **epsilon transition**, the internal transition of the automaton: a
  transition whose symbol is the distinguished symbol `epsilon` is taken
  without consuming an input symbol. The internal moves are compulsory, and
  the run takes them before the first symbol and after every symbol, so the
  shape of a run is `closure -> consume -> closure -> ... -> closure ->
  accept`. The acceptance is a property of the state the automaton rests in
  after the final closure, and it is evaluated once, at the end of the run.
- The **cycle guard**, which refuses a program whose internal transitions form
  a cycle that the rules cannot break, and names the cycle it found: `Epsilon
  cycle detected: q0 -> q1 -> q0`. The check is conservative: a rule whose
  clauses mention a variable, a wildcard or a binding in the symbol position
  of a transition may break the cycle, and so the program is accepted. A cycle
  in a part of the automaton that no run reaches is harmless, and the program
  is accepted as well. The interactive loop keeps the previous session when a
  program is refused.
- The example program `counter-machine.quiver`, which simulates a two-counter
  machine with internal transitions, and its walkthrough in the examples
  document.

### Changed

- A run that exhausts its budget inside a closure now reports the closure as
  the cause of the failure, and names the number of internal steps the closure
  took: `Epsilon closure did not terminate: the budget was exhausted after 20
  internal steps`.

### Fixed

- An arithmetic expression may now be an element of a pair, as in
  `($n + 1, $m)`. The grammar says the elements of a pair are terms, and a
  term may be an arithmetic expression, but the parser rejected it.
- The arithmetic operators are consistently left-associative, so `a + b + c`
  is `(a + b) + c`.

## [1.1.0] - 2026-11-14

### Added

- The **sweep** clause, `each <pattern> => { clauses }`, legal inside a rule
  body and inside a nested block. The pattern is matched against every
  transition of the automaton, and the block runs once per matching
  transition, with the bindings of the pattern extended per firing. The
  transitions that match are collected before the first firing, so the sweep
  sees the automaton as it was when it started, while the actions of the
  firings apply to the live automaton.
- The **inspection primitives**: `targets-from(state)`, which returns the
  distinct targets of the transitions leaving a state, `symbols-from(state)`,
  which returns their distinct symbols, `size(expr)`, which counts the
  elements of a sequence, and `is-empty(expr)`, which tells whether a sequence
  has none.
- The example programs `sweep.quiver` and `inspect.quiver`, and their
  walkthroughs in the examples document.

### Changed

- A **sequence used as a guard** is now false when it is empty, and true when
  it is not. The truthiness of the other values is unchanged: a boolean is its
  own truth value, an integer is true when it is not zero, and every other
  value is true. The new `is-empty` primitive is the explicit emptiness test.

## [1.0.2] - 2026-11-14

### Fixed

- The integers of Quiver are now arbitrary-precision. The integer literals,
  the arithmetic on transition values and the integer patterns are no longer
  limited by the width of a machine integer, so the counters a program carries
  are unbounded, as the language requires. The previous implementation used a
  32-bit integer and silently wrapped around when a counter overflowed.

## [1.0.1] - 2026-11-14

### Fixed

- The `include` directive now identifies the included file by its canonical
  path, so the same file reached through different spellings of its path, such
  as `sub.quiver` and `./sub.quiver`, is merged once and not twice.
- The `:rules` command of the interactive loop now reports the rules of the
  active strategy, which are the ones the run consults. The top-level rules
  that the active strategy does not consult are listed separately, under a
  heading that says so. The command used to report no rules at all when the
  program declared its rules in a named strategy.

## [1.0.0] - 2026-11-14

### Added

- The interpreter of Quiver, a type-safe term-rewriting language for adaptive
  automata, with a fully specified operational semantics.
- The `@fsm` header, which declares the start state, the accepting states and
  the transition function of the automaton.
- The rewrite rules, with their patterns, their optional `when` guards and
  their clauses, which insert, remove or retarget transitions.
- The three rewriting strategies: `first`, `all` and `repeat [max N]`.
- The adaptive primitives: `$state`, `transitions-from(state)`,
  `clone-automaton()` and `emit(expr)`.
- The sequences, the bindings, the closures and the arithmetic of the
  transition values.
- Non-deterministic recognition (`--nondet`): several transitions may leave
  the same `(state, symbol)` key, each one is followed in parallel on its own
  copy of the automaton, and acceptance is NFA-style (any surviving path
  accepts).
- The `include` directive, which pulls the declarations of another file into
  the program.
- The command-line interface, with the `-i`, `--trace`, `--nondet`,
  `--max-steps` and `--help` options.
- The interactive REPL, with the `:load`, `:rules`, `:strategy`, `:trace`,
  `:nondet`, `:step`, `:recognize`, `:debug`, `:reset`, `:help` and `:quit`
  commands.
- The example programs and the documentation: the normative language
  reference and the step-by-step walkthrough of every example.

[Unreleased]: https://github.com/cereda/quiver/compare/v1.3.0...HEAD
[1.3.0]: https://github.com/cereda/quiver/tree/v1.3.0
[1.2.0]: https://github.com/cereda/quiver/tree/v1.2.0
[1.1.0]: https://github.com/cereda/quiver/tree/v1.1.0
[1.0.2]: https://github.com/cereda/quiver/tree/v1.0.2
[1.0.1]: https://github.com/cereda/quiver/tree/v1.0.1
[1.0.0]: https://github.com/cereda/quiver/tree/v1.0.0
