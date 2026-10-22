plugins {
    id("borealis.android.library")
    id("borealis.android.compose")
    id("borealis.android.hilt")
    id("borealis.kotlin.serialization")
}

// Settings, Privacy and diagnostics, the audit trail viewer and Send to phone, in Compose (Phase
// 2). The Library hosts their navigation graph; the app provides the device facts through Hilt.
android { namespace = "io.github.magalz.borealis.settings" }

dependencies {
    implementation(project(":borealis:designsystem"))
    implementation(project(":borealis:diagnostics"))
    implementation(project(":borealis:logging"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    // The repository contract exposes Flow and StateFlow.
    api(libs.kotlinx.coroutines.android)
    testImplementation(testFixtures(project(":borealis:designsystem")))
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    // The tests of the About links decode the QR code that the dialog draws.
    testImplementation(libs.zxing.core)
    add("kspTest", libs.hilt.compiler)
}
