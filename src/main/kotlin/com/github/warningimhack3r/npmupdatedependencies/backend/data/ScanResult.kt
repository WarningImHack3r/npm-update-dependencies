package com.github.warningimhack3r.npmupdatedependencies.backend.data

data class ScanResult(
    val versions: Versions,
    val affectedByFilters: Collection<String> = emptyList()
)
