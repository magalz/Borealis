package io.github.magalz.borealis.stream.game.motion

import android.view.InputDevice
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.MouseButtonPacket
import org.junit.Test

/**
 * Where the pointer of a mouse or a touchpad is and how it moves: the deltas of a captured mouse,
 * the coordinates of a touchpad, the position on the stream view, and the scroll wheel. Every
 * number is what the host receives, so the casts to a short and the clamping show in the
 * expectations.
 */
internal class MouseMovementTest : MotionTestBase() {
    // ----- A captured mouse: relative axes -----

    private fun captured(
        x: Float,
        y: Float,
        action: Int = MotionEvent.ACTION_MOVE,
        build: MotionSpec.() -> Unit = {},
    ) =
        motionEventOf(InputDevice.SOURCE_MOUSE_RELATIVE, action) {
            pointer(x, y)
            build()
        }

    @Test
    fun `a captured mouse sends the distance it moved, cut toward zero and wrapped as a short`() {
        streaming()
        val table =
            listOf(
                Triple(5.6f, -3.7f, listOf(moveBy(5, -3))),
                Triple(0.4f, 2.2f, listOf(moveBy(0, 2))),
                Triple(2.2f, 0.4f, listOf(moveBy(2, 0))),
                // Past the sign bit of a short.
                Triple(40000f, -40000f, listOf(moveBy(-25536, 25536))),
                Triple(32767.9f, -32768.9f, listOf(moveBy(32767, -32768))),
                // Less than a pixel: nothing to send.
                Triple(0.4f, 0.9f, emptyList()),
                Triple(-0.9f, 0f, emptyList()),
                // A move of a whole turn of a short is no move at all.
                Triple(65536f, 0f, emptyList()),
                Triple(0f, -65536f, emptyList()),
            )

        for ((x, y, expected) in table) {
            val handled = rig.game.onGenericMotion(streamView, captured(x, y))

            assertWithMessage("($x, $y) handled").that(handled).isTrue()
            assertWithMessage("($x, $y)").that(sent()).containsExactlyElementsIn(expected).inOrder()
            forgetSent()
        }
    }

    @Test
    fun `a captured mouse sends the sum of the samples that the event batched`() {
        streaming()
        val event =
            captured(3f, -3f) {
                earlier(1f to -1f)
                earlier(2f to -2f)
            }
        assertThat(event.historySize).isEqualTo(2)

        rig.game.onGenericMotion(streamView, event)

        assertThat(sent()).containsExactly(moveBy(6, -6))
    }

    @Test
    fun `the setting for an absolute mouse sends the move on the view, as the position it implies`() {
        rig.preference("checkbox_absolute_mouse_mode", true)
        streaming()

        rig.game.onGenericMotion(streamView, captured(5.6f, -3.7f))

        assertThat(sent()).containsExactly(moveByOnView(5, -3))
    }

    @Test
    fun `the absolute move says how big the stream view is, as a short`() {
        rig.preference("checkbox_absolute_mouse_mode", true)
        streaming(width = 40000)

        rig.game.onGenericMotion(streamView, captured(5f, 6f))

        // 40000 does not fit a short and arrives as -25536.
        assertThat(sent()).containsExactly(moveByOnView(5, 6, width = 40000))
        assertThat(sent().single().args[2]).isEqualTo((-25536).toShort())
    }

    @Test
    fun `a captured mouse of the activity itself has no view, and the absolute move still has a size`() {
        rig.preference("checkbox_absolute_mouse_mode", true)
        streaming()

        val handled = rig.game.onGenericMotionEvent(captured(7f, 8f))

        assertThat(handled).isTrue()
        assertThat(sent()).containsExactly(moveByOnView(7, 8))
    }

    @Test
    fun `the move of a captured mouse comes before the buttons of the same event`() {
        streaming()

        rig.game.onGenericMotion(
            streamView,
            captured(3f, 0f, MotionEvent.ACTION_DOWN) { buttonState = MotionEvent.BUTTON_PRIMARY },
        )

        assertThat(sent())
            .containsExactly(moveBy(3, 0), press(MouseButtonPacket.BUTTON_LEFT))
            .inOrder()
    }

    // ----- A touchpad: its own coordinates -----

    private fun touchpad(
        xRange: Range?,
        yRange: Range?,
        x: Float = 100.7f,
        y: Float = 50.2f,
    ): MotionEvent {
        val ranges = listOfNotNull(xRange, yRange).toTypedArray()
        val device = inputDeviceOf(TOUCHPAD_ID, InputDevice.SOURCE_TOUCHPAD, *ranges)
        return motionEventOf(InputDevice.SOURCE_TOUCHPAD, MotionEvent.ACTION_MOVE) {
            this.device = device
            pointer(x, y, MotionEvent.TOOL_TYPE_FINGER)
        }
    }

    private fun range(axis: Int, min: Float, max: Float) =
        Range(axis, InputDevice.SOURCE_TOUCHPAD, min, max)

    private fun x(max: Float, min: Float = 0f) = range(Axes.X, min, max)

    private fun y(max: Float, min: Float = 0f) = range(Axes.Y, min, max)

    @Test
    fun `a touchpad sends its coordinates on the plane that it reports`() {
        streaming()

        val handled = rig.game.onGenericMotion(streamView, touchpad(x(1000f), y(500f)))

        assertThat(handled).isTrue()
        assertThat(sent()).containsExactly(position(100, 50, 1000, 500))
    }

    @Test
    fun `the size of the plane of a touchpad is cut to a whole number, and it can be as big as a short`() {
        streaming()

        rig.game.onGenericMotion(streamView, touchpad(x(1999.9f), y(500.9f)))
        assertThat(sent()).containsExactly(position(100, 50, 1999, 500))
        forgetSent()

        rig.game.onGenericMotion(streamView, touchpad(x(32767f), y(32767f)))
        assertThat(sent()).containsExactly(position(100, 50, 32767, 32767))
    }

    @Test
    fun `a touchpad is ignored when its plane does not start at zero, or is too big for a short`() {
        streaming()
        val ignored =
            mapOf(
                "an x that starts at 1" to touchpad(x(1000f, min = 1f), y(500f)),
                "a y that starts at 1" to touchpad(x(1000f), y(500f, min = 1f)),
                "an x that starts below 0" to touchpad(x(1000f, min = -1f), y(500f)),
                "an x of 32768" to touchpad(x(32768f), y(500f)),
                "a y of 32768" to touchpad(x(1000f), y(32768f)),
                "no x range" to touchpad(null, y(500f)),
                "no y range" to touchpad(x(1000f), null),
                "no ranges" to touchpad(null, null),
            )

        for ((what, event) in ignored) {
            assertWithMessage("$what handled")
                .that(rig.game.onGenericMotion(streamView, event))
                .isTrue()
            assertWithMessage(what).that(sent()).isEmpty()
        }
    }

    @Test
    fun `a touchpad event with no device is consumed and sends nothing`() {
        streaming()
        val event =
            motionEventOf(InputDevice.SOURCE_TOUCHPAD, MotionEvent.ACTION_MOVE) {
                pointer(100.7f, 50.2f, MotionEvent.TOOL_TYPE_FINGER)
            }

        assertThat(rig.game.onGenericMotion(streamView, event)).isTrue()

        assertThat(sent()).isEmpty()
    }

    @Test
    fun `the coordinates of a touchpad are cut toward zero, and wrap as a short`() {
        streaming()

        rig.game.onGenericMotion(streamView, touchpad(x(1000f), y(500f), x = -1.5f, y = -0.5f))
        assertThat(sent()).containsExactly(position(-1, 0, 1000, 500))
        forgetSent()

        rig.game.onGenericMotion(streamView, touchpad(x(1000f), y(500f), x = 40000.5f, y = 2f))
        assertThat(sent()).containsExactly(position(-25536, 2, 1000, 500))
    }

    @Test
    fun `a touchpad needs no view`() {
        streaming()

        val handled = rig.game.onGenericMotionEvent(touchpad(x(1000f), y(500f)))

        assertThat(handled).isTrue()
        assertThat(sent()).containsExactly(position(100, 50, 1000, 500))
    }

    // ----- The pointer on the stream view -----

    private fun hover(x: Float, y: Float) = mouseEvent(MotionEvent.ACTION_HOVER_MOVE, x, y)

    @Test
    fun `a mouse on the stream view sends where it is on the view, cut toward zero`() {
        streaming()

        assertThat(rig.game.onGenericMotion(streamView, hover(10.9f, 20.1f))).isTrue()
        assertThat(sent()).containsExactly(position(10, 20))
        forgetSent()

        rig.game.onGenericMotion(streamView, hover(319.9f, 179.9f))
        assertThat(sent()).containsExactly(position(319, 179))
    }

    @Test
    fun `a position outside the stream view is brought to its border`() {
        streaming()
        val table =
            listOf(
                Triple(-5f, -1f, position(0, 0)),
                Triple(400.5f, 300f, position(320, 180)),
                Triple(-0.5f, 180.5f, position(0, 180)),
                Triple(320f, 0f, position(320, 0)),
            )

        for ((x, y, expected) in table) {
            rig.game.onGenericMotion(streamView, hover(x, y))

            assertWithMessage("($x, $y)").that(sent()).containsExactly(expected)
            forgetSent()
        }
    }

    @Test
    fun `a position that comes from the background view is moved by the origin of the stream view`() {
        streaming()

        // The stream view starts 145 pixels down.
        rig.game.onGenericMotion(backgroundView, hover(30.5f, 200.2f))
        assertThat(sent()).containsExactly(position(30, 55))
        forgetSent()

        // Above the stream view: the border.
        rig.game.onGenericMotion(backgroundView, hover(100f, 100f))
        assertThat(sent()).containsExactly(position(100, 0))
        forgetSent()

        rig.game.onGenericMotion(backgroundView, hover(500f, 400f))
        assertThat(sent()).containsExactly(position(320, 180))
    }

    @Test
    fun `the origin of the stream view moves the position in x as well`() {
        streaming(left = 40)

        rig.game.onGenericMotion(backgroundView, hover(100.9f, 200f))
        assertThat(sent()).containsExactly(position(60, 55))
        forgetSent()

        rig.game.onGenericMotion(backgroundView, hover(10f, 200f))
        assertThat(sent()).containsExactly(position(0, 55))
        forgetSent()

        // On the stream view itself the coordinates are the view's own.
        rig.game.onGenericMotion(streamView, hover(100.9f, 20f))
        assertThat(sent()).containsExactly(position(100, 20))
    }

    @Test
    fun `the position and the size of the view are sent as shorts`() {
        streaming(width = 40000)

        rig.game.onGenericMotion(streamView, hover(33000.7f, 20f))

        // 33000 and 40000 do not fit a short.
        assertThat(sent()).containsExactly(position(-32536, 20, 40000, STREAM_HEIGHT))
        assertThat(sent().single().args.take(3))
            .containsExactly((-32536).toShort(), 20.toShort(), (-25536).toShort())
            .inOrder()
    }

    @Test
    fun `the activity itself has no view and does not place the pointer`() {
        streaming()

        val handled = rig.game.onGenericMotionEvent(hover(10f, 20f))

        assertThat(handled).isTrue()
        assertThat(sent()).isEmpty()
    }

    @Test
    fun `the activity itself still sends the buttons`() {
        streaming()

        rig.game.onGenericMotionEvent(
            mouseEvent(MotionEvent.ACTION_DOWN, 10f, 20f, MotionEvent.BUTTON_PRIMARY)
        )

        assertThat(sent()).containsExactly(press(MouseButtonPacket.BUTTON_LEFT))
    }

    // ----- The scroll wheel -----

    private fun wheel(vertical: Float, horizontal: Float) =
        motionEventOf(InputDevice.SOURCE_MOUSE, MotionEvent.ACTION_SCROLL) {
            pointer(
                10f,
                20f,
                MotionEvent.TOOL_TYPE_MOUSE,
                mapOf(Axes.VSCROLL to vertical, Axes.HSCROLL to horizontal),
            )
        }

    @Test
    fun `the wheel sends 120 for a notch, cut toward zero, and wrapped as a short`() {
        streaming()
        val table =
            listOf(
                Triple(1f, 0f, 120 to 0),
                Triple(-1f, 0f, -120 to 0),
                Triple(0f, 1f, 0 to 120),
                Triple(0.5f, -0.25f, 60 to -30),
                Triple(1.5f, 2.5f, 180 to 300),
                // A part of a notch: 1.2, cut to 1; and just under 1, which is nothing.
                Triple(0.01f, -0.01f, 1 to -1),
                Triple(0.0083f, -0.0083f, 0 to 0),
                // 273.5 notches are 32820, past the sign bit of a short.
                Triple(273.5f, -273.5f, -32716 to 32716),
                Triple(0f, 0f, 0 to 0),
            )

        for ((vertical, horizontal, expected) in table) {
            val handled = rig.game.onGenericMotion(streamView, wheel(vertical, horizontal))

            assertWithMessage("($vertical, $horizontal) handled").that(handled).isTrue()
            assertWithMessage("($vertical, $horizontal)")
                .that(sent())
                .containsExactly(
                    position(10, 20),
                    scrolled(expected.first),
                    scrolledAcross(expected.second),
                )
                .inOrder()
            forgetSent()
        }
    }

    @Test
    fun `the wheel of a captured mouse is sent with no position`() {
        streaming()
        val event =
            motionEventOf(InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.ACTION_SCROLL) {
                pointer(
                    0f,
                    0f,
                    MotionEvent.TOOL_TYPE_MOUSE,
                    mapOf(Axes.VSCROLL to 0.5f, Axes.HSCROLL to 1f),
                )
            }

        rig.game.onGenericMotion(streamView, event)

        assertThat(sent()).containsExactly(scrolled(60), scrolledAcross(120)).inOrder()
    }

    private companion object {
        const val TOUCHPAD_ID = 12
    }
}
