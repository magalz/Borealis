package io.github.magalz.borealis.buildlogic

import java.io.File
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

internal const val CLEAR_TEST_TEMP_DIR_BEFORE = "Empty the temporary folder of the test JVM"
internal const val DELETE_TEST_TEMP_DIR_AFTER = "Delete the temporary folder of the test JVM"

/** The folder at the root of the checkout's drive that holds the test temp folders on Windows. */
private const val WINDOWS_TEST_TEMP_ROOT = ".bt"

private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")

/**
 * Gives each unit-test task a temporary folder of its own as the test JVM's `java.io.tmpdir`.
 * Robolectric makes a `robolectric-<test>` folder there for every test and deletes it when the JVM
 * exits; on Windows a loaded native library keeps it locked, and a killed JVM never deletes it, so
 * the folders piled up in `%TEMP%` (Sprint 13). The task empties its folder before a run, which
 * also removes what a killed run left, and deletes it after the run, when no test JVM holds it any
 * more. A failed run keeps its folder until the next run.
 */
internal fun Project.configureTestTempDir(windows: Boolean = isWindows()) {
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

/**
 * The temporary folder of the test task [taskName]: `build/tmp/<task>/jvm-tmp`, or on Windows a
 * short `<drive>\.bt\<8 hex>`, unique per checkout, module and task. Robolectric's SQLite paths
 * under a deep build folder pass the 260 characters that native code accepts on Windows (owner
 * decision, 2026-10-25).
 */
internal fun Project.testTempDir(taskName: String, windows: Boolean): File =
    if (windows) {
        val key = "${rootDir.absolutePath}|$path|$taskName"
        rootDir
            .toPath()
            .root
            .toFile()
            .resolve(WINDOWS_TEST_TEMP_ROOT)
            .resolve("%08x".format(key.hashCode()))
    } else {
        layout.buildDirectory.get().asFile.resolve("tmp/$taskName/jvm-tmp")
    }
