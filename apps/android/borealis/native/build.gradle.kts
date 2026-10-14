plugins { id("borealis.android.library") }

android {
    namespace = "io.github.magalz.borealis.nativecore"

    buildFeatures { prefabPublishing = true }

    // The instrumented test replays the golden vectors that core/ is tested with.
    sourceSets {
        getByName("androidTest") {
            assets.directories.add("../../../../core/tests/fixtures/vectors")
        }
    }

    // borealis-core is the CMake project at <repo>/core; src/main/cpp/CMakeLists.txt adds it as a
    // subdirectory and builds the JNI library next to it.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.25.0+"
        }
    }
    defaultConfig {
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_shared") } }
    }

    // Published to consumers (the ndk-build bridge in :app) as the Prefab package "native".
    prefab {
        create("borealis_core") {
            headers = "../../../../core/include"
            libraryName = "libborealis_core"
        }
    }
}

dependencies { implementation(project(":borealis:logging")) }
