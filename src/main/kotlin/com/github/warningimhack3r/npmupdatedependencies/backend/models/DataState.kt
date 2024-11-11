package com.github.warningimhack3r.npmupdatedependencies.backend.models

import kotlinx.datetime.Instant

data class DataState<T>(
    val data: T?,
    val addedAt: Instant,
    val comparator: String
)
