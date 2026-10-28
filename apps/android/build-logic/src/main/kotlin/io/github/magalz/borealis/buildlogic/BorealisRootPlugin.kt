package io.github.magalz.borealis.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.plugin.DetektPlugin
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Formatting for Borealis-owned paths only (upstream files are never touched), detekt for the build
 * logic, and the merged coverage report.
 */
internal class BorealisRootPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply(SpotlessPlugin::class.java)
            pluginManager.apply(DetektPlugin::class.java)
            configureCoverage()

            val kotlinTargets =
                listOf(
                    "borealis/**/*.kt",
                    "build-logic/**/*.kt",
                    "app/src/main/kotlin/**/*.kt",
                    "app/src/test/**/*.kt",
                    "app/src/androidTest/**/*.kt",
                )
            val nativeTargets =
                listOf("borealis/native/src/main/cpp", "app/src/main/jni/borealis").flatMap { dir ->
                    listOf("c", "cc", "cpp", "h", "hpp").map { "$dir/**/*.$it" }
                }
            val coreClangFormat = rootDir.resolve("../../core/.clang-format").canonicalPath
            val excluded = listOf("**/build/**", "**/.gradle/**", "**/.cxx/**")

            // The version catalog accessor is only registered once the build script is evaluated.
            afterEvaluate {
                val ktfmtVersion = libs.findVersion("ktfmt").get().requiredVersion
                val clangFormatVersion = installedClangFormatVersion()
                extensions.configure<SpotlessExtension> {
                    kotlin {
                        target(kotlinTargets)
                        targetExclude(excluded)
                        ktfmt(ktfmtVersion).kotlinlangStyle()
                    }
                    // clang-format must be on PATH (or set spotless's `pathToExe`); the style is
                    // core's, so the native code in this tree follows the same rules.
                    cpp {
                        target(nativeTargets)
                        targetExclude(excluded)
                        clangFormat(clangFormatVersion).style("file:${coreClangFormat}")
                    }
                    kotlinGradle {
                        target("**/*.gradle.kts")
                        targetExclude(excluded)
                        ktfmt(ktfmtVersion).kotlinlangStyle()
                    }
                }
            }

            // The Android modules run detekt with type resolution themselves (BorealisDetekt). The
            // plain task keeps the Gradle code of build-logic: the analysis cannot read it, because
            // it does not apply the compiler plugins of the kotlin-dsl (sam-with-receiver).
            extensions.configure<DetektExtension> {
                source.setFrom(
                    fileTree(layout.projectDirectory) {
                        include("build-logic/**/*.kt")
                        exclude(excluded)
                    }
                )
                config.setFrom(layout.projectDirectory.file(BorealisDetekt.CONFIG))
                buildUponDefaultConfig.set(true)
            }
        }

    /**
     * One merged Kover report for every module that applies the Borealis coverage convention. The
     * XML is in JaCoCo format (Codecov reads it); the modules are listed here instead of in each
     * build file so that a new `:borealis:*` module needs no root edit.
     */
    private fun Project.configureCoverage() {
        pluginManager.apply(BorealisCoverage.PLUGIN_ID)
        subprojects.forEach { module ->
            module.pluginManager.withPlugin(BorealisCoverage.PLUGIN_ID) {
                dependencies.add("kover", module)
            }
        }
        extensions.configure<KoverProjectExtension> {
            currentProject { createVariant(BorealisCoverage.VARIANT) {} }
            reports {
                variant(BorealisCoverage.VARIANT) {
                    filters { with(BorealisCoverage) { borealisFilters() } }
                    xml { xmlFile.set(layout.buildDirectory.file(BorealisCoverage.XML_REPORT)) }
                }
            }
        }
    }

    /**
     * Spotless insists on the exact `clang-format` version it is told, so use the one on PATH
     * instead of pinning a version that CI images and developer machines will not share. Without a
     * binary the C/C++ Spotless tasks fail with [ClangFormat.MISSING_MESSAGE], while every other
     * build keeps working.
     */
    private fun Project.installedClangFormatVersion(): String {
        val output = runCatching {
            providers
                .exec {
                    commandLine("clang-format", "--version")
                    isIgnoreExitValue = true
                }
                .standardOutput
                .asText
                .get()
        }
            .getOrNull()
        val version = ClangFormat.parseVersion(output)
        if (version == null) {
            val missing =
                tasks.register("verifyClangFormat") {
                    doLast { throw GradleException(ClangFormat.MISSING_MESSAGE) }
                }
            tasks
                .matching { it.name.startsWith("spotlessCpp") }
                .configureEach { dependsOn(missing) }
        }
        return version ?: CLANG_FORMAT_FALLBACK_VERSION
    }

    private companion object {
        const val CLANG_FORMAT_FALLBACK_VERSION = "22"
    }
}
