package io.github.magalz.borealis.buildlogic

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.plugin.DetektPlugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named

/**
 * detekt with type resolution on the Kotlin of an Android module. The plain `detekt` task never
 * runs the rules that need the compiler's analysis (unused private members, unsafe casts, platform
 * types and about 40 others), so every module analyses its `debug` sources, unit tests,
 * instrumented tests and test fixtures against the compile classpath. The module's `detekt` task is
 * the entry point for all of them. There is one config for every module and no baseline: a finding
 * is fixed, or suppressed with a reason (docs/development/building.md).
 */
internal object BorealisDetekt {
    /** The one config, relative to the Gradle root. */
    const val CONFIG = "config/detekt/detekt.yml"

    /** The build type that is not analysed: `debug` has the same sources. */
    private const val SKIPPED_BUILD_TYPE = "release"

    fun Project.configureDetekt() {
        pluginManager.apply(DetektPlugin::class.java)
        extensions.configure<DetektExtension> {
            config.setFrom(rootDir.resolve(CONFIG))
            buildUponDefaultConfig.set(true)
            // Analysing `release` too would compile every module a second time to run the same
            // rules on the same sources.
            ignoredBuildTypes.add(SKIPPED_BUILD_TYPE)
            // The plain `detekt` task has nothing left to do: it is the entry point below.
            source.setFrom()
        }

        // The Kotlin compilation does not see the Java that Hilt generates in the module, such as
        // `Hilt_*`, the base class of an `@AndroidEntryPoint` Kotlin class. Without it the analysis
        // reports hundreds of unresolved references and its answers are unreliable; the unit-test
        // and instrumented-test tasks already get it.
        afterEvaluate {
            val javaClasses =
                tasks.named<JavaCompile>("compileDebugJavaWithJavac").flatMap {
                    it.destinationDirectory
                }
            tasks.named<Detekt>("detektDebug") { classpath.from(javaClasses) }
        }

        tasks.named("detekt") { dependsOn("detektMain", "detektTest") }
    }
}
