package com.github.warningimhack3r.npmupdatedependencies.backend.data

sealed class UpdateState {
    data object UpToDate : UpdateState()
    data class Outdated(val update: Update) : UpdateState()
}

data class Update(
    val versions: Versions,
    val affectedByFilters: Collection<String> = emptyList()
)
