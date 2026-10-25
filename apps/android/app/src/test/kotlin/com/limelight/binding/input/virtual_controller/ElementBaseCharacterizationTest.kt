package com.limelight.binding.input.virtual_controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.virtual_controller.VirtualController.ControllerMode
import io.github.magalz.borealis.settings.ShadowMoonBridge
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** An element that does nothing of its own: it notes what the base class hands on, and draws a mark. */
class ProbeElement(
    controller: VirtualController,
    context: Context,
    id: Int = ID,
    private val handles: Boolean = true,
) : VirtualControllerElement(controller, context, id) {
    /** The actions that reached the element, masked, in order. */
    val delivered = mutableListOf<Int>()

    override fun onElementDraw(canvas: Canvas) {
        canvas.drawColor(MARK)
    }

    override fun onElementTouchEvent(event: MotionEvent): Boolean {
        delivered += event.actionMasked
        return handles
    }

    companion object {
        const val ID = 77
        const val MARK = 0x01020304
    }
}

/**
 * What every on-screen control has in common, through an element that adds nothing: where a touch
 * goes in each mode of the controller, how the move and resize modes change the place and the size,
 * the colours, the opacity and the place that is saved.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowMoonBridge::class])
class ElementBaseCharacterizationTest {
    private val kit = ElementRig()
    private val controller = kit.controller
    private val element = kit.add(ProbeElement(controller, kit.rig.activity), 100, 50, 200, 100)

    private fun place(): List<Int> =
        with(element.getConfiguration()) {
            listOf(getInt("LEFT"), getInt("TOP"), getInt("WIDTH"), getInt("HEIGHT"))
        }

    private fun margins(): List<Int> =
        (element.layoutParams as FrameLayout.LayoutParams).let {
            listOf(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin)
        }

    private fun move(x: Float, y: Float) = element.touch(MotionEvent.ACTION_MOVE, x, y)

    private fun down(x: Float, y: Float) = element.touch(MotionEvent.ACTION_DOWN, x, y)

    private fun up(action: Int = MotionEvent.ACTION_UP) = element.touch(action, 0f, 0f)

    // The active mode

    @Test
    fun `an active controller hands every touch to the element and answers what it answers`() {
        val actions =
            listOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_OUTSIDE,
            )

        actions.forEach { assertThat(element.touch(it, 5f, 5f)).isTrue() }

        assertThat(element.delivered).containsExactlyElementsIn(actions).inOrder()
    }

    @Test
    fun `an active controller answers false when the element does`() {
        val refusing = kit.add(ProbeElement(controller, kit.rig.activity, handles = false), 400, 50, 200, 100)

        assertThat(refusing.touch(MotionEvent.ACTION_DOWN, 5f, 5f)).isFalse()
        assertThat(refusing.delivered).containsExactly(MotionEvent.ACTION_DOWN)
    }

    @Test
    fun `a touch by a second finger is ignored and answered as handled`() {
        val down = twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1, 5f, 5f, 9f, 9f)
        val up = twoFingers(MotionEvent.ACTION_POINTER_UP, 1, 5f, 5f, 9f, 9f)

        assertThat(element.deliver(down)).isTrue()
        assertThat(element.deliver(up)).isTrue()

        assertThat(element.delivered).isEmpty()
    }

    @Test
    fun `a second finger that is ignored does not even answer false for an element that would`() {
        val refusing = kit.add(ProbeElement(controller, kit.rig.activity, handles = false), 400, 50, 200, 100)

        assertThat(refusing.deliver(twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1, 5f, 5f, 9f, 9f))).isTrue()
    }

    @Test
    fun `the lift of the first finger while another stays goes to the element`() {
        // The action index of this event is 0: it is the first finger that goes up.
        val liftOfFirst = twoFingers(MotionEvent.ACTION_POINTER_UP, 0, 5f, 5f, 9f, 9f)

        assertThat(element.deliver(liftOfFirst)).isTrue()

        assertThat(element.delivered).containsExactly(MotionEvent.ACTION_POINTER_UP)
    }

    @Test
    fun `a second finger changes nothing in the configuration modes either`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)

        assertThat(element.deliver(twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1, 30f, 30f, 90f, 90f))).isTrue()
        assertThat(element.deliver(twoFingers(MotionEvent.ACTION_POINTER_UP, 1, 30f, 30f, 90f, 90f))).isTrue()
        move(30f, 30f)

        // The pointer events did not end the move: the move that follows still moves.
        assertThat(place()).isEqualTo(listOf(120, 70, 200, 100))
    }

    @Test
    fun `the movement of two fingers is read from the first`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)

        element.deliver(twoFingers(MotionEvent.ACTION_MOVE, 0, 30f, 40f, 500f, 500f))

        assertThat(place()).isEqualTo(listOf(120, 80, 200, 100))
    }

    // The configuration modes: nothing reaches the element

    @Test
    fun `in the configuration modes the element is not told of any touch and every touch is handled`() {
        listOf(ControllerMode.MoveButtons, ControllerMode.ResizeButtons).forEach { mode ->
            kit.mode(mode)
            listOf(
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_OUTSIDE,
                )
                .forEach { assertThat(element.touch(it, 5f, 5f)).isTrue() }
        }

        assertThat(element.delivered).isEmpty()
    }

    // The move mode

    @Test
    fun `moving carries the element by the way the finger went, and clears the margins on the other sides`() {
        kit.mode(ControllerMode.MoveButtons)
        (element.layoutParams as FrameLayout.LayoutParams).apply {
            rightMargin = 7
            bottomMargin = 9
        }

        assertThat(down(10f, 10f)).isTrue()
        assertThat(place()).isEqualTo(listOf(100, 50, 200, 100)) // pressing moves nothing
        assertThat(move(30f, 45f)).isTrue()

        assertThat(place()).isEqualTo(listOf(120, 85, 200, 100))
        assertThat(margins()).isEqualTo(listOf(120, 85, 0, 0))
    }

    @Test
    fun `moving asks for a layout`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)
        assertThat(element.isLayoutRequested).isFalse()

        move(30f, 45f)

        assertThat(element.isLayoutRequested).isTrue()
    }

    @Test
    fun `moving never puts the element over the left or the top edge`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)

        move(-500f, -500f)

        assertThat(margins()).isEqualTo(listOf(0, 0, 0, 0))
    }

    @Test
    fun `moving to the edge itself is the edge, and one pixel in is one pixel in`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)

        move(-90f, -40f) // 100 + (-90) - 10 = 0 and 50 + (-40) - 10 = 0
        assertThat(margins()).isEqualTo(listOf(0, 0, 0, 0))

        move(-89f, -39f)
        assertThat(margins()).isEqualTo(listOf(1, 1, 0, 0))
    }

    @Test
    fun `moving counts a finger in whole pixels, cutting off the fraction toward zero`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10.9f, 10.9f) // 10

        move(30.2f, 45.9f) // 30 and 45

        assertThat(margins()).isEqualTo(listOf(120, 85, 0, 0))
    }

    @Test
    fun `a finger a little left of the element counts as the edge of it, not a pixel beyond`() {
        kit.mode(ControllerMode.MoveButtons)
        down(0.4f, 0.4f) // 0

        move(-0.5f, -0.5f) // truncated to 0, where flooring would give -1

        assertThat(margins()).isEqualTo(listOf(100, 50, 0, 0))
    }

    @Test
    fun `moving starts from the place the element was laid out at, not from its margins`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)

        move(30f, 30f)
        move(50f, 50f) // no layout in between: the element still stands at 100, 50

        assertThat(margins()).isEqualTo(listOf(140, 90, 0, 0))
    }

    @Test
    fun `moving again after a layout carries on from the new place`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)
        move(30f, 30f)
        kit.rig.layOut()

        move(30f, 30f) // the finger has not moved since the layout

        assertThat(margins()).isEqualTo(listOf(140, 90, 0, 0))
    }

    @Test
    fun `a move without a press first does nothing`() {
        kit.mode(ControllerMode.MoveButtons)

        assertThat(move(30f, 45f)).isTrue()

        assertThat(place()).isEqualTo(listOf(100, 50, 200, 100))
        assertThat(element.isLayoutRequested).isFalse()
    }

    @Test
    fun `lifting the finger ends the move, and so does cancelling`() {
        kit.mode(ControllerMode.MoveButtons)
        listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL).forEach { action ->
            down(10f, 10f)
            assertThat(up(action)).isTrue()

            move(30f, 45f)

            assertThat(place()).isEqualTo(listOf(100, 50, 200, 100))
        }
    }

    @Test
    fun `a new press after the lift starts a new move from where the finger is`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)
        up()
        down(20f, 20f)

        move(25f, 30f)

        assertThat(place()).isEqualTo(listOf(105, 60, 200, 100))
    }

    @Test
    fun `an action that is not a press, a move or an end leaves the move as it is`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)

        assertThat(element.touch(MotionEvent.ACTION_OUTSIDE, 90f, 90f)).isTrue()
        move(30f, 45f)

        assertThat(place()).isEqualTo(listOf(120, 85, 200, 100))
    }

    @Test
    fun `moving an element does not move the others`() {
        val other = kit.add(ProbeElement(controller, kit.rig.activity, 78), 400, 300, 50, 60)
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)

        move(30f, 45f)

        assertThat(other.getConfiguration().getInt("LEFT")).isEqualTo(400)
        assertThat(other.getConfiguration().getInt("TOP")).isEqualTo(300)
    }

    // The resize mode

    @Test
    fun `resizing makes the element as much bigger as the finger went`() {
        kit.mode(ControllerMode.ResizeButtons)

        down(10f, 20f)
        assertThat(place()).isEqualTo(listOf(100, 50, 200, 100))
        move(30f, 5f)

        // width 30 + (200 - 10), height 5 + (100 - 20); the place stays
        assertThat(place()).isEqualTo(listOf(100, 50, 220, 85))
    }

    @Test
    fun `resizing with a finger that does not move leaves the size`() {
        kit.mode(ControllerMode.ResizeButtons)
        down(10f, 20f)

        move(10f, 20f)

        assertThat(place()).isEqualTo(listOf(100, 50, 200, 100))
    }

    @Test
    fun `resizing asks for a layout`() {
        kit.mode(ControllerMode.ResizeButtons)
        down(10f, 20f)
        assertThat(element.isLayoutRequested).isFalse()

        move(30f, 5f)

        assertThat(element.isLayoutRequested).isTrue()
    }

    @Test
    fun `resizing never makes the element smaller than 20 pixels either way`() {
        kit.mode(ControllerMode.ResizeButtons)
        down(10f, 20f)

        move(-500f, -500f)
        assertThat(place()).isEqualTo(listOf(100, 50, 20, 20))

        move(-170f, -60f) // width -170 + 190 = 20 and height -60 + 80 = 20 exactly
        assertThat(place()).isEqualTo(listOf(100, 50, 20, 20))

        move(-169f, -59f)
        assertThat(place()).isEqualTo(listOf(100, 50, 21, 21))
    }

    @Test
    fun `resizing counts a finger in whole pixels, cutting off the fraction`() {
        kit.mode(ControllerMode.ResizeButtons)
        down(10.9f, 20.9f) // 10 and 20

        move(30.2f, 5.9f) // 30 and 5

        assertThat(place()).isEqualTo(listOf(100, 50, 220, 85))
    }

    @Test
    fun `resizing measures from the size the element had at the press`() {
        kit.mode(ControllerMode.ResizeButtons)
        down(10f, 20f)
        move(30f, 5f)
        kit.rig.layOut()

        down(30f, 20f) // a new press: the element is 220 by 85 now

        move(40f, 20f)

        assertThat(place()).isEqualTo(listOf(100, 50, 230, 85))
    }

    @Test
    fun `a resize without a press first does nothing`() {
        kit.mode(ControllerMode.ResizeButtons)

        move(30f, 5f)

        assertThat(place()).isEqualTo(listOf(100, 50, 200, 100))
    }

    @Test
    fun `lifting the finger ends the resize, and so does cancelling`() {
        kit.mode(ControllerMode.ResizeButtons)
        listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL).forEach { action ->
            down(10f, 20f)
            assertThat(up(action)).isTrue()

            move(30f, 5f)

            assertThat(place()).isEqualTo(listOf(100, 50, 200, 100))
        }
    }

    @Test
    fun `lifting or cancelling in a configuration mode redraws the element, which drops its frame`() {
        kit.mode(ControllerMode.MoveButtons)

        listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL).forEach { action ->
            down(10f, 10f)
            shadowOf(element).clearWasInvalidated()

            up(action)

            assertThat(shadowOf(element).wasInvalidated()).isTrue()
        }
    }

    // The mark of a selected element

    private fun marks(): List<Drawn> = draw(element).calls

    private val selection
        get() =
            Drawn(
                "rect",
                listOf(4f, 4f, 196f, 96f),
                PaintState(
                    color = 0xF000FF00.toInt(),
                    style = Paint.Style.STROKE,
                    strokeWidth = 4f,
                    textSize = 12f,
                    textAlign = Paint.Align.LEFT,
                    flags = plainPaintFlags(),
                ),
            )

    @Test
    fun `an element that nobody touched draws only itself`() {
        assertThat(marks().map { it.call }).containsExactly("color")
        kit.mode(ControllerMode.MoveButtons)
        assertThat(marks().map { it.call }).containsExactly("color")
    }

    @Test
    fun `a pressed element in the move mode draws its mark, then the green frame around its edge`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)

        val mark = Drawn("color", emptyList(), null, color = ProbeElement.MARK)
        assertThat(marks()).containsExactly(mark, selection).inOrder()
    }

    @Test
    fun `a pressed element in the resize mode draws the same frame`() {
        kit.mode(ControllerMode.ResizeButtons)
        down(10f, 10f)

        assertThat(marks().last()).isEqualTo(selection)
        assertThat(marks()).hasSize(2)
    }

    @Test
    fun `the frame is gone when the finger lifts or is cancelled`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)
        up()
        assertThat(marks()).hasSize(1)

        down(10f, 10f)
        up(MotionEvent.ACTION_CANCEL)
        assertThat(marks()).hasSize(1)
    }

    @Test
    fun `the frame is as thick as 4 tenths of a percent of the screen height, cut to whole pixels`() {
        // 1200 * 0.004 = 4.8 would round to 5
        val tall = ElementRig(Screen(1920, 1200))
        val probe = tall.add(ProbeElement(tall.controller, tall.rig.activity), 10, 10, 100, 80)
        tall.mode(ControllerMode.MoveButtons)
        probe.touch(MotionEvent.ACTION_DOWN, 1f, 1f)

        val frame = draw(probe).calls.last()

        assertThat(frame.paint?.strokeWidth).isEqualTo(4f)
        assertThat(frame.numbers).containsExactly(4f, 4f, 96f, 76f).inOrder()
    }

    @Test
    fun `the frame follows the size the element has`() {
        element.loadConfiguration(JSONObject("""{"LEFT":0,"TOP":0,"WIDTH":300,"HEIGHT":120}"""))
        kit.rig.layOut()
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)

        assertThat(marks().last().numbers).containsExactly(4f, 4f, 296f, 116f).inOrder()
    }

    // Colours

    private fun colours() = element.defaultColor to element.pressedColor

    @Test
    fun `an element is grey and blue, nearly opaque, until it is given an opacity`() {
        assertThat(colours()).isEqualTo(0xF0888888.toInt() to 0xF00000FF.toInt())
    }

    @Test
    fun `the element is red in the move mode and magenta in the resize mode, and the blue stays`() {
        kit.mode(ControllerMode.MoveButtons)
        assertThat(colours()).isEqualTo(0xF0FF0000.toInt() to 0xF00000FF.toInt())

        kit.mode(ControllerMode.ResizeButtons)
        assertThat(colours()).isEqualTo(0xF0FF00FF.toInt() to 0xF00000FF.toInt())

        kit.mode(ControllerMode.Active)
        assertThat(colours()).isEqualTo(0xF0888888.toInt() to 0xF00000FF.toInt())
    }

    @Test
    fun `the opacity is its share of 255 in the top byte, cut to a whole number`() {
        val alphas = listOf(0, 1, 50, 51, 90, 100)

        val colours =
            alphas.map {
                element.setOpacity(it)
                colours()
            }

        assertThat(colours)
            .containsExactly(
                0x00888888 to 0x000000FF, // 0
                0x02888888 to 0x020000FF, // 255 / 100 = 2, not 3
                0x7F888888 to 0x7F0000FF, // 127
                0x82888888.toInt() to 0x820000FF.toInt(), // 51 * 255 / 100 = 130, a negative int
                0xE5888888.toInt() to 0xE50000FF.toInt(), // 229
                0xFF888888.toInt() to 0xFF0000FF.toInt(),
            )
            .inOrder()
    }

    @Test
    fun `an opacity above 100 wraps out of the top byte, and one below 0 counts from below`() {
        element.setOpacity(101) // 257 shl 24 loses its top bit

        assertThat(colours()).isEqualTo(0x01888888 to 0x010000FF)

        element.setOpacity(-1) // -255 / 100 = -2

        assertThat(colours()).isEqualTo(0xFE888888.toInt() to 0xFE0000FF.toInt())
    }

    @Test
    fun `the opacity leaves the colours of the configuration modes as they are`() {
        element.setOpacity(10)

        kit.mode(ControllerMode.MoveButtons)
        assertThat(element.defaultColor).isEqualTo(0xF0FF0000.toInt())
        kit.mode(ControllerMode.ResizeButtons)
        assertThat(element.defaultColor).isEqualTo(0xF0FF00FF.toInt())
        kit.mode(ControllerMode.Active)
        assertThat(element.defaultColor).isEqualTo(0x19888888)
    }

    @Test
    fun `the opacity redraws the element`() {
        shadowOf(element).clearWasInvalidated()

        element.setOpacity(50)

        assertThat(shadowOf(element).wasInvalidated()).isTrue()
    }

    // The place that is saved

    @Test
    fun `the configuration holds the four numbers of the place the layout gave`() {
        val configuration = element.getConfiguration()

        assertThat(configuration.keys().asSequence().toSet()).containsExactly("LEFT", "TOP", "WIDTH", "HEIGHT")
        assertThat(place()).isEqualTo(listOf(100, 50, 200, 100))
    }

    @Test
    fun `the configuration follows a move and a resize`() {
        kit.mode(ControllerMode.MoveButtons)
        down(10f, 10f)
        move(30f, 45f)
        kit.mode(ControllerMode.ResizeButtons)
        up()
        down(10f, 20f)
        move(30f, 5f)

        assertThat(place()).isEqualTo(listOf(120, 85, 220, 85))
    }

    @Test
    fun `loading a configuration sets the four numbers and asks for a layout`() {
        element.loadConfiguration(JSONObject("""{"LEFT":11,"TOP":22,"WIDTH":33,"HEIGHT":44}"""))

        assertThat(place()).isEqualTo(listOf(11, 22, 33, 44))
        assertThat(margins()).isEqualTo(listOf(11, 22, 0, 0))
        assertThat(element.isLayoutRequested).isTrue()
    }

    @Test
    fun `loading a configuration that misses a number fails after the numbers before it were set`() {
        val partial = JSONObject("""{"LEFT":11,"TOP":22,"WIDTH":33}""")

        assertThrows(JSONException::class.java) { element.loadConfiguration(partial) }

        assertThat(place()).isEqualTo(listOf(11, 22, 33, 100))
    }

    // An element that is not in a layout yet

    private fun unplaced() = ProbeElement(controller, kit.rig.activity, 79)

    @Test
    fun `an element that is not in a layout cannot give or take a configuration`() {
        val loose = unplaced()

        assertThrows(NullPointerException::class.java) { loose.getConfiguration() }
        assertThrows(NullPointerException::class.java) {
            loose.loadConfiguration(JSONObject("""{"LEFT":1,"TOP":2,"WIDTH":3,"HEIGHT":4}"""))
        }
    }

    @Test
    fun `an element that is not in a layout fails where it would move or resize`() {
        val loose = unplaced()
        kit.mode(ControllerMode.MoveButtons)
        loose.touch(MotionEvent.ACTION_DOWN, 1f, 1f)
        assertThrows(NullPointerException::class.java) { loose.touch(MotionEvent.ACTION_MOVE, 5f, 5f) }

        kit.mode(ControllerMode.ResizeButtons)
        loose.touch(MotionEvent.ACTION_UP, 1f, 1f)
        loose.touch(MotionEvent.ACTION_DOWN, 1f, 1f)
        assertThrows(NullPointerException::class.java) { loose.touch(MotionEvent.ACTION_MOVE, 5f, 5f) }
    }

    @Test
    fun `an element with layout parameters of another kind fails the same places`() {
        val odd = unplaced()
        odd.layoutParams = ViewGroup.LayoutParams(10, 10)

        assertThrows(ClassCastException::class.java) { odd.getConfiguration() }
        assertThrows(ClassCastException::class.java) {
            odd.loadConfiguration(JSONObject("""{"LEFT":1,"TOP":2,"WIDTH":3,"HEIGHT":4}"""))
        }
    }

    // The constants

    @Test
    fun `the element ids are 1 to 16 in the order of the layout`() {
        assertThat(
                listOf(
                    VirtualControllerElement.EID_DPAD,
                    VirtualControllerElement.EID_LT,
                    VirtualControllerElement.EID_RT,
                    VirtualControllerElement.EID_LB,
                    VirtualControllerElement.EID_RB,
                    VirtualControllerElement.EID_A,
                    VirtualControllerElement.EID_B,
                    VirtualControllerElement.EID_X,
                    VirtualControllerElement.EID_Y,
                    VirtualControllerElement.EID_BACK,
                    VirtualControllerElement.EID_START,
                    VirtualControllerElement.EID_LS,
                    VirtualControllerElement.EID_RS,
                    VirtualControllerElement.EID_LSB,
                    VirtualControllerElement.EID_RSB,
                    VirtualControllerElement.EID_GDB,
                )
            )
            .containsExactlyElementsIn(1..16)
            .inOrder()
    }
}
