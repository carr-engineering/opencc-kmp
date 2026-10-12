package io.github.opencckmp.cli

import kotlin.system.exitProcess

fun main(args: Array<String>) { exitProcess(runCli(CliTool.Convert, platformArguments(args), platformHost())) }
fun dictionaryMain(args: Array<String>) { exitProcess(runCli(CliTool.Dictionary, platformArguments(args), platformHost())) }
fun phraseMain(args: Array<String>) { exitProcess(runCli(CliTool.Phrase, platformArguments(args), platformHost())) }
