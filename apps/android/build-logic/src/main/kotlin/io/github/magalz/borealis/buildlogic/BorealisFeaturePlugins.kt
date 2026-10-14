package io.github.magalz.borealis.buildlogic

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Hilt in a Borealis library module (ADR 0012): the runtime plus its KSP compiler. */
class BorealisHiltPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            dependencies.add("implementation", libs.findLibrary("hilt-android").get())
            dependencies.add("ksp", libs.findLibrary("hilt-compiler").get())
        }
}

/**
 * Room in a Borealis library module (ADR 0011). Schemas are exported to `schemas/` so that every
 * version is committed and migrations can be tested against it.
 */
class BorealisRoomPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            extensions.configure<KspExtension> {
                arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
                arg("room.generateKotlin", "true")
            }
            dependencies.add("implementation", libs.findLibrary("androidx-room-runtime").get())
            dependencies.add("implementation", libs.findLibrary("androidx-room-ktx").get())
            dependencies.add("ksp", libs.findLibrary("androidx-room-compiler").get())
        }
}

/** kotlinx.serialization (ADR 0030): the compiler plugin and the JSON runtime. */
class BorealisSerializationPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            dependencies.add("implementation", libs.findLibrary("kotlinx-serialization-json").get())
        }
}
