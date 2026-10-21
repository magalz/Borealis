import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

plugins {
    id("borealis.android.library")
    id("borealis.android.compose")
    id("borealis.design.tokens")
}

android {
    namespace = "io.github.magalz.borealis.designsystem"

    // Test-only helpers shared by the tests of designsystem, library, settings, stream and app:
    // window settling for the screenshot tests, and fake input devices. A test fixtures artifact is
    // never packaged into an APK, unlike a main source set.
    testFixtures { enable = true }

    sourceSets {
        // IBM Plex lives with its licence under design/ at the repository root, next to
        // design/tokens.json: four levels above this module, two above the Gradle root.
        getByName("main") { res.directories.add("../../../../design/fonts/ibm-plex/res") }
    }
}

// A test reads the halo that docs/ux/accessibility.md chooses (two levels above the Gradle
// root), so the notes are an input of the unit tests: a change of the notes runs them again.
tasks.withType<Test>().configureEach {
    inputs
        .file(rootDir.resolve("../../docs/ux/accessibility.md"))
        .withPathSensitivity(PathSensitivity.NONE)
        .withPropertyName("accessibilityNotes")
}

dependencies {
    // The video styles of the background play their clip with Media3 ExoPlayer, and the player
    // follows the lifecycle of the screen it is on.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.media3.exoplayer)

    // A video background that fails says so in the log, once; the battery saver is a broadcast.
    implementation(libs.androidx.core)
    implementation(project(":borealis:logging"))

    // The shared helpers extend the Compose test rule and drive Robolectric; one draws a backdrop.
    testFixturesApi(platform(libs.androidx.compose.bom))
    testFixturesApi(libs.androidx.compose.ui.test.junit4)
    testFixturesImplementation(libs.androidx.compose.foundation)
    testFixturesImplementation(libs.androidx.compose.material3)
    testFixturesImplementation(libs.robolectric)
    testFixturesApi(libs.roborazzi.accessibility.check)
    testFixturesImplementation(libs.truth)
}
