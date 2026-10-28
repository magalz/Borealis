package io.github.magalz.borealis.stream.game.motion

import android.view.InputDevice
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Test

/**
 * The pen events that the stream activity sends to a host that takes them: the type of touch each
 * action is, the tool, the buttons, the pressure or distance, the rotation, the tilt, the contact
 * area and the normalized position. A pen event replaces the mouse events of the same input, so
 * nothing else reaches the host.
 */
internal class PenEventsTest : MotionTestBase() {
    private val source = InputDevice.SOURCE_STYLUS

    private val orientation = Range(Axes.ORIENTATION, source, -3.14f, 3.14f)
    private val tilt = Range(Axes.TILT, source, 0f, 1.57f)
    private val distance = Range(Axes.DISTANCE, source, 0f, 2f)

    /** A stylus that reports an orientation, a tilt and a distance. */
    private val fullPen = inputDeviceOf(PEN_ID, source, orientation, tilt, distance)

    private fun stylus(
        action: Int,
        x: Float = 160f,
        y: Float = 90f,
        axes: Map<Int, Float> = emptyMap(),
        build: MotionSpec.() -> Unit = {},
    ) =
        motionEventOf(source, action) {
            device = fullPen
            pointer(x, y, MotionEvent.TOOL_TYPE_STYLUS, axes)
            build()
        }

    private fun send(event: MotionEvent): Boolean = rig.game.onGenericMotion(streamView, event)

    private fun onlyPen(): PenCall {
        assertThat(sent().map { it.name }).containsExactly("sendPenEvent")
        return pens().single()
    }

    // ----- The type of touch -----

    @Test
    fun `an action of a stylus is the touch of its kind, and nothing else is sent`() {
        streaming()
        val table =
            mapOf(
                MotionEvent.ACTION_DOWN to MoonBridge.LI_TOUCH_EVENT_DOWN,
                MotionEvent.ACTION_UP to MoonBridge.LI_TOUCH_EVENT_UP,
                MotionEvent.ACTION_MOVE to MoonBridge.LI_TOUCH_EVENT_MOVE,
                MotionEvent.ACTION_HOVER_ENTER to MoonBridge.LI_TOUCH_EVENT_HOVER,
                MotionEvent.ACTION_HOVER_MOVE to MoonBridge.LI_TOUCH_EVENT_HOVER,
                MotionEvent.ACTION_HOVER_EXIT to MoonBridge.LI_TOUCH_EVENT_HOVER_LEAVE,
                MotionEvent.ACTION_BUTTON_PRESS to MoonBridge.LI_TOUCH_EVENT_BUTTON_ONLY,
                MotionEvent.ACTION_BUTTON_RELEASE to MoonBridge.LI_TOUCH_EVENT_BUTTON_ONLY,
            )

        for ((action, type) in table) {
            val name = MotionEvent.actionToString(action)

            assertWithMessage("$name handled").that(send(stylus(action))).isTrue()

            assertWithMessage(name).that(onlyPen().type).isEqualTo(type.toInt())
            forgetSent()
        }
    }

    @Test
    fun `an up with the cancelled flag is a cancel, a cancel of the whole gesture cancels all`() {
        streaming()

        send(stylus(MotionEvent.ACTION_UP) { flags = MotionEvent.FLAG_CANCELED })
        assertThat(onlyPen().type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_CANCEL.toInt())
        forgetSent()

        send(stylus(MotionEvent.ACTION_UP))
        assertThat(onlyPen().type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_UP.toInt())
    }

    @Test
    fun `an action for one of two pointers is the touch of the pointer it is for`() {
        streaming()
        fun twoStylus(action: Int, flags: Int = 0) =
            motionEventOf(source, action) {
                device = fullPen
                pointer(32f, 18f, MotionEvent.TOOL_TYPE_STYLUS)
                pointer(160f, 90f, MotionEvent.TOOL_TYPE_STYLUS)
                actionIndex = 1
                this.flags = flags
            }

        send(twoStylus(MotionEvent.ACTION_POINTER_DOWN))
        assertThat(onlyPen().type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_DOWN.toInt())
        // The coordinates are the ones of the second pointer.
        assertThat(pens().single().x).isWithin(EPSILON).of(0.5f)
        forgetSent()

        send(twoStylus(MotionEvent.ACTION_POINTER_UP))
        assertThat(onlyPen().type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_UP.toInt())
        forgetSent()

        send(twoStylus(MotionEvent.ACTION_POINTER_UP, MotionEvent.FLAG_CANCELED))
        assertThat(onlyPen().type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_CANCEL.toInt())
    }

    @Test
    fun `a cancel is a cancel of every pen, with nothing known about the pen`() {
        streaming()

        assertThat(send(stylus(MotionEvent.ACTION_CANCEL))).isTrue()

        val cancel = onlyPen()
        assertThat(cancel.type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL.toInt())
        assertThat(cancel.tool).isEqualTo(MoonBridge.LI_TOOL_TYPE_UNKNOWN.toInt())
        assertThat(cancel.buttons).isEqualTo(0)
        assertThat(listOf(cancel.x, cancel.y, cancel.pressure, cancel.major, cancel.minor))
            .containsExactly(0f, 0f, 0f, 0f, 0f)
        assertThat(cancel.rotation).isEqualTo(-1)
        assertThat(cancel.tilt).isEqualTo(-1)
        assertThat(MoonBridge.LI_ROT_UNKNOWN.toInt()).isEqualTo(-1)
        assertThat(MoonBridge.LI_TILT_UNKNOWN.toInt()).isEqualTo(-1)
    }

    @Test
    fun `a cancel of a mouse is a pen cancel too, whatever the tool`() {
        streaming()

        val handled = send(mouseEvent(MotionEvent.ACTION_CANCEL, 10f, 20f))

        assertThat(handled).isTrue()
        assertThat(onlyPen().type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL.toInt())
    }

    @Test
    fun `an action that is no touch is not a pen event, the stylus is a mouse for it`() {
        streaming()

        val handled =
            send(
                stylus(
                    MotionEvent.ACTION_SCROLL,
                    axes = mapOf(Axes.VSCROLL to 1f, Axes.HSCROLL to -1f),
                )
            )

        assertThat(handled).isTrue()
        assertThat(pens()).isEmpty()
        assertThat(sent())
            .containsExactly(position(160, 90), scrolled(120), scrolledAcross(-120))
            .inOrder()
    }

    // ----- The tool -----

    @Test
    fun `the tool is a pen for a stylus and an eraser for an eraser`() {
        streaming()

        send(stylus(MotionEvent.ACTION_DOWN))
        send(
            stylus(MotionEvent.ACTION_DOWN) {
                pointers[0] = Pointer(MotionEvent.TOOL_TYPE_ERASER, 160f, 90f)
            }
        )

        assertThat(pens().map { it.tool })
            .containsExactly(
                MoonBridge.LI_TOOL_TYPE_PEN.toInt(),
                MoonBridge.LI_TOOL_TYPE_ERASER.toInt(),
            )
            .inOrder()
    }

    @Test
    fun `a pointer that is not a stylus is no pen, it is placed as a mouse`() {
        streaming()

        val handled =
            send(
                stylus(MotionEvent.ACTION_HOVER_MOVE) {
                    pointers[0] = Pointer(MotionEvent.TOOL_TYPE_MOUSE, 160f, 90f)
                }
            )

        assertThat(handled).isTrue()
        assertThat(pens()).isEmpty()
        assertThat(sent()).containsExactly(position(160, 90))
    }

    // ----- The buttons of the pen -----

    @Test
    fun `the buttons of the stylus are the buttons of the pen`() {
        streaming()
        val table =
            mapOf(
                MotionEvent.BUTTON_STYLUS_PRIMARY to 1,
                MotionEvent.BUTTON_STYLUS_SECONDARY to 2,
                (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY) to 3,
                0 to 0,
                // The buttons of a mouse are not buttons of the pen.
                (MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY) to 0,
            )

        for ((state, buttons) in table) {
            send(stylus(MotionEvent.ACTION_DOWN) { buttonState = state })

            assertWithMessage("state $state").that(pens().last().buttons).isEqualTo(buttons)
            forgetSent()
        }
    }

    // ----- Pressure and distance -----

    @Test
    fun `a pen that touches reports its pressure, a pen that hovers its distance`() {
        streaming()
        val axes = mapOf(Axes.PRESSURE to 0.7f, Axes.DISTANCE to 0.5f)

        send(stylus(MotionEvent.ACTION_DOWN, axes = axes))
        assertThat(pens().single().pressure).isEqualTo(0.7f)
        forgetSent()

        // The distance is a fraction of the range of the device: 0.5 of 0 to 2.
        for (action in
            listOf(
                MotionEvent.ACTION_HOVER_ENTER,
                MotionEvent.ACTION_HOVER_MOVE,
                MotionEvent.ACTION_HOVER_EXIT,
            )) {
            send(stylus(action, axes = axes))

            assertWithMessage(MotionEvent.actionToString(action))
                .that(pens().single().pressure)
                .isEqualTo(0.25f)
            forgetSent()
        }
    }

    @Test
    fun `the distance counts from the start of the range of the device`() {
        streaming()
        val device = inputDeviceOf(PEN_ID, source, Range(Axes.DISTANCE, source, -1f, 3f))

        send(
            stylus(MotionEvent.ACTION_HOVER_MOVE, axes = mapOf(Axes.DISTANCE to 1f)) {
                this.device = device
            }
        )

        assertThat(pens().single().pressure).isEqualTo(0.5f)
    }

    @Test
    fun `a hover of a pen that has no distance range, or no device, reports a distance of zero`() {
        streaming()
        val axes = mapOf(Axes.PRESSURE to 0.9f, Axes.DISTANCE to 0.5f)
        val withoutDistance = inputDeviceOf(PEN_ID, source, orientation, tilt)

        send(stylus(MotionEvent.ACTION_HOVER_MOVE, axes = axes) { device = withoutDistance })
        send(stylus(MotionEvent.ACTION_HOVER_MOVE, axes = axes) { device = null })

        assertThat(pens().map { it.pressure }).containsExactly(0f, 0f)
    }

    // ----- Rotation -----

    private fun rotationOf(radians: Float, device: InputDevice? = fullPen): Int {
        forgetSent()
        send(
            stylus(MotionEvent.ACTION_DOWN, axes = mapOf(Axes.ORIENTATION to radians)) {
                this.device = device
            }
        )
        return pens().single().rotation
    }

    @Test
    fun `the rotation is the orientation in degrees, cut toward zero, never negative`() {
        streaming()
        val table =
            mapOf(
                0f to 0,
                1f to 57,
                3f to 171,
                // A negative angle is the turn that completes it to a whole one.
                -1f to 303,
                -3f to 189,
                // Under a degree below zero is no turn at all.
                -0.0001f to 0,
                // Past the sign bit of a short: 57295 degrees wrap to -8241, and 360 are added.
                1000f to -7881,
            )

        for ((radians, degrees) in table) {
            assertWithMessage("$radians rad").that(rotationOf(radians)).isEqualTo(degrees)
        }
    }

    @Test
    fun `a pen without an orientation, or without a device, has an unknown rotation`() {
        streaming()
        val withoutOrientation = inputDeviceOf(PEN_ID, source, tilt, distance)

        assertThat(rotationOf(1f, withoutOrientation)).isEqualTo(MoonBridge.LI_ROT_UNKNOWN.toInt())
        assertThat(rotationOf(1f, null)).isEqualTo(MoonBridge.LI_ROT_UNKNOWN.toInt())
    }

    // ----- Tilt -----

    private fun tiltOf(radians: Float, device: InputDevice? = fullPen): Int {
        forgetSent()
        send(
            stylus(MotionEvent.ACTION_DOWN, axes = mapOf(Axes.TILT to radians)) {
                this.device = device
            }
        )
        return pens().single().tilt
    }

    @Test
    fun `the tilt is the angle in degrees, cut toward zero, as a byte`() {
        streaming()
        val table =
            mapOf(
                0f to 0,
                0.5f to 28,
                1.5f to 85,
                -0.5f to -28,
                // Past the sign bit of a byte: 171 degrees wrap to -85.
                3f to -85,
            )

        for ((radians, degrees) in table) {
            assertWithMessage("$radians rad").that(tiltOf(radians)).isEqualTo(degrees)
        }
    }

    @Test
    fun `a pen without a tilt, or without a device, has an unknown tilt`() {
        streaming()
        val withoutTilt = inputDeviceOf(PEN_ID, source, orientation, distance)

        assertThat(tiltOf(0.5f, withoutTilt)).isEqualTo(MoonBridge.LI_TILT_UNKNOWN.toInt())
        assertThat(tiltOf(0.5f, null)).isEqualTo(MoonBridge.LI_TILT_UNKNOWN.toInt())
    }

    // ----- The position -----

    private fun positionOf(view: android.view.View, x: Float, y: Float): Pair<Float, Float> {
        forgetSent()
        rig.game.onGenericMotion(view, stylus(MotionEvent.ACTION_DOWN, x, y))
        return pens().single().let { it.x to it.y }
    }

    @Test
    fun `the position is a fraction of the stream view, held to its border`() {
        streaming()
        val table =
            listOf(
                Triple(160f, 90f, 0.5f to 0.5f),
                Triple(32f, 18f, 0.1f to 0.1f),
                Triple(320f, 0f, 1f to 0f),
                Triple(-10f, -10f, 0f to 0f),
                Triple(400f, 300f, 1f to 1f),
            )

        for ((x, y, expected) in table) {
            val (fractionX, fractionY) = positionOf(streamView, x, y)

            assertWithMessage("x of ($x, $y)").that(fractionX).isWithin(EPSILON).of(expected.first)
            assertWithMessage("y of ($x, $y)").that(fractionY).isWithin(EPSILON).of(expected.second)
        }
    }

    @Test
    fun `a position from the background view is moved by the origin of the stream view`() {
        streaming()

        val (x, y) = positionOf(backgroundView, 160f, 235f)
        assertThat(x).isWithin(EPSILON).of(0.5f)
        assertThat(y).isWithin(EPSILON).of(0.5f)

        // Above the stream view: the border, not a negative fraction.
        val (_, above) = positionOf(backgroundView, 10f, 100f)
        assertThat(above).isEqualTo(0f)

        val (beyond, below) = positionOf(backgroundView, 500f, 400f)
        assertThat(beyond).isEqualTo(1f)
        assertThat(below).isEqualTo(1f)
    }

    @Test
    fun `the origin of the stream view moves the pen in x as well`() {
        streaming(left = 40)

        val (x, y) = positionOf(backgroundView, 200f, 235f)

        assertThat(x).isWithin(EPSILON).of(0.5f)
        assertThat(y).isWithin(EPSILON).of(0.5f)
    }

    // ----- Contact area -----

    private fun contactOf(
        action: Int,
        axes: Map<Int, Float>,
        device: InputDevice?,
    ): Pair<Float, Float> {
        forgetSent()
        send(stylus(action, axes = axes) { this.device = device })
        return pens().single().let { it.major to it.minor }
    }

    private val areaAxes =
        mapOf(
            Axes.TOUCH_MAJOR to 32f,
            Axes.TOUCH_MINOR to 18f,
            Axes.TOOL_MAJOR to 64f,
            Axes.TOOL_MINOR to 36f,
        )

    @Test
    fun `the contact area of a touch is the touch size along the orientation, over the view`() {
        streaming()

        // At orientation 0 the major axis is along x and the minor along y: 32 of 320, 18 of 180.
        val (major, minor) =
            contactOf(MotionEvent.ACTION_DOWN, areaAxes + (Axes.ORIENTATION to 0f), fullPen)

        assertThat(major).isWithin(EPSILON).of(0.1f)
        assertThat(minor).isWithin(EPSILON).of(0.1f)
    }

    @Test
    fun `the contact area of a hover is the tool size`() {
        streaming()

        val (major, minor) =
            contactOf(MotionEvent.ACTION_HOVER_MOVE, areaAxes + (Axes.ORIENTATION to 0f), fullPen)

        assertThat(major).isWithin(EPSILON).of(0.2f)
        assertThat(minor).isWithin(EPSILON).of(0.2f)
    }

    @Test
    fun `a turn of a quarter swaps the axes of the contact area`() {
        streaming()

        // At 90 degrees the major axis is along y: 32 of 180, and the minor along x: 18 of 320.
        val (major, minor) =
            contactOf(
                MotionEvent.ACTION_DOWN,
                areaAxes + (Axes.ORIENTATION to 1.5707964f),
                fullPen,
            )

        assertThat(major).isWithin(EPSILON).of(0.17777778f)
        assertThat(minor).isWithin(EPSILON).of(0.05625f)
    }

    @Test
    fun `the contact area at another orientation is the size spread on x and y`() {
        streaming()

        val (major, minor) =
            contactOf(MotionEvent.ACTION_DOWN, areaAxes + (Axes.ORIENTATION to -1f), fullPen)

        assertThat(major).isWithin(EPSILON).of(0.15905309f)
        assertThat(minor).isWithin(EPSILON).of(0.071830735f)
    }

    @Test
    fun `without an orientation the contact area is taken at forty-five degrees`() {
        streaming()
        val withoutOrientation = inputDeviceOf(PEN_ID, source, tilt, distance)

        for (device in listOf(withoutOrientation, null)) {
            val (major, minor) =
                contactOf(MotionEvent.ACTION_DOWN, areaAxes + (Axes.ORIENTATION to 1f), device)

            assertThat(major).isWithin(EPSILON).of(0.1442306f)
            assertThat(minor).isWithin(EPSILON).of(0.08112972f)
        }
    }

    @Test
    fun `a contact area bigger than the view is held to the view`() {
        streaming()
        val huge =
            mapOf(Axes.TOUCH_MAJOR to 1000f, Axes.TOUCH_MINOR to 1000f, Axes.ORIENTATION to 0f)

        val (major, minor) = contactOf(MotionEvent.ACTION_DOWN, huge, fullPen)

        assertThat(major).isWithin(EPSILON).of(1f)
        assertThat(minor).isWithin(EPSILON).of(1f)
    }

    // ----- Many pointers -----

    @Test
    fun `a move reports each stylus of the event and skips the pointers that are not`() {
        streaming()
        val event =
            motionEventOf(source, MotionEvent.ACTION_MOVE) {
                device = fullPen
                pointer(32f, 18f, MotionEvent.TOOL_TYPE_STYLUS)
                pointer(1f, 1f, MotionEvent.TOOL_TYPE_FINGER)
                pointer(160f, 90f, MotionEvent.TOOL_TYPE_ERASER)
            }

        assertThat(send(event)).isTrue()

        assertThat(sent().map { it.name }).containsExactly("sendPenEvent", "sendPenEvent")
        val (first, second) = pens()
        assertThat(first.tool).isEqualTo(MoonBridge.LI_TOOL_TYPE_PEN.toInt())
        assertThat(first.x).isWithin(EPSILON).of(0.1f)
        assertThat(second.tool).isEqualTo(MoonBridge.LI_TOOL_TYPE_ERASER.toInt())
        assertThat(second.x).isWithin(EPSILON).of(0.5f)
        assertThat(pens().map { it.type }).containsExactly(3, 3)
    }

    @Test
    fun `a move of pointers that are no stylus is no pen event, it moves the mouse`() {
        streaming()
        val event =
            motionEventOf(source, MotionEvent.ACTION_MOVE) {
                device = fullPen
                pointer(32f, 18f, MotionEvent.TOOL_TYPE_MOUSE)
                pointer(160f, 90f, MotionEvent.TOOL_TYPE_MOUSE)
            }

        assertThat(send(event)).isTrue()

        assertThat(pens()).isEmpty()
        assertThat(sent()).containsExactly(position(32, 18))
    }

    private companion object {
        const val PEN_ID = 10
        const val EPSILON = 1e-6f
    }
}
