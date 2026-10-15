plugins {
    id("borealis.android.library")
    id("borealis.android.compose")
    id("borealis.design.tokens")
}

android {
    namespace = "io.github.magalz.borealis.designsystem"

    sourceSets {
        // IBM Plex lives with its licence under design/ at the repository root, next to
        // design/tokens.json: four levels above this module, two above the Gradle root.
        getByName("main") { res.directories.add("../../../../design/fonts/ibm-plex/res") }
    }
}
