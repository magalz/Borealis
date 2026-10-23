package io.github.magalz.borealis.buildlogic

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ResValue
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * Applies the Borealis identity and build settings to the upstream Moonlight `:app` module through
 * the AGP variant API, so `app/build.gradle` only needs one hook line.
 */
class BorealisAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")
            with(BorealisCoverage) { configureCoverage() }
            with(BorealisLicenses) { configureLicensee() }
            with(BorealisDetekt) { configureDetekt() }
            configureFixtures()

            val components = extensions.getByType<ApplicationAndroidComponentsExtension>()
            val version = BorealisVersion.fromGitDescribe(gitDescribe())
            requireTagForReleases(version)
            with(BorealisSbom) { configureSbom(version) }
            configureDsl(components)
            configureVariants(components, version, gitSha())
            buildNativeCoreBeforeNdkBuild()

            dependencies.add("implementation", project(":borealis:designsystem"))
            dependencies.add("implementation", project(":borealis:diagnostics"))
            dependencies.add("implementation", project(":borealis:host"))
            dependencies.add("implementation", project(":borealis:library"))
            dependencies.add("implementation", project(":borealis:logging"))
            dependencies.add("implementation", project(":borealis:native"))
            dependencies.add("implementation", project(":borealis:settings"))
            dependencies.add("implementation", project(":borealis:stream"))
            dependencies.add("implementation", libs.findLibrary("hilt-android").get())
            dependencies.add("ksp", libs.findLibrary("hilt-compiler").get())
            for (name in
                listOf(
                    "junit",
                    "truth",
                    "robolectric",
                    "androidx-test-core",
                    "kotlinx-coroutines-test",
                )) {
                dependencies.add("testImplementation", libs.findLibrary(name).get())
            }
            // The input device helpers of the controller tests.
            dependencies.add(
                "testImplementation",
                dependencies.testFixtures(project(":borealis:designsystem")),
            )
            addInstrumentedTestDependencies()
        }

    /** Runs after app/build.gradle has been evaluated, so these values win over upstream's. */
    private fun Project.configureDsl(components: ApplicationAndroidComponentsExtension) {
        val signing =
            BorealisSigning.load(rootDir) { missing ->
                logger.warn(
                    "Borealis release signing is partially configured (missing: " +
                        "${missing.joinToString()}); the release build will be unsigned."
                )
            }
        components.finalizeDsl { dsl ->
            dsl.defaultConfig.minSdk = BorealisBuild.MIN_SDK
            // Imports the Prefab package published by :borealis:native into the ndk-build.
            dsl.buildFeatures.prefab = true
            dsl.defaultConfig.testInstrumentationRunner = BorealisBuild.INSTRUMENTATION_RUNNER
            // The merged manifest is what the launcher tests query.
            dsl.testOptions.unitTests.isIncludeAndroidResources = true
            dsl.defaultConfig.testInstrumentationRunnerArguments[CORE_VERSION_ARGUMENT] =
                coreVersion()
            // Prefab rejects a shared library that bundles its own libc++, so the app uses the
            // shared one. Upstream native code is plain C: only Borealis modules are affected.
            dsl.defaultConfig.externalNativeBuild.ndkBuild.arguments += "APP_STL=c++_shared"
            dsl.lint {
                baseline = file("lint-baseline.xml")
                warningsAsErrors = true
                abortOnError = true
                // These checks compare the versions in use with what upstream has released, so
                // the result changes with the date and a build is not reproducible: the same
                // commit passes until a release appears. Dependabot reports new versions instead.
                disable +=
                    setOf(
                        "NewerVersionAvailable",
                        "GradleDependency",
                        "AndroidGradlePluginVersion",
                        "OutdatedLibrary",
                    )
            }
            if (signing != null) {
                val release =
                    dsl.signingConfigs.create("borealisRelease") {
                        storeFile = signing.storeFile
                        storePassword = signing.storePassword
                        keyAlias = signing.keyAlias
                        keyPassword = signing.keyPassword
                    }
                dsl.buildTypes.getByName("release").signingConfig = release
            }
        }
    }

    private fun Project.configureVariants(
        components: ApplicationAndroidComponentsExtension,
        version: BorealisVersion,
        gitSha: String,
    ) {
        val legalAssets =
            tasks.register<LegalAssetsTask>("borealisLegalAssets") {
                // The repository root is two levels above the Gradle root.
                sources.from(LegalAssetsTask.FILES.map { rootDir.resolve("../../$it") })
            }
        components.onVariants { variant ->
            val debug = variant.buildType == "debug"
            variant.applicationId.set(
                if (debug) "${BorealisBuild.APPLICATION_ID}.debug" else BorealisBuild.APPLICATION_ID
            )
            val label = if (debug) "Borealis Debug" else "Borealis"
            variant.resValues.put(variant.makeResValueKey("string", "app_label"), ResValue(label))
            // Overrides the "unknown" default that Borealis library modules declare.
            variant.resValues.put(
                variant.makeResValueKey("string", "borealis_git_sha"),
                ResValue(gitSha),
            )
            variant.sources.assets?.addGeneratedSourceDirectory(
                legalAssets,
                LegalAssetsTask::outputDir,
            )
            variant.outputs.forEach { output ->
                output.versionName.set(version.name)
                output.versionCode.set(version.code)
            }
        }
    }

    /**
     * The Prefab package points ndk-build at the core library inside `:borealis:native`'s CMake
     * output, and ndk-build checks that the file exists while configuring. AGP does not order the
     * two, so build the core first.
     */
    private fun Project.buildNativeCoreBeforeNdkBuild() {
        tasks
            .matching { it.name.startsWith("configureNdkBuild") }
            .configureEach {
                dependsOn(
                    ":borealis:native:externalNativeBuild${nativeBuildTypeForConfigureTask(name)}"
                )
            }
    }

    /** Debug builds keep the 0.0.0-dev fallback; release builds must come from a tag. */
    private fun Project.requireTagForReleases(version: BorealisVersion) {
        if (version != BorealisVersion.DEV) return
        val guard =
            tasks.register("verifyBorealisReleaseVersion") {
                doLast { throw GradleException(UNTAGGED_RELEASE_MESSAGE) }
            }
        tasks
            .matching { it.name.matches(Regex("""pre\w*ReleaseBuild""")) }
            .configureEach {
                dependsOn(guard)
            }
    }

    /** Short SHA of the built commit, or "unknown" outside a git checkout. */
    private fun Project.gitSha(): String =
        runCatching {
            providers
                .exec {
                    workingDir = rootDir
                    commandLine("git", "rev-parse", "--short=12", "HEAD")
                    isIgnoreExitValue = true
                }
                .standardOutput
                .asText
                .get()
                .trim()
        }
            .getOrNull()
            ?.ifEmpty { null } ?: "unknown"

    private fun Project.gitDescribe(): String? = runCatching {
        providers
            .exec {
                workingDir = rootDir
                commandLine(listOf("git") + BorealisVersion.GIT_DESCRIBE_ARGS)
                isIgnoreExitValue = true
            }
            .standardOutput
            .asText
            .get()
            .trim()
    }
        .getOrNull()
        ?.ifEmpty { null }

    private companion object {
        const val UNTAGGED_RELEASE_MESSAGE =
            "Release builds need an android-vMAJOR.MINOR.PATCH git tag in the history " +
                "(git describe found none, so the version would be 0.0.0-dev). " +
                "Fetch tags (CI: fetch-depth: 0) or tag the release, e.g. 'git tag android-v1.0.0'."
    }
}
