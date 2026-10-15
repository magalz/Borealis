plugins {
    id("borealis.android.library")
    id("borealis.android.compose")
    id("borealis.android.hilt")
    id("borealis.kotlin.serialization")
}

android { namespace = "io.github.magalz.borealis.library" }

dependencies {
    implementation(project(":borealis:designsystem"))
    implementation(project(":borealis:logging"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.hilt.android.testing)
    add("kspTest", libs.hilt.compiler)
}
