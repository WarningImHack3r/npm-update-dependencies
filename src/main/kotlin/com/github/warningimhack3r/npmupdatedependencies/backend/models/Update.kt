package com.github.warningimhack3r.npmupdatedependencies.backend.models

data class Update(
    val versions: Versions,
    val affectedByFilters: Collection<String> = emptyList()
)
