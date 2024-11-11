package com.github.warningimhack3r.npmupdatedependencies.backend.models

data class Update(
    val versions: Versions,
    val channel: Channel = Channel.Latest(),
    val affectedByFilters: Collection<String> = emptyList()
) {
    sealed interface Channel {
        class Latest : Channel {
            companion object {
                const val LATEST = "latest"
            }

            override fun raw() = LATEST
        }

        data class Other(val name: String) : Channel {
            override fun raw() = name
        }

        fun raw(): String
    }
}
