package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.jetbrains.rd.util.printlnError

object ShellRunner {
    fun execute(command: Array<String>): String? {
        return try {
            val process = ProcessBuilder(*command)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            process.waitFor()
            process.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            printlnError("Error while executing \"$command\": ${e.message}")
            null
        }
    }
}
