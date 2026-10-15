plugins {
    id("borealis.android.library")
    id("borealis.android.hilt")
    id("borealis.android.room")
    id("borealis.kotlin.serialization")
}

android {
    namespace = "io.github.magalz.borealis.diagnostics"
    // Robolectric runs the Room and Android-framework tests on the JVM.
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation(project(":borealis:logging"))
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
