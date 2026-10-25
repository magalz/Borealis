package io.github.magalz.borealis.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

internal const val CLEAR_TEST_TEMP_DIR_BEFORE = "Empty the temporary folder of the test JVM"
internal const val DELETE_TEST_TEMP_DIR_AFTER = "Delete the temporary folder of the test JVM"

/**
 * Gives each unit-test task a temporary folder of its own, `build/tmp/<task>/jvm-tmp`, as the test
 * JVM's `java.io.tmpdir`. Robolectric makes a `robolectric-<test>` folder there for every test and
 * deletes it when the JVM exits; on Windows a loaded native library keeps it locked, and a killed
 * JVM never deletes it, so the folders piled up in `%TEMP%` (Sprint 13). The task empties the
 * folder before a run, which also removes what a killed run left, and deletes it after the run,
 * when no test JVM holds it any more. A failed run keeps its folder until the next run.
 */
internal fun Project.configureTestTempDir(windows: Boolean = false) {
    tasks.withType(Test::class.java).configureEach {
        val dir = testTempDir(name, windows)
        systemProperty("java.io.tmpdir", dir.absolutePath)
        doFirst(CLEAR_TEST_TEMP_DIR_BEFORE) {
            dir.deleteRecursively()
            dir.mkdirs()
        }
        doLast(DELETE_TEST_TEMP_DIR_AFTER) { dir.deleteRecursively() }
    }
}

/** Stub: the folder of [taskName]. */
@Suppress("UnusedParameter") // Not used yet.
internal fun Project.testTempDir(taskName: String, windows: Boolean): java.io.File =
    layout.buildDirectory.get().asFile.resolve("tmp/$taskName/jvm-tmp")
