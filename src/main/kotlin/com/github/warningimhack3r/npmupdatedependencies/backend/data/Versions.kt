package com.github.warningimhack3r.npmupdatedependencies.backend.data

data class Versions(
    val latest: String,
    val satisfies: String?
) {
    enum class Kind {
        LATEST,
        SATISFIES;

        override fun toString(): String {
            return when (this) {
                LATEST -> "Latest"
                SATISFIES -> "Satisfying"
            }
        }
    }

    fun from(kind: Kind): String? = when (kind) {
        Kind.LATEST -> latest
        Kind.SATISFIES -> satisfies
    }

    fun orderedAvailableKinds(placeFirst: Kind? = null): List<Kind> = listOfNotNull(
        Kind.SATISFIES.takeIf { satisfies != null },
        Kind.LATEST
    ).let {
        if (placeFirst == null) it
        else listOf(placeFirst) + it.filter { kind -> kind != placeFirst }
    }

    fun isEqualToAny(other: String): Boolean = latest == other || satisfies == other
}
