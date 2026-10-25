package io.github.magalz.borealis.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Convention for Borealis Kotlin library modules (`:borealis:*`). */
class BorealisAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.android.library")
            with(BorealisCoverage) { configureCoverage() }
            with(BorealisLicenses) { configureLicensee() }
            with(BorealisDetekt) { configureDetekt() }
            configureFixtures()
            configureTestTempDir()

            extensions.configure<LibraryExtension> {
                compileSdk = BorealisBuild.COMPILE_SDK
                ndkVersion = BorealisBuild.NDK_VERSION
                defaultConfig {
                    minSdk = BorealisBuild.MIN_SDK
                    testInstrumentationRunner = BorealisBuild.INSTRUMENTATION_RUNNER
                    testInstrumentationRunnerArguments[CORE_VERSION_ARGUMENT] = coreVersion()
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                lint {
                    warningsAsErrors = true
                    abortOnError = true
                    // These checks compare the versions in use with what upstream has released, so
                    // the result changes with the date and a build is not reproducible: the same
                    // commit passes until a release appears. Dependabot reports new versions
                    // instead.
                    disable +=
                        setOf(
                            "NewerVersionAvailable",
                            "GradleDependency",
                            "AndroidGradlePluginVersion",
                            "OutdatedLibrary",
                        )
                }
            }

            dependencies.add("testImplementation", libs.findLibrary("junit").get())
            dependencies.add("testImplementation", libs.findLibrary("truth").get())
            addInstrumentedTestDependencies()
        }
}
