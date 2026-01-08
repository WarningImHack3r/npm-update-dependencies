package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File

@Service(Service.Level.PROJECT)
class ShellRunner(private val project: Project) {
    companion object {
        private val log = logger<ShellRunner>()

        @JvmStatic
        fun getInstance(project: Project): ShellRunner = project.service()
    }

    private val isWindows by lazy {
        System.getProperty("os.name").lowercase().contains("win", ignoreCase = true)
    }

    private val packageJsonDirectory by lazy {
        val fileName = "package.json"
        runReadAction {
            FilenameIndex.getVirtualFilesByName(
                fileName,
                GlobalSearchScope.projectScope(project)
            )
        }.filter { file ->
            !file.path.contains("/node_modules/")
                    && !file.path.contains("/.git/")
                    && !file.parent.name.startsWith('.')
        }.sortedBy { file ->
            fileName.toRegex().find(file.path)?.range?.first ?: Int.MAX_VALUE
        }.also {
            log.debug("Found ${it.size} file(s) named $fileName: $it")
        }.firstOrNull()?.parent?.path?.also { path ->
            log.debug("Directory for $fileName: $path")
        }
    }

    private fun getExecutionCommand(originalCommand: Array<String>, retry: Boolean = false): Array<String>? {
        val program = originalCommand.firstOrNull() ?: return null.also {
            log.warn("Empty command provided")
        }
        return when (isWindows) {
            true -> {
                if (!retry) return arrayOf("cmd", "/c", *originalCommand)
                val command = arrayOf("cmd", "/c", "$program.cmd", *originalCommand.drop(1).toTypedArray())
                log.warn("Retrying command with .cmd extension: \"${command.joinToString(" ")}\"")
                command
            }

            false -> {
                if (!retry) return originalCommand
                val stringify = originalCommand.joinToString(" ")
                val command = arrayOf(
                    "sh", "-c",
                    """
                    [ -f ~/.zshrc ] && source ~/.zshrc
                    [ -f ~/.bashrc ] && source ~/.bashrc
                    [ -f ~/.profile ] && source ~/.profile
                    $stringify
                    """.trimIndent()
                )
                log.warn("Retrying command \"$stringify\" with resolved PATH")
                command
            }
        }
    }

    fun execute(command: Array<String>, retry: Boolean = false): String? {
        val exec = getExecutionCommand(command, retry) ?: return null
        log.debug("Executing command: \"${exec.joinToString(" ")}\"")
        return try {
            val process = ProcessBuilder(*exec)
                .directory(packageJsonDirectory?.let { File(it) })
                .start()
            val output = process.inputStream?.bufferedReader()?.readText()?.also {
                if (it.isNotBlank())
                    log.debug("Stdout of command \"${exec.joinToString(" ")}\":\n${it.take(200)}")
            }
            process.errorStream?.bufferedReader()?.readText()?.also {
                if (it.isNotBlank())
                    log.warn("Stderr of command \"${exec.joinToString(" ")}\":\n${it.take(200)}")
            }
            process.waitFor()
            if (process.exitValue() != 0) throw Exception("Non-successful status code")
            output?.ifBlank { null }
        } catch (e: Exception) {
            if (!retry) {
                val res = execute(command, true)
                if (res == null) {
                    log.warn("Error while executing \"${exec.joinToString(" ")}\" on second attempt")
                }
                return res
            }
            log.warn("Error while executing \"${exec.joinToString(" ")}\"", e)
            null
        }
    }
}
