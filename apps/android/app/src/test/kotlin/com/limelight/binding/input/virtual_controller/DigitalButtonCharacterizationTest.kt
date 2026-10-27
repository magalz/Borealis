package com.limelight.binding.input.virtual_controller

import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.ShadowMoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Notes what a button tells its listeners, in order: `click`, `long` and `release`. */
class ButtonLog(private val name: String = "", private val shared: MutableList<String>? = null) :
    DigitalButton.DigitalButtonListener {
    val events = mutableListOf<String>()

    private fun note(event: String) {
        events += event
        shared?.add("$name:$event")
    }

    override fun onClick() = note("click")

    override fun onLongClick() = note("long")

    override fun onRelease() = note("release")
}

/**
 * What a button does with a finger: it presses on the touch, calls its long click after three
 * seconds, releases on the lift, and passes a finger that slides on to the buttons of its layer.
 *
 * The buttons of these tests are 200 by 100 pixels, in a row: `a` at x = 100, `b` at x = 400, both
 * of layer 1, and `c` at x = 700 of layer 2. All of them are at y = 100.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowMoonBridge::class])
class DigitalButtonCharacterizationTest {
    private val kit = ElementRig()
    private val controller = kit.controller
    private val activity = kit.rig.activity
    private val a = kit.add(DigitalButton(controller, 6, 1, activity), 100, 100, 200, 100)
    private val b = kit.add(DigitalButton(controller, 7, 1, activity), 400, 100, 200, 100)
    private val c = kit.add(DigitalButton(controller, 8, 2, activity), 700, 100, 200, 100)
    private val aLog = ButtonLog().also { a.addDigitalButtonListener(it) }
    private val bLog = ButtonLog().also { b.addDigitalButtonListener(it) }
    private val cLog = ButtonLog().also { c.addDigitalButtonListener(it) }

    private fun press(button: DigitalButton, x: Float = 10f, y: Float = 10f) =
        button.touch(MotionEvent.ACTION_DOWN, x, y)

    private fun lift(button: DigitalButton, x: Float = 10f, y: Float = 10f) =
        button.touch(MotionEvent.ACTION_UP, x, y)

    /** Slides the finger that is on [from] to the screen point ([x], [y]). */
    private fun slide(from: DigitalButton, x: Float, y: Float) =
        from.touch(MotionEvent.ACTION_MOVE, x - from.x, y - from.y)

    // The finger on the button

    @Test
    fun `a touch presses the button and clicks`() {
        assertThat(press(a)).isTrue()

        assertThat(a.isPressed).isTrue()
        assertThat(aLog.events).containsExactly("click")
    }

    @Test
    fun `a lift releases the button`() {
        press(a)

        assertThat(lift(a)).isTrue()

        assertThat(a.isPressed).isFalse()
        assertThat(aLog.events).containsExactly("click", "release").inOrder()
    }

    @Test
    fun `a cancel releases the button`() {
        press(a)

        assertThat(a.touch(MotionEvent.ACTION_CANCEL, 10f, 10f)).isTrue()

        assertThat(a.isPressed).isFalse()
        assertThat(aLog.events).containsExactly("click", "release").inOrder()
    }

    @Test
    fun `a lift without a touch before it still releases`() {
        assertThat(lift(a)).isTrue()

        assertThat(aLog.events).containsExactly("release")
        assertThat(a.isPressed).isFalse()
    }

    @Test
    fun `a move that is not a slide from a press and any other action are handled and change nothing`() {
        assertThat(a.touch(MotionEvent.ACTION_OUTSIDE, 10f, 10f)).isTrue()
        assertThat(a.touch(MotionEvent.ACTION_MOVE, 10f, 10f)).isTrue()

        assertThat(aLog.events).isEmpty()
        assertThat(a.isPressed).isFalse()
    }

    @Test
    fun `a second finger is ignored`() {
        press(a)

        assertThat(
                a.deliver(twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1, 10f to 10f, 50f to 50f))
            )
            .isTrue()
        assertThat(a.deliver(twoFingers(MotionEvent.ACTION_POINTER_UP, 1, 10f to 10f, 50f to 50f)))
            .isTrue()

        assertThat(aLog.events).containsExactly("click")
        assertThat(a.isPressed).isTrue()
    }

    @Test
    fun `a button is redrawn when it is pressed and when it is released`() {
        shadowOf(a).clearWasInvalidated()
        press(a)
        assertThat(shadowOf(a).wasInvalidated()).isTrue()

        shadowOf(a).clearWasInvalidated()
        lift(a)
        assertThat(shadowOf(a).wasInvalidated()).isTrue()
    }

    // The long click

    @Test
    fun `a button held for three seconds long clicks, once`() {
        press(a)

        kit.rig.advanceBy(2_999)
        assertThat(aLog.events).containsExactly("click")

        kit.rig.advanceBy(1)
        assertThat(aLog.events).containsExactly("click", "long").inOrder()

        kit.rig.advanceBy(10_000)
        assertThat(aLog.events).containsExactly("click", "long").inOrder()
    }

    @Test
    fun `a button released before three seconds does not long click`() {
        press(a)
        kit.rig.advanceBy(2_999)

        lift(a)
        kit.rig.advanceBy(10_000)

        assertThat(aLog.events).containsExactly("click", "release").inOrder()
    }

    @Test
    fun `a button cancelled before three seconds does not long click`() {
        press(a)
        kit.rig.advanceBy(1_000)

        a.touch(MotionEvent.ACTION_CANCEL, 10f, 10f)
        kit.rig.advanceBy(10_000)

        assertThat(aLog.events).containsExactly("click", "release").inOrder()
    }

    @Test
    fun `a new press restarts the three seconds, and the long click that was waiting does not come`() {
        press(a)
        kit.rig.advanceBy(2_000)

        press(a) // a second press with no lift between: one long click waits for the new press only
        kit.rig.advanceBy(1_000) // three seconds after the first press
        assertThat(aLog.events).containsExactly("click", "click")

        kit.rig.advanceBy(1_999)
        assertThat(aLog.events).containsExactly("click", "click")

        kit.rig.advanceBy(1)
        assertThat(aLog.events).containsExactly("click", "click", "long").inOrder()

        kit.rig.advanceBy(10_000)
        assertThat(aLog.events).hasSize(3)
    }

    @Test
    fun `the long click of a button is not the long click of the one beside it`() {
        press(a)
        kit.rig.advanceBy(1_000)
        press(b)

        kit.rig.advanceBy(2_000) // 3000 for a, 2000 for b

        assertThat(aLog.events).containsExactly("click", "long").inOrder()
        assertThat(bLog.events).containsExactly("click")

        kit.rig.advanceBy(1_000)
        assertThat(bLog.events).containsExactly("click", "long").inOrder()
    }

    @Test
    fun `releasing one button leaves the long click of another coming`() {
        press(a)
        press(b)

        lift(a)
        kit.rig.advanceBy(3_000)

        assertThat(aLog.events).containsExactly("click", "release").inOrder()
        assertThat(bLog.events).containsExactly("click", "long").inOrder()
    }

    @Test
    fun `a button that was pressed by sliding long clicks three seconds after the slide`() {
        press(a)
        slide(a, 500f, 150f)
        kit.rig.advanceBy(2_999)
        assertThat(bLog.events).containsExactly("click")

        kit.rig.advanceBy(1)

        assertThat(bLog.events).containsExactly("click", "long").inOrder()
    }

    @Test
    fun `every listener is told in the order they were added`() {
        val order = mutableListOf<String>()
        a.addDigitalButtonListener(ButtonLog("first", order))
        a.addDigitalButtonListener(ButtonLog("second", order))

        press(a)
        kit.rig.advanceBy(3_000)
        lift(a)

        assertThat(order)
            .containsExactly(
                "first:click",
                "second:click",
                "first:long",
                "second:long",
                "first:release",
                "second:release",
            )
            .inOrder()
    }

    // Sliding

    @Test
    fun `a finger that slides onto another button of the layer presses it too`() {
        press(a)

        assertThat(slide(a, 500f, 150f)).isTrue()

        assertThat(b.isPressed).isTrue()
        assertThat(bLog.events).containsExactly("click")
        assertThat(a.isPressed).isTrue()
        assertThat(aLog.events).containsExactly("click")
    }

    @Test
    fun `sliding on across the same button says nothing more`() {
        press(a)
        slide(a, 500f, 150f)

        slide(a, 510f, 160f)
        slide(a, 520f, 170f)

        assertThat(bLog.events).containsExactly("click")
    }

    @Test
    fun `a finger that slides off the other button releases it, and back on presses it again`() {
        press(a)
        slide(a, 500f, 150f)

        slide(a, 350f, 150f) // between a and b
        assertThat(b.isPressed).isFalse()
        assertThat(bLog.events).containsExactly("click", "release").inOrder()

        slide(a, 500f, 150f)
        assertThat(bLog.events).containsExactly("click", "release", "click").inOrder()
        assertThat(a.isPressed).isTrue()
    }

    @Test
    fun `lifting the finger over the other button releases both`() {
        press(a)
        slide(a, 500f, 150f)

        a.touch(MotionEvent.ACTION_UP, 400f, 50f) // over b

        assertThat(aLog.events).containsExactly("click", "release").inOrder()
        assertThat(bLog.events).containsExactly("click", "release").inOrder()
        assertThat(b.isPressed).isFalse()
    }

    @Test
    fun `lifting the finger outside the other button releases it too`() {
        press(a)
        slide(a, 500f, 150f)

        lift(a)

        assertThat(bLog.events).containsExactly("click", "release").inOrder()
    }

    @Test
    fun `a button of another layer is not pressed by a slide, in any place`() {
        press(a)

        slide(a, 800f, 150f)
        lift(a, 800f - a.x, 150f - a.y)

        assertThat(cLog.events).isEmpty()
        assertThat(c.isPressed).isFalse()
    }

    @Test
    fun `a button does not answer to the slide of its own finger`() {
        press(a)

        slide(a, 150f, 150f)
        slide(a, 800f, 150f)

        assertThat(aLog.events).containsExactly("click")
        assertThat(a.isPressed).isTrue()
    }

    @Test
    fun `a button that its own finger holds stays pressed when another finger slides over it and away`() {
        press(b)
        press(a)

        slide(a, 500f, 150f) // over b, which was pressed already
        slide(a, 350f, 150f) // and away

        assertThat(bLog.events).containsExactly("click")
        assertThat(b.isPressed).isTrue()
    }

    @Test
    fun `touching a button that a slide holds makes it the button of the new finger`() {
        press(a)
        slide(a, 500f, 150f)

        press(b, 10f, 10f) // a finger of its own goes down on b
        slide(a, 350f, 150f) // the sliding finger goes away

        assertThat(bLog.events).containsExactly("click", "click")
        assertThat(b.isPressed).isTrue()
    }

    @Test
    fun `the edges of a button are not in it`() {
        press(a)

        // left edge at 400, right edge at 600, top at 100 and bottom at 200
        listOf(
                400f to 150f,
                600f to 150f,
                500f to 100f,
                500f to 200f,
            )
            .forEach { (x, y) ->
                slide(a, x, y)
                assertThat(b.isPressed).isFalse()
            }

        listOf(
                400.5f to 150f,
                599.5f to 150f,
                500f to 100.5f,
                500f to 199.5f,
            )
            .forEach { (x, y) ->
                slide(a, x, y)
                assertThat(b.isPressed).isTrue()
                slide(a, 350f, 150f)
                assertThat(b.isPressed).isFalse()
            }
    }

    @Test
    fun `checking a movement says whether the button changed, and is false for another layer`() {
        press(a)

        assertThat(b.checkMovement(500f, 150f, a)).isTrue()
        assertThat(b.checkMovement(510f, 150f, a)).isFalse()
        assertThat(c.checkMovement(800f, 150f, a)).isFalse()
        assertThat(b.checkMovement(350f, 150f, a)).isTrue()
        assertThat(b.checkMovement(350f, 150f, a)).isFalse()
    }

    @Test
    fun `a button that is not pressed does not press another by checking`() {
        assertThat(b.checkMovement(500f, 150f, a)).isFalse()

        assertThat(b.isPressed).isFalse()
        assertThat(bLog.events).isEmpty()
    }

    // Text and icon

    @Test
    fun `a button has no text and no icon until it is given them`() {
        assertThat(a.label()).isEmpty()
        assertThat(a.icon()).isEqualTo(-1)
    }

    @Test
    fun `the text and the icon are kept, and the button is redrawn`() {
        shadowOf(a).clearWasInvalidated()
        a.setText("GO")
        assertThat(a.label()).isEqualTo("GO")
        assertThat(shadowOf(a).wasInvalidated()).isTrue()

        shadowOf(a).clearWasInvalidated()
        a.setIcon(1234)
        assertThat(a.icon()).isEqualTo(1234)
        assertThat(shadowOf(a).wasInvalidated()).isTrue()
    }
}
