package io.github.magalz.borealis.connection

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements

/**
 * Marks a sandbox of its own for [FirstStartTest]. The place that lets one connection start at a
 * time is static, so it belongs to the sandbox, and every other test class resets it before it
 * starts. A shadow that no other class loads gives this test the place as a new process has it.
 */
@Implements(className = "io.github.magalz.borealis.stream.Utf8Text") class ShadowFreshSandbox

/** The connections of a new process: one of them may start, the next one waits for it. */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowRecordingBridge::class,
            ShadowMonkeyActivityManager::class,
            ShadowLoadedNativeLibrary::class,
            ShadowHostRules::class,
            ShadowFreshSandbox::class,
        ]
)
class FirstStartTest {
    @get:Rule val rig = ConnectionRig(resetPlace = false)

    @Test
    fun `the first connection starts and the second waits until the first is stopped`() {
        val first = rig.connection()
        rig.start(first)
        ShadowRecordingBridge.await("startConnection")
        rig.listener.awaitEnded()
        assertThat(rig.permits()).isEqualTo(0)
        val secondListener = rig.newListener()

        rig.start(rig.connection(), listener = secondListener)
        secondListener.await("stageComplete")
        val thread = secondListener.threads.single()
        while (thread.state != Thread.State.WAITING) {
            check(thread.state != Thread.State.TERMINATED) { "The second start did not wait" }
            Thread.sleep(POLL_MS)
        }
        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(1)
        first.stop()
        ShadowRecordingBridge.await("startConnection", count = 2)
    }

    private companion object {
        const val POLL_MS = 10L
    }
}
