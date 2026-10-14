plugins { id("borealis.android.library") }

android { namespace = "io.github.magalz.borealis.host" }

dependencies {
    // Same version as :app (app/build.gradle). It stays out of the version catalog: a catalog entry
    // makes Lint's UseTomlInstead flag the upstream declaration in app/build.gradle.
    //noinspection UseTomlInstead
    api("com.squareup.okhttp3:okhttp:5.5.0")
    implementation(project(":borealis:logging"))
    implementation(project(":borealis:native"))
}
