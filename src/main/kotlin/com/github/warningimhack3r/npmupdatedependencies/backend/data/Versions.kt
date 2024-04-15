package com.github.warningimhack3r.npmupdatedependencies.backend.data

import org.semver4j.Semver

data class Versions(
    val latest: Semver,
    val satisfies: Semver? = null
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

    fun from(kind: Kind): Semver? = when (kind) {
        Kind.LATEST -> latest
        Kind.SATISFIES -> satisfies
    }

    fun orderedAvailableKinds(placeFirst: Kind? = null): List<Kind> = listOfNotNull(
        Kind.SATISFIES.takeIf { satisfies != null },
        Kind.LATEST
    ).let {
        if (placeFirst == null || (placeFirst == Kind.SATISFIES && satisfies == null)) it
        else listOf(placeFirst) + it.filter { kind -> kind != placeFirst }
    }

    fun isEqualToAny(other: Semver): Boolean = latest == other || satisfies == other
}
