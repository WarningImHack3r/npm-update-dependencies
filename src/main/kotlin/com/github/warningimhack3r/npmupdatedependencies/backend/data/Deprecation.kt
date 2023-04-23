package com.github.warningimhack3r.npmupdatedependencies.backend.data

data class Deprecation(
    val reason: String,
    val replacement: Replacement?
) {
    data class Replacement(
        val name: String,
        val version: String
    )

    enum class Action(val text: String) {
        REPLACE("Replace"),
        REMOVE("Remove")
    }
}
