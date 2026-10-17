plugins {
    id("borealis.android.library")
    id("borealis.android.compose")
    id("borealis.android.hilt")
    id("borealis.kotlin.serialization")
}

android {
    namespace = "io.github.magalz.borealis.library"
    lint {
        // The launcher entry is declared here, but the banner and the leanback and touchscreen
        // features are declared once by the app manifest, which a library check cannot see.
        disable += setOf("MissingTvBanner", "MissingLeanbackSupport", "ImpliedTouchscreenHardware")
    }
}

dependencies {
    implementation(project(":borealis:designsystem"))
    implementation(project(":borealis:logging"))
    // The Settings graph (Settings, Privacy and diagnostics, Audit trail) is a Library destination.
    implementation(project(":borealis:settings"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    // The Hilt test components aggregate the Settings graph's modules, which reference diagnostics
    // types (the export sections), so those types must resolve on the test classpath.
    testImplementation(project(":borealis:diagnostics"))
    testImplementation(testFixtures(project(":borealis:designsystem")))
    testImplementation(libs.hilt.android.testing)
    add("kspTest", libs.hilt.compiler)
}
