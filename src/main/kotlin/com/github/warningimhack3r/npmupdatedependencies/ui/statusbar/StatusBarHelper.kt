package com.github.warningimhack3r.npmupdatedependencies.ui.statusbar

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager

object StatusBarHelper {

    fun updateWidget() {
        val projectManager = ProjectManager.getInstanceIfCreated() ?: return
        for (project in projectManager.openProjects) {
            val widgetBar = WindowManager.getInstance().getStatusBar(project).getWidget(WidgetBar.ID) as? WidgetBar ?: continue
            widgetBar.update()
        }
    }
}
