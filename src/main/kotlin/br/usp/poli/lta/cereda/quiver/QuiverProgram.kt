// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import java.io.File

/**
 * The facts of the header of a program.
 *
 * The facts are what the header says about the automaton and about the input
 * of the run; a program with no header has no facts but the absence of the
 * header itself.
 *
 * @property present whether the program declares an FSM header.
 * @property start the initial state of the automaton.
 * @property accepting the accepting states of the automaton.
 * @property states the states of the automaton.
 * @property alphabet the symbols the automaton reacts to.
 * @property transitions the number of transitions of the automaton.
 * @property input the input the program declares, or null when it declares
 *   none.
 */
internal data class HeaderFacts(
    val present: Boolean,
    val start: String?,
    val accepting: List<String>,
    val states: List<String>,
    val alphabet: List<String>,
    val transitions: Int,
    val input: List<String>?,
) {

    /** The facts of a program with no header. */
    companion object {
        /** The facts of a program that declares no FSM header. */
        val NONE: HeaderFacts =
            HeaderFacts(
                present = false,
                start = null,
                accepting = emptyList(),
                states = emptyList(),
                alphabet = emptyList(),
                transitions = 0,
                input = null,
            )
    }
}

/**
 * The summary of a loaded Quiver program.
 *
 * The summary is what the surfaces of the tool render: the REPL reports it
 * when a program is loaded, and the `inspect` command reports it as well. The
 * summary knows nothing of the terminal, so the two surfaces agree on the
 * facts and differ only on the way they show them.
 *
 * @property path the path of the file the program was loaded from.
 * @property rules the top-level rules of the program.
 * @property strategies the named strategies of the program, in source order.
 * @property activeStrategy the strategy the run consults.
 * @property automaton the automaton of the program: the one the header builds,
 *   and the default one when the program declares no header.
 * @property header the facts of the header of the program.
 */
internal data class ProgramSummary(
    val path: String,
    val rules: List<Rule>,
    val strategies: List<Strategy>,
    val activeStrategy: Strategy,
    val automaton: Automaton,
    val header: HeaderFacts,
)

/**
 * A program, parsed and ready to run.
 *
 * The body of a program is what its source says: the header, the rules, the
 * strategies and the automaton the header builds. Every surface of the tool
 * reads the same body, so the command line, the interactive session and the
 * inspection cannot disagree about the program they run.
 *
 * @property fsm the header of the program, or null when it declares none.
 * @property rules the top-level rules of the program.
 * @property strategies the named strategies of the program, in source order.
 * @property activeStrategy the strategy the header names, or the default one.
 * @property automaton the automaton the header builds.
 */
internal data class LoadedProgram(
    val fsm: Decl.Fsm?,
    val rules: List<Rule>,
    val strategies: List<Strategy>,
    val activeStrategy: Strategy,
    val automaton: Automaton,
)

/**
 * The outcome of loading a Quiver program.
 */
internal sealed interface LoadResult {

    /**
     * The program was loaded.
     *
     * @property summary the summary of the program.
     */
    data class Loaded(val summary: ProgramSummary) : LoadResult

    /**
     * The program could not be loaded.
     *
     * @property path the path of the file that failed.
     * @property detail the reason of the failure.
     */
    data class Failed(val path: String, val detail: String) : LoadResult
}

/**
 * Parses a program and builds its body.
 *
 * The body is built before it is returned: a program that fails to parse, to
 * resolve or to build never yields half a program. The body is the single
 * source of the truth of a run, so the surfaces of the tool cannot disagree
 * about the program they run.
 *
 * @param source the source of the program.
 * @param baseDir the directory the includes are resolved against.
 * @param sourcePath the path of the source file, when it is known: the file
 *   takes part in the cycle detection of the `include` directives.
 * @return the body of the program.
 * @throws QuiverException when the program is malformed.
 */
internal fun loadBody(source: String, baseDir: String, sourcePath: String?): LoadedProgram {
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
    val resolved = resolveIncludes(decls, baseDir, included, chain)
    val fsm = resolved.filterIsInstance<Decl.Fsm>().firstOrNull()
    val rules = resolved.filterIsInstance<Decl.RuleDecl>().map { it.rule }
    val strategies = resolved.filterIsInstance<Decl.StrategyDecl>().map { it.strategy }
    // The active strategy is the one named in the header, and the default one
    // otherwise. A label that two strategies share names the last of them.
    val active = fsm?.strategyLabel?.let { label -> strategies.lastOrNull { it.label == label } }
        ?: Strategy("default", StrategyKind.FIRST, DEFAULT_MAX_ITERATIONS, rules)
    val automaton = buildAutomaton(fsm)
    // A cycle among the internal transitions that the rules cannot break is a
    // program that never finishes: it is refused before it is run.
    checkEpsilonCycles(automaton, active.rules)
    return LoadedProgram(fsm, rules, strategies, active, automaton)
}

/**
 * Loads a Quiver program and summarizes it.
 *
 * The load either succeeds and yields the summary of the program, or it
 * fails and yields the reason; a failed load never leaves a half-built
 * program behind, since the summary is built before it is returned.
 *
 * @param path the path of the file to load.
 * @return the outcome of the load.
 */
internal fun loadProgram(path: String): LoadResult {
    // The catch is deliberately generic: the load must survive any bad input,
    // be it a parse error, an unreadable file or a malformed mapping, and a
    // failure reports the reason and loads nothing.
    @Suppress("TooGenericExceptionCaught")
    return try {
        val file = File(path)
        val source = file.readText()
        // The `include` directives are resolved relative to the directory of
        // the loaded file, not to the working directory.
        val baseDir = file.absoluteFile.parent ?: "."
        val body = loadBody(source, baseDir, file.absolutePath)
        LoadResult.Loaded(
            ProgramSummary(
                path = path,
                rules = body.rules,
                strategies = body.strategies,
                activeStrategy = body.activeStrategy,
                automaton = body.automaton,
                header = headerFacts(body.fsm, body.automaton),
            ),
        )
    } catch (e: Exception) {
        LoadResult.Failed(path, e.message ?: e.toString())
    }
}

/**
 * The facts of the header of a program.
 *
 * @param fsm the header of the program, or null when it declares none.
 * @param automaton the automaton the header builds.
 * @return the facts of the header.
 */
private fun headerFacts(fsm: Decl.Fsm?, automaton: Automaton): HeaderFacts = if (fsm == null) {
    HeaderFacts.NONE
} else {
    HeaderFacts(
        present = true,
        start = fsm.start,
        accepting = fsm.accepting,
        states = automaton.states.sorted(),
        alphabet = symbolsOf(automaton),
        transitions = automaton.transitions.size,
        input = fsm.input,
    )
}

/**
 * The symbols an automaton reacts to.
 *
 * The symbols are the labels of the transitions of the automaton, and the
 * internal marker is not one of them: it is the transition taken without
 * consuming a symbol. A symbol whose name is not a plain one is written the
 * way a program writes it, that is, quoted.
 *
 * @param automaton the automaton.
 * @return the names of the symbols, in alphabetical order.
 */
internal fun symbolsOf(automaton: Automaton): List<String> = automaton
    .transitions
    .map { it.label }
    .filterIsInstance<Label.Symbol>()
    .map { it.render() }
    .distinct()
    .sorted()

/**
 * The line that reports one rule.
 *
 * @param index the position of the rule in its group.
 * @param rule the rule to report.
 * @return the line the rule is reported in.
 */
internal fun ruleLine(index: Int, rule: Rule): String =
    "[$index] ${exprToString(rule.pattern)} => { ${rule.clauses.size} clause(s) }"

/**
 * The report of a successful load.
 *
 * @param summary the summary of the loaded program.
 * @return the lines the load reports.
 */
internal fun loadReport(summary: ProgramSummary): List<ReportLine> {
    val lines =
        mutableListOf(
            ReportLine(
                "Loaded ${summary.path}: ${summary.rules.size} rule(s), " +
                    "${summary.strategies.size} strategy(ies)",
            ),
        )
    val header = summary.header
    if (!header.present) {
        lines += ReportLine("  (no FSM header; recognition requires :load a file with @fsm)")
        return lines
    }
    lines += ReportLine("  FSM: start=${header.start}, accepting=${header.accepting.joinToString(",")}")
    lines += ReportLine("  mapping: ${header.transitions} transition(s)")
    lines += ReportLine("  input: ${inputFact(header.input)}")
    return lines
}

/**
 * The fact about the input of a program.
 *
 * @param input the input the program declares, or null when it declares none.
 * @return the text of the fact.
 */
internal fun inputFact(input: List<String>?): String = if (input == null) {
    "(none declared; give one on the command line)"
} else {
    "${input.size} symbol(s): ${input.joinToString(", ")}"
}

/**
 * The report of the rules of a program.
 *
 * The report names the rules the active strategy fires, the rules the
 * strategy does not consult, and the strategy itself.
 *
 * @param summary the summary of the program.
 * @return the lines the report holds.
 */
internal fun rulesReport(summary: ProgramSummary): List<ReportLine> {
    val active = summary.activeStrategy.rules
    if (summary.rules.isEmpty() && active.isEmpty()) return listOf(ReportLine("(no rules loaded)"))
    val lines = mutableListOf<ReportLine>()
    // The rules of the active strategy are the ones that fire, so they are
    // the ones the session reports as the loaded rules.
    lines += ReportLine("Loaded rules (${active.size}):")
    active.forEachIndexed { i, rule -> lines += ReportLine("  ${ruleLine(i, rule)}") }
    // The selection of a strategy is exclusive: a program that names a
    // strategy in its header runs that strategy's rules and not the
    // top-level ones. The top-level rules that the strategy does not
    // consult are listed separately, so that the session shows what the
    // program declares as well as what it runs.
    val shadowed = summary.rules.filterNot { it in active }
    if (shadowed.isNotEmpty()) {
        lines += ReportLine("Top-level rules (${shadowed.size}), not consulted by the active strategy:")
        shadowed.forEachIndexed { i, rule -> lines += ReportLine("  ${ruleLine(i, rule)}") }
    }
    lines += ReportLine("Active strategy: ${summary.activeStrategy.kind.name} (${active.size} rules)")
    return lines
}

/**
 * The report of a program, for the `inspect` command.
 *
 * The report is the summary of the program in one block: the facts of the
 * header, the strategies, and the rules the run consults.
 *
 * @param summary the summary of the program.
 * @return the lines the report holds.
 */
internal fun inspectReport(summary: ProgramSummary): List<ReportLine> {
    val lines = mutableListOf<ReportLine>()
    lines += ReportLine("Program: ${summary.path}")
    lines += ReportLine("  top-level rules: ${summary.rules.size}")
    lines += ReportLine("  strategies: ${summary.strategies.size}")
    lines += headerLines(summary.header)
    val active = summary.activeStrategy
    lines += ReportLine("  active strategy: ${active.label} (${active.kind.name}, ${active.rules.size} rules)")
    if (summary.strategies.isNotEmpty()) {
        lines += ReportLine("  strategies:")
        summary.strategies.forEach {
            lines += ReportLine("    ${it.label} (${it.kind.name}, ${it.rules.size} rules)")
        }
    }
    lines += ruleLines(summary)
    return lines
}

/**
 * The lines that report the facts of the header of a program.
 *
 * @param header the facts of the header.
 * @return the lines the facts are reported in.
 */
private fun headerLines(header: HeaderFacts): List<ReportLine> {
    if (!header.present) {
        return listOf(ReportLine("  (no FSM header; recognition requires a file with @fsm)"))
    }
    return listOf(
        ReportLine("  FSM: start=${header.start}, accepting=${header.accepting.joinToString(",")}"),
        ReportLine("    states: ${header.states.joinToString(", ")}"),
        ReportLine("    alphabet: ${header.alphabet.joinToString(", ")}"),
        ReportLine("    transitions: ${header.transitions}"),
        ReportLine("  input: ${inputFact(header.input)}"),
    )
}

/**
 * The lines that report the rules of a program.
 *
 * @param summary the summary of the program.
 * @return the lines the rules are reported in.
 */
private fun ruleLines(summary: ProgramSummary): List<ReportLine> {
    val active = summary.activeStrategy.rules
    val lines = mutableListOf<ReportLine>()
    if (active.isNotEmpty()) {
        lines += ReportLine("  rules:")
        active.forEachIndexed { i, rule -> lines += ReportLine("    ${ruleLine(i, rule)}") }
    }
    val shadowed = summary.rules.filterNot { it in active }
    if (shadowed.isNotEmpty()) {
        lines += ReportLine("  top-level rules (${shadowed.size}), not consulted by the active strategy:")
        shadowed.forEachIndexed { i, rule -> lines += ReportLine("    ${ruleLine(i, rule)}") }
    }
    return lines
}
