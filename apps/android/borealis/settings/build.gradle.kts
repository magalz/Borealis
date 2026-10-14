plugins { id("borealis.android.library") }

android {
    namespace = "io.github.magalz.borealis.settings"
    // Robolectric runs the preference screen tests on the JVM.
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation(project(":borealis:diagnostics"))
    implementation(project(":borealis:logging"))
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
