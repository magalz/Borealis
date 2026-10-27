package io.github.magalz.borealis.stream.game

import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Robolectric setup that every test of the stream activity shares: one SDK level (the one of
 * `robolectric.properties`) and one shadow set, the one of the connection tests, so that all of
 * them run in one sandbox. `ShadowRecordingBridge` records what reaches the native library.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
// Abstract so that Gradle does not run the base as a test of its own.
@Suppress("AbstractClassCanBeConcreteClass")
internal abstract class GameTestBase {
    protected val rig = GameRig()

    @Before
    fun forgetTheBridge() {
        ShadowRecordingBridge.reset()
    }

    @After
    fun destroyTheRig() {
        rig.destroy()
    }
}
