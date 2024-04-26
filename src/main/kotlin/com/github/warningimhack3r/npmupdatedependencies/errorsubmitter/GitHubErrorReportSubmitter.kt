package com.github.warningimhack3r.npmupdatedependencies.errorsubmitter

import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.ide.troubleshooting.CompositeGeneralTroubleInfoCollector
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.Consumer
import java.awt.Component
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


// Inspired by:
// https://github.com/ChrisCarini/loc-change-count-detector-jetbrains-plugin/blob/main/src/main/java/com/chriscarini/jetbrains/locchangecountdetector/errorhandler/GitHubErrorReportSubmitter.java
// https://github.com/SonarSource/sonarlint-intellij/blob/master/src/main/java/org/sonarlint/intellij/errorsubmitter/BlameSonarSource.java
class GitHubErrorReportSubmitter : ErrorReportSubmitter() {
    companion object {
        private const val MAX_URL_LENGTH = 2083
        private const val BUG_LOGS_KEY = "bug-logs"
        private const val TRIMMED_STACKTRACE_MARKER = "\n\n<TRIMMED STACKTRACE>"
        private const val UNICODE_WORM = "\uD83D\uDC1B"
    }

    override fun getReportActionText() = "$UNICODE_WORM Open an Issue on GitHub"

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        return try {
            // Base data
            val event = if (events.isNotEmpty()) events.first() else null

            val stackTrace = event?.throwableText ?: ""
            val simpleErrorMessage = if (event != null && !event.message.isNullOrEmpty()) {
                event.message
            } else stackTrace.substringBefore("\n")

            val project = CommonDataKeys.PROJECT.getData(
                DataManager.getInstance().getDataContext(parentComponent)
            ) ?: getLastFocusedOrOpenedProject()

            // Computed data
            var causedByLastIndex = -1
            val splitStackTrace = stackTrace.split("\n")
            splitStackTrace.reversed().forEachIndexed { index, s ->
                if (s.lowercase().startsWith("caused by")) {
                    causedByLastIndex = splitStackTrace.size - index
                    return@forEachIndexed
                }
            }

            // Build URL and content
            BrowserUtil.browse(buildAbbreviatedUrl(
                mapOf(
                    "title" to "[crash] $simpleErrorMessage",
                    "bug-explanation" to (additionalInfo ?: ""),
                    BUG_LOGS_KEY to splitStackTrace.filterIndexed { index, s ->
                        if (index == 0) return@filterIndexed true
                        val line = s.trim()
                        if (causedByLastIndex > 0 && line.startsWith("at ") && index < causedByLastIndex) {
                            return@filterIndexed false
                        }
                        !line.startsWith("at java.desktop/")
                                && !line.startsWith("at java.base/")
                                && !line.startsWith("at kotlin.")
                                && !line.startsWith("at kotlinx.")
                                && !line.startsWith("at com.intellij.")
                    }.joinToString("\n"),
                    /*"device-os" to with(System.getProperty("os.name").lowercase()) {
                        when { // Windows, macOS or Linux
                            startsWith("windows") -> "Windows"
                            startsWith("mac") -> "macOS"
                            else -> "Linux"
                        }
                    },*/ // currently cannot be set (https://github.com/orgs/community/discussions/44983)
                    "additional-device-info" to getPlatformAndPluginsInfo(project)
                ).filterValues { it.isNotEmpty() }
            ))
            consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
            true
        } catch (e: Exception) {
            consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
            false
        }
    }

    /**
     * Build the URL for the GitHub issue from the given fields, abbreviating the URL if necessary
     * to fit within the maximum URL length.
     *
     * @param fields the fields to include in the URL.
     * @return the URL for the GitHub issue.
     */
    private fun buildAbbreviatedUrl(fields: Map<String, String>): URI {
        val url = buildUrl(fields)
        return URI(
            if (url.length > MAX_URL_LENGTH) {
                val newMap = fields.toMutableMap()
                newMap[BUG_LOGS_KEY]?.let { fullLog ->
                    val logLessUrlLength = buildUrl(fields.mapValues { (key, value) ->
                        if (key == BUG_LOGS_KEY) "" else value
                    }).length
                    val encodedLogDiff = URLEncoder.encode(fullLog, StandardCharsets.UTF_8).length - fullLog.length
                    newMap[BUG_LOGS_KEY] = fullLog.take(
                        (MAX_URL_LENGTH - logLessUrlLength - encodedLogDiff).coerceAtLeast(fullLog.substringBefore("\n").length)
                    ).run {
                        if (length > fullLog.substringBefore("\n").length + TRIMMED_STACKTRACE_MARKER.length) {
                            "${take(length - TRIMMED_STACKTRACE_MARKER.length)}$TRIMMED_STACKTRACE_MARKER"
                        } else this
                    }
                }
                val shorterLogUrl = buildUrl(newMap)
                if (shorterLogUrl.length > MAX_URL_LENGTH) {
                    buildUrl(fields.filter { (key, _) ->
                        key == "title" || key == "additional-device-info"
                    })
                } else shorterLogUrl
            } else url
        )
    }

    /**
     * Build the URL for the GitHub issue from the given fields.
     *
     * @param fields the fields to include in the URL.
     * @return the URL for the GitHub issue.
     */
    private fun buildUrl(fields: Map<String, String>) = buildString {
        append("https://github.com/WarningImHack3r/npm-update-dependencies/issues/new?labels=bug&template=bug_report.yml")
        fields.forEach { (key, value) ->
            append("&$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}")
        }
    }

    /**
     * Get the platform and plugins information for the given project.
     * Used in the "Additional platform info" section of the GitHub issue.
     *
     * @param project the [Project][com.intellij.openapi.project.Project] to get the platform and plugins information from.
     * @return the platform and plugins information for the given project.
     */
    private fun getPlatformAndPluginsInfo(project: Project): String {
        return CompositeGeneralTroubleInfoCollector().collectInfo(project).run {
            val trimmedAndCleaned = split("\n".toRegex()).filter { trim().isNotEmpty() }
            // Build, JRE, JVM, OS
            buildString {
                append(
                    trimmedAndCleaned
                        .dropWhile { s -> s == "=== About ===" }
                        .takeWhile { s -> s != "=== System ===" }
                        .filter { s -> !s.startsWith("idea.") && !s.startsWith("Theme") }
                        .joinToString("\n")
                )
                append("\n")
                // Plugins
                append(
                    trimmedAndCleaned
                        .dropWhile { s -> s != "=== Plugins ===" }
                        .takeWhile { s -> s.isNotBlank() && s.isNotEmpty() }
                        .joinToString("\n")
                )
            }
        }
    }

    /**
     * Get the last focused or opened project; this is a best-effort attempt.
     * Code pulled from [org.jetbrains.ide.RestService]
     *
     * @return the [Project][com.intellij.openapi.project.Project] that was last in focus or open.
     */
    private fun getLastFocusedOrOpenedProject(): Project {
        return IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project ?: run {
            val projectManager = ProjectManager.getInstance()
            val openProjects = projectManager.openProjects
            if (openProjects.isNotEmpty()) openProjects.first() else projectManager.defaultProject
        }
    }
}
