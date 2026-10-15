package io.github.magalz.borealis.buildlogic

import com.android.build.api.dsl.LibraryExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Jetpack Compose in a Borealis library module (Phase 2): the compiler plugin, the BOM-managed UI
 * and Material 3 artifacts, and the Roborazzi screenshot tests that run on Robolectric. Apply it to
 * a module that already applies `borealis.android.library`.
 */
class BorealisComposePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("io.github.takahirom.roborazzi")

            extensions.configure<LibraryExtension> {
                buildFeatures { compose = true }
                // Robolectric needs the merged resources and manifest to start a Compose host.
                testOptions { unitTests { isIncludeAndroidResources = true } }
            }
            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
            }
            // Native graphics render real pixels, which the screenshot tests compare.
            tasks.withType<Test>().configureEach {
                systemProperty("robolectric.graphicsMode", "NATIVE")
            }

            val bom = dependencies.platform(libs.findLibrary("androidx-compose-bom").get())
            dependencies.add("implementation", bom)
            for (name in COMPOSE_IMPLEMENTATION) {
                dependencies.add("implementation", libs.findLibrary(name).get())
            }
            for (name in COMPOSE_DEBUG) {
                dependencies.add("debugImplementation", libs.findLibrary(name).get())
            }
            dependencies.add("testImplementation", bom)
            for (name in COMPOSE_TEST) {
                dependencies.add("testImplementation", libs.findLibrary(name).get())
            }
        }

    private companion object {
        val COMPOSE_IMPLEMENTATION =
            listOf(
                "androidx-compose-ui",
                "androidx-compose-ui-graphics",
                "androidx-compose-foundation",
                "androidx-compose-material3",
                "androidx-compose-ui-tooling-preview",
            )
        val COMPOSE_DEBUG =
            listOf("androidx-compose-ui-tooling", "androidx-compose-ui-test-manifest")
        val COMPOSE_TEST =
            listOf(
                "androidx-compose-ui-test-junit4",
                "robolectric",
                "roborazzi",
                "roborazzi-compose",
                "roborazzi-junit-rule",
                "androidx-test-core",
                "kotlinx-coroutines-test",
                "turbine",
            )
    }
}
