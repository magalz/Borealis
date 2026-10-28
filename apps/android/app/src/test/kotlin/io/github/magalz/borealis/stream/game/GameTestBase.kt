package io.github.magalz.borealis.stream.game

import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Robolectric setup that every test of the stream activity shares: one SDK level (the one of
 * `robolectric.properties`) and one shadow set, `ShadowRecordingBridge` alone, which the
 * touch-context tests and `MoonBridgeCallbacksTest` use too, so that all of them run in one
 * sandbox. `ShadowRecordingBridge` records what reaches the native library.
 *
 * The shadow set of the connection tests does not fit: its `ShadowMonkeyActivityManager` replaces
 * the activity manager of Robolectric, which then answers no OpenGL facts, and `MediaCodecHelper`
 * needs them when the activity starts.
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
        // The shadow lives as long as the sandbox: what this test told it must not reach the
        // tests of the other classes that share it.
        ShadowRecordingBridge.reset()
    }
}
