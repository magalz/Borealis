plugins {
    id("borealis.android.library")
    id("borealis.android.compose")
}

android { namespace = "io.github.magalz.borealis.stream" }

dependencies {
    // The Game activity extends ComponentActivity and hosts the chrome, so callers need it.
    api(libs.androidx.activity)
    // ConflictChoice and ConflictAnswer, and the failure types, are part of the contracts.
    api(project(":borealis:host"))
    // LaunchRetry carries a LaunchCategory, which :borealis:host does not re-export.
    api(project(":borealis:native"))
    implementation(project(":borealis:designsystem"))
    implementation(project(":borealis:logging"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    // The input device helpers of the gamepad tests.
    testImplementation(testFixtures(project(":borealis:designsystem")))
}
