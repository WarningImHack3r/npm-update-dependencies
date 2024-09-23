package com.github.warningimhack3r.npmupdatedependencies.backend.data

import java.util.Date

data class DataState<T>(
    val data: T?,
    val scannedAt: Date,
    val comparator: String
)
