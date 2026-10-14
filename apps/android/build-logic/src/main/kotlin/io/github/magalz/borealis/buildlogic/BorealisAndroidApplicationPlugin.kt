package io.github.magalz.borealis.buildlogic

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ResValue
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/**
 * Applies the Borealis identity and build settings to the upstream Moonlight `:app` module through
 * the AGP variant API, so `app/build.gradle` only needs one hook line.
 */
class BorealisAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            val components = extensions.getByType<ApplicationAndroidComponentsExtension>()
            val version = BorealisVersion.fromGitDescribe(gitDescribe())
            requireTagForReleases(version)
            configureDsl(components)
            configureVariants(components, version)
            buildNativeCoreBeforeNdkBuild()

            dependencies.add("implementation", project(":borealis:logging"))
            dependencies.add("implementation", project(":borealis:native"))
            dependencies.add("implementation", libs.findLibrary("hilt-android").get())
            dependencies.add("ksp", libs.findLibrary("hilt-compiler").get())
            addInstrumentedTestDependencies()
        }

    /** Runs after app/build.gradle has been evaluated, so these values win over upstream's. */
    private fun Project.configureDsl(components: ApplicationAndroidComponentsExtension) {
        val signing =
            BorealisSigning.load(rootDir) { missing ->
                logger.warn(
                    "Borealis release signing is partially configured (missing: " +
                        "${missing.joinToString()}); the release build will be unsigned."
                )
            }
        components.finalizeDsl { dsl ->
            dsl.defaultConfig.minSdk = BorealisBuild.MIN_SDK
            // Imports the Prefab package published by :borealis:native into the ndk-build.
            dsl.buildFeatures.prefab = true
            dsl.defaultConfig.testInstrumentationRunner = BorealisBuild.INSTRUMENTATION_RUNNER
            // Prefab rejects a shared library that bundles its own libc++, so the app uses the
            // shared one. Upstream native code is plain C: only Borealis modules are affected.
            dsl.defaultConfig.externalNativeBuild.ndkBuild.arguments += "APP_STL=c++_shared"
            dsl.lint {
                baseline = file("lint-baseline.xml")
                warningsAsErrors = true
                abortOnError = true
            }
            if (signing != null) {
                val release =
                    dsl.signingConfigs.create("borealisRelease") {
                        storeFile = signing.storeFile
                        storePassword = signing.storePassword
                        keyAlias = signing.keyAlias
                        keyPassword = signing.keyPassword
                    }
                dsl.buildTypes.getByName("release").signingConfig = release
            }
        }
    }

    private fun configureVariants(
        components: ApplicationAndroidComponentsExtension,
        version: BorealisVersion,
    ) {
        // minSdk 28 makes upstream's `root` flavor (maxSdk 25) empty: disable, do not delete.
        components.beforeVariants(components.selector().withFlavor("root" to "root")) {
            it.enable = false
        }

        components.onVariants { variant ->
            val debug = variant.buildType == "debug"
            variant.applicationId.set(
                if (debug) "${BorealisBuild.APPLICATION_ID}.debug" else BorealisBuild.APPLICATION_ID
            )
            val label = if (debug) "Borealis Debug" else "Borealis"
            variant.resValues.put(variant.makeResValueKey("string", "app_label"), ResValue(label))
            variant.resValues.put(
                variant.makeResValueKey("string", "app_label_root"),
                ResValue(label),
            )
            variant.outputs.forEach { output ->
                output.versionName.set(version.name)
                output.versionCode.set(version.code)
            }
        }
    }

    /**
     * The Prefab package points ndk-build at the core library inside `:borealis:native`'s CMake
     * output, and ndk-build checks that the file exists while configuring. AGP does not order the
     * two, so build the core first.
     */
    private fun Project.buildNativeCoreBeforeNdkBuild() {
        tasks
            .matching { it.name.startsWith("configureNdkBuild") }
            .configureEach {
                val buildType =
                    if (name.startsWith("configureNdkBuildDebug")) "Debug" else "Release"
                dependsOn(":borealis:native:externalNativeBuild$buildType")
            }
    }

    /** Debug builds keep the 0.0.0-dev fallback; release builds must come from a tag. */
    private fun Project.requireTagForReleases(version: BorealisVersion) {
        if (version != BorealisVersion.DEV) return
        val guard =
            tasks.register("verifyBorealisReleaseVersion") {
                doLast { throw GradleException(UNTAGGED_RELEASE_MESSAGE) }
            }
        tasks
            .matching { it.name.matches(Regex("""pre\w*ReleaseBuild""")) }
            .configureEach {
                dependsOn(guard)
            }
    }

    private fun Project.gitDescribe(): String? = runCatching {
        providers
            .exec {
                workingDir = rootDir
                commandLine(listOf("git") + BorealisVersion.GIT_DESCRIBE_ARGS)
                isIgnoreExitValue = true
            }
            .standardOutput
            .asText
            .get()
            .trim()
    }
        .getOrNull()
        ?.ifEmpty { null }

    private companion object {
        const val UNTAGGED_RELEASE_MESSAGE =
            "Release builds need an android-vMAJOR.MINOR.PATCH git tag in the history " +
                "(git describe found none, so the version would be 0.0.0-dev). " +
                "Fetch tags (CI: fetch-depth: 0) or tag the release, e.g. 'git tag android-v1.0.0'."
    }
}
