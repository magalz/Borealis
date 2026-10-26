package io.github.magalz.borealis.input

import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.capture.AndroidNativePointerCaptureProvider
import com.limelight.binding.input.capture.InputCaptureManager
import io.github.magalz.borealis.crypto.LimeLogCapture
import java.util.logging.Level
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `Game` asks the manager for its capture provider once, in `onCreate`, after the layout is
 * there. There is one kind of provider since the Android O check went: the native pointer capture,
 * on the stream view of the activity, with the pointer-icon hiding it inherits.
 */
@RunWith(RobolectricTestRunner::class)
class InputCaptureManagerCharacterizationTest {
    private val rig = CaptureRig()

    @Test
    fun `the provider is the native pointer capture, and the manager says so in the log`() {
        LimeLogCapture().use { log ->
            val provider = InputCaptureManager.getInputCaptureProvider(rig.activity)

            assertThat(provider).isInstanceOf(AndroidNativePointerCaptureProvider::class.java)
            assertThat(log.records).containsExactly(Level.INFO to "Using Android O+ native mouse capture")
        }
    }

    @Test
    fun `the provider works on the stream view of the activity`() {
        rig.connect(MOUSE, Sources.MOUSE)
        val provider = InputCaptureManager.getInputCaptureProvider(rig.activity)

        provider.enableCapture()

        assertThat(rig.view.pointerIcon).isEqualTo(rig.nullIcon)
        assertThat(rig.view.requests).isEqualTo(1)
    }

    @Test
    fun `every call makes a provider of its own, not capturing yet`() {
        val first = InputCaptureManager.getInputCaptureProvider(rig.activity)
        val second = InputCaptureManager.getInputCaptureProvider(rig.activity)

        first.enableCapture()

        assertThat(second).isNotSameInstanceAs(first)
        assertThat(first.isCapturingActive()).isTrue()
        assertThat(second.isCapturingActive()).isFalse()
    }

    private companion object {
        const val MOUSE = 3
    }
}
