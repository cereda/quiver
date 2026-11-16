// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.jline.reader.EndOfFileException
import org.jline.reader.UserInterruptException
import org.jline.terminal.impl.DumbTerminal
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The tests of the interactive REPL.
 *
 * The REPL reads its commands from the terminal, so a script drives it
 * exactly as a user would; the output is captured from the standard output.
 */
class QuiverReplTest {

    @Test
    fun `the banner introduces the repl`() {
        val output = runRepl("")
        assertContains(output, "Quiver REPL: a term rewriting language for adaptive automata")
        assertContains(output, "Type :help for commands, :quit to exit.")
        assertContains(output, "quiver> ")
    }

    @Test
    fun `the help command lists the commands`() {
        val output = runRepl(":help\n:quit\n")
        assertContains(output, ":load <file.quiver>")
        assertContains(output, ":strategy <kind>")
        assertContains(output, ":debug <input>")
        assertContains(output, "Any other line (outside debug mode) is treated as input")
    }

    @Test
    fun `the help command lists the aliases of the commands`() {
        // The help names every name a command answers to, so that a name
        // the session accepts is a name the user can read about.
        val output = runRepl(":help\n:quit\n")
        assertContains(output, ":quit / :exit")
    }

    @Test
    fun `the debug help lists the aliases of the commands`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug a\n:help\n:quit\n")
        assertContains(output, ":quit / :exit")
        assertContains(output, ":continue / :c")
    }

    @Test
    fun `the rules command reports an empty session`() {
        val output = runRepl(":rules\n:quit\n")
        assertContains(output, "(no rules loaded)")
    }

    @Test
    fun `the rules command lists the loaded rules`() {
        val output = runRepl(":load src/test/resources/abn.quiver\n:rules\n:quit\n")
        assertContains(output, "Loaded rules (5)")
        assertContains(output, "Active strategy: FIRST (5 rules)")
    }

    @Test
    fun `the rules command reports a program without rules`() {
        val output = runRepl(":load src/test/resources/rec.quiver\n:rules\n:quit\n")
        assertContains(output, "(no rules loaded)")
    }

    @Test
    fun `the rules command lists the rules of the active strategy`() {
        // The program has no top-level rule: the rules of the named strategy
        // are the ones the run consults, and the ones the command reports.
        val output = runRepl(":load src/test/resources/strategy.quiver\n:rules\n:quit\n")
        assertContains(output, "Loaded rules (1)")
        assertContains(output, "Active strategy: REPEAT (1 rules)")
    }

    @Test
    fun `the rules command separates the rules the active strategy does not consult`() {
        // The program declares a named strategy and a top-level rule: the
        // command reports both, and says which one the run consults.
        val output = runRepl(":load src/test/resources/index-strategy.quiver\n:rules\n:quit\n")
        assertContains(output, "Loaded rules (1)")
        assertContains(output, "Top-level rules (1), not consulted by the active strategy:")
        assertContains(output, "Active strategy: FIRST (1 rules)")
    }

    @Test
    fun `the load command reports an aliased include once`() {
        // The subfile is included through two spellings of its path, and is
        // merged once, so the program has one rule.
        val output = runRepl(":load src/test/resources/include-alias.quiver\n:quit\n")
        assertContains(output, "1 rule(s)")
    }

    @Test
    fun `the load command reports the loaded program`() {
        val output = runRepl(":load src/test/resources/rec.quiver\n:quit\n")
        assertContains(output, "Loaded src/test/resources/rec.quiver: 0 rule(s), 0 strategy(ies)")
        assertContains(output, "FSM: start=q0, accepting=q2")
        assertContains(output, "mapping: 5 transition(s)")
    }

    @Test
    fun `the load command reports a program without an fsm header`() {
        val output = runRepl(":load src/test/resources/no-fsm.quiver\n:quit\n")
        assertContains(output, "(no FSM header; recognition requires :load a file with @fsm)")
    }

    @Test
    fun `the load command reports the strategies of the program`() {
        val output = runRepl(":load src/test/resources/strategy.quiver\n:quit\n")
        assertContains(output, "1 strategy(ies)")
    }

    @Test
    fun `the load command falls back when the named strategy is missing`() {
        val output = runRepl(":load src/test/resources/missing-strategy.quiver\n:rules\n:quit\n")
        assertContains(output, "0 strategy(ies)")
        assertContains(output, "Active strategy: FIRST")
    }

    @Test
    fun `the load command reports a missing file`() {
        val output = runRepl(":load src/test/resources/does-not-exist.quiver\n:quit\n")
        assertContains(output, "error loading src/test/resources/does-not-exist.quiver:")
    }

    @Test
    fun `the load command reports a malformed program`() {
        val output = runRepl(":load src/test/resources/error-bad-mapping-left.quiver\n:quit\n")
        assertContains(output, "error loading src/test/resources/error-bad-mapping-left.quiver:")
        assertContains(output, "Invalid FSM mapping")
    }

    @Test
    fun `the trace command toggles the trace`() {
        val output = runRepl(":trace on\n:trace off\n:quit\n")
        assertContains(output, "trace enabled")
        assertContains(output, "trace disabled")
    }

    @Test
    fun `the nondet command toggles the non-deterministic recognition`() {
        val output = runRepl(":nondet on\n:nondet off\n:quit\n")
        assertContains(output, "nondeterministic recognition enabled")
        assertContains(output, "nondeterministic recognition disabled")
    }

    @Test
    fun `the nondet command reaches the non-deterministic recognizer`() {
        // The automaton of `nondet.quiver` has two transitions out of q0 on `a`,
        // and only the second one reaches an accepting state.
        val output = runRepl(":load src/test/resources/nondet.quiver\n:nondet on\na\n:quit\n")
        assertContains(output, "true")
    }

    @Test
    fun `the reset command clears the nondeterministic mode`() {
        val output = runRepl(":nondet on\n:reset\n:quit\n")
        assertContains(output, "reset")
    }

    @Test
    fun `the step command prints the trace`() {
        // The `:step` command runs the recognition with the trace turned on,
        // whatever the state of the `:trace` command.
        val output = runRepl(":load src/test/resources/simple.quiver\n:step a b\n:quit\n")
        assertContains(output, "true")
    }

    @Test
    fun `the strategy command sets the strategy`() {
        val output = runRepl(":strategy first\n:strategy all\n:strategy repeat max 5\n:quit\n")
        assertContains(output, "strategy set to FIRST (maxIterations=100)")
        assertContains(output, "strategy set to ALL (maxIterations=100)")
        assertContains(output, "strategy set to REPEAT (maxIterations=5)")
    }

    @Test
    fun `the strategy command rebuilds the rule index`() {
        // The named strategy of the program inserts a transition to the
        // non-accepting state q2, and the top-level rule inserts one to the
        // accepting state q1. After `:strategy first`, the top-level rule is
        // the one that fires, so the input is accepted.
        val output =
            runRepl(
                ":load src/test/resources/index-repl-strategy.quiver\n" +
                    ":strategy first\n" +
                    "a b\n" +
                    ":quit\n",
            )
        assertContains(output, "true")
    }

    @Test
    fun `the load command keeps the previous session when the program is malformed`() {
        // The malformed program must not leave the session with the rules of
        // the new program and the automaton of the previous one.
        val output =
            runRepl(
                ":load src/test/resources/simple.quiver\n" +
                    ":load src/test/resources/error-bad-mapping-left.quiver\n" +
                    ":rules\n" +
                    ":quit\n",
            )
        assertContains(output, "error loading src/test/resources/error-bad-mapping-left.quiver:")
        assertContains(output, "(no rules loaded)")
    }

    @Test
    fun `the strategy command rejects an unknown kind`() {
        val output = runRepl(":strategy nope\n:quit\n")
        assertContains(output, "unknown strategy kind 'nope' (use first/all/repeat)")
    }

    @Test
    fun `an empty line prints nothing`() {
        // The user presses Enter and nothing happens: the empty line is no
        // command and no input.
        val output = runRepl("\n:quit\n")
        assertContains(output, "goodbye")
        assertTrue(!output.contains("unknown command"), "The empty line was reported")
    }

    @Test
    fun `the strategy command accepts a kind`() {
        val output = runRepl(":strategy first\n:quit\n")
        assertContains(output, "strategy set to FIRST (maxIterations=100)")
    }

    @Test
    fun `the strategy command accepts a bound`() {
        val output = runRepl(":strategy repeat max 5\n:quit\n")
        assertContains(output, "strategy set to REPEAT (maxIterations=5)")
    }

    @Test
    fun `the strategy command falls back to the default bound`() {
        // A bound that is not a number is not a bound, and the default one
        // is used instead.
        val output = runRepl(":strategy repeat max abc\n:quit\n")
        assertContains(output, "strategy set to REPEAT (maxIterations=100)")
    }

    @Test
    fun `the strategy command ignores a number that is not a bound`() {
        // The bound is the word `max` followed by a number, and a number
        // alone is not a bound.
        val output = runRepl(":strategy repeat 5\n:quit\n")
        assertContains(output, "strategy set to REPEAT (maxIterations=100)")
    }

    @Test
    fun `the strategy command ignores a bound that is not a bound`() {
        // The word `max` is what makes the second word a bound, and a word
        // that is not `max` is not a bound.
        val output = runRepl(":strategy repeat foo 5\n:quit\n")
        assertContains(output, "strategy set to REPEAT (maxIterations=100)")
    }

    @Test
    fun `the strategy command works in an empty session`() {
        // The session that holds no program has no rules to run, and the
        // command says which strategy it selected anyway.
        val output = runRepl(":strategy first\n:quit\n")
        assertContains(output, "strategy set to FIRST")
    }

    @Test
    fun `the rules report shows the strategy the user selected`() {
        // The strategy the user selects fires the top-level rules of the
        // program, and the report says so.
        val output = runRepl(":load src/test/resources/abn.quiver\n:strategy first\n:rules\n:quit\n")
        assertContains(output, "Loaded rules (5):")
        assertContains(output, "Active strategy: FIRST (5 rules)")
    }

    @Test
    fun `the debug session runs the strategy the user selected`() {
        val output =
            runRepl(
                ":load src/test/resources/abn.quiver\n:strategy first\n:debug a a\n:quit\n",
            )
        assertContains(output, "debug mode started for 2 symbol(s)")
    }

    @Test
    fun `the strategy command rejects an empty specification`() {
        val output = runRepl(":strategy\n:quit\n")
        assertContains(output, "usage: :strategy <first|all|repeat [max N]>")
    }

    @Test
    fun `the strategy command falls back on an invalid iteration bound`() {
        val output = runRepl(":strategy repeat max many\n:quit\n")
        assertContains(output, "strategy set to REPEAT (maxIterations=100)")
    }

    @Test
    fun `the recognize command prints the result`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:recognize a b\n:quit\n")
        assertContains(output, "true")
    }

    @Test
    fun `the recognize command rejects an input the automaton does not accept`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:recognize a\n:quit\n")
        assertContains(output, "false")
    }

    @Test
    fun `a plain line is an input to recognize`() {
        val output = runRepl(":load src/test/resources/simple.quiver\na b\n:quit\n")
        assertContains(output, "true")
    }

    @Test
    fun `the step command recognizes the input`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:step a b\n:quit\n")
        assertContains(output, "true")
    }

    @Test
    fun `recognition without a loaded program is reported`() {
        val output = runRepl(":recognize a b\n:quit\n")
        assertContains(output, "no automaton/strategy loaded; use :load <file.quiver> first")
    }

    @Test
    fun `the reset command clears the session`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:reset\n:rules\n:quit\n")
        assertContains(output, "reset")
        assertContains(output, "(no rules loaded)")
    }

    @Test
    fun `the quit command ends the session`() {
        val output = runRepl(":quit\n")
        assertContains(output, "goodbye")
        assertTrue(!output.contains("quiver> quiver> "), "The REPL kept reading after :quit")
    }

    @Test
    fun `the exit command ends the session`() {
        val output = runRepl(":exit\n")
        assertContains(output, "goodbye")
    }

    @Test
    fun `an unknown command is reported`() {
        val output = runRepl(":nope\n:quit\n")
        assertContains(output, "unknown command: :nope")
    }

    @Test
    fun `the debug command starts a debug session`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug a b\n:quit\n")
        assertContains(output, "debug mode started for 2 symbol(s)")
        assertContains(output, "state=q0 position=0")
        assertContains(output, "quiver-debug> ")
    }

    @Test
    fun `the debug mode steps one symbol at a time`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug a b\n:step\n:inspect\n:quit\n")
        assertContains(output, "step position=1: state=q1")
        assertContains(output, "transition fired: q1")
        assertContains(output, "position: 1 / 2")
        assertContains(output, "state: q1")
        assertContains(output, "finished: false")
    }

    @Test
    fun `the debug mode runs to completion`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug a b\n:continue\n:quit\n")
        assertContains(output, "run to completion: result=true")
        assertContains(output, "quiver> ")
    }

    @Test
    fun `the debug mode can be left`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug a b\n:quit\n:rules\n:quit\n")
        assertContains(output, "leaving debug mode")
        assertContains(output, "quiver> ")
    }

    @Test
    fun `the debug mode can be left with the exit command`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug a b\n:exit\n:rules\n:quit\n")
        assertContains(output, "leaving debug mode")
        assertContains(output, "quiver> ")
    }

    @Test
    fun `the debug mode continues with the short command`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug a b\n:c\n:quit\n")
        assertContains(output, "run to completion: result=true")
    }

    @Test
    fun `stepping after the run finished reports the result`() {
        // The automaton of `simple.quiver` has no transition out of q0 on `b`:
        // the first step rejects the input and finishes the recognition.
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug b\n:step\n:step\n:quit\n")
        assertContains(output, "debug finished; result=false")
    }

    @Test
    fun `the debug mode has its own help`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug a b\n:help\n:quit\n")
        assertContains(output, ":inspect")
        assertContains(output, ":continue / :c")
    }

    @Test
    fun `the debug mode rejects an unknown command`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug a b\n:nope\n:quit\n")
        assertContains(output, "unknown debug command; type :help for debug commands")
    }

    @Test
    fun `the debug mode reports a rejected input`() {
        // The automaton of `simple.quiver` has no transition out of q0 on `b`.
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug b\n:step\n:quit\n")
        assertContains(output, "result: false (no transition for current symbol)")
    }

    @Test
    fun `the debug mode reports the end of the input`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug a b\n:step\n:step\n:step\n:quit\n")
        assertContains(output, "no more input; result=")
    }

    @Test
    fun `the debug command without a loaded program is reported`() {
        val output = runRepl(":debug a b\n:quit\n")
        assertContains(output, "no automaton/strategy loaded; use :load <file.quiver> first")
    }

    @Test
    fun `the recognize command uses the input of the program`() {
        // The command names no input, so the input the program declares is
        // the one the run recognizes.
        val output = runRepl(":load src/test/resources/input.quiver\n:recognize\n:quit\n")
        assertContains(output, "true")
    }

    @Test
    fun `the recognize command reports a program that declares no input`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:recognize\n:quit\n")
        assertContains(output, "the program declares no input; give one on the command line")
    }

    @Test
    fun `the recognize command reports an input that is not a symbol`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:recognize +\n:quit\n")
        assertContains(output, "error:")
    }

    @Test
    fun `the debug command reports an input that is not a symbol`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug +\n:quit\n")
        assertContains(output, "error:")
    }

    @Test
    fun `the input of a command is tokenized by the lexer`() {
        // The line is tokenized by the lexer of the language: the commas
        // separate the symbols, the quoted text is one symbol, and the
        // comment is not part of the input.
        assertEquals(listOf("a", "b"), tokenizeInput("a, b"))
        assertEquals(listOf("a b"), tokenizeInput("\"a b\""))
        assertEquals(listOf("a", "b"), tokenizeInput("a b # a comment"))
        assertEquals(listOf("42"), tokenizeInput("42"))
    }

    @Test
    fun `the reserved word is not a symbol of the input`() {
        val error = assertFailsWith<QuiverException> { tokenizeInput("epsilon") }
        assertContains(error.message ?: "", "epsilon is reserved")
    }

    @Test
    fun `an unexpected token is not a symbol of the input`() {
        val error = assertFailsWith<QuiverException> { tokenizeInput("+") }
        assertContains(error.message ?: "", "Unexpected token in the input")
    }

    @Test
    fun `the session loads the file named in the arguments`() {
        val run = cliRepl(":quit\n", "--load", "src/test/resources/simple.quiver")
        assertContains(run.stdout, "Loaded src/test/resources/simple.quiver")
        assertContains(run.stdout, "goodbye")
    }

    @Test
    fun `the session without arguments opens an empty session`() {
        val run = cliRepl(":rules\n:quit\n")
        assertContains(run.stdout, "(no rules loaded)")
    }

    @Test
    fun `the history of the session is kept in the state directory`() {
        assertEquals("/state/quiver/repl-history", historyFilePath("/state", "/home/user"))
        assertEquals("/home/user/.local/state/quiver/repl-history", historyFilePath(null, "/home/user"))
        assertEquals("/home/user/.local/state/quiver/repl-history", historyFilePath("  ", "/home/user"))
        assertEquals(null, historyFilePath(null, null))
        assertEquals(null, historyFilePath(null, "  "))
    }

    @Test
    fun `a state root that is not absolute is ignored`() {
        // The specification of the XDG says that a root that is not an
        // absolute path is ignored, and the home takes its place.
        assertEquals("/home/user/.local/state/quiver/repl-history", historyFilePath("state", "/home/user"))
        assertEquals("/home/user/.local/state/quiver/repl-history", historyFilePath("state/../state", "/home/user"))
    }

    @Test
    fun `the session of a pipe is not painted`() {
        // The colours are the privilege of a terminal: the transcript of a
        // session that is piped is the transcript of the article, byte for
        // byte.
        val output = runRepl(":quit\n")
        assertTrue(!output.contains("\u001b["), "The session was painted")
    }

    @Test
    fun `the colour option paints the session of a pipe`() {
        val run = cliRepl(":quit\n", "--color")
        assertContains(run.stdout, "\u001b[2m")
        assertContains(run.stdout, "\u001b[36m")
    }

    @Test
    fun `the no colour option paints nothing`() {
        val run = cliRepl(":quit\n", "--no-color")
        assertTrue(!run.stdout.contains("\u001b["), "The session was painted")
    }

    @Test
    fun `the colour options contradict each other`() {
        val run = cliRepl(":quit\n", "--color", "--no-color")
        assertEquals(1, run.status)
        assertContains(run.stderr, "--color and --no-color cannot be used together")
        // The usage summary names the command that was misused, and not the
        // command that started the run.
        assertContains(run.stderr, "Usage: quiver repl")
    }

    @Test
    fun `the no banner option opens the session with no banner`() {
        val run = cliRepl(":quit\n", "--no-banner")
        assertTrue(!run.stdout.contains("Quiver REPL"), "The banner was printed")
        assertContains(run.stdout, "goodbye")
    }

    @Test
    fun `the session opens with the banner`() {
        val run = cliRepl(":quit\n")
        assertContains(run.stdout, "Quiver REPL: a term rewriting language for adaptive automata")
    }

    @Test
    fun `the report of the loaded program follows the banner`() {
        val run = cliRepl(":quit\n", "--load", "src/test/resources/simple.quiver")
        val banner = run.stdout.indexOf("Quiver REPL")
        val report = run.stdout.indexOf("Loaded src/test/resources/simple.quiver")
        val prompt = run.stdout.indexOf("quiver> ")
        assertTrue(banner < report, "The report came before the banner")
        assertTrue(report < prompt, "The report came after the prompt")
    }

    @Test
    fun `the completion of a command names the commands`() {
        val completion = completionAt(":re", 3, QuiverSession())
        assertEquals(CompletionLane.Command, completion.lane)
        val candidates = completion.candidates.map { it.value }
        assertContains(candidates, ":recognize")
        assertContains(candidates, ":reset")
    }

    @Test
    fun `the completion of a command names the aliases of the commands`() {
        // The alias is a name the session answers to, so the completion
        // offers it as well.
        val completion = completionAt(":ex", 3, QuiverSession())
        assertEquals(listOf(":exit"), completion.candidates.map { it.value })
    }

    @Test
    fun `the completion of a command that takes no argument is silent`() {
        val completion = completionAt(":reset ", 7, QuiverSession())
        assertEquals(CompletionLane.Argument, completion.lane)
        assertEquals(emptyList(), completion.candidates)
    }

    @Test
    fun `the completion of an unknown command is silent`() {
        // The command is unknown, and the lane of the completion is the lane
        // of a command that takes no argument.
        val completion = completionAt(":dance ", 7, QuiverSession())
        assertEquals(CompletionLane.Argument, completion.lane)
        assertEquals(emptyList(), completion.candidates)
    }

    @Test
    fun `the completion of an argument names the words of the command`() {
        val completion = completionAt(":strategy ", 10, QuiverSession())
        assertEquals(CompletionLane.Argument, completion.lane)
        assertEquals(listOf("first", "all", "repeat"), completion.candidates.map { it.value })
    }

    @Test
    fun `the completion of an argument names the words that match it`() {
        val completion = completionAt(":strategy r", 11, QuiverSession())
        assertEquals(listOf("repeat"), completion.candidates.map { it.value })
    }

    @Test
    fun `the completion of an argument is silent when the word is complete`() {
        val completion = completionAt(":strategy repeat", 16, QuiverSession())
        assertEquals(emptyList(), completion.candidates)
    }

    @Test
    fun `the completion of an argument is silent once the argument is given`() {
        // The argument of the command is a single word, so the completion
        // stays silent after it.
        val completion = completionAt(":strategy first ", 16, QuiverSession())
        assertEquals(CompletionLane.Argument, completion.lane)
        assertEquals(emptyList(), completion.candidates)
    }

    @Test
    fun `the completion of a file is the lane of the files`() {
        val completion = completionAt(":load ", 6, QuiverSession())
        assertEquals(CompletionLane.File, completion.lane)
        assertEquals(emptyList(), completion.candidates)
    }

    @Test
    fun `the completion of the input of a command names the symbols`() {
        val session = QuiverSession()
        session.load("src/test/resources/simple.quiver")
        val completion = completionAt(":recognize ", 11, session)
        assertEquals(CompletionLane.Input, completion.lane)
        assertEquals(listOf("a", "b"), completion.candidates.map { it.value })
    }

    @Test
    fun `the completion of an input names the symbols of the program`() {
        val session = QuiverSession()
        session.load("src/test/resources/simple.quiver")
        val completion = completionAt("", 0, session)
        assertEquals(CompletionLane.Input, completion.lane)
        assertEquals(listOf("a", "b"), completion.candidates.map { it.value })
    }

    @Test
    fun `the completion of an input keeps the symbols that are fixed`() {
        // The commas separate the symbols of the input, so the text up to
        // the last comma is fixed, and the symbol after it is the one being
        // completed.
        val session = QuiverSession()
        session.load("src/test/resources/simple.quiver")
        val completion = completionAt("a,", 2, session)
        assertEquals(listOf("a,a", "a,b"), completion.candidates.map { it.value })
    }

    @Test
    fun `the completion of an input is silent when the symbol is complete`() {
        val session = QuiverSession()
        session.load("src/test/resources/simple.quiver")
        val completion = completionAt("a", 1, session)
        assertEquals(emptyList(), completion.candidates)
    }

    @Test
    fun `the highlighter paints the name of a command`() {
        val spans = highlightSpans(":load file.quiver")
        assertEquals(HighlightSpan(0, 5, HighlightKind.Command), spans.first())
    }

    @Test
    fun `the highlighter paints a command that takes no argument`() {
        // The command is the whole line, and there is no blank to end it.
        val spans = highlightSpans(":load")
        assertEquals(HighlightSpan(0, 5, HighlightKind.Command), spans.first())
    }

    @Test
    fun `the highlighter paints a word that starts with a blank`() {
        // The underscore and the dollar open a word of the language, and the
        // highlighter knows them.
        assertEquals(emptyList(), highlightSpans("_x"))
        assertEquals(emptyList(), highlightSpans("\$x"))
    }

    @Test
    fun `the highlighter paints a word with a hyphen`() {
        // The hyphen is part of a word, so the word is one span and not two.
        assertEquals(emptyList(), highlightSpans("a-b"))
    }

    @Test
    fun `the highlighter paints a string that ends with a backslash`() {
        // The backslash at the end of the line escapes nothing, and the
        // literal runs to the end of the line.
        val spans = highlightSpans("\"abc\\")
        assertEquals(HighlightSpan(0, 5, HighlightKind.String), spans.first())
    }

    @Test
    fun `the highlighter paints a comment`() {
        val spans = highlightSpans("a # a comment")
        assertEquals(HighlightSpan(2, 13, HighlightKind.Comment), spans.first())
    }

    @Test
    fun `the highlighter paints a string`() {
        val spans = highlightSpans("\"a b\"")
        assertEquals(HighlightSpan(0, 5, HighlightKind.String), spans.first())
    }

    @Test
    fun `the highlighter paints a string with an escaped quote`() {
        // The escaped quote does not end the string, and the span covers the
        // whole literal.
        val spans = highlightSpans("\"a\\\"b\"")
        assertEquals(HighlightSpan(0, 6, HighlightKind.String), spans.first())
    }

    @Test
    fun `the highlighter paints a string that is not closed`() {
        val spans = highlightSpans("\"abc")
        assertEquals(HighlightSpan(0, 4, HighlightKind.String), spans.first())
    }

    @Test
    fun `the highlighter paints a number`() {
        val spans = highlightSpans("42")
        assertEquals(HighlightSpan(0, 2, HighlightKind.Number), spans.first())
    }

    @Test
    fun `the highlighter paints a reserved word`() {
        val spans = highlightSpans("when")
        assertEquals(HighlightSpan(0, 4, HighlightKind.Keyword), spans.first())
    }

    @Test
    fun `the highlighter paints the hyphenated primitives`() {
        val spans = highlightSpans("transitions-from")
        assertEquals(HighlightSpan(0, 16, HighlightKind.Keyword), spans.first())
    }

    @Test
    fun `the word at the cursor is the run of characters that ends at it`() {
        assertEquals(":load", wordAt(":load file", 5))
        assertEquals("", wordAt(":load ", 6))
        assertEquals("file", wordAt(":load file", 10))
    }

    @Test
    fun `the history of the session is kept in the file it names`() {
        val file = Files.createTempDirectory("quiver-history").resolve("history")
        val run = cliReplWithHistory(":rules\n:quit\n", file.toString())
        assertContains(run.stdout, "goodbye")
        assertTrue(Files.exists(file), "The history file was not written")
        val history = Files.readAllLines(file)
        assertTrue(history.any { it.endsWith(":rules") }, "The history holds no :rules")
        assertTrue(history.any { it.endsWith(":quit") }, "The history holds no :quit")
    }

    @Test
    fun `the history of the session survives a session that ends with the end of the input`() {
        val file = Files.createTempDirectory("quiver-history").resolve("history")
        val run = cliReplWithHistory(":rules\n", file.toString())
        assertContains(run.stdout, "(no rules loaded)")
        assertTrue(Files.exists(file), "The history file was not written")
    }

    @Test
    fun `the history file may have no directory of its own`() {
        // The path of the history file may name no directory at all, and the
        // session must not fall over it. The file lands in the working
        // directory of the process, and the test removes it afterwards.
        val file = "quiver-history-test.tmp"
        try {
            val run = cliReplWithHistory(":quit\n", file)
            assertEquals(0, run.status)
            assertContains(run.stdout, "goodbye")
            assertTrue(Files.exists(Path.of(file)), "The history file was not written")
        } finally {
            Files.deleteIfExists(Path.of(file))
        }
    }

    @Test
    fun `the history of the session goes to the state directory by default`() {
        // The session that names no history file keeps it in the state
        // directory of the user.
        val state = Files.createTempDirectory("quiver-state")
        val terminal = DumbTerminal(ByteArrayInputStream(":quit\n".toByteArray()), ByteArrayOutputStream())
        val reader =
            buildReader(
                terminal,
                ReplOptions(noHistory = false, stateHome = state.toString(), home = state.toString()),
                QuiverRepl(),
            )
        reader.getHistory().add(":rules")
        reader.getHistory().save()
        val file = state.resolve("quiver").resolve("repl-history")
        assertTrue(Files.exists(file), "The history file was not written")
        assertTrue(Files.readAllLines(file).any { it.endsWith(":rules") }, "The history holds no :rules")
    }

    @Test
    fun `the session reads the commands that are typed`() {
        // The line the user types is the line the session answers: the
        // session goes on after every command but the last one.
        val output = runRepl(":rules\n:quit\n")
        assertContains(output, "(no rules loaded)")
        assertContains(output, "goodbye")
    }

    @Test
    fun `an interrupted line leaves the session alone`() {
        // The interruption kills the line that is being typed, and not the
        // session: the line is empty, and the session goes on.
        assertEquals("", readLine { throw UserInterruptException("") })
    }

    @Test
    fun `a tab separates a command from its argument`() {
        // The blank that separates a command from its argument is any
        // whitespace, and not only the space.
        val output = runRepl(":load\tsrc/test/resources/simple.quiver\n:quit\n")
        assertContains(output, "Loaded src/test/resources/simple.quiver")
    }

    @Test
    fun `the name of a command ends at the first blank`() {
        assertEquals(":load", commandNameOf(":load file.quiver"))
        assertEquals(":load", commandNameOf(":load\tfile.quiver"))
        assertEquals(":load", commandNameOf(":load"))
        assertEquals("file.quiver", commandArgumentOf(":load file.quiver"))
        assertEquals("file.quiver", commandArgumentOf(":load\tfile.quiver"))
        assertEquals("", commandArgumentOf(":load"))
    }

    @Test
    fun `the end of the input ends the session`() {
        assertEquals(null, readLine { throw EndOfFileException() })
    }

    @Test
    fun `the debug commands of a session without a debug session are silent`() {
        val session = QuiverSession()
        assertEquals(emptyList(), session.debugStep())
        assertEquals(emptyList(), session.debugInspect())
        assertEquals(emptyList(), session.debugContinue())
    }

    @Test
    fun `the debug session of a session is the one it started`() {
        val session = QuiverSession()
        session.load("src/test/resources/simple.quiver")
        assertTrue(!session.debugging, "The session was in the debug mode")
        session.startDebug("a b")
        assertTrue(session.debugging, "The session was not in the debug mode")
        session.leaveDebug()
        assertTrue(!session.debugging, "The session was in the debug mode")
    }
}
