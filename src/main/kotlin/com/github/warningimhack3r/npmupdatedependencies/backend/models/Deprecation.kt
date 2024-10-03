package com.github.warningimhack3r.npmupdatedependencies.backend.models

data class Deprecation(
    val kind: Kind,
    val reason: String,
    val replacement: Replacement?
) {
    enum class Kind {
        DEPRECATED,
        UNMAINTAINED;

        override fun toString(): String {
            return when (this) {
                DEPRECATED -> "Deprecated"
                UNMAINTAINED -> "Unmaintained"
            }
        }
    }

    data class Replacement(
        val name: String,
        val version: String
    )

    enum class Action {
        REPLACE,
        REMOVE,
        IGNORE;

        override fun toString(): String {
            return when (this) {
                REPLACE -> "Replace"
                REMOVE -> "Remove"
                IGNORE -> "Ignore"
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
