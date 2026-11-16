# Quiver: a term-rewriting language for adaptive automata

A Kotlin/JVM implementation of **Quiver**, a type-safe term-rewriting language
for adaptive automata. The current release is **3.0.0**.

An **adaptive automaton** is a finite automaton whose transition function may change
while it runs. Quiver describes such an automaton as a set of rewrite rules: each rule
*consults* the transition the automaton is about to take and *rewrites* it, inserting
or removing transitions as it recognizes the input.

This project is a ground-up interpreter for Quiver, with a fully specified operational
semantics.

* **Language reference:** [`docs/language-reference.md`](docs/language-reference.md),
  the normative description of the syntax and the semantics.
* **Examples:** [`docs/examples.md`](docs/examples.md), a step-by-step walkthrough
  of every example program that ships with the interpreter.

## Quick start

The source lives at <https://github.com/cereda/quiver>. Clone it and build it:

```bash
git clone https://github.com/cereda/quiver.git
cd quiver
./gradlew build                                              # compile, test, lint, document
./gradlew run --args="run -i src/main/resources/rec.quiver a a b b c c"  # run the example
```

Requirements: JDK 17 or later to run the build (Gradle 9.9.1 does not run on
Java 11); the interpreter itself targets Java 11. The Gradle wrapper is included.

The build produces a self-contained executable jar (the shadow jar replaces the thin
jar in `build/libs`), so the interpreter runs with no Gradle and no classpath:

```bash
java -jar build/libs/quiver-3.0.0.jar run -i src/main/resources/rec.quiver a a b b c c
# the same jar starts the interactive REPL when it is told to:
java -jar build/libs/quiver-3.0.0.jar repl
```

The command prints `true` when the input is recognized and `false` otherwise; an
error is reported on the standard error stream and the process exits with status 1.
A bare invocation, with no subcommand at all, prints the help summary and exits
with status 0.

### Command-line interface

The tool has three subcommands:

```
quiver run -i <file.quiver> [<symbol>...] [--trace] [--nondet] [--max-steps N]
quiver repl [--no-history] [--history PATH] [--load FILE] [--no-banner] [--color|--no-color]
quiver inspect -i <file.quiver>
```

| Subcommand | Meaning |
|---|---|
| `run` | run a program over an input, and print `true` or `false` |
| `repl` | start the interactive session |
| `inspect` | summarize a program, without running it |

The options of `run`:

| Option | Meaning |
|---|---|
| `-i`, `--input <file.quiver>` | the Quiver source file to execute (required) |
| `<symbol>...` | the symbols of the input, one per argument (optional) |
| `--trace` | print the step trace on the standard error stream |
| `--nondet` | non-deterministic recognition (NFA-style, any path accepts) |
| `--max-steps N` | the fuel budget; 10000 steps by default |
| `-h`, `--help` | print the usage summary |

The options may appear in any order. Every argument that is none of the options
above is one symbol of the input, and the name of the symbol is the text of the
argument. The command line overrides the input the program declares in its
`input` key, and a program that declares no input and gets none from the command
line fails.

The `include` directives of the source file are resolved relative to the directory
of that file.

The option `--generate-completion` writes the shell completion of the command
line, and `-V` (or `--version`) reports the version of the interpreter.

### REPL

```bash
java -jar build/libs/quiver-3.0.0.jar repl
```

While developing, `./gradlew runRepl` starts the same REPL from the sources.

| Option | Meaning |
|---|---|
| `--no-history` | keep no history of the session on disk |
| `--history PATH` | keep the history of the session in `PATH` |
| `--load FILE` | load a program before the session starts |
| `--no-banner` | open the session without the banner |
| `--color` | paint the output, even when it does not go to a terminal |
| `--no-color` | never paint the output |

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
| `:quit` / `:exit` | leave the REPL |

Any other line is treated as an input to recognize against the loaded automaton.
The line is tokenized by the lexer of the language: the commas separate the
symbols, the quoted text is one symbol, and a `#` starts a comment. The
`:recognize` command with no argument recognizes the input the loaded program
declares in its `input` key.

The interactive debug mode always follows a single path, so it runs
deterministically even when the non-deterministic mode is on. Its own commands
are `:step`, `:inspect`, `:continue` (or `:c`) and `:quit` (or `:exit`), which
returns to the REPL.

The session keeps its history on disk, by default in the file the XDG
specification names: `$XDG_STATE_HOME/quiver/repl-history`, or
`~/.local/state/quiver/repl-history` when the state root is unset. The history of
a session is there when the next one starts.

The session paints its banner, its prompt, its verdicts and the line being
edited, and the colours are the privilege of a terminal: a session whose output
is piped is the same session, byte for byte. The `--color` and `--no-color`
options override the terminal, and the environment variable `NO_COLOR` forbids
the colours whenever it is present and not empty. The trace of the interpreter is
never painted.

## Examples

The example programs live in [`src/main/resources`](src/main/resources), and
[`docs/examples.md`](docs/examples.md) walks through each of them step by
step, with the trace of a concrete input.

| Example | What it shows |
|---|---|
| [`simple.quiver`](src/main/resources/simple.quiver) | a plain automaton, with no rules at all |
| [`rec.quiver`](src/main/resources/rec.quiver) | the phase recognizer for `a+ b+ c+` |
| [`abn.quiver`](src/main/resources/abn.quiver) | an automaton that reads its own counter to recognize `a^n b^n` |
| [`guard.quiver`](src/main/resources/guard.quiver) | a `when` guard |
| [`guard-literal.quiver`](src/main/resources/guard-literal.quiver) | the `true` and `false` literals in a guard |
| [`emit.quiver`](src/main/resources/emit.quiver) | printing values with `emit` |
| [`primitives.quiver`](src/main/resources/primitives.quiver) | `$state`, `transitions-from` and `clone-automaton` |
| [`sweep.quiver`](src/main/resources/sweep.quiver) | a sweep: one action applied to many transitions at once |
| [`inspect.quiver`](src/main/resources/inspect.quiver) | `targets-from`, `symbols-from`, `size` and `is-empty` |
| [`set.quiver`](src/main/resources/set.quiver) | the set algebra: sets, set variables and bulk actions |
| [`counter-machine.quiver`](src/main/resources/counter-machine.quiver) | the simulation of a counter machine, with internal transitions |
| [`closure.quiver`](src/main/resources/closure.quiver) | a closure stored as a transition value |
| [`strategy.quiver`](src/main/resources/strategy.quiver) | a named rewriting strategy |
| [`seq.quiver`](src/main/resources/seq.quiver) | sequences, built and matched |
| [`binding.quiver`](src/main/resources/binding.quiver) | a binding that names a matched term |
| [`adaptive-insert.quiver`](src/main/resources/adaptive-insert.quiver) | inserting a transition that did not exist |
| [`adaptive-threshold.quiver`](src/main/resources/adaptive-threshold.quiver) | retargeting a transition when a counter reaches a threshold |
| [`adaptive-remove.quiver`](src/main/resources/adaptive-remove.quiver) | removing a transition for good |
| [`include-main.quiver`](src/main/resources/include-main.quiver) | a program that pulls its rules in from another file |
| [`include-sub.quiver`](src/main/resources/include-sub.quiver) | the rule file that `include-main.quiver` pulls in |
| [`nondet.quiver`](src/main/resources/nondet.quiver) | non-determinism without rules |
| [`nondet-adaptive.quiver`](src/main/resources/nondet-adaptive.quiver) | per-path adaptive actions |
| [`nondet-strategy.quiver`](src/main/resources/nondet-strategy.quiver) | non-determinism with the `all` strategy |
| [`nondet-repeat.quiver`](src/main/resources/nondet-repeat.quiver) | non-determinism with the `repeat` strategy |
| [`symbols.quiver`](src/main/resources/symbols.quiver) | symbols whose names are not plain identifiers, and the symbol named `epsilon` |
| [`input.quiver`](src/main/resources/input.quiver) | a program that declares its own input |
| [`fresh.quiver`](src/main/resources/fresh.quiver) | the generator of fresh states |

Each of them is a complete program:

```bash
java -jar build/libs/quiver-3.0.0.jar run -i src/main/resources/abn.quiver a a b b  # true
java -jar build/libs/quiver-3.0.0.jar run -i src/main/resources/abn.quiver a a b    # false
```

This interpreter defines the precise operational semantics, notably the matching
discipline of a rule's clauses and the adaptive insert/remove interplay, and
documents them in the [language reference](docs/language-reference.md).

## The language at a glance

* **FSM header:** `@fsm => { start => q0; accepting => $seq [ q1 ]; mapping => { ... } }`
* **Rewrite rules:** `pattern [when guard] => { clauses }`
* **Patterns:** symbols, variables (`$x`), the wildcard (`_`), pairs `(x, a)`,
  arrows `(x, a) -> (x, 1)`, and sequences `$seq [ ... ]`, whose items are atoms
* **Quoted symbols:** a double-quoted string is a symbol whose name is the text
  of the string, so `"a b"` is one symbol and not two
* **Internal transitions:** a transition whose symbol is `epsilon` is taken
  without consuming an input symbol; the word is reserved, and the symbol named
  `epsilon` is written `"epsilon"`
* **Clauses:** `pat => _` removes a transition, `_ => pat` inserts one, and
  `=> { ... }` nests a block of clauses; a clause whose pattern does not match the
  subject is skipped
* **Sweeps:** `each <pattern> => { ... }` runs a block of clauses once per
  matching transition of the automaton, with the bindings of the pattern per
  firing
* **Sets:** `set(seq)`, `union(a, b)`, `intersection(a, b)`,
  `difference(a, b)` and `contains(a, b)` build and query sets of values, and
  `size` and `is-empty` accept them
* **Set variables:** a name that starts with `%` matches a set, and denotes its
  elements once it is bound; the set variable of a sweep collects the values it
  matches across the firings
* **Bulk actions:** an insertion whose symbol (or target state) is a set
  inserts one transition per element of the set, and a removal whose symbol is
  a set removes every transition under those symbols
* **Bindings:** `x <- t` matches `t` and binds the matched term to `$x`
* **Arithmetic:** `y + 1`, `y - 1`, over arbitrary-precision integers, so the
  counters a program carries are unbounded
* **Closures:** `fn $x => body`, applied as `f(arg)`
* **Strategies:** `first`, `all` and `repeat [max N]`
* **Adaptive primitives:** `$state`, `transitions-from(state)`, `clone-automaton()`,
  `emit(expr)`
* **Generator of fresh states:** `fresh-state()` mints a state the automaton does
  not know yet, and the set of the states only grows
* **Input:** the `input` key of the header declares the default input of the
  run, and the command line overrides it
* **Inspection primitives:** `targets-from(state)`, `symbols-from(state)`,
  `size(expr)`, `is-empty(expr)`
* **Modularity:** `include "other-file.quiver"`, resolved relative to the including file
* **Recognition:** drives the adaptive automaton symbol by symbol; the internal
  transitions are taken before the first symbol and after every one of them, and
  the input is accepted when it is fully consumed and the automaton rests in an
  accepting state
* **Non-determinism:** `--nondet` follows every transition that matches a state
  and a symbol, each on its own copy of the automaton, and accepts when any
  surviving path ends in an accepting state

## Build, tests and quality tooling

```bash
./gradlew test          # the test suite (669 tests)
./gradlew detekt        # the static analysis
./gradlew spotlessCheck # the formatting and the license headers
./gradlew koverVerify   # the coverage floors
./gradlew dokkaGenerateHtml  # the API documentation (build/dokka)
./gradlew clean build   # the whole gate: compile, test, analyse, cover, document
```

| Tool | What it does here |
|---|---|
| [Detekt](https://detekt.dev/) | static analysis; the ruleset is `detekt-config.yml` |
| [Spotless](https://github.com/diffplug/spotless) | ktlint formatting and the SPDX license headers |
| [Kover](https://github.com/Kotlin/kotlinx-kover) | coverage report, with a floor of 99% lines and 92% branches |
| [Dokka](https://kotlinlang.org/docs/dokka.html) | the API documentation, with `failOnWarning` and `reportUndocumented` |
| [tapmoc](https://github.com/gradleup/tapmoc) | owns the JVM and Kotlin targets |
| [shadow](https://github.com/gradleup/shadow) | the self-contained executable jar |
| [Clikt](https://github.com/ajalt/clikt) | the command line: the options, the subcommands and the usage summaries |
| [JLine](https://github.com/jline/jline3) | the terminal of the interactive session: the line editing, the history and the completion |

The **version** derives from `CHANGELOG.md`: spotless-changelog reads the section that
describes the release, so a build is `<version>-SNAPSHOT` unless `-Prelease=true` is
passed, and promoting the `[Unreleased]` section to a version heading declares the
release.

## Notes

* Kotlin 2.4.21 (language and API level 2.4), Java target 11, Gradle 9.9.1 wrapper.
* The interpreter targets **Java 11**: the fat jar runs on a Java 11 runtime. The
  *build* needs **JDK 17 or later**, because Gradle 9 no longer runs on Java 11.
* The runtime dependencies are the Kotlin standard library, Clikt and JLine; the
  executable jar bundles all of them.
* Licensed under the GNU General Public License, version 3 or later; the full
  text is in [`LICENSE`](LICENSE), and every source file carries the
  `SPDX-License-Identifier: GPL-3.0-or-later` header.
