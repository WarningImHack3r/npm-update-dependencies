package com.github.warningimhack3r.npmupdatedependencies.backend.data

import kotlinx.datetime.Instant

data class DataState<T>(
    val data: T?,
    val scannedAt: Instant,
    val comparator: String
)
