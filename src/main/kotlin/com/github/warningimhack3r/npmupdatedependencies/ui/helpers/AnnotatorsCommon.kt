package com.github.warningimhack3r.npmupdatedependencies.ui.helpers

import com.github.warningimhack3r.npmupdatedependencies.backend.engine.NUDState
import com.github.warningimhack3r.npmupdatedependencies.backend.engine.RegistriesScanner
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.parallelMap
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay

object AnnotatorsCommon {
    private val log = logger<AnnotatorsCommon>()

    /**
     * Performs common setup before annotating, including scanning registries if necessary.
     *
     * @param project The current project context.
     */
    fun beforeAnnotate(project: Project) {
        val state = NUDState.getInstance(project)
        val registriesScanner = RegistriesScanner.getInstance(project)
        if (!registriesScanner.scanned && !state.isScanningForRegistries) {
            log.debug("Registries not scanned yet, scanning now")
            state.isScanningForRegistries = true
            registriesScanner.scan()
            state.isScanningForRegistries = false
            log.debug("Registries scanned")
        }
    }

    /**
     * Runs a set of tasks in parallel for the given collection.
     *
     * @param collection The collection of items to process.
     * @param batchSize The maximum number of concurrent tasks. If null, no batching is applied and all the tasks are executed at once.
     * @param incrementer A function to be called after each task completion.
     * @param action The action to perform for each item.
     * @return A list of results from the action.
     */
    fun <T, R> runParallelTasks(
        collection: Iterable<T>,
        batchSize: Int? = null,
        incrementer: () -> Unit = {},
        action: (T) -> R
    ): List<R> {
        var activeTasks = 0
        return collection.parallelMap { item ->
            if (batchSize != null) {
                while (activeTasks >= batchSize) {
                    // Wait for the active tasks count to decrease
                    delay(50)
                }
                activeTasks++
                log.debug("Task $activeTasks/$batchSize started: $item")
            }
            val ret = action(item)

            log.debug("Task finished for $item")
            incrementer()
            activeTasks--

            ret
        }
    }
}
