plugins { `kotlin-dsl` }

group = "io.github.magalz.borealis.buildlogic"

dependencies {
    // AGP must live in the plugin classloader of the Borealis convention plugins.
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
    implementation(libs.spotless.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("borealisSettings") {
            id = "borealis.settings"
            implementationClass = "io.github.magalz.borealis.buildlogic.BorealisSettingsPlugin"
        }
        register("borealisAndroidApplication") {
            id = "borealis.android.application"
            implementationClass =
                "io.github.magalz.borealis.buildlogic.BorealisAndroidApplicationPlugin"
        }
        register("borealisAndroidLibrary") {
            id = "borealis.android.library"
            implementationClass =
                "io.github.magalz.borealis.buildlogic.BorealisAndroidLibraryPlugin"
        }
    }
}
