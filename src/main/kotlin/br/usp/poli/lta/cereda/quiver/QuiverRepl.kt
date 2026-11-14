// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import java.io.BufferedReader
import java.io.InputStreamReader

/** Number of tokens in a fully-specified `:strategy` command: `kind max N`. */
private const val STRATEGY_SPEC_PARTS = 3

/**
 * Interactive REPL for the Quiver term-rewriting language.
 *
 * Usage:
 *   ./gradlew runRepl
 *
 * Commands:
 *   :load <file.quiver>   load a Quiver source file (rules + FSM header)
 *   :rules             list the currently loaded rules
 *   :strategy <kind>   set the rewriting strategy (first/all/repeat [max N])
 *   :trace on|off      enable/disable step tracing
 *   :nondet on|off     enable/disable non-deterministic recognition
 *   :step <input>      recognize input and print trace to stderr
 *   :recognize <input> recognize input (print true/false)
 *   :debug <input>     enter interactive step-by-step debug mode for input
 *   :reset             clear all rules and the automaton
 *   :quit              exit the REPL
 *   :help              show this help
 *
 * Debug mode commands:
 *   :step   step one input symbol
 *   :inspect show current state, position, and automaton
 *   :continue / :c run to completion
 *   :quit   exit debug mode (return to REPL)
 *
 * Any other line (outside debug mode) is treated as input to recognize
 * against the loaded automaton.
 */
class QuiverRepl(private val reader: BufferedReader = BufferedReader(InputStreamReader(System.`in`))) {

    /** Rules loaded from the most recent `:load`, in source order. */
    private var rules: List<Rule> = emptyList()

    /** Strategies loaded from the most recent `:load`, keyed by label. */
    private var strategies: Map<String, Strategy> = emptyMap()

    /** The strategy currently used for recognition. */
    private var activeStrategy: Strategy? = null

    /** The automaton built from the most recently loaded FSM header. */
    private var automaton: Automaton? = null

    /** Rule index keyed by pattern head key, for faster rule lookup. */
    private var ruleIndex: Map<String, List<Rule>>? = null

    /** Whether step tracing is enabled (`:trace on|off`). */
    private var trace = false

    /** Whether non-deterministic recognition is enabled (`:nondet on|off`). */
    private var nondet = false

    /** True while the REPL is in interactive debug mode. */
    private var debugMode = false

    /** The recognizer driving the current debug session, if any. */
    private var debugRecognizer: Recognizer? = null

    /** Runs the read-eval-print loop until `:quit` or end of input. */
    internal fun run() {
        println("Quiver REPL: a term rewriting language for adaptive automata")
        println("Type :help for commands, :quit to exit.")
        println()

        while (true) {
            print(if (debugMode) "quiver-debug> " else "quiver> ")
            val line = reader.readLine() ?: return
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> Unit
                debugMode -> handleDebugCommand(trimmed)
                else -> if (!handleCommand(trimmed)) return
            }
        }
    }

    /**
     * Dispatches one REPL command line; plain text is input to recognize.
     *
     * @return false when the REPL must stop (`:quit`).
     */
    private fun handleCommand(trimmed: String): Boolean {
        when {
            trimmed == ":quit" || trimmed == ":exit" -> {
                println("goodbye")
                return false
            }

            trimmed == ":help" -> printHelp()

            trimmed == ":rules" -> printRules()

            trimmed == ":trace on" -> {
                trace = true
                println("trace enabled")
            }

            trimmed == ":trace off" -> {
                trace = false
                println("trace disabled")
            }

            trimmed == ":nondet on" -> {
                nondet = true
                println("nondeterministic recognition enabled")
            }

            trimmed == ":nondet off" -> {
                nondet = false
                println("nondeterministic recognition disabled")
            }

            trimmed.startsWith(":load ") -> loadFile(trimmed.removePrefix(":load ").trim())

            // The command with no argument reports its usage, instead of being
            // taken for an input to recognize.
            trimmed == ":strategy" -> setStrategy("")

            trimmed.startsWith(":strategy ") -> setStrategy(trimmed.removePrefix(":strategy ").trim())

            trimmed.startsWith(":step ") -> stepInput(trimmed.removePrefix(":step ").trim())

            trimmed.startsWith(":recognize ") -> recognizeInput(trimmed.removePrefix(":recognize ").trim())

            trimmed.startsWith(":debug ") -> startDebug(trimmed.removePrefix(":debug ").trim())

            trimmed == ":reset" -> reset()

            else -> recognizeInput(trimmed)
        }
        return true
    }

    /** Dispatches a command typed while in debug mode. */
    private fun handleDebugCommand(trimmed: String) {
        when {
            trimmed == ":quit" || trimmed == ":exit" -> {
                println("leaving debug mode")
                debugMode = false
                debugRecognizer = null
            }

            trimmed == ":help" -> printDebugHelp()

            trimmed == ":step" -> debugStep()

            trimmed == ":inspect" -> debugInspect()

            trimmed == ":continue" || trimmed == ":c" -> debugContinue()

            else -> println("unknown debug command; type :help for debug commands")
        }
    }

    /** Prints the main command help. */
    private fun printHelp() {
        println("Commands:")
        println("  :load <file.quiver>   load a Quiver source file (rules + FSM header)")
        println("  :rules             list the currently loaded rules")
        println("  :strategy <kind>   set rewriting strategy (first / all / repeat [max N])")
        println("  :trace on|off      enable/disable step tracing")
        println("  :nondet on|off     enable/disable non-deterministic recognition")
        println("  :step <input>      recognize input and print trace to stderr")
        println("  :recognize <input> recognize input (print true/false)")
        println("  :debug <input>     enter interactive step-by-step debug mode for input")
        println("  :reset             clear all rules and the automaton")
        println("  :quit              exit the REPL")
        println("  :help              show this help")
        println()
        println("Any other line (outside debug mode) is treated as input to recognize")
        println("against the loaded automaton.")
    }

    /** Prints the debug-mode command help. */
    private fun printDebugHelp() {
        println("Debug commands:")
        println("  :step        step one input symbol")
        println("  :inspect     show current state, position, and automaton")
        println("  :continue / :c   run to completion")
        println("  :quit        exit debug mode (return to REPL)")
    }

    /**
     * Prints the rules of the active strategy, the rules of the program that
     * the active strategy does not consult, and a summary of the strategy.
     */
    private fun printRules() {
        val strategy = activeStrategy
        if (strategy == null) {
            println("(no rules loaded)")
            return
        }
        if (rules.isEmpty() && strategy.rules.isEmpty()) {
            println("(no rules loaded)")
            return
        }
        // The rules of the active strategy are the ones that fire, so they are
        // the ones the session reports as the loaded rules.
        val active = strategy.rules
        println("Loaded rules (${active.size}):")
        active.forEachIndexed { i, rule ->
            println("  [$i] ${exprToString(rule.pattern)} => { ${rule.clauses.size} clause(s) }")
        }
        // The selection of a strategy is exclusive: a program that names a
        // strategy in its header runs that strategy's rules and not the
        // top-level ones. The top-level rules that the strategy does not
        // consult are listed separately, so that the session shows what the
        // program declares as well as what it runs.
        val shadowed = rules.filterNot { it in active }
        if (shadowed.isNotEmpty()) {
            println("Top-level rules (${shadowed.size}), not consulted by the active strategy:")
            shadowed.forEachIndexed { i, rule ->
                println("  [$i] ${exprToString(rule.pattern)} => { ${rule.clauses.size} clause(s) }")
            }
        }
        println("Active strategy: ${strategy.kind.name} (${strategy.rules.size} rules)")
    }

    /**
     * Loads a Quiver file: parses it, rebuilds the rules, strategies, rule index,
     * and the automaton from the FSM header, and selects the active strategy.
     *
     * @param path the file path to load.
     */
    internal fun loadFile(path: String) {
        // The catch is deliberately generic: the REPL must survive any bad
        // input, be it a parse error, an unreadable file or a malformed
        // mapping, and a load failure reports the message and leaves the
        // previous session state intact.
        @Suppress("TooGenericExceptionCaught")
        try {
            val file = java.io.File(path)
            val source = file.readText()
            val decls = parseSource(source)
            // The `include` directives are resolved relative to the directory
            // of the loaded file, not to the working directory.
            val baseDir = file.absoluteFile.parent ?: "."
            val sourcePath = canonicalPath(file)
            val resolved =
                resolveIncludes(
                    decls,
                    baseDir,
                    mutableSetOf(sourcePath),
                    mutableSetOf(sourcePath),
                )
            val fsm = resolved.filterIsInstance<Decl.Fsm>().firstOrNull()
            val newRules = resolved.filterIsInstance<Decl.RuleDecl>().map { it.rule }
            val newStrategies = resolved.filterIsInstance<Decl.StrategyDecl>().associateBy {
                it.strategy.label
            }.mapValues { it.value.strategy }

            // Select active strategy: the one named in the FSM header, else default FIRST.
            val selected: Strategy = fsm?.strategyLabel?.let { newStrategies[it] }
                ?: Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, newRules)

            // The rule index covers the rules of the active strategy: a program
            // that selects a named strategy runs that strategy's rules, and not
            // the top-level ones.
            val newRuleIndex = buildRuleIndex(selected.rules)

            // Build the automaton from the FSM header if present.
            val newAutomaton = buildAutomaton(fsm)

            // Everything is ready: the new session state replaces the old one.
            rules = newRules
            strategies = newStrategies
            activeStrategy = selected
            ruleIndex = newRuleIndex
            automaton = newAutomaton

            println("Loaded $path: ${rules.size} rule(s), ${strategies.size} strategy(ies)")
            if (fsm != null) {
                println("  FSM: start=${fsm.start}, accepting=${fsm.accepting.joinToString(",")}")
                println("  mapping: ${fsm.mapping.size} transition(s)")
            } else {
                println("  (no FSM header; recognition requires :load a file with @fsm)")
            }
        } catch (e: Exception) {
            println("error loading $path: ${e.message}")
        }
    }

    /** Applies a `:strategy <first|all|repeat [max N]>` command. */
    private fun setStrategy(spec: String) {
        val parts = spec.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            println("usage: :strategy <first|all|repeat [max N]>")
            return
        }
        val kind = when (parts[0]) {
            "first" -> StrategyKind.FIRST

            "all" -> StrategyKind.ALL

            "repeat" -> StrategyKind.REPEAT

            else -> {
                println("unknown strategy kind '${parts[0]}' (use first/all/repeat)")
                return
            }
        }
        var maxIter = DEFAULT_MAX_ITERATIONS
        if (parts.size >= STRATEGY_SPEC_PARTS && parts[1] == "max") {
            maxIter = parts[2].toIntOrNull() ?: DEFAULT_MAX_ITERATIONS
        }
        activeStrategy = Strategy("repl", kind, maxIter, rules)
        // The index must follow the strategy: it covers the rules the strategy
        // fires, and not the ones of the strategy that was active before.
        ruleIndex = buildRuleIndex(rules)
        println("strategy set to ${kind.name} (maxIterations=$maxIter)")
    }

    /** Recognizes an input against the loaded automaton, printing true/false. */
    private fun recognizeInput(input: String) {
        recognizeWith(input, trace)
    }

    /** Handles `:step <input>`: recognition with the trace turned on. */
    private fun stepInput(input: String) {
        recognizeWith(input, true)
    }

    /**
     * Recognizes an input against the loaded automaton, with the given trace
     * setting, and prints the result.
     *
     * @param input the input to recognize.
     * @param withTrace whether the step trace is printed.
     */
    private fun recognizeWith(input: String, withTrace: Boolean) {
        val automaton = automaton
        val strategy = activeStrategy
        if (automaton == null || strategy == null) {
            println("no automaton/strategy loaded; use :load <file.quiver> first")
            return
        }
        val result =
            if (nondet) {
                recognizeNondet(input, automaton, strategy, withTrace, ruleIndex = ruleIndex)
            } else {
                recognize(input, automaton, strategy, withTrace, ruleIndex = ruleIndex)
            }
        println(if (result) "true" else "false")
    }

    /** Enters interactive debug mode for the given input (`:debug <input>`). */
    private fun startDebug(input: String) {
        val automaton = automaton
        val strategy = activeStrategy
        if (automaton == null || strategy == null) {
            println("no automaton/strategy loaded; use :load <file.quiver> first")
            return
        }
        // The debug mode steps a single path, so it always runs the
        // deterministic recognizer.
        val recognizer = Recognizer(automaton, strategy, trace, ruleIndex = ruleIndex)
        recognizer.input = input
        debugRecognizer = recognizer
        debugMode = true
        println("debug mode started for input '$input' (length ${input.length})")
        println("state=${recognizer.state} position=${recognizer.position}")
    }

    /** Handles `:step` in debug mode: consumes one input symbol. */
    private fun debugStep() {
        val rec = debugRecognizer ?: return
        if (rec.finished) {
            println("debug finished; result=${rec.result}")
            return
        }
        val r = rec.step()
        if (r == null) {
            println("no more input; result=${rec.result}")
            return
        }
        println("step position=${rec.position}: state=${rec.state}")
        println("  transition fired: ${r.nextState ?: "none (rejected)"}")
        if (r.nextState == null) {
            println("  result: false (no transition for current symbol)")
        }
    }

    /** Handles `:inspect` in debug mode: prints recognizer and automaton state. */
    private fun debugInspect() {
        val rec = debugRecognizer ?: return
        println("input: '${rec.input}'")
        println("position: ${rec.position} / ${rec.input.length}")
        println("state: ${rec.state}")
        println("finished: ${rec.finished}")
        println("result: ${rec.result}")
        println("automaton states: ${rec.automaton.states.sorted().joinToString(",")}")
        println("accepting: ${rec.automaton.accepting.sorted().joinToString(",")}")
        println("transitions (${rec.automaton.transitions.size}):")
        rec.automaton.transitions.forEach { t ->
            println("  (${t.from}, ${t.symbol}) -> (${t.to}, ${valueToString(t.value)})")
        }
    }

    /** Handles `:continue` in debug mode: runs to completion and exits debug mode. */
    private fun debugContinue() {
        val rec = debugRecognizer ?: return
        val result = rec.runToCompletion()
        println("run to completion: result=$result")
        debugMode = false
        debugRecognizer = null
    }

    /** Handles `:reset`: clears all REPL state (rules, automaton, strategy). */
    private fun reset() {
        rules = emptyList()
        strategies = emptyMap()
        activeStrategy = null
        automaton = null
        ruleIndex = null
        trace = false
        nondet = false
        debugMode = false
        debugRecognizer = null
        println("reset")
    }
}

/**
 * REPL entry point. Invoked by the Gradle `runRepl` task.
 */
fun main(args: Array<String>) {
    startRepl(args)
}

/**
 * Starts the interactive REPL.
 *
 * @param args the arguments of the session; a single one names a source file
 *   to load before the session starts.
 */
internal fun startRepl(args: Array<String>) {
    val repl = QuiverRepl()
    // If a file argument is given, load it then enter the REPL.
    if (args.isNotEmpty()) {
        repl.loadFile(args[0])
    }
    repl.run()
}
