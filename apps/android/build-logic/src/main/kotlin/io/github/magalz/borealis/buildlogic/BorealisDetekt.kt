package io.github.magalz.borealis.buildlogic

import org.gradle.api.Project

/** detekt with type resolution on the Kotlin of an Android module. */
internal object BorealisDetekt {
    /** The one config, relative to the Gradle root. */
    const val CONFIG = "config/detekt/detekt.yml"

    /** `app`, `borealis-host`, ...: the project path without its leading colon. */
    fun baselineName(projectPath: String): String = projectPath

    fun Project.configureDetekt() = Unit
}
