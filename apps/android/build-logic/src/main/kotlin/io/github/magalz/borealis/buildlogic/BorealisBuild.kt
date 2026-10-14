package io.github.magalz.borealis.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** Build constants shared by the Borealis convention plugins. */
internal object BorealisBuild {
    const val APPLICATION_ID = "io.github.magalz.borealis"
    const val COMPILE_SDK = 37
    const val MIN_SDK = 28

    const val INSTRUMENTATION_RUNNER = "androidx.test.runner.AndroidJUnitRunner"

    /** Must match `android.ndkVersion` in the upstream app/build.gradle. */
    const val NDK_VERSION = "29.0.14206865"
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Instrumentation argument that carries the borealis-core version the tests expect. */
internal const val CORE_VERSION_ARGUMENT = "expectedCoreVersion"

/** borealis-core's version, from `core/CMakeLists.txt` (the repository root is two levels up). */
internal fun Project.coreVersion(): String = BorealisCoreVersion.read(rootDir.resolve("../.."))

/** Instrumented (`androidTest`) tests: AndroidX runner, JUnit 4 extensions and Truth. */
internal fun Project.addInstrumentedTestDependencies() {
    for (name in listOf("androidx-test-runner", "androidx-test-ext-junit", "truth")) {
        dependencies.add("androidTestImplementation", libs.findLibrary(name).get())
    }
}

/**
 * The AGP build type ("Debug" or "Release") of an ndk-build configure task such as
 * `configureNdkBuildDebug[arm64-v8a]`. Anything else fails the build: guessing would silently order
 * the wrong native build.
 */
internal fun nativeBuildTypeForConfigureTask(taskName: String): String =
    when {
        taskName.startsWith("configureNdkBuildDebug") -> "Debug"
        taskName.startsWith("configureNdkBuildRelease") -> "Release"
        else ->
            throw GradleException(
                "Unsupported build type for task '$taskName': add it to " +
                    "nativeBuildTypeForConfigureTask in build-logic."
            )
    }
