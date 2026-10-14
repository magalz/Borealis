package io.github.magalz.borealis.buildlogic

import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component
import org.gradle.api.Project
import org.gradle.api.file.RegularFile

/**
 * CycloneDX SBOM of the Gradle dependencies the release APK ships (ADR 0042). Native components
 * that Gradle cannot see are listed by hand in `apps/android/sbom/native-components.cdx.json`.
 */
internal object BorealisSbom {
    const val PLUGIN_ID = "org.cyclonedx.bom"

    /** The variant that is released; test and debug classpaths are not part of the SBOM. */
    private const val RELEASE_CLASSPATH = "nonRootReleaseRuntimeClasspath"

    /** Written by `./gradlew :app:cyclonedxDirectBom`, relative to the `:app` build directory. */
    const val OUTPUT = "reports/sbom/borealis-android.cdx.json"

    fun Project.configureSbom(version: BorealisVersion) {
        pluginManager.apply(PLUGIN_ID)
        tasks.named("cyclonedxDirectBom", CyclonedxDirectTask::class.java).configure {
            projectType.set(Component.Type.APPLICATION)
            componentGroup.set(BorealisBuild.APPLICATION_ID)
            componentName.set("borealis-android")
            componentVersion.set(version.name)
            includeConfigs.set(listOf(RELEASE_CLASSPATH))
            jsonOutput.set(layout.buildDirectory.file(OUTPUT))
            // JSON is the published format.
            xmlOutput.convention(null as RegularFile?)
        }
    }
}
