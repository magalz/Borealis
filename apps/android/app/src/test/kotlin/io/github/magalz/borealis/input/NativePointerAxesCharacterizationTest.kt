package io.github.magalz.borealis.input

import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What `Game` reads from a mouse that has the pointer capture: whether an event carries relative
 * axes, and how far the mouse moved since the last event, which is the sum of every sample the
 * event batched. A captured mouse reports X and Y as deltas; a touchpad reports them in
 * `AXIS_RELATIVE_X` and `AXIS_RELATIVE_Y` while the view has the capture.
 */
@RunWith(RobolectricTestRunner::class)
class NativePointerAxesCharacterizationTest {
    private val rig = CaptureRig()

    private fun hasRelativeAxes(source: Int, toolType: Int, capture: Boolean): Boolean {
        rig.view.hasCapture = capture
        return rig.provider.eventHasRelativeMouseAxes(
            motionEvent(source, mapOf(MotionEvent.AXIS_X to 1f), toolType)
        )
    }

    @Test
    fun `a relative mouse event has relative axes when the tool is a mouse`() {
        assertThat(hasRelativeAxes(Sources.MOUSE_RELATIVE, MotionEvent.TOOL_TYPE_MOUSE, false))
            .isTrue()
        assertThat(hasRelativeAxes(Sources.MOUSE_RELATIVE, MotionEvent.TOOL_TYPE_MOUSE, true))
            .isTrue()
    }

    @Test
    fun `a relative mouse event of another tool has none, whatever the view holds`() {
        for (tool in listOf(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS)) {
            assertWithMessage("tool $tool")
                .that(hasRelativeAxes(Sources.MOUSE_RELATIVE, tool, true))
                .isFalse()
        }
    }

    @Test
    fun `a touchpad event has relative axes only while the view holds the capture`() {
        assertThat(hasRelativeAxes(Sources.TOUCHPAD, MotionEvent.TOOL_TYPE_FINGER, true)).isTrue()
        assertThat(hasRelativeAxes(Sources.TOUCHPAD, MotionEvent.TOOL_TYPE_FINGER, false)).isFalse()
        // The tool does not matter for a touchpad.
        assertThat(hasRelativeAxes(Sources.TOUCHPAD, MotionEvent.TOOL_TYPE_MOUSE, true)).isTrue()
    }

    @Test
    fun `an event of any other source has none, even with the capture`() {
        for (source in
            listOf(
                Sources.MOUSE,
                Sources.TOUCHSCREEN,
                Sources.KEYBOARD,
                // The source has to be the relative mouse, not just include it.
                Sources.MOUSE_RELATIVE or Sources.KEYBOARD,
            )) {
            assertWithMessage("source $source")
                .that(hasRelativeAxes(source, MotionEvent.TOOL_TYPE_MOUSE, true))
                .isFalse()
        }
    }

    @Test
    fun `a relative mouse moves by the sum of its X and Y samples`() {
        val event =
            motionEvent(
                Sources.MOUSE_RELATIVE,
                listOf(
                    mapOf(MotionEvent.AXIS_X to 1f, MotionEvent.AXIS_Y to -0.5f),
                    mapOf(MotionEvent.AXIS_X to 2.5f, MotionEvent.AXIS_Y to 4f),
                    mapOf(MotionEvent.AXIS_X to -4f, MotionEvent.AXIS_Y to 8f),
                ),
            )

        assertThat(event.historySize).isEqualTo(2)
        assertThat(rig.provider.getRelativeAxisX(event)).isEqualTo(-0.5f)
        assertThat(rig.provider.getRelativeAxisY(event)).isEqualTo(11.5f)
    }

    @Test
    fun `a relative mouse ignores the relative axes`() {
        val event =
            motionEvent(
                Sources.MOUSE_RELATIVE,
                mapOf(
                    MotionEvent.AXIS_X to 3f,
                    MotionEvent.AXIS_Y to 4f,
                    MotionEvent.AXIS_RELATIVE_X to 100f,
                    MotionEvent.AXIS_RELATIVE_Y to 200f,
                ),
            )

        assertThat(rig.provider.getRelativeAxisX(event)).isEqualTo(3f)
        assertThat(rig.provider.getRelativeAxisY(event)).isEqualTo(4f)
    }

    @Test
    fun `any other source moves by the sum of its relative axes and ignores X and Y`() {
        for (source in listOf(Sources.TOUCHPAD, Sources.MOUSE)) {
            val event =
                motionEvent(
                    source,
                    listOf(
                        mapOf(
                            MotionEvent.AXIS_RELATIVE_X to 1f,
                            MotionEvent.AXIS_RELATIVE_Y to 2f,
                            MotionEvent.AXIS_X to 100f,
                            MotionEvent.AXIS_Y to 200f,
                        ),
                        mapOf(
                            MotionEvent.AXIS_RELATIVE_X to 3.5f,
                            MotionEvent.AXIS_RELATIVE_Y to -6f,
                            MotionEvent.AXIS_X to 300f,
                            MotionEvent.AXIS_Y to 400f,
                        ),
                    ),
                )

            assertWithMessage("source $source")
                .that(rig.provider.getRelativeAxisX(event))
                .isEqualTo(4.5f)
            assertWithMessage("source $source")
                .that(rig.provider.getRelativeAxisY(event))
                .isEqualTo(-4f)
        }
    }

    @Test
    fun `an event without history moves by its own value, and one without relative axes by nothing`() {
        val single =
            motionEvent(
                Sources.TOUCHPAD,
                mapOf(MotionEvent.AXIS_RELATIVE_X to 7f, MotionEvent.AXIS_RELATIVE_Y to -2f),
            )
        val empty = motionEvent(Sources.TOUCHPAD, mapOf(MotionEvent.AXIS_X to 9f))

        assertThat(single.historySize).isEqualTo(0)
        assertThat(rig.provider.getRelativeAxisX(single)).isEqualTo(7f)
        assertThat(rig.provider.getRelativeAxisY(single)).isEqualTo(-2f)
        assertThat(rig.provider.getRelativeAxisX(empty)).isEqualTo(0f)
        assertThat(rig.provider.getRelativeAxisY(empty)).isEqualTo(0f)
    }
}
