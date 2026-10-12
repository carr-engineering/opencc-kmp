package io.github.opencckmp.cli

import kotlin.system.exitProcess

object DictionaryCommand {
    @JvmStatic fun main(args: Array<String>) { exitProcess(runCli(CliTool.Dictionary, args.toList(), platformHost())) }
}
object PhraseCommand {
    @JvmStatic fun main(args: Array<String>) { exitProcess(runCli(CliTool.Phrase, args.toList(), platformHost())) }
}
