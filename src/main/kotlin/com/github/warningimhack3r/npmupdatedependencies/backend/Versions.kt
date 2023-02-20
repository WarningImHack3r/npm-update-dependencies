package com.github.warningimhack3r.npmupdatedependencies.backend

data class Versions(
    val latest: String,
    val satisfies: String?
) {
    enum class Kind(val text: String) {
        LATEST("latest"),
        SATISFIES("satisfying")
    }

    fun from(kind: Kind): String? = when (kind) {
        Kind.LATEST -> latest
        Kind.SATISFIES -> satisfies
    }

    fun orderedAvailableKinds(): List<Kind> = listOfNotNull(
        Kind.SATISFIES.takeIf { satisfies != null },
        Kind.LATEST
    )

    fun isEqualToAny(other: String): Boolean = latest == other || satisfies == other
}
