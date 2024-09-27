package com.github.warningimhack3r.npmupdatedependencies.backend.models

import kotlinx.datetime.Instant

interface AbstractCacheEntry<T> {
    val data: T
    val addedAt: Instant
}

data class CacheEntry<T>(
    override val data: T,
    override val addedAt: Instant
) : AbstractCacheEntry<T>
