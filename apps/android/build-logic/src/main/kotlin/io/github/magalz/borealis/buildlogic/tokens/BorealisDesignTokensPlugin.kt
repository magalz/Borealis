package io.github.magalz.borealis.buildlogic.tokens

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * Generates the design tokens of `design/tokens.json` into every variant of an Android library
 * module that already applies `borealis.android.library`.
 */
class BorealisDesignTokensPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            val generate =
                tasks.register<BorealisTokensTask>("borealisTokens") {
                    // The repository root is two levels above the Gradle root.
                    tokens.set(rootDir.resolve("../../design/tokens.json"))
                }
            extensions.getByType<LibraryAndroidComponentsExtension>().onVariants { variant ->
                variant.sources.kotlin?.addGeneratedSourceDirectory(
                    generate,
                    BorealisTokensTask::kotlinOutputDir,
                )
                variant.sources.res?.addGeneratedSourceDirectory(
                    generate,
                    BorealisTokensTask::resOutputDir,
                )
            }
        }
}
