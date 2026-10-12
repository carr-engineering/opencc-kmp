package io.github.opencckmp.cli

internal expect fun exitCli(status: Int): Nothing

fun main(args: Array<String>) { exitCli(runCli(CliTool.Convert, platformArguments(args), platformHost())) }
fun dictionaryMain(args: Array<String>) { exitCli(runCli(CliTool.Dictionary, platformArguments(args), platformHost())) }
fun phraseMain(args: Array<String>) { exitCli(runCli(CliTool.Phrase, platformArguments(args), platformHost())) }
