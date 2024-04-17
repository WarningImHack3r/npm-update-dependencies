package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.intellij.openapi.diagnostic.logger

object ShellRunner {
    private val log = logger<ShellRunner>()
    private val failedCommands = mutableSetOf<String>()

    private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")

    fun execute(command: Array<String>): String? {
        val commandName = command.firstOrNull() ?: return null
        if (isWindows() && failedCommands.contains(commandName)) {
            command[0] = "$commandName.cmd"
            log.warn("Retrying command with .cmd extension: \"${command.joinToString(" ")}\"")
        }
        return try {
            log.debug("Executing \"${command.joinToString(" ")}\"")
            val process = ProcessBuilder(*command)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            process.waitFor()
            process.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            if (isWindows() && !commandName.endsWith(".cmd")) {
                failedCommands.add(commandName)
                return execute(arrayOf("$commandName.cmd") + command.drop(1).toTypedArray())
            }
            log.warn("Error while executing \"${command.joinToString(" ")}\"", e)
            null
        }
    }
}
