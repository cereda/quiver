// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

/** Number of tokens in a fully-specified `:strategy` command: `kind max N`. */
private const val STRATEGY_SPEC_PARTS = 3

/**
 * The message reported when the reserved word `epsilon` appears in an input
 * line of the REPL.
 */
private const val RESERVED_EPSILON =
    "epsilon is reserved: it denotes the internal transition; write \"epsilon\" for the symbol"

/**
 * The message reported when a command needs a program and the session holds
 * none.
 */
private const val NO_PROGRAM = "no automaton/strategy loaded; use :load <file.quiver> first"

/**
 * The message reported when a command needs an input and the program
 * declares none.
 */
private const val NO_INPUT = "the program declares no input; give one on the command line"

/**
 * The session of the interactive REPL, without a terminal.
 *
 * The session owns the state of a Quiver program (the rules, the strategies,
 * the automaton and the flags) and answers the commands of the REPL with the
 * lines they print. It knows nothing of JLine: the terminal loop is a thin
 * layer on top of it, and the session is tested on its own.
 *
 * The state of the session is a single program, and not a bag of fields: the
 * automaton, the rules and the strategies of a session always come from the
 * same program, and the states that no program could produce are therefore
 * unrepresentable.
 */
internal class QuiverSession {

    /** The program loaded in the session, or null when it holds none. */
    private var program: ProgramSummary? = null

    /**
     * The strategy the user selected, or null when the strategy of the
     * program is the active one.
     */
    private var chosen: Strategy? = null

    /** Rule index keyed by pattern head key, for faster rule lookup. */
    private var ruleIndex: Map<String, List<Rule>>? = null

    /**
     * The symbols of the loaded program.
     *
     * The symbols are the ones the completion offers, and they change only
     * when the program changes, so they are computed once per load.
     */
    private var symbols: List<String> = emptyList()

    /** Whether step tracing is enabled (`:trace on|off`). */
    private var trace = false

    /** Whether non-deterministic recognition is enabled (`:nondet on|off`). */
    private var nondet = false

    /** The debug session, while one is running. */
    private var debug: QuiverDebugSession? = null

    /** True while the session is in the interactive debug mode. */
    val debugging: Boolean
        get() = debug != null

    /**
     * Loads a Quiver file.
     *
     * The load either succeeds and replaces the state of the session, or it
     * fails and leaves the previous state untouched.
     *
     * @param path the file path to load.
     * @return the lines the load reports.
     */
    fun load(path: String): List<ReportLine> = when (val result = loadProgram(path)) {
        is LoadResult.Failed ->
            listOf(ReportLine("error loading ${result.path}: ${result.detail}", LineKind.Failure))

        is LoadResult.Loaded -> {
            install(result.summary)
            loadReport(result.summary)
        }
    }

    /**
     * Installs the state of a loaded program in the session.
     *
     * @param summary the summary of the loaded program.
     */
    private fun install(summary: ProgramSummary) {
        program = summary
        // A load selects the strategy afresh: the choice the user made before
        // the load does not survive it.
        chosen = null
        // The index covers the rules of the active strategy: a program that
        // selects a named strategy runs that strategy's rules, and not the
        // top-level ones.
        ruleIndex = buildRuleIndex(summary.activeStrategy.rules)
        symbols = symbolsOf(summary.automaton)
    }

    /**
     * The summary of the state of the session.
     *
     * @return the summary, or null when the session holds no program.
     */
    private fun summary(): ProgramSummary? {
        val program = program ?: return null
        val active = chosen ?: program.activeStrategy
        return program.copy(activeStrategy = active)
    }

    /**
     * The report of the rules of the session.
     *
     * The report names the rules the active strategy fires, the rules the
     * strategy does not consult, and the strategy itself.
     *
     * @return the lines the report holds.
     */
    fun rulesReport(): List<ReportLine> {
        val summary = summary() ?: return listOf(ReportLine("(no rules loaded)"))
        return rulesReport(summary)
    }

    /**
     * Sets the rewriting strategy of the session.
     *
     * @param spec the specification of the strategy: the kind, and the
     *   iteration bound of the repeating one.
     * @return the lines the command reports.
     */
    fun setStrategy(spec: String): List<ReportLine> {
        val parts = spec.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val kind = parts.firstOrNull()?.let(::strategyKind)
        if (parts.isEmpty() || kind == null) {
            val message =
                if (parts.isEmpty()) {
                    "usage: :strategy <first|all|repeat [max N]>"
                } else {
                    "unknown strategy kind '${parts[0]}' (use first/all/repeat)"
                }
            return listOf(ReportLine(message))
        }
        var maxIter = DEFAULT_MAX_ITERATIONS
        if (parts.size >= STRATEGY_SPEC_PARTS && parts[1] == "max") {
            maxIter = parts[2].toIntOrNull() ?: DEFAULT_MAX_ITERATIONS
        }
        // The strategy the user chooses fires the top-level rules of the
        // program, and not the rules of the strategy the program names.
        val rules = program?.rules ?: emptyList()
        chosen = Strategy("repl", kind, maxIter, rules)
        // The index must follow the strategy: it covers the rules the strategy
        // fires, and not the ones of the strategy that was active before.
        ruleIndex = buildRuleIndex(rules)
        return listOf(ReportLine("strategy set to ${kind.name} (maxIterations=$maxIter)"))
    }

    /**
     * The kind of strategy a name denotes.
     *
     * @param name the name of the kind.
     * @return the kind, or null when the name denotes none.
     */
    private fun strategyKind(name: String): StrategyKind? = when (name) {
        "first" -> StrategyKind.FIRST
        "all" -> StrategyKind.ALL
        "repeat" -> StrategyKind.REPEAT
        else -> null
    }

    /**
     * Enables or disables the step trace.
     *
     * @param enabled whether the trace is on.
     * @return the lines the command reports.
     */
    fun setTrace(enabled: Boolean): List<ReportLine> {
        trace = enabled
        return listOf(ReportLine(if (enabled) "trace enabled" else "trace disabled"))
    }

    /**
     * Enables or disables the non-deterministic recognition.
     *
     * @param enabled whether the non-deterministic recognition is on.
     * @return the lines the command reports.
     */
    fun setNondet(enabled: Boolean): List<ReportLine> {
        nondet = enabled
        return listOf(
            ReportLine(
                if (enabled) {
                    "nondeterministic recognition enabled"
                } else {
                    "nondeterministic recognition disabled"
                },
            ),
        )
    }

    /**
     * Recognizes an input against the loaded automaton.
     *
     * @param input the input line, or null to use the input of the loaded
     *   program.
     * @return the lines the command reports.
     */
    fun recognize(input: String?): List<ReportLine> = recognizeWith(input, trace)

    /**
     * Recognizes an input with the trace turned on.
     *
     * @param input the input line to recognize.
     * @return the lines the command reports.
     */
    fun step(input: String): List<ReportLine> = recognizeWith(input, true)

    /**
     * Recognizes an input against the loaded automaton, with the given trace
     * setting, and reports the result.
     *
     * @param input the input line, or null to use the input of the loaded
     *   program.
     * @param withTrace whether the step trace is printed.
     * @return the lines the command reports.
     */
    private fun recognizeWith(input: String?, withTrace: Boolean): List<ReportLine> {
        val program = program ?: return listOf(ReportLine(NO_PROGRAM, LineKind.Failure))
        val automaton = program.automaton
        val strategy = chosen ?: program.activeStrategy
        val resolution = resolveInput(program, input)
        return when (resolution) {
            is InputResolution.Failed -> resolution.lines

            is InputResolution.Symbols ->
                recognizeSymbols(resolution.names, automaton, strategy, withTrace)
        }
    }

    /**
     * Recognizes the symbols of an input against the loaded automaton.
     *
     * @param symbols the symbols of the input, in order.
     * @param automaton the automaton to recognize against.
     * @param strategy the strategy of the recognition.
     * @param withTrace whether the step trace is printed.
     * @return the lines the command reports.
     */
    private fun recognizeSymbols(
        symbols: List<String>,
        automaton: Automaton,
        strategy: Strategy,
        withTrace: Boolean,
    ): List<ReportLine> {
        val result =
            if (nondet) {
                recognizeNondet(symbols, automaton, strategy, withTrace, ruleIndex = ruleIndex)
            } else {
                recognize(symbols, automaton, strategy, withTrace, ruleIndex = ruleIndex)
            }
        return listOf(verdict(result))
    }

    /**
     * Resolves the symbols of the input of a recognition.
     *
     * The line given to the command is tokenized by the lexer of the
     * language, and the program loaded supplies the input when the command
     * names none.
     *
     * @param program the program the session holds.
     * @param input the input line, or null to use the input of the loaded
     *   program.
     * @return the symbols of the input, or the report of the failure.
     */
    private fun resolveInput(program: ProgramSummary, input: String?): InputResolution = if (input == null) {
        val symbols = program.header.input
        if (symbols == null) {
            InputResolution.Failed(listOf(ReportLine(NO_INPUT, LineKind.Failure)))
        } else {
            InputResolution.Symbols(symbols)
        }
    } else {
        symbolsOfLine(input)
    }

    /**
     * The symbols of an input line.
     *
     * @param line the input line.
     * @return the symbols of the input, or the report of the failure.
     */
    private fun symbolsOfLine(line: String): InputResolution = try {
        InputResolution.Symbols(tokenizeInput(line))
    } catch (e: QuiverException) {
        InputResolution.Failed(listOf(ReportLine("error: ${e.message}", LineKind.Failure)))
    }

    /**
     * Clears the state of the session.
     *
     * @return the lines the command reports.
     */
    fun reset(): List<ReportLine> {
        program = null
        chosen = null
        ruleIndex = null
        symbols = emptyList()
        trace = false
        nondet = false
        debug = null
        return listOf(ReportLine("reset"))
    }

    /**
     * The symbols of the loaded program.
     *
     * The symbols are the labels of the transitions of the automaton, and the
     * internal marker is not one of them: it is the transition taken without
     * consuming a symbol. A symbol whose name is not a plain one is written
     * the way a program writes it, that is, quoted.
     *
     * @return the names of the symbols, in alphabetical order.
     */
    fun symbols(): List<String> = symbols

    /**
     * Starts an interactive debug session over an input.
     *
     * @param input the input line to step through.
     * @return the lines the command reports.
     */
    fun startDebug(input: String): List<ReportLine> {
        val program = program ?: return listOf(ReportLine(NO_PROGRAM, LineKind.Failure))
        val automaton = program.automaton
        val strategy = chosen ?: program.activeStrategy
        val resolution = symbolsOfLine(input)
        return when (resolution) {
            is InputResolution.Failed -> resolution.lines
            is InputResolution.Symbols -> beginDebug(resolution.names, automaton, strategy)
        }
    }

    /**
     * Begins the interactive debug session over an input.
     *
     * @param symbols the symbols of the input, in order.
     * @param automaton the automaton to step through.
     * @param strategy the strategy of the recognition.
     * @return the lines the command reports.
     */
    private fun beginDebug(symbols: List<String>, automaton: Automaton, strategy: Strategy): List<ReportLine> {
        // The debug mode steps a single path, so it always runs the
        // deterministic recognizer.
        val recognizer = Recognizer(automaton, strategy, trace, ruleIndex = ruleIndex)
        recognizer.input = symbols
        debug = QuiverDebugSession(recognizer)
        return listOf(
            ReportLine("debug mode started for ${symbols.size} symbol(s)"),
            ReportLine("state=${recognizer.state} position=${recognizer.position}"),
        )
    }

    /**
     * Steps the debug session one symbol at a time.
     *
     * @return the lines the command reports, and no lines at all when no
     *   debug session is running.
     */
    fun debugStep(): List<ReportLine> {
        val session = debug ?: return emptyList()
        return session.step()
    }

    /**
     * Reports the state of the debug session and of the automaton.
     *
     * @return the lines the command reports, and no lines at all when no
     *   debug session is running.
     */
    fun debugInspect(): List<ReportLine> {
        val session = debug ?: return emptyList()
        return session.inspect()
    }

    /**
     * Runs the debug session to completion, and leaves the debug mode.
     *
     * @return the lines the command reports, and no lines at all when no
     *   debug session is running.
     */
    fun debugContinue(): List<ReportLine> {
        val session = debug ?: return emptyList()
        val lines = session.continueToEnd()
        debug = null
        return lines
    }

    /**
     * Leaves the interactive debug session.
     *
     * @return the lines the command reports.
     */
    fun leaveDebug(): List<ReportLine> {
        debug = null
        return listOf(ReportLine("leaving debug mode"))
    }
}

/**
 * The line that reports the verdict of a recognition.
 *
 * @param result whether the input was recognized.
 * @return the line the verdict is reported in.
 */
internal fun verdict(result: Boolean): ReportLine = ReportLine(
    if (result) "true" else "false",
    if (result) LineKind.Recognized else LineKind.Rejected,
)

/**
 * The line that reports a result the debug mode reached.
 *
 * @param text the text of the line.
 * @param result whether the input was recognized.
 * @return the line the result is reported in.
 */
internal fun resultLine(text: String, result: Boolean): ReportLine =
    ReportLine(text, if (result) LineKind.Recognized else LineKind.Rejected)

/**
 * The outcome of resolving the input of a recognition.
 */
private sealed interface InputResolution {

    /**
     * The input is usable.
     *
     * @property names the names of the input symbols, in order.
     */
    data class Symbols(val names: List<String>) : InputResolution

    /**
     * The input is not usable.
     *
     * @property lines the report of the failure.
     */
    data class Failed(val lines: List<ReportLine>) : InputResolution
}

/**
 * The interactive debug session: the step-by-step walk over an input.
 *
 * @property recognizer the recognizer the session drives.
 */
internal class QuiverDebugSession(val recognizer: Recognizer) {

    /**
     * Steps one input symbol.
     *
     * @return the lines the step reports.
     */
    fun step(): List<ReportLine> {
        val rec = recognizer
        if (rec.finished) {
            return listOf(resultLine("debug finished; result=${rec.result}", rec.result == true))
        }
        val r = rec.step()
        return if (r == null) {
            listOf(resultLine("no more input; result=${rec.result}", rec.result == true))
        } else {
            stepLines(rec, r)
        }
    }

    /**
     * The lines that report one step of the debug mode.
     *
     * @param rec the recognizer the session drives.
     * @param r the result of the step.
     * @return the lines the step reports.
     */
    private fun stepLines(rec: Recognizer, r: StepResult): List<ReportLine> {
        val lines = mutableListOf<ReportLine>()
        lines += ReportLine("step position=${rec.position}: state=${rec.state}")
        lines += ReportLine("  transition fired: ${r.nextState ?: "none (rejected)"}")
        if (r.nextState == null) {
            lines += ReportLine("  result: false (no transition for current symbol)", LineKind.Rejected)
        }
        return lines
    }

    /**
     * Reports the state of the recognizer and of the automaton.
     *
     * @return the lines the report holds.
     */
    fun inspect(): List<ReportLine> {
        val rec = recognizer
        val lines = mutableListOf<ReportLine>()
        lines += ReportLine("input: ${rec.input.joinToString(", ")}")
        lines += ReportLine("position: ${rec.position} / ${rec.input.size}")
        lines += ReportLine("state: ${rec.state}")
        lines += ReportLine("finished: ${rec.finished}")
        lines += ReportLine("result: ${rec.result}")
        lines += ReportLine("automaton states: ${rec.automaton.states.sorted().joinToString(",")}")
        lines += ReportLine("accepting: ${rec.automaton.accepting.sorted().joinToString(",")}")
        lines += ReportLine("transitions (${rec.automaton.transitions.size}):")
        rec.automaton.transitions.forEach { t ->
            lines += ReportLine("  (${t.from}, ${t.label.render()}) -> (${t.to}, ${valueToString(t.value)})")
        }
        return lines
    }

    /**
     * Runs the recognition to completion.
     *
     * @return the lines the command reports.
     */
    fun continueToEnd(): List<ReportLine> {
        val result = recognizer.runToCompletion()
        return listOf(resultLine("run to completion: result=$result", result))
    }
}

/**
 * Tokenizes an input line into the names of the input symbols.
 *
 * The line is read with the lexer of the language: a bare token is one
 * symbol, and its name is the text of the token; a quoted string is one
 * symbol, and its name is the text of the string; the commas separate the
 * symbols, and they may be left out.
 *
 * @param line the input line.
 * @return the names of the input symbols, in order.
 * @throws QuiverException when the line holds a token that is not a symbol.
 */
internal fun tokenizeInput(line: String): List<String> {
    val symbols = mutableListOf<String>()
    for (token in lex(line)) {
        when (token) {
            is Token.Ident -> symbols += token.name
            is Token.StringLit -> symbols += token.text
            is Token.IntLit -> symbols += token.n.toString()
            is Token.Comma -> Unit
            is Token.Comment -> Unit
            is Token.Epsilon -> throw QuiverException(token.line, token.column, RESERVED_EPSILON)
            else -> throw QuiverException(token.line, token.column, "Unexpected token in the input: $token")
        }
    }
    return symbols
}
