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
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.hilt.android.testing)
    add("kspTest", libs.hilt.compiler)
}
