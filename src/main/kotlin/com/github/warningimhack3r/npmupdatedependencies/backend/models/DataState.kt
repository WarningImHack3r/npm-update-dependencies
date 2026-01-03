package com.github.warningimhack3r.npmupdatedependencies.backend.models

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class DataState<T> @OptIn(ExperimentalTime::class) constructor(
    val data: T?,
    val addedAt: Instant,
    val comparator: String
)
