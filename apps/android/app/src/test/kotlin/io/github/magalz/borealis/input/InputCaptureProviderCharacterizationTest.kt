package io.github.magalz.borealis.input

import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.capture.AndroidPointerIconCaptureProvider
import com.limelight.binding.input.capture.InputCaptureProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What every capture provider does, and what the plain pointer-icon provider adds: capture is a
 * flag with a cursor that follows it, and a provider says that it has no relative axes until a
 * subclass says otherwise.
 */
@RunWith(RobolectricTestRunner::class)
class InputCaptureProviderCharacterizationTest {
    /** The base class with nothing added but a note of what the cursor was told, and when. */
    private class Probe : InputCaptureProvider() {
        val notes = mutableListOf<String>()
        val capturing: Boolean
            get() = isCapturing

        val cursorVisible: Boolean
            get() = isCursorVisible

        override fun hideCursor() {
            super.hideCursor()
            notes += "hide while capturing=$isCapturing"
        }

        override fun showCursor() {
            super.showCursor()
            notes += "show while capturing=$isCapturing"
        }
    }

    private val relativeEvent =
        motionEvent(
            Sources.MOUSE_RELATIVE,
            mapOf(
                MotionEvent.AXIS_X to 3f,
                MotionEvent.AXIS_Y to 4f,
                MotionEvent.AXIS_RELATIVE_X to 5f,
                MotionEvent.AXIS_RELATIVE_Y to 6f,
            ),
        )

    @Test
    fun `a provider is not capturing, and thinks the cursor is hidden, until it is told`() {
        val probe = Probe()

        assertThat(probe.isCapturingActive()).isFalse()
        assertThat(probe.capturing).isFalse()
        assertThat(probe.cursorVisible).isFalse()
        assertThat(probe.notes).isEmpty()
    }

    @Test
    fun `enabling the capture turns it on first and then hides the cursor`() {
        val probe = Probe()

        probe.enableCapture()

        assertThat(probe.isCapturingActive()).isTrue()
        assertThat(probe.notes).containsExactly("hide while capturing=true")
        assertThat(probe.cursorVisible).isFalse()
    }

    @Test
    fun `disabling the capture turns it off first and then shows the cursor`() {
        val probe = Probe()
        probe.enableCapture()
        probe.notes.clear()

        probe.disableCapture()

        assertThat(probe.isCapturingActive()).isFalse()
        assertThat(probe.notes).containsExactly("show while capturing=false")
        assertThat(probe.cursorVisible).isTrue()
    }

    @Test
    fun `the cursor can be shown and hidden without changing the capture`() {
        val probe = Probe()
        probe.enableCapture()

        probe.showCursor()
        assertThat(probe.cursorVisible).isTrue()
        assertThat(probe.isCapturingActive()).isTrue()

        probe.hideCursor()
        assertThat(probe.cursorVisible).isFalse()
        assertThat(probe.isCapturingActive()).isTrue()
    }

    @Test
    fun `the base provider has no relative axes and does nothing on destroy or on focus`() {
        val probe = Probe()
        probe.enableCapture()

        assertThat(probe.eventHasRelativeMouseAxes(relativeEvent)).isFalse()
        assertThat(probe.getRelativeAxisX(relativeEvent)).isEqualTo(0f)
        assertThat(probe.getRelativeAxisY(relativeEvent)).isEqualTo(0f)

        probe.onWindowFocusChanged(true)
        probe.onWindowFocusChanged(false)
        probe.destroy()

        assertThat(probe.isCapturingActive()).isTrue()
        assertThat(probe.notes).containsExactly("hide while capturing=true")
    }

    @Test
    fun `the pointer-icon provider hides the cursor with the null icon and shows it with none`() {
        val rig = CaptureRig()
        val provider = AndroidPointerIconCaptureProvider(rig.activity, rig.view)
        assertThat(rig.view.pointerIcon).isNull()

        provider.hideCursor()
        assertThat(rig.view.pointerIcon).isEqualTo(rig.nullIcon)

        provider.showCursor()
        assertThat(rig.view.pointerIcon).isNull()
    }

    @Test
    fun `the pointer-icon provider hides the cursor when capture is enabled and shows it when it is disabled`() {
        val rig = CaptureRig()
        val provider = AndroidPointerIconCaptureProvider(rig.activity, rig.view)

        provider.enableCapture()
        assertThat(provider.isCapturingActive()).isTrue()
        assertThat(rig.view.pointerIcon).isEqualTo(rig.nullIcon)

        provider.disableCapture()
        assertThat(provider.isCapturingActive()).isFalse()
        assertThat(rig.view.pointerIcon).isNull()
    }

    @Test
    fun `the pointer-icon provider never asks for pointer capture and has no relative axes`() {
        val rig = CaptureRig()
        val provider = AndroidPointerIconCaptureProvider(rig.activity, rig.view)

        provider.enableCapture()
        provider.onWindowFocusChanged(true)
        rig.advanceBy(1_000)
        provider.disableCapture()

        assertThat(rig.view.requests).isEqualTo(0)
        assertThat(rig.view.releases).isEqualTo(0)
        assertThat(provider.eventHasRelativeMouseAxes(relativeEvent)).isFalse()
        assertThat(provider.getRelativeAxisX(relativeEvent)).isEqualTo(0f)
        assertThat(provider.getRelativeAxisY(relativeEvent)).isEqualTo(0f)
    }
}
