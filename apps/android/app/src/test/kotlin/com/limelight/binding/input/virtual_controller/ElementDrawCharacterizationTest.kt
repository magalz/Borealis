package com.limelight.binding.input.virtual_controller

import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.binding.input.virtual_controller.VirtualController.ControllerMode
import io.github.magalz.borealis.settings.ShadowMoonBridge
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the elements draw: the circles of the stick, the oval and the text of the button, and the
 * eight shapes of the pad, with the numbers and the paint of each call, and some pixels of what the
 * calls make on a bitmap.
 *
 * The colours are the ones an element has before it is given an opacity: grey, blue, and red and
 * magenta in the configuration modes. The stroke is 4 tenths of a percent of the screen height, cut
 * to whole pixels: 4 on the screen of 1080, and 5 on the screen of 1300 that the button uses.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowMoonBridge::class])
class ElementDrawCharacterizationTest {
    private fun paint(
        color: Int,
        stroke: Float = 4f,
        style: Paint.Style = Paint.Style.STROKE,
        textSize: Float = 12f,
        align: Paint.Align = Paint.Align.LEFT,
    ) = PaintState(color, style, stroke, textSize, align, plainPaintFlags())

    private fun transparent() = Drawn("color", emptyList(), null, color = Color.TRANSPARENT)

    // The stick

    private val grey = 0xF0888888.toInt()
    private val blue = 0xF00000FF.toInt()
    private val red = 0xF0FF0000.toInt()
    private val magenta = 0xF0FF00FF.toInt()
    private val green = 0xF000FF00.toInt()

    private val kit by lazy { ElementRig() }
    private val activity by lazy { kit.rig.activity }

    private fun stick(width: Int = 301, height: Int = 301) =
        kit.add(AnalogStick(kit.controller, activity, 12), 100, 100, width, height)

    private fun circle(x: Float, y: Float, radius: Float, color: Int, stroke: Float = 4f) =
        Drawn("circle", listOf(x, y, radius), paint(color, stroke))

    /** The middle of a stick and the radius of each of its three circles. */
    private class StickShape(
        val x: Float = 150f,
        val y: Float = 150f,
        val outer: Float = 142f,
        val dead: Float = 45f,
        val small: Float = 30f,
    )

    /**
     * The three circles of a stick of [shape]; its stick is at [stickAt], which is the middle by
     * default.
     */
    private fun stickCircles(
        outerColor: Int,
        deadColor: Int,
        stickColor: Int,
        shape: StickShape = StickShape(),
        stickAt: Pair<Float, Float> = shape.x to shape.y,
    ) =
        listOf(
            transparent(),
            circle(shape.x, shape.y, shape.outer, outerColor),
            circle(shape.x, shape.y, shape.dead, deadColor),
            circle(stickAt.first, stickAt.second, shape.small, stickColor),
        )

    /** The x, y and radius of the stick circle, which a stick draws last. */
    private fun assertStickAt(stick: AnalogStick, x: Float, y: Float, radius: Float) {
        val circle = draw(stick).calls.last().numbers
        assertThat(circle).comparingElementsUsing(near).containsExactly(x, y, radius).inOrder()
    }

    private fun stickTouch(stick: AnalogStick, action: Int, dx: Float, dy: Float, time: Long) =
        stick.touch(action, 150f + dx, 150f + dy, time)

    @Test
    fun `a stick that nobody touches is three grey circles around its middle, which is half of 301 in whole pixels`() {
        val calls = draw(stick()).calls

        assertThat(calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(stickCircles(grey, grey, grey))
            .inOrder()
    }

    @Test
    fun `a held stick in the middle draws its stick blue`() {
        val stick = stick()
        stickTouch(stick, MotionEvent.ACTION_DOWN, 0f, 0f, 1_000)

        assertThat(draw(stick).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(stickCircles(grey, grey, blue))
            .inOrder()
    }

    @Test
    fun `a stick held after a double click draws its outer circle blue too`() {
        val stick = stick()
        stickTouch(stick, MotionEvent.ACTION_DOWN, 0f, 0f, 1_000)
        stickTouch(stick, MotionEvent.ACTION_UP, 0f, 0f, 1_100)
        stickTouch(stick, MotionEvent.ACTION_DOWN, 0f, 0f, 1_200)

        assertThat(draw(stick).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(stickCircles(blue, grey, blue))
            .inOrder()
    }

    @Test
    fun `a stick that was double clicked and let go is grey again, and a click after the let go is not blue`() {
        val stick = stick()
        stickTouch(stick, MotionEvent.ACTION_DOWN, 0f, 0f, 1_000)
        stickTouch(stick, MotionEvent.ACTION_UP, 0f, 0f, 1_100)
        stickTouch(stick, MotionEvent.ACTION_DOWN, 0f, 0f, 1_200)
        stickTouch(stick, MotionEvent.ACTION_UP, 0f, 0f, 1_300)
        assertThat(draw(stick).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(stickCircles(grey, grey, grey))
            .inOrder()

        stickTouch(
            stick,
            MotionEvent.ACTION_DOWN,
            0f,
            0f,
            3_000,
        ) // a single click, a long time later

        assertThat(draw(stick).calls[1]).isEqualTo(circle(150f, 150f, 142f, grey))
    }

    @Test
    fun `the stick follows the finger, up and right to the corner`() {
        val stick = stick()
        stickTouch(stick, MotionEvent.ACTION_DOWN, 0f, 0f, 1_000)
        stickTouch(stick, MotionEvent.ACTION_MOVE, 60f, -80f, 1_400)

        assertThat(draw(stick).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(stickCircles(grey, grey, blue, stickAt = 210f to 70f))
            .inOrder()
    }

    @Test
    fun `the stick follows the finger in the other three quarters and is held at the end of its way`() {
        val stick = stick()
        stickTouch(stick, MotionEvent.ACTION_DOWN, 0f, 0f, 1_000)

        stickTouch(stick, MotionEvent.ACTION_MOVE, 60f, 80f, 1_400)
        assertStickAt(stick, 210f, 230f, 30f)

        stickTouch(stick, MotionEvent.ACTION_MOVE, -60f, 80f, 1_410)
        assertStickAt(stick, 90f, 230f, 30f)

        stickTouch(stick, MotionEvent.ACTION_MOVE, -60f, -80f, 1_420)
        assertStickAt(stick, 90f, 70f, 30f)

        stickTouch(stick, MotionEvent.ACTION_MOVE, 500f, 0f, 1_430) // held at 112 pixels
        assertStickAt(stick, 262f, 150f, 30f)
    }

    @Test
    fun `a stick pressed in its dead zone draws its stick where the finger is`() {
        val stick = stick()
        stickTouch(stick, MotionEvent.ACTION_DOWN, 30f, 0f, 1_000)

        assertThat(draw(stick).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(stickCircles(grey, grey, blue, stickAt = 180f to 150f))
            .inOrder()
    }

    @Test
    fun `a finger exactly above the middle draws the stick below it`() {
        val stick = stick()
        stickTouch(stick, MotionEvent.ACTION_DOWN, 0f, -100f, 1_000)

        // the same inversion as the number the stick sends: see the test of the stick itself
        assertStickAt(stick, 150f, 250f, 30f)
    }

    @Test
    fun `a stick that is not pressed is grey, and red or magenta in the configuration modes`() {
        val stick = stick()

        kit.mode(ControllerMode.MoveButtons)
        assertThat(draw(stick).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(stickCircles(red, red, red))
            .inOrder()

        kit.mode(ControllerMode.ResizeButtons)
        assertThat(draw(stick).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(stickCircles(magenta, magenta, magenta))
            .inOrder()
    }

    @Test
    fun `a stick touched in the resize mode draws its circles magenta and the frame, and does not move`() {
        val stick = stick()
        kit.mode(ControllerMode.ResizeButtons)
        stickTouch(stick, MotionEvent.ACTION_DOWN, 60f, 0f, 1_000)

        val frame = Drawn("rect", listOf(4f, 4f, 297f, 297f), paint(green))
        assertThat(draw(stick).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(stickCircles(magenta, magenta, magenta) + frame)
            .inOrder()
    }

    @Test
    fun `a stick that is wider than it is high has its middle in the middle and its circles by the shorter side`() {
        val calls = draw(stick(width = 401, height = 301)).calls

        assertThat(calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(
                stickCircles(grey, grey, grey, StickShape(x = 200f, y = 150f))
            )
            .inOrder()
    }

    @Test
    fun `the circles follow a new size of the stick`() {
        val stick = stick()
        stick.loadConfiguration(JSONObject("""{"LEFT":100,"TOP":100,"WIDTH":201,"HEIGHT":201}"""))
        kit.rig.layOut()

        assertThat(draw(stick).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(
                stickCircles(
                    grey,
                    grey,
                    grey,
                    StickShape(x = 100f, y = 100f, outer = 92f, dead = 30f, small = 20f),
                )
            )
            .inOrder()
    }

    @Test
    fun `the stroke is cut to whole pixels, not rounded`() {
        // 1150 * 0.004 = 4.6: a stroke of 4, an outer circle of 150 - 2 * 4
        val high = ElementRig(Screen(2000, 1150))
        val stick = high.add(AnalogStick(high.controller, high.rig.activity, 12), 10, 10, 301, 301)

        assertThat(draw(stick).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(stickCircles(grey, grey, grey))
            .inOrder()
    }

    @Test
    fun `the pixels of a stick are its rings, and the middle is clear`() {
        val canvas = draw(stick())

        assertColour(canvas.bitmap.getPixel(292, 150), grey) // on the outer circle
        assertColour(canvas.bitmap.getPixel(150, 150 + 45), grey) // on the dead zone
        assertColour(canvas.bitmap.getPixel(150 + 30, 150), grey) // on the stick
        assertThat(Color.alpha(canvas.bitmap.getPixel(150, 150))).isEqualTo(0)
        assertThat(Color.alpha(canvas.bitmap.getPixel(2, 2))).isEqualTo(0)
    }

    @Test
    fun `the pixels of a held stick show its stick in blue where the finger is`() {
        val stick = stick()
        stickTouch(stick, MotionEvent.ACTION_DOWN, 0f, 0f, 1_000)
        stickTouch(stick, MotionEvent.ACTION_MOVE, 60f, -80f, 1_400)

        val canvas = draw(stick)

        assertColour(canvas.bitmap.getPixel(210 + 30, 70), blue)
        assertColour(canvas.bitmap.getPixel(150 + 30, 150), 0)
        assertColour(canvas.bitmap.getPixel(292, 150), grey)
    }

    @Test
    fun `the outer circle of a stick of 433 pixels is 216 over 100 times 100 in float, less two strokes`() {
        // 216 / 100 * 100 is 216.00002 in float, where 216 * 100 / 100 is 216
        val high = ElementRig(Screen(1920, 1200))
        val stick = high.add(AnalogStick(high.controller, high.rig.activity, 12), 10, 10, 433, 433)

        assertThat(draw(stick).of("circle")[0].numbers)
            .containsExactly(216f, 216f, 208.00002f)
            .inOrder()
    }

    // The button

    private val tall by lazy { ElementRig(Screen(2400, 1300)) }

    private fun button(text: String = "A") =
        tall.add(DigitalButton(tall.controller, 6, 1, tall.rig.activity), 100, 100, 200, 100).also {
            it.setText(text)
        }

    private fun oval(color: Int) =
        Drawn(
            "oval",
            listOf(5f, 5f, 195f, 95f),
            paint(color, 5f, textSize = 50f, align = Paint.Align.CENTER),
        )

    private fun text(text: String, color: Int) =
        Drawn(
            "text",
            listOf(100f, 63f),
            paint(color, 2f, Paint.Style.FILL_AND_STROKE, 50f, Paint.Align.CENTER),
            text = text,
        )

    @Test
    fun `a button draws an oval and its text in the middle of it`() {
        val calls = draw(button()).calls

        assertThat(calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactly(transparent(), oval(grey), text("A", grey))
            .inOrder()
    }

    @Test
    fun `the text of a button is placed at value over 100 times percent in float, which 105 shows`() {
        // 105 / 100 * 63 is 66.149994 in float, where 105 * 63 / 100 is 66.15
        val start =
            tall.add(DigitalButton(tall.controller, 7, 1, tall.rig.activity), 400, 100, 180, 105)
        start.setText("START")

        assertThat(draw(start).of("text").single().numbers)
            .containsExactly(90f, 66.149994f)
            .inOrder()
    }

    @Test
    fun `a button draws its text with half the stroke, as a whole number`() {
        // the stroke is 5, and half of it is 2, not 2.5
        assertThat(draw(button()).of("text").single().paint?.strokeWidth).isEqualTo(2f)
    }

    @Test
    fun `a button that was given no text draws an empty one`() {
        val calls =
            draw(
                    tall.add(
                        DigitalButton(tall.controller, 7, 1, tall.rig.activity),
                        400,
                        100,
                        200,
                        100,
                    )
                )
                .calls

        assertThat(calls.last().text).isEmpty()
    }

    @Test
    fun `a pressed button draws its oval and text blue`() {
        val button = button()
        button.touch(MotionEvent.ACTION_DOWN, 10f, 10f)

        assertThat(draw(button).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactly(transparent(), oval(blue), text("A", blue))
            .inOrder()
    }

    @Test
    fun `a released button is grey again`() {
        val button = button()
        button.touch(MotionEvent.ACTION_DOWN, 10f, 10f)
        button.touch(MotionEvent.ACTION_UP, 10f, 10f)

        assertThat(draw(button).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactly(transparent(), oval(grey), text("A", grey))
            .inOrder()
    }

    @Test
    fun `a button is red in the move mode and magenta in the resize mode`() {
        val button = button()

        tall.mode(ControllerMode.MoveButtons)
        assertThat(draw(button).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactly(transparent(), oval(red), text("A", red))
            .inOrder()

        tall.mode(ControllerMode.ResizeButtons)
        assertThat(draw(button).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactly(transparent(), oval(magenta), text("A", magenta))
            .inOrder()
    }

    @Test
    fun `the text of a button follows its size`() {
        val button = button()
        button.loadConfiguration(JSONObject("""{"LEFT":100,"TOP":100,"WIDTH":300,"HEIGHT":200}"""))
        tall.rig.layOut()

        val calls = draw(button).calls

        assertThat(calls[1].numbers)
            .comparingElementsUsing(near)
            .containsExactly(5f, 5f, 295f, 195f)
            .inOrder()
        assertThat(calls[1].paint?.textSize).isEqualTo(75f)
        assertThat(calls[2].numbers)
            .comparingElementsUsing(near)
            .containsExactly(150f, 126f)
            .inOrder()
    }

    @Test
    fun `a button with an icon draws its oval, then the icon in place of the text`() {
        val button = button()
        button.setIcon(R.drawable.borealis_launcher_background)

        val canvas = draw(button)

        assertThat(canvas.calls.map { it.call }).containsExactly("color", "oval").inOrder()
        assertThat(canvas.calls.last()).isEqualTo(oval(grey))
        // the icon fills the button but 5 pixels all round, and is opaque
        assertThat(Color.alpha(canvas.bitmap.getPixel(4, 4))).isEqualTo(0)
        assertThat(Color.alpha(canvas.bitmap.getPixel(5, 5))).isEqualTo(255)
        assertThat(Color.alpha(canvas.bitmap.getPixel(194, 94))).isEqualTo(255)
        assertThat(Color.alpha(canvas.bitmap.getPixel(195, 95))).isEqualTo(0)
        assertThat(Color.alpha(canvas.bitmap.getPixel(100, 50))).isEqualTo(255)
    }

    @Test
    fun `the pixels of a button are its oval, and the oval is blue when it is pressed`() {
        val button = button("")

        assertColour(draw(button).bitmap.getPixel(5, 50), grey) // the left end of the oval
        assertThat(Color.alpha(draw(button).bitmap.getPixel(2, 2))).isEqualTo(0)

        button.touch(MotionEvent.ACTION_DOWN, 10f, 10f)
        assertColour(draw(button).bitmap.getPixel(5, 50), blue)
    }

    // The pad

    private val padKit by lazy { ElementRig() }
    private val pad by lazy {
        padKit.add(DigitalPad(padKit.controller, padKit.rig.activity), 100, 100, 330, 240)
    }

    private fun padPaint(color: Int) = paint(color, 4f, textSize = 48f, align = Paint.Align.CENTER)

    private val centre = Drawn("rect", listOf(118.8f, 86.4f, 207.9f, 151.2f), padPaint(grey))

    private data class Shape(val call: String, val numbers: List<Float>)

    // The eight shapes in the order that the pad draws them; the numbers are for 330 by 240.
    private val shapes =
        linkedMapOf(
            "left" to Shape("rect", listOf(9f, 79.2f, 108.9f, 158.4f)),
            "up" to Shape("rect", listOf(108.9f, 9f, 217.8f, 79.2f)),
            "right" to Shape("rect", listOf(217.8f, 79.2f, 321f, 158.4f)),
            "down" to Shape("rect", listOf(108.9f, 158.4f, 217.8f, 231f)),
            "left up" to Shape("line", listOf(9f, 79.2f, 108.9f, 9f)),
            "up right" to Shape("line", listOf(217.8f, 9f, 321f, 79.2f)),
            "right down" to Shape("line", listOf(326f, 158.4f, 217.8f, 231f)),
            "down left" to Shape("line", listOf(108.9f, 231f, 9f, 158.4f)),
        )

    /** What the pad draws with no direction (when [centred]) or with the given shapes pressed. */
    private fun padCalls(centred: Boolean, pressed: Set<String>, rest: Int = grey) =
        listOf(transparent()) +
            listOfNotNull(if (centred) centre else null) +
            shapes.map { (name, shape) ->
                Drawn(shape.call, shape.numbers, padPaint(if (name in pressed) blue else rest))
            }

    private fun touchPad(x: Float, y: Float) = pad.touch(MotionEvent.ACTION_DOWN, x, y)

    @Test
    fun `a pad nobody touches is a square in the middle and eight grey shapes`() {
        assertThat(draw(pad).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(padCalls(centred = true, pressed = emptySet()))
            .inOrder()
    }

    @Test
    fun `the sides of the pad are value over 100 times percent in float, which 240 shows`() {
        // 240 / 100 * 33 is 79.200005 and 240 / 100 * 66 is 158.40001 in float, where the products
        // divided by 100 are 79.2 and 158.4
        val left = draw(pad).of("rect")[1]

        assertThat(left.numbers).containsExactly(9f, 79.200005f, 108.9f, 158.40001f).inOrder()
    }

    @Test
    fun `each side of the pad that is pressed turns its rectangle blue and takes the middle square away`() {
        val sides =
            mapOf(
                1f to 120f to setOf("left"),
                329f to 120f to setOf("right"),
                165f to 1f to setOf("up"),
                165f to 239f to setOf("down"),
            )

        sides.forEach { (at, pressed) ->
            touchPad(at.first, at.second)

            assertThat(draw(pad).calls)
                .comparingElementsUsing(sameDrawing)
                .containsExactlyElementsIn(padCalls(centred = false, pressed = pressed))
                .inOrder()
        }
    }

    @Test
    fun `a corner of the pad turns two rectangles and the line between them blue`() {
        val corners =
            mapOf(
                1f to 1f to setOf("left", "up", "left up"),
                329f to 1f to setOf("up", "right", "up right"),
                329f to 239f to setOf("right", "down", "right down"),
                1f to 239f to setOf("down", "left", "down left"),
            )

        corners.forEach { (at, pressed) ->
            touchPad(at.first, at.second)

            assertThat(draw(pad).calls)
                .comparingElementsUsing(sameDrawing)
                .containsExactlyElementsIn(padCalls(centred = false, pressed = pressed))
                .inOrder()
        }
    }

    @Test
    fun `a pad that was pressed and let go is grey again`() {
        touchPad(1f, 1f)
        pad.touch(MotionEvent.ACTION_UP, 1f, 1f)

        assertThat(draw(pad).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(padCalls(centred = true, pressed = emptySet()))
            .inOrder()
    }

    @Test
    fun `a pad is red in the move mode and magenta in the resize mode`() {
        padKit.mode(ControllerMode.MoveButtons)
        assertThat(draw(pad).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(
                padCalls(centred = true, pressed = emptySet(), rest = red).map { recolour(it, red) }
            )
            .inOrder()

        padKit.mode(ControllerMode.ResizeButtons)
        assertThat(draw(pad).calls)
            .comparingElementsUsing(sameDrawing)
            .containsExactlyElementsIn(
                padCalls(centred = true, pressed = emptySet(), rest = magenta).map {
                    recolour(it, magenta)
                }
            )
            .inOrder()
    }

    /** The middle square is as coloured as the rest in a configuration mode. */
    private fun recolour(drawn: Drawn, color: Int) =
        if (drawn.paint == null) drawn else drawn.copy(paint = drawn.paint.copy(color = color))

    @Test
    fun `the pixels of a pad are its rectangles, and a pressed side is blue`() {
        assertColour(draw(pad).bitmap.getPixel(165, 9), grey) // the top of the up rectangle
        assertColour(
            draw(pad).bitmap.getPixel(118, 120),
            grey,
        ) // the left edge of the middle square

        touchPad(165f, 1f)
        val canvas = draw(pad)

        assertColour(canvas.bitmap.getPixel(165, 9), blue)
        assertColour(canvas.bitmap.getPixel(50, 79), grey) // the top of the left rectangle
        assertThat(Color.alpha(canvas.bitmap.getPixel(118, 120)))
            .isEqualTo(0) // the middle square is gone
    }

    /**
     * The colour of a pixel is the colour that was drawn, give or take the rounding of the alpha.
     */
    private fun assertColour(actual: Int, expected: Int) {
        listOf(24, 16, 8, 0).forEach { shift ->
            val difference = abs(((actual shr shift) and 0xFF) - ((expected shr shift) and 0xFF))
            assertThat(difference).isAtMost(3)
        }
    }

    private companion object {
        val near: Correspondence<Number, Number> = Correspondence.tolerance(0.001)
    }
}
