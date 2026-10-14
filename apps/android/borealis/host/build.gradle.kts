plugins { id("borealis.android.library") }

android { namespace = "io.github.magalz.borealis.host" }

dependencies {
    implementation(project(":borealis:logging"))
    implementation(project(":borealis:native"))
}
