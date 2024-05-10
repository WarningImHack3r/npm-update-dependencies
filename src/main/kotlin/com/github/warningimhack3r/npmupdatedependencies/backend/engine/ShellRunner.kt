package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.intellij.openapi.diagnostic.logger

object ShellRunner {
    private const val MAX_ATTEMPTS = 3
    private val log = logger<ShellRunner>()
    private val failedWindowsPrograms = mutableSetOf<String>()

    private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")

    fun execute(command: Array<String>): String? {
        val program = command.firstOrNull() ?: return null
        val isWindows = isWindows()
        var attempts = 0

        fun runCommand(): String? {
            if (isWindows && failedWindowsPrograms.contains(program)) {
                command[0] = "$program.cmd"
                log.warn("(Re)trying command with .cmd extension: \"${command.joinToString(" ")}\"")
            }
            return try {
                log.debug("Executing \"${command.joinToString(" ")}\"")
                val process = ProcessBuilder(*command)
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .start()
                process.waitFor()
                process.inputStream.bufferedReader().readText().also {
                    log.debug("Executed command \"${command.joinToString(" ")}\" with output:\n$it")
                }
            } catch (e: Exception) {
                if (isWindows && !program.endsWith(".cmd")) {
                    failedWindowsPrograms.add(program)
                    return execute(arrayOf("$program.cmd") + command.drop(1).toTypedArray())
                }
                log.warn("Error while executing \"${command.joinToString(" ")}\"", e)
                null
            }
        }

        while (attempts < MAX_ATTEMPTS) {
            if (attempts > 0) log.warn("Retrying command \"${command.joinToString(" ")}\"")
            runCommand()?.let { return it }
            attempts++
        }

        return null
    }
}
