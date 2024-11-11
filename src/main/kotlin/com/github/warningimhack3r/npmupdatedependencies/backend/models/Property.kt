package com.github.warningimhack3r.npmupdatedependencies.backend.models

import com.intellij.json.psi.JsonProperty

data class Property(
    val jsonProperty: JsonProperty,
    val name: String,
    val comparator: String?
)
