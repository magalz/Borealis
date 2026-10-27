package com.limelight.binding.input.virtual_controller

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import com.google.common.truth.Correspondence
import kotlin.math.abs

/**
 * A touch at ([x], [y]) in the element's own coordinates, at [eventTime] (the stick measures its
 * double click and its dead zone with it, not with the looper's clock).
 */
fun motion(
    action: Int,
    x: Float,
    y: Float,
    eventTime: Long = 0,
    downTime: Long = eventTime,
): MotionEvent = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)

/**
 * A touch of two fingers, where the finger of [index] is the one that moves, goes down or lifts:
 * `getActionIndex()` of the event is [index]. The first finger is at [first] and the second at
 * [second].
 */
fun twoFingers(
    action: Int,
    index: Int,
    first: Pair<Float, Float>,
    second: Pair<Float, Float>,
    eventTime: Long = 0,
): MotionEvent {
    val properties = arrayOf(finger(0), finger(1))
    val coordinates =
        arrayOf(coordinates(first.first, first.second), coordinates(second.first, second.second))
    return MotionEvent.obtain(
        eventTime,
        eventTime,
        action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
        2,
        properties,
        coordinates,
        0,
        0,
        1f,
        1f,
        0,
        0,
        0,
        0,
    )
}

private fun finger(id: Int) =
    PointerProperties().apply {
        this.id = id
        toolType = MotionEvent.TOOL_TYPE_FINGER
    }

private fun coordinates(x: Float, y: Float) =
    PointerCoords().apply {
        this.x = x
        this.y = y
    }

/** Delivers [event] to the view the way the framework does, and gives it back to the pool. */
fun VirtualControllerElement.deliver(event: MotionEvent): Boolean {
    val handled = onTouchEvent(event)
    event.recycle()
    return handled
}

/** Touches the element at ([x], [y]) of its own coordinates, at [eventTime]. */
fun VirtualControllerElement.touch(
    action: Int,
    x: Float,
    y: Float,
    eventTime: Long = 0,
): Boolean = deliver(motion(action, x, y, eventTime))

/** What a [Paint] held when a canvas was asked to draw with it. */
data class PaintState(
    val color: Int,
    val style: Paint.Style,
    val strokeWidth: Float,
    val textSize: Float,
    val textAlign: Paint.Align,
    val flags: Int,
)

/** One call to draw, with the numbers it was given and the paint it was given. */
data class Drawn(
    val call: String,
    val numbers: List<Float>,
    val paint: PaintState?,
    val text: String? = null,
    val color: Int? = null,
)

private fun Paint.state() = PaintState(color, style, strokeWidth, textSize, textAlign, flags)

/** The flags of a plain paint on this platform: what an element that sets none of them has. */
fun plainPaintFlags(): Int = Paint().flags

/**
 * A canvas on a bitmap that remembers what it was asked to draw, then draws it: the calls of the
 * elements, with the paint each one had at the time, and the pixels they make.
 */
class RecordingCanvas(val bitmap: Bitmap) : Canvas(bitmap) {
    val calls = mutableListOf<Drawn>()

    override fun drawColor(color: Int) {
        calls += Drawn("color", emptyList(), null, color = color)
        super.drawColor(color)
    }

    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        calls += Drawn("circle", listOf(cx, cy, radius), paint.state())
        super.drawCircle(cx, cy, radius, paint)
    }

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        calls += Drawn("rect", listOf(left, top, right, bottom), paint.state())
        super.drawRect(left, top, right, bottom, paint)
    }

    override fun drawOval(oval: RectF, paint: Paint) {
        calls += Drawn("oval", listOf(oval.left, oval.top, oval.right, oval.bottom), paint.state())
        super.drawOval(oval, paint)
    }

    override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
        calls += Drawn("line", listOf(startX, startY, stopX, stopY), paint.state())
        super.drawLine(startX, startY, stopX, stopY, paint)
    }

    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        calls += Drawn("text", listOf(x, y), paint.state(), text)
        super.drawText(text, x, y, paint)
    }

    /** The calls of one kind, in order. */
    fun of(call: String): List<Drawn> = calls.filter { it.call == call }
}

/** Draws [element] on a fresh canvas the size of the element, and gives the canvas back. */
fun draw(element: VirtualControllerElement): RecordingCanvas {
    val canvas =
        RecordingCanvas(Bitmap.createBitmap(element.width, element.height, Bitmap.Config.ARGB_8888))
    element.draw(canvas)
    return canvas
}

/** The settings button of the controller, which `refreshLayout` creates and the layout drops. */
class ElementRig(screen: Screen = OscRig.PHONE) {
    val rig = OscRig(screen).also { it.settings() }
    val controller = rig.controller
    private val configureButton: android.widget.Button

    init {
        // Clicking the settings button is how a user changes the mode. The default layout that
        // `refreshLayout` builds is not wanted: its buttons would answer to the sliding fingers.
        controller.refreshLayout()
        configureButton = rig.configureButton
        controller.removeElements()
    }

    /** Puts [element] in the frame at ([x], [y]) with the given size, and lays the frame out. */
    fun <T : VirtualControllerElement> add(element: T, x: Int, y: Int, width: Int, height: Int): T {
        controller.addElement(element, x, y, width, height)
        rig.layOut()
        return element
    }

    /** Clicks the settings button until the controller is in [mode]. */
    fun mode(mode: VirtualController.ControllerMode) {
        repeat(MODES) {
            if (controller.controllerMode != mode) configureButton.performClick()
        }
        check(controller.controllerMode == mode) { "the controller did not reach $mode" }
    }

    private companion object {
        const val MODES = 3
    }
}

/** [Drawn] calls that agree on everything but the last decimals of the numbers they were given. */
val sameDrawing: Correspondence<Drawn, Drawn> =
    Correspondence.from(
        { actual: Drawn?, expected: Drawn? ->
            actual != null &&
                expected != null &&
                actual.call == expected.call &&
                actual.paint == expected.paint &&
                actual.text == expected.text &&
                actual.color == expected.color &&
                actual.numbers.size == expected.numbers.size &&
                actual.numbers.zip(expected.numbers).all { (a, e) -> abs(a - e) <= 0.001f }
        },
        "draws",
    )
