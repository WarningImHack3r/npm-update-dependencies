package com.github.warningimhack3r.npmupdatedependencies.backend.data

data class Update(
    val versions: Versions,
    val affectedByFilters: Collection<String> = emptyList()
)
