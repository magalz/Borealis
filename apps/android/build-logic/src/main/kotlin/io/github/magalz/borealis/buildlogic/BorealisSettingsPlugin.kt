package io.github.magalz.borealis.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

/**
 * Settings plugin: applies the Borealis root-project tooling (Spotless, detekt) without editing the
 * upstream root build.gradle.
 */
class BorealisSettingsPlugin : Plugin<Settings> {
    override fun apply(target: Settings) {
        target.gradle.rootProject { pluginManager.apply(BorealisRootPlugin::class.java) }
    }
}
