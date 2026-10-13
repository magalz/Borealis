package io.github.magalz.borealis.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** Build constants shared by the Borealis convention plugins. */
internal object BorealisBuild {
    const val APPLICATION_ID = "io.github.magalz.borealis"
    const val COMPILE_SDK = 37
    const val MIN_SDK = 28
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
