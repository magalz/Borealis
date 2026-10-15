plugins { `kotlin-dsl` }

group = "io.github.magalz.borealis.buildlogic"

dependencies {
    // AGP must live in the plugin classloader of the Borealis convention plugins.
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.compose.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.serialization.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
    implementation(libs.spotless.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
    implementation(libs.licensee.gradlePlugin)
    implementation(libs.roborazzi.gradlePlugin)
    implementation(libs.cyclonedx.gradlePlugin)
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
        register("borealisAndroidCompose") {
            id = "borealis.android.compose"
            implementationClass = "io.github.magalz.borealis.buildlogic.BorealisComposePlugin"
        }
        register("borealisAndroidHilt") {
            id = "borealis.android.hilt"
            implementationClass = "io.github.magalz.borealis.buildlogic.BorealisHiltPlugin"
        }
        register("borealisAndroidRoom") {
            id = "borealis.android.room"
            implementationClass = "io.github.magalz.borealis.buildlogic.BorealisRoomPlugin"
        }
        register("borealisDesignTokens") {
            id = "borealis.design.tokens"
            implementationClass =
                "io.github.magalz.borealis.buildlogic.tokens.BorealisDesignTokensPlugin"
        }
        register("borealisKotlinSerialization") {
            id = "borealis.kotlin.serialization"
            implementationClass = "io.github.magalz.borealis.buildlogic.BorealisSerializationPlugin"
        }
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

tasks.test { useJUnit() }
