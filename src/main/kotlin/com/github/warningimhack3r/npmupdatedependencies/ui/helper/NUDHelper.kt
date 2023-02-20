package com.github.warningimhack3r.npmupdatedependencies.ui.helper

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory

object NUDHelper {
    object Regex {
        val semverPrefix = Regex("^[\"']?\\D+") // Matches everything before the first digit
    }

    fun asyncWrite(project: Project, action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                action()
            }
        }
    }

    fun createElement(project: Project, content: String, language: String): PsiElement {
        return PsiFileFactory.getInstance(project)
            .createFileFromText("temp.file", Language.findLanguageByID(language)!!, content)
            .firstChild
    }
}
