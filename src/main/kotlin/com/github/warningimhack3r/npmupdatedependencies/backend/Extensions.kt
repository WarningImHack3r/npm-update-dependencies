package com.github.warningimhack3r.npmupdatedependencies.backend

import com.intellij.json.psi.JsonValue
import kotlinx.coroutines.*

fun JsonValue.stringValue(): String {
    return this.text.replace("\"", "")
}

// Credit: https://jivimberg.io/blog/2018/05/04/parallel-map-in-kotlin/
fun <A, B> Iterable<A>.parallelMap(f: suspend (A) -> B) = runBlocking(Dispatchers.Default) {
    coroutineScope { map { async { f(it) } }.awaitAll() }
}
