package com.github.warningimhack3r.npmupdatedependencies.backend

data class Deprecation(
    val reason: String,
    val replacement: Replacement?
) {
    data class Replacement(
        val name: String,
        val version: String
    )
}
