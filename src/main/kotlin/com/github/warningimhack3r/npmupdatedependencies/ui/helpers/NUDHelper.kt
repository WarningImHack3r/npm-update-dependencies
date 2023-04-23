package com.github.warningimhack3r.npmupdatedependencies.ui.helpers

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory

object NUDHelper {
    object Regex {
        val semverPrefix = Regex("^[\"']?\\D+") // Matches everything before the first digit
    }

    fun safeFileWrite(file: PsiFile, description: String, async: Boolean = true, action: () -> Unit) {
        val writeAction = {
            WriteCommandAction.runWriteCommandAction(
                file.project, description,
                "com.github.warningimhack3r.npmupdatedependencies",
                action, file)
        }
        if (async) {
            ApplicationManager.getApplication().invokeLater(writeAction)
        } else {
            ApplicationManager.getApplication().invokeAndWait(writeAction)
        }
    }

    fun createElement(project: Project, content: String, language: String): PsiElement {
        return PsiFileFactory.getInstance(project)
            .createFileFromText("temp.file", Language.findLanguageByID(language)!!, content)
            .firstChild
    }

    fun getClosestElementMatching(match: (PsiElement) -> Boolean, element: PsiElement, cls: Class<out PsiElement> = PsiElement::class.java): PsiElement? {
        var sibling = element.nextSibling
        while (sibling != null) {
            if (sibling.javaClass == cls && match(sibling)) {
                return sibling
            }
            sibling = sibling.nextSibling
        }
        sibling = element.prevSibling
        while (sibling != null) {
            if (sibling.javaClass == cls && match(sibling)) {
                return sibling
            }
            sibling = sibling.prevSibling
        }
        return null
    }
}
