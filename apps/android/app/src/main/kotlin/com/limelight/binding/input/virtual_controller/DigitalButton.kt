/** Created by Karim Mreisi. */
package com.limelight.binding.input.virtual_controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent

/**
 * This is a digital button on screen element. It is used to get click and double click user input.
 */
// Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
// The elements have no (Context) constructor on purpose: they need their controller and their id.
@SuppressLint("ViewConstructor")
open class DigitalButton(
    controller: VirtualController,
    elementId: Int,
    private val layer: Int,
    context: Context,
) : VirtualControllerElement(controller, context, elementId) {

    /** Listener interface to update registered observers. */
    interface DigitalButtonListener {

        /** onClick event will be fired on button click. */
        fun onClick()

        /** onLongClick event will be fired on button long click. */
        fun onLongClick()

        /** onRelease event will be fired on button unpress. */
        fun onRelease()
    }

    private val listeners = ArrayList<DigitalButtonListener>()
    private var text = ""
    private var icon = -1
    private val longClickRunnable = Runnable { onLongClickCallback() }

    private val paint = Paint()
    private val rect = RectF()

    private var movingButton: DigitalButton? = null

    private fun inRange(pointX: Float, pointY: Float): Boolean =
        (this.x < pointX && this.x + this.width > pointX) &&
            (this.y < pointY && this.y + this.height > pointY)

    fun checkMovement(x: Float, y: Float, movingButton: DigitalButton): Boolean {
        // check if the movement happened in the same layer
        if (movingButton.layer != this.layer) {
            return false
        }

        // save current pressed state
        val wasPressed = isPressed

        // check if the movement directly happened on the button
        if (
            (this.movingButton == null || movingButton === this.movingButton) && this.inRange(x, y)
        ) {
            // set button pressed state depending on moving button pressed state
            if (this.isPressed != movingButton.isPressed) {
                this.isPressed = movingButton.isPressed
            }
        }
        // check if the movement is outside of the range and the movement button
        // is the saved moving button
        else if (movingButton === this.movingButton) {
            this.isPressed = false
        }

        // check if a change occurred
        if (wasPressed != isPressed) {
            if (isPressed) {
                // is pressed set moving button and emit click event
                this.movingButton = movingButton
                onClickCallback()
            } else {
                // no longer pressed reset moving button and emit release event
                this.movingButton = null
                onReleaseCallback()
            }

            invalidate()

            return true
        }

        return false
    }

    private fun checkMovementForAllButtons(x: Float, y: Float) {
        for (element in virtualController.elements) {
            if (element !== this && element is DigitalButton) {
                element.checkMovement(x, y, this)
            }
        }
    }

    fun addDigitalButtonListener(listener: DigitalButtonListener) {
        listeners.add(listener)
    }

    fun setText(text: String) {
        this.text = text
        invalidate()
    }

    fun setIcon(id: Int) {
        this.icon = id
        invalidate()
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    // The icon is read with the deprecated Resources.getDrawable(int), as the Java did.
    @Suppress("DEPRECATION")
    override fun onElementDraw(canvas: Canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT)

        paint.textSize = getPercent(width.toFloat(), TEXT_SIZE_PERCENT)
        paint.textAlign = Paint.Align.CENTER
        paint.strokeWidth = defaultStrokeWidth.toFloat()

        paint.color = if (isPressed) pressedColor else defaultColor
        paint.style = Paint.Style.STROKE

        rect.top = paint.strokeWidth
        rect.left = rect.top
        rect.right = width - rect.left
        rect.bottom = height - rect.top

        canvas.drawOval(rect, paint)

        if (icon != -1) {
            val d = resources.getDrawable(icon)
            d.setBounds(ICON_MARGIN, ICON_MARGIN, width - ICON_MARGIN, height - ICON_MARGIN)
            d.draw(canvas)
        } else {
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = (defaultStrokeWidth / 2).toFloat()
            canvas.drawText(
                text,
                getPercent(width.toFloat(), TEXT_X_PERCENT),
                getPercent(height.toFloat(), TEXT_Y_PERCENT),
                paint,
            )
        }
    }

    private fun onClickCallback() {
        // notify listeners
        for (listener in listeners) {
            listener.onClick()
        }

        virtualController.handler.removeCallbacks(longClickRunnable)
        virtualController.handler.postDelayed(longClickRunnable, LONG_CLICK_TIMEOUT_MS)
    }

    private fun onLongClickCallback() {
        // notify listeners
        for (listener in listeners) {
            listener.onLongClick()
        }
    }

    private fun onReleaseCallback() {
        // notify listeners
        for (listener in listeners) {
            listener.onRelease()
        }

        // We may be called for a release without a prior click
        virtualController.handler.removeCallbacks(longClickRunnable)
    }

    override fun onElementTouchEvent(event: MotionEvent): Boolean {
        // get masked (not specific to a pointer) action
        val fingerX = this.x + event.x
        val fingerY = this.y + event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                movingButton = null
                isPressed = true
                onClickCallback()

                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                checkMovementForAllButtons(fingerX, fingerY)
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                isPressed = false
                onReleaseCallback()

                checkMovementForAllButtons(fingerX, fingerY)

                invalidate()
            }
        }
        return true
    }

    private companion object {
        /** The time a button is held until it is long clicked, in milliseconds. */
        private const val LONG_CLICK_TIMEOUT_MS = 3000L

        // The text is sized and placed in percent of the width and of the height of the button
        private const val TEXT_SIZE_PERCENT = 25f
        private const val TEXT_X_PERCENT = 50f
        private const val TEXT_Y_PERCENT = 63f

        // The icon is drawn this many pixels inside the button
        private const val ICON_MARGIN = 5
    }
}
