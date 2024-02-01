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
        private const val WORM_UNICODE = "\uD83D\uDC1B"
    }

    override fun getReportActionText() = "$WORM_UNICODE Open an Issue on GitHub"

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        return try {
            val event = if (events.isNotEmpty()) events.first() else null

            val stackTrace = event?.throwableText ?: ""
            val simpleErrorMessage = if (event != null && !event.message.isNullOrEmpty()) {
                event.message
            } else stackTrace.substringBefore("\n")

            val project = CommonDataKeys.PROJECT.getData(
                DataManager.getInstance().getDataContext(parentComponent)
            ) ?: getLastFocusedOrOpenedProject()

            BrowserUtil.browse(buildAbbreviatedUrl(
                mapOf(
                    "title" to "[crash] $simpleErrorMessage",
                    "bug-explanation" to (additionalInfo ?: ""),
                    BUG_LOGS_KEY to stackTrace.split("\n").filter {
                        !it.trim().startsWith("at java.desktop/")
                                && !it.trim().startsWith("at java.base/")
                    }.joinToString("\n"),
                    /*"device-os" to with(System.getProperty("os.name").lowercase()) {
                        when { // Windows, macOS or Linux
                            startsWith("windows") -> "Windows"
                            startsWith("mac") -> "macOS"
                            else -> "Linux"
                        }
                    },*/ // currently cannot be set (https://github.com/orgs/community/discussions/44983)
                    "additional-device-info" to getDefaultHelpBlock(project)
                ).filterValues { it.isNotEmpty() }
            ))
            consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
            true
        } catch (e: Exception) {
            consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
            false
        }
    }

    private fun buildAbbreviatedUrl(fields: Map<String, String>): URI {
        val url = buildUrl(fields)
        return URI(if (url.length > MAX_URL_LENGTH) {
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
        } else url)
    }

    private fun buildUrl(fields: Map<String, String>) = buildString {
        append("https://github.com/WarningImHack3r/npm-update-dependencies/issues/new?labels=bug&template=bug_report.yml")
        fields.forEach { (key, value) ->
            append("&$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}")
        }
    }

    private fun getDefaultHelpBlock(project: Project): String {
        return CompositeGeneralTroubleInfoCollector().collectInfo(project).run {
            val trimmedAndCleaned = split("\n".toRegex()).filter { trim().isNotEmpty() }
            // Build, JRE, JVM, OS
            trimmedAndCleaned
                .dropWhile { s -> s == "=== About ==="}
                .takeWhile { s -> s != "=== System ===" }
                .filter { s -> !s.startsWith("idea.") && !s.startsWith("Theme") }
                .joinToString("\n") + "\n" +
            // Plugins
            trimmedAndCleaned
                .dropWhile { s -> s != "=== Plugins ===" }
                .takeWhile { s -> s.isNotBlank() && s.isNotEmpty() }
                .joinToString("\n")
        }
    }

    /**
     * Get the last focused or opened project; this is a best-effort attempt.
     * Code pulled from [org.jetbrains.ide.RestService]
     *
     * @return the [Project][com.intellij.openapi.project.Project] that was last in focus or open.
     */
    private fun getLastFocusedOrOpenedProject(): Project {
        val project = IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project
        if (project == null) {
            val projectManager = ProjectManager.getInstance()
            val openProjects = projectManager.openProjects
            return if (openProjects.isNotEmpty()) openProjects.first() else projectManager.defaultProject
        }
        return project
    }
}
