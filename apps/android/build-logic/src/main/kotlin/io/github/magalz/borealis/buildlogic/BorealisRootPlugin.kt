package io.github.magalz.borealis.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.plugin.DetektPlugin
import org.gradle.api.GradleException
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
