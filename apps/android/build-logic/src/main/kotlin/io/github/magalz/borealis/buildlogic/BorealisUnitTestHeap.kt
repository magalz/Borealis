package io.github.magalz.borealis.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * The heap of each unit-test worker, by project path, where the default of 512 MB is too small.
 * Robolectric keeps one sandbox for each SDK level and set of shadows a test asks for, and the
 * app's tests ask for many, so the default ran out once (Sprint 13). The Compose accessibility
 * tests of `:borealis:library` and `:borealis:settings` ran out under load (Sprint 15). For those
 * two modules the value is about twice the largest heap after a collection that a full run of their
 * tests measured, rounded up to the next step; `docs/development/building.md` records the
 * measurements. A module that is not listed keeps the default.
 */
internal val UNIT_TEST_HEAPS =
    mapOf(":app" to "2g", ":borealis:library" to "1g", ":borealis:settings" to "1536m")

/** Sizes the heap of the unit-test workers of this module, when [UNIT_TEST_HEAPS] lists it. */
internal fun Project.configureUnitTestHeap() {
    val heap = UNIT_TEST_HEAPS[path] ?: return
    tasks.withType(Test::class.java).configureEach { maxHeapSize = heap }
}
