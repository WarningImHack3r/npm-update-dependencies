package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class ShellRunner(private val project: Project) {
    companion object {
        private const val MAX_ATTEMPTS = 3
        private val log = logger<ShellRunner>()

        @JvmStatic
        fun getInstance(project: Project): ShellRunner = project.service()
    }

    private val failedWindowsPrograms = mutableSetOf<String>()

    private fun isWindows() = System.getProperty("os.name").contains("win", ignoreCase = true)

    fun execute(command: Array<String>): String? {
        val program = command.firstOrNull() ?: return null.also {
            log.warn("No command name provided")
        }
        val isWindows = isWindows()
        var attempts = 0

        fun runCommand(): String? {
            if (isWindows && failedWindowsPrograms.contains(program)) {
                command[0] = "$program.cmd"
                log.warn("(Re)trying command with .cmd extension: \"${command.joinToString(" ")}\"")
            }
            return try {
                val platformCommand = if (isWindows) {
                    arrayOf("cmd", "/c")
                } else {
                    emptyArray()
                } + command
                log.debug("Executing command: \"${platformCommand.joinToString(" ")}\"")
                val process = ProcessBuilder(*platformCommand)
                    .directory(project.basePath?.let { File(it) })
                    .start()
                val output = process.inputStream?.bufferedReader()?.readText()?.also {
                    log.debug("Successfully executed \"${platformCommand.joinToString(" ")}\" with output:\n$it")
                }
                val error = process.errorStream?.bufferedReader()?.readText()
                process.waitFor()
                if (output.isNullOrBlank() && !error.isNullOrBlank()) {
                    log.warn("Error while executing \"${platformCommand.joinToString(" ")}\":\n${error.take(150)}")
                }
                output
            } catch (e: Exception) {
                if (isWindows && !program.endsWith(".cmd")) {
                    log.warn(
                        "Failed to execute \"${command.joinToString(" ")}\". Trying to execute \"$program.cmd\" instead",
                        e
                    )
                    failedWindowsPrograms.add(program)
                } else log.warn("Error while executing \"${command.joinToString(" ")}\"", e)
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
