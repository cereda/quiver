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

## [3.0.1] - 2026-11-17

### Changed

- The **executable JAR** is now minimized with R8 in shrink-only mode:
  unreachable runtime code is removed without renaming classes or methods,
  optimizing bytecode, or desugaring class files. The JLine native providers,
  the Mordant JNA backend, the command line, the interactive session and the
  completion keep their behavior, and the published artifact is roughly half
  the size of the previous one.

## [3.0.0] - 2026-11-16

### Added

- The **`inspect` command**, which summarizes a program without running it: it
  reports the header of the automaton, the strategies the program declares, the
  strategy the run consults, and the rules of that strategy. The summary is
  built by the same loader the command line and the interactive session use, so
  the three surfaces cannot disagree about the program they read.
- The **colours** of the interactive session: the banner, the prompt, the
  verdict of a recognition and the line being edited are painted, and the
  colours are the privilege of a terminal. A session whose output is piped is
  the same session, byte for byte, and the trace of the interpreter is never
  painted.
- The **`--color`** and **`--no-color`** options of the interactive session:
  the first asks for the colours whatever the terminal is, and the second
  forbids them. The environment variable `NO_COLOR` forbids them as well,
  whenever it is present and not empty.
- The **`--no-banner`** option of the interactive session, which opens the
  session without the banner.
- The **persistent history** of the interactive session: the commands of a
  session are kept in a file, and the file is the one the XDG specification
  names (`$XDG_STATE_HOME/quiver/repl-history`, or
  `~/.local/state/quiver/repl-history` when the state root is unset). The
  `--history PATH` option names another file, and `--no-history` keeps the
  history in memory only.
- The **completion** and the **highlighting** of the interactive session: the
  commands, the arguments they accept, the files they load and the symbols of
  the loaded automaton are completed, and the line being edited is painted with
  the kinds of span the language knows.
- The **`:exit` alias** of the `:quit` command, in the interactive session and
  in the interactive debug mode.
- The **version option**, `-V` and `--version`, which reports the version of
  the interpreter.
- The **completion option**, `--generate-completion`, which writes the shell
  completion of the command line.

### Changed

- **BREAKING:** the command line has subcommands now, and the options of the
  run live in the `run` subcommand: the run is `quiver run -i
  <file.quiver> [<symbol>...]`, and not `quiver -i <file.quiver>
  [<symbol>...]`. A bare `quiver` prints the help summary of the tool, and it
  does not run anything.
- **BREAKING:** the interactive session is the `repl` subcommand: the session
  is `quiver repl`, and not a bare `quiver`.
- The command line is built on **Clikt**, and the interactive session is built
  on **JLine**: the usage summaries, the completion, the line editing and the
  history are the ones of those libraries. The session reads a pipe and a
  terminal through the same code path, and a failure of the terminal is a
  failure of the command, reported with the exit status 1.
- The interactive session keeps its history on disk, and it saves it when the
  session ends: the history of a session is there when the next one starts.
- The **rules of a program** are reported by the interactive session in the
  order the program declares them, and the rules of a strategy that two
  declarations share are the rules of the last of them.

### Fixed

- The `:exit` alias of the `:quit` command was undocumented: it is now listed
  in the help of the interactive session, in the help of the interactive debug
  mode, in the completion, in the language reference and in the article.
- The **history of the interactive session** was never written to the disk: it
  is now saved when the session ends, and it is loaded when the session
  starts.
- The **highlighter** of the interactive session ignored the `--color` and
  `--no-color` options: the line being edited is painted under the same
  policy as the lines the session prints.
- The **state root of the XDG specification** was used even when it was not
  an absolute path: it is now ignored, as the specification says.
- The **history file** of the interactive session could name a path with no
  directory of its own, and the session fell over it: the file now lands in
  the working directory of the process, and the session goes on.
- The **strategies of a program** were read in two different ways, by the
  command line and by the interactive session, and the two ways disagreed when
  two strategies shared a label: every surface now reads them the same way.
- The **terminal of the interactive session** was not released when the
  session failed: it is now released even then, and the history is saved.

## [2.0.0] - 2026-11-15

### Added

- The **quoted symbol**: a double-quoted string is a symbol whose name is the
  text of the string, so a symbol may hold spaces, commas and any other
  character. A quoted symbol and a bare one denote the same symbol when the
  text is the same: `"a"` and `a` are one symbol.
- The **input of the run**, declared by the `input` key of the `@fsm` header:
  the header holds the default input of the run, a sequence of symbols, and the
  command line overrides it. A program that declares no input and gets none
  from the command line fails, with the message `the program declares no
  input; give one on the command line`.
- The **generator of fresh states**, `fresh-state()`: it evaluates to the name
  of a state the automaton does not know yet. The names are `g0`, `g1`, and so
  on, every evaluation mints a name of its own, and the set of the states only
  grows. The generator is a value and not a pattern, and using it in a pattern
  is an error.
- The example programs `symbols.quiver`, `input.quiver` and `fresh.quiver`,
  and their walkthroughs in the examples document.

### Changed

- **Breaking change:** the word `epsilon` is reserved. It denotes the internal
  transition in the symbol position of a transition, and it is an error
  everywhere else: `epsilon is reserved: it denotes the internal transition;
  write "epsilon" for the symbol`. The symbol named `epsilon` is now written
  `"epsilon"`, and it is an ordinary symbol of the alphabet: the internal
  transition and the symbol named `epsilon` are two different labels, and they
  never collide. A state may still be named `epsilon`, since a state name is
  never a symbol.
- **Breaking change:** the input of a run is a sequence of symbols, and not a
  string. Every trailing argument of the command line is one symbol of the
  input, and the name of the symbol is the text of the argument. The empty
  argument denotes the empty input, and it cannot be combined with other
  symbols. The `--` argument ends the options, so that an argument that looks
  like an option may be a symbol.
- **Breaking change:** the transition of the automaton carries a label, which
  is either a symbol of the alphabet or the internal marker, and the symbol of
  a transition is no longer a plain string. The rendering of a label follows
  the program: the internal marker is written `epsilon`, and a literal symbol
  is written bare when its name is a plain one and quoted otherwise.
- The interactive loop tokenizes the input of a command with the lexer of the
  language: the commas separate the symbols, the quoted text is one symbol, and
  a `#` starts a comment. The `:recognize` command with no argument recognizes
  the input the loaded program declares in its `input` key.

### Fixed

- The trace of a bulk removal names the labels of the transitions it removes as
  the symbols they are, and not as the values that hold them.
- The generator of fresh states is rejected in every slot of a pattern, and not
  only when it is the pattern itself: a generator that sits in the symbol slot
  of a transition-shaped pattern could never match, and it is an error now, as
  the reference says.

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

[Unreleased]: https://github.com/cereda/quiver/compare/v3.0.0...HEAD
[3.0.0]: https://github.com/cereda/quiver/tree/v3.0.0
[2.0.0]: https://github.com/cereda/quiver/tree/v2.0.0
[1.3.0]: https://github.com/cereda/quiver/tree/v1.3.0
[1.2.0]: https://github.com/cereda/quiver/tree/v1.2.0
[1.1.0]: https://github.com/cereda/quiver/tree/v1.1.0
[1.0.2]: https://github.com/cereda/quiver/tree/v1.0.2
[1.0.1]: https://github.com/cereda/quiver/tree/v1.0.1
[1.0.0]: https://github.com/cereda/quiver/tree/v1.0.0
