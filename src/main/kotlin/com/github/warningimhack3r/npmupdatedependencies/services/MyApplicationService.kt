package com.github.warningimhack3r.npmupdatedependencies.services

import com.github.warningimhack3r.npmupdatedependencies.MyBundle

class MyApplicationService {

    init {
        println(MyBundle.message("applicationService"))

        System.getenv("CI")
            ?: TODO("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }
}
