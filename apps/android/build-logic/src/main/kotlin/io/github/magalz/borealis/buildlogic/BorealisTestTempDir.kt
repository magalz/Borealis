package io.github.magalz.borealis.buildlogic

import org.gradle.api.Project

internal const val CLEAR_TEST_TEMP_DIR_BEFORE = "Empty the temporary folder of the test JVM"
internal const val DELETE_TEST_TEMP_DIR_AFTER = "Delete the temporary folder of the test JVM"

/** Gives each unit-test task a temporary folder of its own. */
internal fun Project.configureTestTempDir() {
    // Not set yet.
}
