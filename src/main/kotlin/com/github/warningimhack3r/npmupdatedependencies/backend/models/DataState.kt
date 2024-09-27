package com.github.warningimhack3r.npmupdatedependencies.backend.models

import kotlinx.datetime.Instant

data class DataState<T>(
    override val data: T?,
    override val addedAt: Instant,
    val comparator: String
) : AbstractCacheEntry<T?>
