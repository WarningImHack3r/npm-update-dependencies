package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.jetbrains.rd.util.printlnError

object ShellRunner {
    fun execute(command: String): String? {
        return try {
            Runtime.getRuntime().exec(command).inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            printlnError("Error while executing \"$command\": ${e.message}")
            null
        }
    }
}
