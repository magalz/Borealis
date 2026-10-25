package io.github.magalz.borealis.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * The heap of each unit-test worker of `:app`. The default is 512 MB. Robolectric keeps one sandbox
 * for each SDK level and set of shadows a test asks for, and the app's tests ask for many, so the
 * default ran out once (Sprint 13).
 */
internal const val APP_UNIT_TEST_HEAP = "2g"

/** Sizes the heap of the unit-test workers of `:app`. */
internal fun Project.configureUnitTestHeap() {
    tasks.withType(Test::class.java).configureEach { maxHeapSize = APP_UNIT_TEST_HEAP }
}
