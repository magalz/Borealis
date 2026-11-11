plugins { id("borealis.android.library") }

android {
    namespace = "io.github.magalz.borealis.logging"

    // The JUnit rules for coroutines that the tests of every module share: this is the lowest
    // module, so a module that needs them gets no Compose on its test classpath. A test fixtures
    // artifact is never packaged into an APK, unlike a main source set.
    testFixtures { enable = true }
}

dependencies {
    testFixturesApi(libs.junit)
    testFixturesApi(libs.kotlinx.coroutines.test)
}
