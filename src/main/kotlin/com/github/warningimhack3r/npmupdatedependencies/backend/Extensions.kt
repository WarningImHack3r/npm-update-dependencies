package com.github.warningimhack3r.npmupdatedependencies.backend

import com.intellij.json.psi.JsonValue

fun JsonValue.stringValue(): String {
    return this.text.replace("\"", "")
}
