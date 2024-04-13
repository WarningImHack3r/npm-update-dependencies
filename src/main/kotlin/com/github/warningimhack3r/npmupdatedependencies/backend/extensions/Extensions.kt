package com.github.warningimhack3r.npmupdatedependencies.backend.extensions

import com.intellij.json.psi.JsonValue
import kotlinx.coroutines.*

fun JsonValue.stringValue(): String = text.replace("\"", "")

// Credit: https://jivimberg.io/blog/2018/05/04/parallel-map-in-kotlin/
fun <T, R> Iterable<T>.parallelMap(mapper: suspend (T) -> R) = runBlocking(SupervisorJob() + Dispatchers.Default) {
    coroutineScope { map { async { mapper(it) } }.awaitAll() }
}
