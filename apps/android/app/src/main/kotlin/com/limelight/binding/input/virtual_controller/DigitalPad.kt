/** Created by Karim Mreisi. */
package com.limelight.binding.input.virtual_controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
// The elements have no (Context) constructor on purpose: they need their controller and their id.
@SuppressLint("ViewConstructor")
class DigitalPad(controller: VirtualController, context: Context) :
    VirtualControllerElement(controller, context, EID_DPAD) {
    private var direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION
    private val listeners = ArrayList<DigitalPadListener>()

    private val paint = Paint()

    fun addDigitalPadListener(listener: DigitalPadListener) {
        listeners.add(listener)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("LongMethod")
    override fun onElementDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // set transparent background
        canvas.drawColor(Color.TRANSPARENT)

        paint.textSize = getPercent(correctWidth.toFloat(), TEXT_SIZE_PERCENT)
        paint.textAlign = Paint.Align.CENTER
        paint.strokeWidth = defaultStrokeWidth.toFloat()

        if (direction == DIGITAL_PAD_DIRECTION_NO_DIRECTION) {
            // draw no direction rect
            paint.style = Paint.Style.STROKE
            paint.color = defaultColor
            canvas.drawRect(
                getPercent(w, MIDDLE_NEAR_PERCENT),
                getPercent(h, MIDDLE_NEAR_PERCENT),
                getPercent(w, MIDDLE_FAR_PERCENT),
                getPercent(h, MIDDLE_FAR_PERCENT),
                paint,
            )
        }

        // draw left rect
        paint.color =
            if ((direction and DIGITAL_PAD_DIRECTION_LEFT) > 0) pressedColor else defaultColor
        paint.style = Paint.Style.STROKE
        canvas.drawRect(
            paint.strokeWidth + DPAD_MARGIN,
            getPercent(h, THIRD_PERCENT),
            getPercent(w, THIRD_PERCENT),
            getPercent(h, TWO_THIRDS_PERCENT),
            paint,
        )

        // draw up rect
        paint.color =
            if ((direction and DIGITAL_PAD_DIRECTION_UP) > 0) pressedColor else defaultColor
        paint.style = Paint.Style.STROKE
        canvas.drawRect(
            getPercent(w, THIRD_PERCENT),
            paint.strokeWidth + DPAD_MARGIN,
            getPercent(w, TWO_THIRDS_PERCENT),
            getPercent(h, THIRD_PERCENT),
            paint,
        )

        // draw right rect
        paint.color =
            if ((direction and DIGITAL_PAD_DIRECTION_RIGHT) > 0) pressedColor else defaultColor
        paint.style = Paint.Style.STROKE
        canvas.drawRect(
            getPercent(w, TWO_THIRDS_PERCENT),
            getPercent(h, THIRD_PERCENT),
            w - (paint.strokeWidth + DPAD_MARGIN),
            getPercent(h, TWO_THIRDS_PERCENT),
            paint,
        )

        // draw down rect
        paint.color =
            if ((direction and DIGITAL_PAD_DIRECTION_DOWN) > 0) pressedColor else defaultColor
        paint.style = Paint.Style.STROKE
        canvas.drawRect(
            getPercent(w, THIRD_PERCENT),
            getPercent(h, TWO_THIRDS_PERCENT),
            getPercent(w, TWO_THIRDS_PERCENT),
            h - (paint.strokeWidth + DPAD_MARGIN),
            paint,
        )

        // draw left up line
        paint.color =
            if (
                (direction and DIGITAL_PAD_DIRECTION_LEFT) > 0 &&
                    (direction and DIGITAL_PAD_DIRECTION_UP) > 0
            ) {
                pressedColor
            } else {
                defaultColor
            }
        paint.style = Paint.Style.STROKE
        canvas.drawLine(
            paint.strokeWidth + DPAD_MARGIN,
            getPercent(h, THIRD_PERCENT),
            getPercent(w, THIRD_PERCENT),
            paint.strokeWidth + DPAD_MARGIN,
            paint,
        )

        // draw up right line
        paint.color =
            if (
                (direction and DIGITAL_PAD_DIRECTION_UP) > 0 &&
                    (direction and DIGITAL_PAD_DIRECTION_RIGHT) > 0
            ) {
                pressedColor
            } else {
                defaultColor
            }
        paint.style = Paint.Style.STROKE
        canvas.drawLine(
            getPercent(w, TWO_THIRDS_PERCENT),
            paint.strokeWidth + DPAD_MARGIN,
            w - (paint.strokeWidth + DPAD_MARGIN),
            getPercent(h, THIRD_PERCENT),
            paint,
        )

        // draw right down line
        paint.color =
            if (
                (direction and DIGITAL_PAD_DIRECTION_RIGHT) > 0 &&
                    (direction and DIGITAL_PAD_DIRECTION_DOWN) > 0
            ) {
                pressedColor
            } else {
                defaultColor
            }
        paint.style = Paint.Style.STROKE
        canvas.drawLine(
            w - paint.strokeWidth,
            getPercent(h, TWO_THIRDS_PERCENT),
            getPercent(w, TWO_THIRDS_PERCENT),
            h - (paint.strokeWidth + DPAD_MARGIN),
            paint,
        )

        // draw down left line
        paint.color =
            if (
                (direction and DIGITAL_PAD_DIRECTION_DOWN) > 0 &&
                    (direction and DIGITAL_PAD_DIRECTION_LEFT) > 0
            ) {
                pressedColor
            } else {
                defaultColor
            }
        paint.style = Paint.Style.STROKE
        canvas.drawLine(
            getPercent(w, THIRD_PERCENT),
            h - (paint.strokeWidth + DPAD_MARGIN),
            paint.strokeWidth + DPAD_MARGIN,
            getPercent(h, TWO_THIRDS_PERCENT),
            paint,
        )
    }

    private fun newDirectionCallback(direction: Int) {
        // notify listeners
        for (listener in listeners) {
            listener.onDirectionChange(direction)
        }
    }

    override fun onElementTouchEvent(event: MotionEvent): Boolean {
        // get masked (not specific to a pointer) action
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                direction = 0

                if (event.x < getPercent(width.toFloat(), THIRD_PERCENT)) {
                    direction = direction or DIGITAL_PAD_DIRECTION_LEFT
                }
                if (event.x > getPercent(width.toFloat(), TWO_THIRDS_PERCENT)) {
                    direction = direction or DIGITAL_PAD_DIRECTION_RIGHT
                }
                if (event.y > getPercent(height.toFloat(), TWO_THIRDS_PERCENT)) {
                    direction = direction or DIGITAL_PAD_DIRECTION_DOWN
                }
                if (event.y < getPercent(height.toFloat(), THIRD_PERCENT)) {
                    direction = direction or DIGITAL_PAD_DIRECTION_UP
                }
                newDirectionCallback(direction)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                direction = 0
                newDirectionCallback(direction)
                invalidate()
            }
        }

        return true
    }

    fun interface DigitalPadListener {
        fun onDirectionChange(direction: Int)
    }

    companion object {
        const val DIGITAL_PAD_DIRECTION_NO_DIRECTION = 0
        const val DIGITAL_PAD_DIRECTION_LEFT = 1
        const val DIGITAL_PAD_DIRECTION_UP = 2
        const val DIGITAL_PAD_DIRECTION_RIGHT = 4
        const val DIGITAL_PAD_DIRECTION_DOWN = 8

        private const val DPAD_MARGIN = 5

        // The pad is drawn and read in percent of its width and of its height
        private const val TEXT_SIZE_PERCENT = 20f
        private const val MIDDLE_NEAR_PERCENT = 36f
        private const val MIDDLE_FAR_PERCENT = 63f
        private const val THIRD_PERCENT = 33f
        private const val TWO_THIRDS_PERCENT = 66f
    }
}
