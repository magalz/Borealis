/** Created by Karim Mreisi. */
package com.limelight.binding.input.virtual_controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import kotlin.math.atan

/** This is a analog stick on screen element. It is used to get 2-Axis user input. */
// Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
// The elements have no (Context) constructor on purpose: they need their controller and their id.
@SuppressLint("ViewConstructor")
open class AnalogStick(controller: VirtualController, context: Context, elementId: Int) :
    VirtualControllerElement(controller, context, elementId) {

    /** Listener interface to update registered observers. */
    interface AnalogStickListener {

        /**
         * onMovement event will be fired on real analog stick movement (outside of the deadzone).
         *
         * @param x horizontal position, value from -1.0 ... 0 .. 1.0
         * @param y vertical position, value from -1.0 ... 0 .. 1.0
         */
        fun onMovement(x: Float, y: Float)

        /** onClick event will be fired on click on the analog stick */
        fun onClick()

        /**
         * onDoubleClick event will be fired on a double click in a short time frame on the analog
         * stick.
         */
        fun onDoubleClick()

        /** onRevoke event will be fired on unpress of the analog stick. */
        fun onRevoke()
    }

    /** Movement states of the analog sick. */
    private enum class StickState {
        NO_MOVEMENT,
        MOVED_IN_DEAD_ZONE,
        MOVED_ACTIVE,
    }

    /** Click type states. */
    private enum class ClickState {
        SINGLE,
        DOUBLE,
    }

    /** outer radius, this size will be automatically updated on resize */
    private var radiusComplete = 0f

    /** analog stick radius, this size will be automatically updated on resize */
    private var radiusAnalogStick = 0f

    /** dead zone radius, this size will be automatically updated on resize */
    private var radiusDeadZone = 0f

    /** horizontal position in relation to the center of the element */
    private var relativeX = 0f

    /** vertical position in relation to the center of the element */
    private var relativeY = 0f

    private var movementRadius = 0.0
    private var movementAngle = 0.0

    private var positionStickX = 0f
    private var positionStickY = 0f

    private val paint = Paint()

    private var stickState = StickState.NO_MOVEMENT
    private var clickState = ClickState.SINGLE

    private val listeners = ArrayList<AnalogStickListener>()
    private var timeLastClick = 0L

    init {
        // reset stick position
        positionStickX = (width / 2).toFloat()
        positionStickY = (height / 2).toFloat()
    }

    fun addAnalogStickListener(listener: AnalogStickListener) {
        listeners.add(listener)
    }

    private fun notifyOnMovement(x: Float, y: Float) {
        // notify listeners
        for (listener in listeners) {
            listener.onMovement(x, y)
        }
    }

    private fun notifyOnClick() {
        // notify listeners
        for (listener in listeners) {
            listener.onClick()
        }
    }

    private fun notifyOnDoubleClick() {
        // notify listeners
        for (listener in listeners) {
            listener.onDoubleClick()
        }
    }

    private fun notifyOnRevoke() {
        // notify listeners
        for (listener in listeners) {
            listener.onRevoke()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // calculate new radius sizes depending
        radiusComplete =
            getPercent((correctWidth / 2).toFloat(), RADIUS_COMPLETE_PERCENT) -
                2 * defaultStrokeWidth
        radiusDeadZone = getPercent((correctWidth / 2).toFloat(), RADIUS_DEAD_ZONE_PERCENT)
        radiusAnalogStick = getPercent((correctWidth / 2).toFloat(), RADIUS_ANALOG_STICK_PERCENT)

        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onElementDraw(canvas: Canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = defaultStrokeWidth.toFloat()

        // draw outer circle
        if (!isPressed || clickState == ClickState.SINGLE) {
            paint.color = defaultColor
        } else {
            paint.color = pressedColor
        }
        canvas.drawCircle((width / 2).toFloat(), (height / 2).toFloat(), radiusComplete, paint)

        paint.color = defaultColor
        // draw dead zone
        canvas.drawCircle((width / 2).toFloat(), (height / 2).toFloat(), radiusDeadZone, paint)

        // draw stick depending on state
        when (stickState) {
            StickState.NO_MOVEMENT -> {
                paint.color = defaultColor
                canvas.drawCircle(
                    (width / 2).toFloat(),
                    (height / 2).toFloat(),
                    radiusAnalogStick,
                    paint,
                )
            }
            StickState.MOVED_IN_DEAD_ZONE,
            StickState.MOVED_ACTIVE -> {
                paint.color = pressedColor
                canvas.drawCircle(positionStickX, positionStickY, radiusAnalogStick, paint)
            }
        }
    }

    private fun updatePosition(eventTime: Long) {
        // get 100% way
        val complete = radiusComplete - radiusAnalogStick

        // calculate relative way
        val correlatedY = (Math.sin(Math.PI / 2 - movementAngle) * movementRadius).toFloat()
        val correlatedX = (Math.cos(Math.PI / 2 - movementAngle) * movementRadius).toFloat()

        // update positions
        positionStickX = width / 2 - correlatedX
        positionStickY = height / 2 - correlatedY

        // Stay active even if we're back in the deadzone because we know the user is actively
        // giving analog stick input and we don't want to snap back into the deadzone.
        // We also release the deadzone if the user keeps the stick pressed for a bit to allow
        // them to make precise movements.
        stickState =
            if (
                stickState == StickState.MOVED_ACTIVE ||
                    eventTime - timeLastClick > TIMEOUT_DEADZONE ||
                    movementRadius > radiusDeadZone
            ) {
                StickState.MOVED_ACTIVE
            } else {
                StickState.MOVED_IN_DEAD_ZONE
            }

        //  trigger move event if state active
        if (stickState == StickState.MOVED_ACTIVE) {
            notifyOnMovement(-correlatedX / complete, correlatedY / complete)
        }
    }

    override fun onElementTouchEvent(event: MotionEvent): Boolean {
        // save last click state
        val lastClickState = clickState

        // get absolute way for each axis
        relativeX = -(width / 2 - event.x)
        relativeY = -(height / 2 - event.y)

        // get radius and angel of movement from center
        movementRadius = getMovementRadius(relativeX, relativeY)
        movementAngle = getAngle(relativeX, relativeY)

        // pass touch event to parent if out of outer circle
        if (movementRadius > radiusComplete && !isPressed) {
            return false
        }

        // chop radius if out of outer circle or near the edge
        if (movementRadius > (radiusComplete - radiusAnalogStick)) {
            movementRadius = (radiusComplete - radiusAnalogStick).toDouble()
        }

        // handle event depending on action
        when (event.actionMasked) {
            // down event (touch event)
            MotionEvent.ACTION_DOWN -> {
                // set to dead zoned, will be corrected in update position if necessary
                stickState = StickState.MOVED_IN_DEAD_ZONE
                // check for double click
                if (
                    lastClickState == ClickState.SINGLE &&
                        event.eventTime - timeLastClick <= TIMEOUT_DOUBLE_CLICK
                ) {
                    clickState = ClickState.DOUBLE
                    notifyOnDoubleClick()
                } else {
                    clickState = ClickState.SINGLE
                    notifyOnClick()
                }
                // reset last click timestamp
                timeLastClick = event.eventTime
                // set item pressed and update
                isPressed = true
            }
            // up event (revoke touch)
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                isPressed = false
            }
        }

        if (isPressed) {
            // when is pressed calculate new positions (will trigger movement if necessary)
            updatePosition(event.eventTime)
        } else {
            stickState = StickState.NO_MOVEMENT
            notifyOnRevoke()

            // not longer pressed reset analog stick
            notifyOnMovement(0f, 0f)
        }
        // refresh view
        invalidate()
        // accept the touch event
        return true
    }

    private companion object {
        /** time frame for a double click */
        private const val TIMEOUT_DOUBLE_CLICK = 350L

        /**
         * touch down time until the deadzone is lifted to allow precise movements with the analog
         * sticks
         */
        private const val TIMEOUT_DEADZONE = 150L

        // The circles, in percent of half of the shorter side of the element
        private const val RADIUS_COMPLETE_PERCENT = 100f
        private const val RADIUS_DEAD_ZONE_PERCENT = 30f
        private const val RADIUS_ANALOG_STICK_PERCENT = 20f

        // 3 * PI / 2 is three quarter turns
        private const val QUARTER_TURNS_RIGHT = 3

        private fun getMovementRadius(x: Float, y: Float): Double =
            Math.sqrt((x * x + y * y).toDouble())

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
        @Suppress("ReturnCount")
        private fun getAngle(wayX: Float, wayY: Float): Double {
            // prevent divisions by zero for corner cases
            if (wayX == 0f) {
                return if (wayY < 0) Math.PI else 0.0
            } else if (wayY == 0f) {
                if (wayX > 0) {
                    return Math.PI * QUARTER_TURNS_RIGHT / 2
                } else if (wayX < 0) {
                    return Math.PI * 1 / 2
                }
            }
            // return correct calculated angle for each quadrant
            return if (wayX > 0) {
                if (wayY < 0) {
                    // first quadrant
                    QUARTER_TURNS_RIGHT * Math.PI / 2 + atan((-wayY / wayX).toDouble())
                } else {
                    // second quadrant
                    Math.PI + atan((wayX / wayY).toDouble())
                }
            } else {
                if (wayY > 0) {
                    // third quadrant
                    Math.PI / 2 + atan((wayY / -wayX).toDouble())
                } else {
                    // fourth quadrant
                    0 + atan((-wayX / -wayY).toDouble())
                }
            }
        }
    }
}
