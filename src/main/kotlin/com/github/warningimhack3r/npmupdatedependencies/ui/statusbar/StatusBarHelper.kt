package com.github.warningimhack3r.npmupdatedependencies.ui.statusbar

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager

object StatusBarHelper {
    private val log = logger<StatusBarHelper>()

    fun updateWidget() {
        log.info("Updating widget")
        val projectManager = ProjectManager.getInstanceIfCreated() ?: return
        for (project in projectManager.openProjects) {
            log.debug("Updating widget for project ${project.name}")
            val widgetBar =
                WindowManager.getInstance().getStatusBar(project).getWidget(WidgetBar.ID) as? WidgetBar ?: continue
            widgetBar.update()
            log.debug("Widget updated for project ${project.name}")
        }
    }
}
