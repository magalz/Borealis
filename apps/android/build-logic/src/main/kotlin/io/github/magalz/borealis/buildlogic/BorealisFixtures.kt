package io.github.magalz.borealis.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

/** System property that tells unit tests where `core/tests/fixtures` is. */
internal const val FIXTURES_DIR_PROPERTY = "borealis.fixtures.dir"

/**
 * Gives every unit-test task the shared host fixtures of `core/tests/fixtures` (the repository root
 * is two levels above the Gradle root) and tracks them as an input, so changing a fixture re-runs
 * the tests.
 */
internal fun Project.configureFixtures() {
    val fixtures = rootDir.resolve("../../core/tests/fixtures").canonicalFile
    tasks.withType(Test::class.java).configureEach {
        systemProperty(FIXTURES_DIR_PROPERTY, fixtures.path)
        inputs
            .dir(fixtures)
            .withPathSensitivity(PathSensitivity.RELATIVE)
            .withPropertyName("borealisFixtures")
    }
}
