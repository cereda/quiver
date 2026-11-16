// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import org.jline.builtins.Completers
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

/**
 * The lane a completion belongs to.
 *
 * The lane tells the adapter what the candidates of a completion are: the
 * names of the commands, the words of an argument, the names of the files or
 * the symbols of an input.
 */
internal enum class CompletionLane {

    /** The lane completes the name of a command. */
    Command,

    /** The lane completes the argument of a command. */
    Argument,

    /** The lane completes the name of a file. */
    File,

    /** The lane completes the symbols of an input. */
    Input,
}

/**
 * A candidate of the completion.
 *
 * @property value the text the candidate puts in the line.
 * @property description the one-line help of the candidate.
 */
internal data class CompletionCandidate(val value: String, val description: String? = null)

/**
 * A completion of the interactive REPL.
 *
 * @property lane the lane the completion belongs to.
 * @property word the word at the cursor, which the candidates replace.
 * @property candidates the candidates of the lane.
 */
internal data class Completion(val lane: CompletionLane, val word: String, val candidates: List<CompletionCandidate>)

/**
 * The completion of the interactive REPL, without a terminal.
 *
 * The policy knows four lanes:
 * - the name of a command, when the line starts with a colon and names no
 *   argument yet;
 * - the argument of a command, whose words depend on the command;
 * - the name of a file, for the command that loads a program;
 * - the symbols of an input, for the commands that recognize one and for the
 *   plain lines that are inputs themselves.
 *
 * @param buffer the line the cursor is on.
 * @param cursor the position of the cursor in the line.
 * @param session the session the completion consults.
 * @return the completion of the cursor.
 */
internal fun completionAt(buffer: String, cursor: Int, session: QuiverSession): Completion {
    val word = wordAt(buffer, cursor)
    val head = buffer.substring(0, cursor.coerceAtMost(buffer.length)).trimStart()
    return if (head.startsWith(":")) {
        commandCompletion(head, word, session)
    } else {
        inputCompletion(word, session.symbols())
    }
}

/**
 * The completion of a command line.
 *
 * @param head the text before the cursor, without its leading blanks.
 * @param word the word at the cursor.
 * @param session the session the completion consults.
 * @return the completion of the cursor.
 */
private fun commandCompletion(head: String, word: String, session: QuiverSession): Completion {
    val name = commandNameOf(head)
    if (name.length == head.length) {
        // (the name of the command is the word being typed)
        val prefix = word.removePrefix(":")
        val candidates = REPL_COMMANDS
            .flatMap { command ->
                command.names
                    .filter { it.startsWith(prefix) && it != prefix }
                    .map { CompletionCandidate(":$it", command.description) }
            }
        return Completion(CompletionLane.Command, word, candidates)
    }
    val command = findReplCommand(name.removePrefix(":"))
    val argument = command?.argument
    val text = commandArgumentOf(head)
    return when (argument) {
        null, ReplArgument.None -> Completion(CompletionLane.Argument, word, emptyList())
        ReplArgument.File -> Completion(CompletionLane.File, word, emptyList())
        is ReplArgument.Words -> wordsCompletion(text, word, argument.words)
        ReplArgument.Input -> inputCompletion(word, session.symbols())
    }
}

/**
 * The completion of the words of an argument.
 *
 * The argument of the command is a single word, so the completion stays
 * silent once the word is given.
 *
 * @param text the argument, without its leading blanks.
 * @param word the word at the cursor.
 * @param words the words the argument accepts.
 * @return the completion of the cursor.
 */
private fun wordsCompletion(text: String, word: String, words: List<String>): Completion {
    // The text before the word at the cursor holds the argument when the
    // user has already given it.
    if (text.dropLast(word.length).isNotBlank()) {
        return Completion(CompletionLane.Argument, word, emptyList())
    }
    val candidates = words
        .filter { it.startsWith(word) && it != word }
        .map { CompletionCandidate(it) }
    return Completion(CompletionLane.Argument, word, candidates)
}

/**
 * The completion of an input.
 *
 * The commas separate the symbols of an input, so the text up to the last
 * comma is fixed, and the symbol after it is the one being completed.
 *
 * @param word the word at the cursor.
 * @param symbols the symbols of the loaded program.
 * @return the completion of the cursor.
 */
private fun inputCompletion(word: String, symbols: List<String>): Completion {
    val comma = word.lastIndexOf(',')
    val fixed = if (comma >= 0) word.substring(0, comma + 1) else ""
    val prefix = word.substring(fixed.length)
    val candidates = symbols
        .filter { it.startsWith(prefix) && it != prefix }
        .map { CompletionCandidate(fixed + it) }
    return Completion(CompletionLane.Input, word, candidates)
}

/**
 * The command a name denotes.
 *
 * @param name the name of the command, without the leading colon.
 * @return the command, or null when the name denotes none.
 */
private fun findReplCommand(name: String): ReplCommandSpec? = REPL_COMMANDS.firstOrNull { name in it.names }

/**
 * The word at the cursor: the run of non-blank characters that ends at the
 * cursor.
 *
 * @param buffer the line the cursor is on.
 * @param cursor the position of the cursor in the line.
 * @return the word at the cursor.
 */
internal fun wordAt(buffer: String, cursor: Int): String {
    val end = cursor.coerceIn(0, buffer.length)
    var start = end
    while (start > 0 && !buffer[start - 1].isWhitespace()) start--
    return buffer.substring(start, end)
}

/**
 * The completer of the interactive REPL.
 *
 * JLine's word at the cursor decides nothing by itself: the policy of
 * [completionAt] names the lane, and the adapter maps the candidates of the
 * lane onto JLine's own.
 *
 * @property session the session the completion consults.
 */
internal class QuiverCompleter(private val session: () -> QuiverSession) : Completer {

    /**
     * Completes the line at the cursor.
     *
     * @param reader the reader of the session.
     * @param parsed the line the cursor is on.
     * @param candidates the list the candidates are added to.
     */
    override fun complete(reader: LineReader, parsed: ParsedLine, candidates: MutableList<Candidate>) {
        val completion = completionAt(parsed.line(), parsed.cursor(), session())
        if (completion.lane == CompletionLane.File) {
            // (the names of the files are JLine's own business)
            Completers.FileNameCompleter().complete(reader, parsed, candidates)
            return
        }
        for (candidate in completion.candidates) {
            candidates.add(
                Candidate(
                    candidate.value,
                    candidate.value,
                    null,
                    candidate.description,
                    null,
                    null,
                    true,
                ),
            )
        }
    }
}
