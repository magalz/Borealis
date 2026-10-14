package io.github.magalz.borealis.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.plugin.DetektPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Formatting and static analysis for Borealis-owned paths only; upstream files are never touched.
 */
internal class BorealisRootPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply(SpotlessPlugin::class.java)
            pluginManager.apply(DetektPlugin::class.java)

            val kotlinTargets =
                listOf("borealis/**/*.kt", "build-logic/**/*.kt", "app/src/main/kotlin/**/*.kt")
            val excluded = listOf("**/build/**", "**/.gradle/**")

            // The version catalog accessor is only registered once the build script is evaluated.
            afterEvaluate {
                val ktfmtVersion = libs.findVersion("ktfmt").get().requiredVersion
                extensions.configure<SpotlessExtension> {
                    kotlin {
                        target(kotlinTargets)
                        targetExclude(excluded)
                        ktfmt(ktfmtVersion).kotlinlangStyle()
                    }
                    kotlinGradle {
                        target("**/*.gradle.kts")
                        targetExclude(excluded)
                        ktfmt(ktfmtVersion).kotlinlangStyle()
                    }
                }
            }

            extensions.configure<DetektExtension> {
                source.setFrom(
                    fileTree(layout.projectDirectory) {
                        include(kotlinTargets)
                        exclude(excluded)
                    }
                )
                config.setFrom(layout.projectDirectory.file("config/detekt/detekt.yml"))
                buildUponDefaultConfig.set(true)
            }
        }
}
