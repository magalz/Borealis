package io.github.magalz.borealis.buildlogic

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ResValue
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
            val signing = BorealisSigning.load(rootDir)

            // Runs after app/build.gradle has been evaluated, so these values win over upstream's.
            components.finalizeDsl { dsl ->
                dsl.defaultConfig.minSdk = BorealisBuild.MIN_SDK
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

            // minSdk 28 makes upstream's `root` flavor (maxSdk 25) empty: disable, do not delete.
            components.beforeVariants(components.selector().withFlavor("root" to "root")) {
                it.enable = false
            }

            components.onVariants { variant ->
                val debug = variant.buildType == "debug"
                variant.applicationId.set(
                    if (debug) "${BorealisBuild.APPLICATION_ID}.debug"
                    else BorealisBuild.APPLICATION_ID
                )
                val label = if (debug) "Borealis Debug" else "Borealis"
                variant.resValues.put(
                    variant.makeResValueKey("string", "app_label"),
                    ResValue(label),
                )
                variant.resValues.put(
                    variant.makeResValueKey("string", "app_label_root"),
                    ResValue(label),
                )
                variant.outputs.forEach { output ->
                    output.versionName.set(version.name)
                    output.versionCode.set(version.code)
                }
            }

            dependencies.add("implementation", project(":borealis:logging"))
            dependencies.add("implementation", libs.findLibrary("hilt-android").get())
            dependencies.add("ksp", libs.findLibrary("hilt-compiler").get())
        }

    private fun Project.gitDescribe(): String? = runCatching {
        providers
            .exec {
                workingDir = rootDir
                commandLine("git", "describe", "--tags", "--long", "--match", "android-v*")
                isIgnoreExitValue = true
            }
            .standardOutput
            .asText
            .get()
            .trim()
    }
        .getOrNull()
        ?.ifEmpty { null }
}
