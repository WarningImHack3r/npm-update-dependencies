package com.github.warningimhack3r.npmupdatedependencies.backend.models

data class Deprecation(
    val reason: String,
    val replacement: Replacement?
) {
    data class Replacement(
        val name: String,
        val version: String
    )

    enum class Action {
        REPLACE,
        REMOVE;

        override fun toString(): String {
            return when (this) {
                REPLACE -> "Replace"
                REMOVE -> "Remove"
            }
        }

        companion object {
            fun orderedActions(placeFirst: Action? = null) = Action.entries.toList().let {
                if (placeFirst == null) it
                else listOf(placeFirst) + it.filter { action -> action != placeFirst }
            }
        }
    }
}
