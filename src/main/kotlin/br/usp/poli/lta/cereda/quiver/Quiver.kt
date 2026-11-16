// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.quiver

import com.github.ajalt.clikt.core.main

/**
 * Entry point of the interpreter.
 *
 * The command line has three subcommands: `run` runs a program over an input,
 * `repl` starts the interactive session, and `inspect` summarizes a program.
 * A bare invocation prints the help summary.
 */
fun main(args: Array<String>) {
    QuiverCommand().main(args)
}
