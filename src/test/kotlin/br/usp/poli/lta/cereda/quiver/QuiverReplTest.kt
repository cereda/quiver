// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * The tests of the interactive REPL.
 *
 * The REPL reads its commands from a reader, so a script drives it exactly as
 * a user would; the output is captured from the standard output.
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
        val output = runRepl(":load src/test/resources/simple.quiver\n:step ab\n:quit\n")
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
                    "ab\n" +
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
        val output = runRepl(":load src/test/resources/simple.quiver\n:recognize ab\n:quit\n")
        assertContains(output, "true")
    }

    @Test
    fun `the recognize command rejects an input the automaton does not accept`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:recognize a\n:quit\n")
        assertContains(output, "false")
    }

    @Test
    fun `a plain line is an input to recognize`() {
        val output = runRepl(":load src/test/resources/simple.quiver\nab\n:quit\n")
        assertContains(output, "true")
    }

    @Test
    fun `the step command recognizes the input`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:step ab\n:quit\n")
        assertContains(output, "true")
    }

    @Test
    fun `recognition without a loaded program is reported`() {
        val output = runRepl(":recognize ab\n:quit\n")
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
    fun `the debug command starts a debug session`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug ab\n:quit\n")
        assertContains(output, "debug mode started for input 'ab' (length 2)")
        assertContains(output, "state=q0 position=0")
        assertContains(output, "quiver-debug> ")
    }

    @Test
    fun `the debug mode steps one symbol at a time`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug ab\n:step\n:inspect\n:quit\n")
        assertContains(output, "step position=1: state=q1")
        assertContains(output, "transition fired: q1")
        assertContains(output, "position: 1 / 2")
        assertContains(output, "state: q1")
        assertContains(output, "finished: false")
    }

    @Test
    fun `the debug mode runs to completion`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug ab\n:continue\n:quit\n")
        assertContains(output, "run to completion: result=true")
        assertContains(output, "quiver> ")
    }

    @Test
    fun `the debug mode can be left`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug ab\n:quit\n:rules\n:quit\n")
        assertContains(output, "leaving debug mode")
        assertContains(output, "quiver> ")
    }

    @Test
    fun `the debug mode can be left with the exit command`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug ab\n:exit\n:rules\n:quit\n")
        assertContains(output, "leaving debug mode")
        assertContains(output, "quiver> ")
    }

    @Test
    fun `the debug mode continues with the short command`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug ab\n:c\n:quit\n")
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
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug ab\n:help\n:quit\n")
        assertContains(output, ":inspect")
        assertContains(output, ":continue / :c")
    }

    @Test
    fun `the debug mode rejects an unknown command`() {
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug ab\n:nope\n:quit\n")
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
        val output = runRepl(":load src/test/resources/simple.quiver\n:debug ab\n:step\n:step\n:step\n:quit\n")
        assertContains(output, "no more input; result=")
    }

    @Test
    fun `the debug command without a loaded program is reported`() {
        val output = runRepl(":debug ab\n:quit\n")
        assertContains(output, "no automaton/strategy loaded; use :load <file.quiver> first")
    }

    @Test
    fun `the repl entry point loads the file named in the arguments`() {
        val output =
            captureStdout {
                withStandardInput(":quit\n") {
                    invokeReplMain(arrayOf("src/test/resources/simple.quiver"))
                }
            }
        assertContains(output, "Loaded src/test/resources/simple.quiver")
        assertContains(output, "goodbye")
    }

    @Test
    fun `the repl entry point without arguments opens an empty session`() {
        val output =
            captureStdout {
                withStandardInput(":rules\n:quit\n") {
                    invokeReplMain(emptyArray())
                }
            }
        assertContains(output, "(no rules loaded)")
    }

    /**
     * Invokes the entry point of the REPL.
     *
     * The two entry points of the project share their name, so the REPL's is
     * reached through the facade class of its file.
     *
     * @param args the arguments of the entry point.
     */
    private fun invokeReplMain(args: Array<String>) {
        val facade = Class.forName("br.usp.poli.lta.cereda.quiver.QuiverReplKt")
        val entryPoint = facade.getDeclaredMethod("main", Array<String>::class.java)
        entryPoint.invoke(null, args)
    }

    /**
     * Drives the REPL with a script and returns everything it printed.
     *
     * @param script the commands to type.
     */
    private fun runRepl(script: String): String = captureStdout {
        QuiverRepl(BufferedReader(StringReader(script))).run()
    }
}
