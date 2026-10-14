plugins { id("borealis.android.library") }

android { namespace = "io.github.magalz.borealis.host" }

dependencies {
    api(libs.okhttp)
    implementation(project(":borealis:logging"))
    implementation(project(":borealis:native"))
}
