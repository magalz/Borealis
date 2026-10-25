package io.github.magalz.borealis.input.events

import android.view.InputDevice.SOURCE_JOYSTICK
import android.view.InputDevice.SOURCE_KEYBOARD
import android.view.InputDevice.SOURCE_MOUSE
import android.view.InputDevice.SOURCE_TOUCHPAD
import android.view.InputDevice.SOURCE_TOUCHSCREEN
import android.view.MotionEvent.ACTION_BUTTON_PRESS
import android.view.MotionEvent.ACTION_BUTTON_RELEASE
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_HOVER_MOVE
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_POINTER_DOWN
import android.view.MotionEvent.ACTION_POINTER_UP
import android.view.MotionEvent.ACTION_UP
import android.view.MotionEvent.AXIS_PRESSURE
import android.view.MotionEvent.AXIS_X
import android.view.MotionEvent.AXIS_Y
import android.view.MotionEvent.BUTTON_PRIMARY
import android.view.MotionEvent.BUTTON_SECONDARY
import android.view.MotionEvent.FLAG_CANCELED
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.ControllerPacket.TOUCHPAD_FLAG
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `tryHandleTouchpadEvent` and the controller touch events it sends: which events it takes, the
 * type of touch each action is, the coordinates and pressure it normalizes, what it answers when the
 * host has no controller touch, and the touchpad button on a pad whose touchpad is a mouse.
 *
 * The pad has a touchpad 1000 by 500 with pressure from 0 to 2, and sits on controller number 2.
 */
@RunWith(RobolectricTestRunner::class)
class TouchpadCharacterizationTest {
    private val rig = EventsRig()

    private val xRange = motionRange(AXIS_X, 0f, 1000f)
    private val yRange = motionRange(AXIS_Y, 0f, 500f)
    private val pressureRange = motionRange(AXIS_PRESSURE, 0f, 2f)

    private fun touchpadPad(deviceId: Int = 7, extra: Map<String, Any?> = emptyMap()) =
        rig.addPad(
            deviceId,
            controllerNumber = 2,
            fields =
                mapOf(
                    "touchpadXRange" to xRange,
                    "touchpadYRange" to yRange,
                    "touchpadPressureRange" to pressureRange,
                ) + extra,
        )

    private val pad = touchpadPad()

    private fun finger(id: Int = 0, x: Float = 250f, y: Float = 125f, pressure: Float = 1f) =
        PointerState(id, mapOf(AXIS_X to x, AXIS_Y to y, AXIS_PRESSURE to pressure))

    private val touches
        get() = rig.connection.touches

    private fun touch(
        controller: Int = 2,
        type: Int,
        id: Int = 0,
        x: Float = 0.25f,
        y: Float = 0.25f,
        pressure: Float = 0.5f,
    ) =
        Touch(controller, type, id, x, y, pressure)

    // Touch types, as the host library numbers them
    private val down = 1
    private val up = 2
    private val move = 3
    private val cancel = 4
    private val cancelAll = 7

    private val unsupported = EventsConnection.UNSUPPORTED

    // ----- Which events it takes -----

    @Test
    fun `an event of a source that is not a touchpad or a mouse is left to the system`() {
        for (source in listOf(SOURCE_JOYSTICK, SOURCE_TOUCHSCREEN, SOURCE_KEYBOARD, 0)) {
            assertWithMessage("source $source")
                .that(pad.touch(ACTION_DOWN, listOf(finger()), source = source))
                .isFalse()
        }

        assertThat(touches).isEmpty()
        assertThat(rig.packets).isEmpty()
    }

    @Test
    fun `the source has to be exactly a touchpad or a mouse, not a combination`() {
        assertThat(pad.touch(ACTION_DOWN, listOf(finger()), source = SOURCE_TOUCHPAD or SOURCE_KEYBOARD)).isFalse()
        assertThat(pad.touch(ACTION_DOWN, listOf(finger()), source = SOURCE_MOUSE or SOURCE_KEYBOARD)).isFalse()

        assertThat(touches).isEmpty()
    }

    @Test
    fun `an event of a device with no context is left to the system and makes none`() {
        val stranger = Pad(rig, 99, null, pad.context)

        assertThat(stranger.touch(ACTION_DOWN, listOf(finger()))).isFalse()
        assertThat(stranger.touch(ACTION_DOWN, listOf(finger()), source = SOURCE_MOUSE)).isFalse()

        assertThat(touches).isEmpty()
        assertThat(rig.packets).isEmpty()
    }

    @Test
    fun `an event of device id 0 is left to the system, though the default context is a pad`() {
        assertThat(rig.default.touch(ACTION_DOWN, listOf(finger()))).isFalse()

        assertThat(touches).isEmpty()
    }

    @Test
    fun `while captured the touchpad of a pad is taken and sends nothing`() {
        rig.handler.setCaptured(true)
        touches.clear()
        rig.connection.packets.clear()

        assertThat(pad.touch(ACTION_DOWN, listOf(finger()))).isTrue()
        assertThat(pad.touch(ACTION_MOVE, listOf(finger()))).isTrue()
        assertThat(pad.touch(ACTION_DOWN, listOf(finger()), source = SOURCE_MOUSE)).isTrue()
        assertThat(pad.touch(ACTION_BUTTON_PRESS, listOf(finger()), actionButton = BUTTON_PRIMARY)).isTrue()

        assertThat(touches).isEmpty()
        assertThat(rig.packets).isEmpty()
    }

    // ----- The types of touch -----

    @Test
    fun `a first finger down is a touch down for that finger`() {
        assertThat(pad.touch(ACTION_DOWN, listOf(finger(id = 3)))).isTrue()

        assertThat(touches).containsExactly(touch(type = down, id = 3))
    }

    @Test
    fun `a first finger up is a touch up`() {
        assertThat(pad.touch(ACTION_UP, listOf(finger(id = 3)))).isTrue()

        assertThat(touches).containsExactly(touch(type = up, id = 3))
    }

    @Test
    fun `a finger down and up after the first are for the finger the action points at`() {
        val fingers = listOf(finger(id = 4, x = 100f), finger(id = 9, x = 500f, y = 250f, pressure = 2f))

        assertThat(pad.touch(ACTION_POINTER_DOWN, fingers, index = 1)).isTrue()
        assertThat(pad.touch(ACTION_POINTER_UP, fingers, index = 0)).isTrue()

        assertThat(touches)
            .containsExactly(
                touch(type = down, id = 9, x = 0.5f, y = 0.5f, pressure = 1f),
                touch(type = up, id = 4, x = 0.1f, y = 0.25f, pressure = 0.5f),
            )
            .inOrder()
    }

    @Test
    fun `an up with the canceled flag is a cancel of that finger, for the first finger and for the others`() {
        val fingers = listOf(finger(id = 4), finger(id = 9))

        assertThat(pad.touch(ACTION_UP, listOf(finger(id = 3)), flags = FLAG_CANCELED)).isTrue()
        assertThat(pad.touch(ACTION_POINTER_UP, fingers, index = 1, flags = FLAG_CANCELED)).isTrue()

        assertThat(touches).containsExactly(touch(type = cancel, id = 3), touch(type = cancel, id = 9)).inOrder()
    }

    @Test
    fun `the canceled flag changes nothing for a finger down`() {
        pad.touch(ACTION_DOWN, listOf(finger()), flags = FLAG_CANCELED)

        assertThat(touches).containsExactly(touch(type = down))
    }

    @Test
    fun `a move sends every finger, in the order of the event`() {
        val fingers =
            listOf(
                finger(id = 4, x = 100f),
                finger(id = 9, x = 500f, y = 250f),
                finger(id = 2, x = 1000f, y = 500f),
            )

        assertThat(pad.touch(ACTION_MOVE, fingers)).isTrue()

        assertThat(touches)
            .containsExactly(
                touch(type = move, id = 4, x = 0.1f, y = 0.25f),
                touch(type = move, id = 9, x = 0.5f, y = 0.5f),
                touch(type = move, id = 2, x = 1f, y = 1f),
            )
            .inOrder()
    }

    @Test
    fun `a cancel of the gesture is a cancel of all fingers, with no coordinates`() {
        assertThat(pad.touch(ACTION_CANCEL, listOf(finger(id = 5), finger(id = 6)))).isTrue()

        assertThat(touches).containsExactly(Touch(2, cancelAll, 0, 0f, 0f, 0f))
    }

    @Test
    fun `an action that is not a touch is left to the system`() {
        assertThat(pad.touch(ACTION_HOVER_MOVE, listOf(finger()))).isFalse()

        assertThat(touches).isEmpty()
        assertThat(rig.packets).isEmpty()
    }

    @Test
    fun `a touch goes to the controller number of the pad, as a byte`() {
        val third = touchpadPad(deviceId = 8).also { it.set("controllerNumber", 5.toShort()) }

        third.touch(ACTION_DOWN, listOf(finger()))

        assertThat(touches).containsExactly(touch(controller = 5, type = down))
    }

    // ----- Coordinates and pressure -----

    @Test
    fun `a coordinate is its place in the range of the touchpad, from 0 to 1`() {
        pad.touch(ACTION_DOWN, listOf(finger(x = 0f, y = 0f, pressure = 0f)))
        pad.touch(ACTION_DOWN, listOf(finger(x = 1000f, y = 500f, pressure = 2f)))
        pad.touch(ACTION_DOWN, listOf(finger(x = 600f, y = 100f, pressure = 0.5f)))

        assertThat(touches.map { Triple(it.x, it.y, it.pressure) })
            .containsExactly(Triple(0f, 0f, 0f), Triple(1f, 1f, 1f), Triple(0.6f, 0.2f, 0.25f))
            .inOrder()
    }

    @Test
    fun `a value outside the range is held at its edge`() {
        pad.touch(ACTION_DOWN, listOf(finger(x = -50f, y = 900f, pressure = 7f)))
        pad.touch(ACTION_DOWN, listOf(finger(x = 5000f, y = -1f, pressure = -3f)))

        assertThat(touches.map { Triple(it.x, it.y, it.pressure) })
            .containsExactly(Triple(0f, 1f, 1f), Triple(1f, 0f, 0f))
            .inOrder()
    }

    @Test
    fun `a range that does not start at zero is counted from its minimum`() {
        val centered =
            rig.addPad(
                8,
                controllerNumber = 2,
                fields =
                    mapOf(
                        "touchpadXRange" to motionRange(AXIS_X, -500f, 500f),
                        "touchpadYRange" to motionRange(AXIS_Y, 100f, 300f),
                    ),
            )

        centered.touch(ACTION_DOWN, listOf(finger(x = 0f, y = 200f)))
        centered.touch(ACTION_DOWN, listOf(finger(x = -500f, y = 100f)))
        centered.touch(ACTION_DOWN, listOf(finger(x = 250f, y = 400f)))

        assertThat(touches.map { it.x to it.y })
            .containsExactly(0.5f to 0.5f, 0f to 0f, 0.75f to 1f)
            .inOrder()
    }

    @Test
    fun `a pad with no pressure range reports a pressure of 0`() {
        val flat = touchpadPad(deviceId = 8, extra = mapOf("touchpadPressureRange" to null))

        flat.touch(ACTION_DOWN, listOf(finger(pressure = 1f)))

        assertThat(touches).containsExactly(touch(type = down, pressure = 0f))
    }

    @Test
    fun `a range with no width makes the coordinate not a number`() {
        val empty =
            touchpadPad(deviceId = 8, extra = mapOf("touchpadXRange" to motionRange(AXIS_X, 5f, 5f)))

        empty.touch(ACTION_DOWN, listOf(finger(x = 5f)))

        assertThat(touches.single().x).isNaN()
        assertThat(touches.single().y).isEqualTo(0.25f)
    }

    // ----- The host without controller touch -----

    @Test
    fun `a down or up the host does not support is left to the system`() {
        rig.connection.touchResult = unsupported

        assertThat(pad.touch(ACTION_DOWN, listOf(finger()))).isFalse()
        assertThat(pad.touch(ACTION_UP, listOf(finger()))).isFalse()
        assertThat(pad.touch(ACTION_POINTER_DOWN, listOf(finger(), finger(id = 1)), index = 1)).isFalse()
        assertThat(pad.touch(ACTION_UP, listOf(finger()), flags = FLAG_CANCELED)).isFalse()
    }

    @Test
    fun `a move the host does not support stops at the first finger`() {
        rig.connection.touchResult = unsupported

        assertThat(pad.touch(ACTION_MOVE, listOf(finger(id = 1), finger(id = 2), finger(id = 3)))).isFalse()

        assertThat(touches).hasSize(1)
    }

    @Test
    fun `a cancel of all fingers the host does not support is left to the system`() {
        rig.connection.touchResult = unsupported

        assertThat(pad.touch(ACTION_CANCEL, listOf(finger()))).isFalse()

        assertThat(touches).hasSize(1)
    }

    @Test
    fun `any other answer of the host, an error included, counts as taken`() {
        for (answer in listOf(0, 1, -1, unsupported + 1)) {
            rig.connection.touchResult = answer

            assertWithMessage("answer $answer").that(pad.touch(ACTION_DOWN, listOf(finger()))).isTrue()
            assertWithMessage("answer $answer").that(pad.touch(ACTION_MOVE, listOf(finger()))).isTrue()
            assertWithMessage("answer $answer").that(pad.touch(ACTION_CANCEL, listOf(finger()))).isTrue()
        }
    }

    // ----- The touchpad as a mouse -----

    @Test
    fun `when the touchpad controls the mouse no touch is sent, and the system gets the event`() {
        val mouse = EventsRig { gamepadTouchpadAsMouse = true }
        val mousePad =
            mouse.addPad(
                7,
                controllerNumber = 2,
                fields = mapOf("touchpadXRange" to xRange, "touchpadYRange" to yRange),
            )

        val actions =
            listOf(
                ACTION_DOWN,
                ACTION_UP,
                ACTION_MOVE,
                ACTION_POINTER_DOWN,
                ACTION_POINTER_UP,
                ACTION_CANCEL,
            )
        for (action in actions) {
            assertWithMessage("action $action").that(mousePad.touch(action, listOf(finger()))).isFalse()
        }

        assertThat(mouse.connection.touches).isEmpty()
        assertThat(mouse.packets).isEmpty()
    }

    @Test
    fun `a pad with no ranges has no touches to send`() {
        val none = rig.addPad(8, controllerNumber = 2)

        assertThat(none.touch(ACTION_DOWN, listOf(finger()))).isFalse()
        assertThat(none.touch(ACTION_MOVE, listOf(finger()))).isFalse()
        assertThat(none.touch(ACTION_CANCEL, listOf(finger()))).isFalse()

        assertThat(touches).isEmpty()
    }

    @Test
    fun `a pad with only one of the two ranges has none either`() {
        val xOnly = rig.addPad(8, controllerNumber = 2, fields = mapOf("touchpadXRange" to xRange))
        val yOnly = rig.addPad(9, controllerNumber = 2, fields = mapOf("touchpadYRange" to yRange))

        assertThat(xOnly.touch(ACTION_DOWN, listOf(finger()))).isFalse()
        assertThat(yOnly.touch(ACTION_DOWN, listOf(finger()))).isFalse()

        assertThat(touches).isEmpty()
    }

    // ----- The clickpad button of a touchpad -----

    @Test
    fun `the primary button of the touchpad presses the touchpad button of the pad`() {
        assertThat(pad.touch(ACTION_BUTTON_PRESS, listOf(finger()), actionButton = BUTTON_PRIMARY)).isTrue()

        assertThat(rig.packets).containsExactly(Sent(2, 1, TOUCHPAD_FLAG))
        assertThat(touches).isEmpty()
    }

    @Test
    fun `and its release lets the touchpad button go`() {
        pad.touch(ACTION_BUTTON_PRESS, listOf(finger()), actionButton = BUTTON_PRIMARY)

        assertThat(pad.touch(ACTION_BUTTON_RELEASE, listOf(finger()), actionButton = BUTTON_PRIMARY)).isTrue()

        assertThat(rig.buttons()).containsExactly(TOUCHPAD_FLAG, 0).inOrder()
    }

    @Test
    fun `another button of the touchpad is left to the system`() {
        assertThat(pad.touch(ACTION_BUTTON_PRESS, listOf(finger()), actionButton = BUTTON_SECONDARY)).isFalse()
        assertThat(pad.touch(ACTION_BUTTON_RELEASE, listOf(finger()), actionButton = BUTTON_SECONDARY)).isFalse()
        assertThat(pad.touch(ACTION_BUTTON_PRESS, listOf(finger()), actionButton = 0)).isFalse()

        assertThat(rig.packets).isEmpty()
    }

    @Test
    fun `the touchpad button works when the touchpad controls the mouse, and the event goes on to the system`() {
        val mouse = EventsRig { gamepadTouchpadAsMouse = true }
        val mousePad = mouse.addPad(7, controllerNumber = 2)

        assertThat(mousePad.touch(ACTION_BUTTON_PRESS, listOf(finger()), actionButton = BUTTON_PRIMARY)).isFalse()
        assertThat(mousePad.touch(ACTION_BUTTON_RELEASE, listOf(finger()), actionButton = BUTTON_PRIMARY)).isFalse()

        assertThat(mouse.packets.map { it.buttons }).containsExactly(TOUCHPAD_FLAG, 0).inOrder()
    }

    @Test
    fun `the touchpad button is added to the buttons the pad holds`() {
        val held = rig.addPad(8, controllerNumber = 2, fields = mapOf("inputMap" to 0x1000))

        held.touch(ACTION_BUTTON_PRESS, listOf(finger()), actionButton = BUTTON_PRIMARY)
        held.touch(ACTION_BUTTON_RELEASE, listOf(finger()), actionButton = BUTTON_PRIMARY)

        assertThat(rig.buttons()).containsExactly(0x1000 or TOUCHPAD_FLAG, 0x1000).inOrder()
    }

    // ----- A touchpad that is a mouse source -----

    private fun mouseSource(action: Int, actionButton: Int = 0, pad: Pad = this.pad) =
        pad.touch(action, listOf(finger()), source = SOURCE_MOUSE, actionButton = actionButton)

    @Test
    fun `a mouse source presses the touchpad button when its left button goes down`() {
        assertThat(mouseSource(ACTION_DOWN)).isTrue()

        assertThat(rig.packets).containsExactly(Sent(2, 1, TOUCHPAD_FLAG))
        assertThat(touches).isEmpty()
    }

    @Test
    fun `a mouse source lets the touchpad button go when the left button goes up or is canceled`() {
        for (action in listOf(ACTION_UP, ACTION_CANCEL)) {
            rig.connection.packets.clear()
            mouseSource(ACTION_DOWN)

            assertWithMessage("action $action").that(mouseSource(action)).isTrue()

            assertWithMessage("action $action").that(rig.buttons()).containsExactly(TOUCHPAD_FLAG, 0).inOrder()
        }
    }

    @Test
    fun `any other action of a mouse source sends nothing and is still answered`() {
        val others =
            listOf(
                ACTION_MOVE,
                ACTION_HOVER_MOVE,
                ACTION_POINTER_DOWN,
                ACTION_BUTTON_PRESS,
                ACTION_BUTTON_RELEASE,
            )
        for (action in others) {
            assertWithMessage("action $action").that(mouseSource(action, BUTTON_PRIMARY)).isTrue()
        }

        assertThat(rig.packets).isEmpty()
        assertThat(touches).isEmpty()
    }

    @Test
    fun `a mouse source is left to the system when the touchpad controls the mouse`() {
        val mouse = EventsRig { gamepadTouchpadAsMouse = true }
        val mousePad = mouse.addPad(7, controllerNumber = 2)

        assertThat(mouseSource(ACTION_DOWN, pad = mousePad)).isFalse()
        assertThat(mouseSource(ACTION_MOVE, pad = mousePad)).isFalse()
        assertThat(mouseSource(ACTION_UP, pad = mousePad)).isFalse()

        // The button still goes to the pad; only the answer changes.
        assertThat(mouse.packets.map { it.buttons }).containsExactly(TOUCHPAD_FLAG, 0).inOrder()
    }

    @Test
    fun `a mouse source needs no ranges, since it only has the button`() {
        val none = rig.addPad(8, controllerNumber = 2)

        assertThat(mouseSource(ACTION_DOWN, pad = none)).isTrue()

        assertThat(rig.packets).containsExactly(Sent(2, 1, TOUCHPAD_FLAG))
    }
}
