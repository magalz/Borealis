plugins {
    id("borealis.android.library")
    id("borealis.android.compose")
    id("borealis.design.tokens")
}

android {
    namespace = "io.github.magalz.borealis.designsystem"

    // Test-only helpers shared by the screenshot tests of designsystem, library and settings. A
    // test fixtures artifact is never packaged into an APK, unlike a main source set.
    testFixtures { enable = true }

    sourceSets {
        // IBM Plex lives with its licence under design/ at the repository root, next to
        // design/tokens.json: four levels above this module, two above the Gradle root.
        getByName("main") { res.directories.add("../../../../design/fonts/ibm-plex/res") }
    }
}

dependencies {
    // The shared window-settling helper extends the Compose test rule and drives Robolectric.
    testFixturesApi(platform(libs.androidx.compose.bom))
    testFixturesApi(libs.androidx.compose.ui.test.junit4)
    testFixturesImplementation(libs.robolectric)
}
