package com.limelight.binding.input.touch

import android.os.Handler
import android.os.Looper
import android.view.View
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.preferences.PreferenceConfiguration
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

private const val TAP_MOVEMENT_THRESHOLD = 20
private const val TAP_DISTANCE_THRESHOLD = 25
private const val TAP_TIME_THRESHOLD = 250
private const val DRAG_TIME_THRESHOLD = 650L

private const val SCROLL_SPEED_FACTOR = 5

// The mouse button of a tap goes up this long after it went down.
private const val BUTTON_RELEASE_DELAY = 100L

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
@Suppress("TooManyFunctions")
class RelativeTouchContext(
    private val conn: NvConnection,
    override val actionIndex: Int,
    private val referenceWidth: Int,
    private val referenceHeight: Int,
    private val targetView: View,
    private val prefConfig: PreferenceConfiguration,
) : TouchContext {
    private var lastTouchX = 0
    private var lastTouchY = 0
    private var originalTouchX = 0
    private var originalTouchY = 0
    private var originalTouchTime = 0L
    private var cancelled = false
    private var confirmedMove = false
    private var confirmedDrag = false
    private var confirmedScroll = false
    private var distanceMoved = 0.0
    private var xFactor = 0.0
    private var yFactor = 0.0
    private var pointerCount = 0
    private var maxPointerCountInGesture = 0

    private val handler = Handler(Looper.getMainLooper())

    private val dragTimerRunnable = Runnable {
        // Check if someone already set move
        if (confirmedMove) {
            return@Runnable
        }

        // The drag should only be processed for the primary finger
        if (actionIndex != maxPointerCountInGesture - 1) {
            return@Runnable
        }

        // We haven't been cancelled before the timer expired so begin dragging
        confirmedDrag = true
        conn.sendMouseButtonDown(getMouseButtonIndex())
    }

    // Indexed by MouseButtonPacket.BUTTON_XXX - 1
    private val buttonUpRunnables =
        arrayOf(
            Runnable { conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT) },
            Runnable { conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE) },
            Runnable { conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT) },
            Runnable { conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1) },
            Runnable { conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2) },
        )

    private fun isWithinTapBounds(touchX: Int, touchY: Int): Boolean {
        val xDelta = abs(touchX - originalTouchX)
        val yDelta = abs(touchY - originalTouchY)
        return xDelta <= TAP_MOVEMENT_THRESHOLD && yDelta <= TAP_MOVEMENT_THRESHOLD
    }

    private fun isTap(eventTime: Long): Boolean {
        if (confirmedDrag || confirmedMove || confirmedScroll) {
            return false
        }

        // If this input wasn't the last finger down, do not report
        // a tap. This ensures we don't report duplicate taps for each
        // finger on a multi-finger tap gesture
        if (actionIndex + 1 != maxPointerCountInGesture) {
            return false
        }

        val timeDelta = eventTime - originalTouchTime
        return isWithinTapBounds(lastTouchX, lastTouchY) && timeDelta <= TAP_TIME_THRESHOLD
    }

    private fun getMouseButtonIndex(): Byte =
        if (actionIndex == 1) {
            MouseButtonPacket.BUTTON_RIGHT
        } else {
            MouseButtonPacket.BUTTON_LEFT
        }

    override fun touchDownEvent(
        eventX: Int,
        eventY: Int,
        eventTime: Long,
        isNewFinger: Boolean,
    ): Boolean {
        // Get the view dimensions to scale inputs on this touch
        xFactor = referenceWidth / targetView.width.toDouble()
        yFactor = referenceHeight / targetView.height.toDouble()

        originalTouchX = eventX
        lastTouchX = eventX
        originalTouchY = eventY
        lastTouchY = eventY

        if (isNewFinger) {
            maxPointerCountInGesture = pointerCount
            originalTouchTime = eventTime
            cancelled = false
            confirmedDrag = false
            confirmedMove = false
            confirmedScroll = false
            distanceMoved = 0.0

            if (actionIndex == 0) {
                // Start the timer for engaging a drag
                startDragTimer()
            }
        }

        return true
    }

    override fun touchUpEvent(eventX: Int, eventY: Int, eventTime: Long) {
        if (cancelled) {
            return
        }

        // Cancel the drag timer
        cancelDragTimer()

        val buttonIndex = getMouseButtonIndex()

        if (confirmedDrag) {
            // Raise the button after a drag
            conn.sendMouseButtonUp(buttonIndex)
        } else if (isTap(eventTime)) {
            // Lower the mouse button
            conn.sendMouseButtonDown(buttonIndex)

            // Release the mouse button in 100ms to allow for apps that use polling
            // to detect mouse button presses.
            val buttonUpRunnable = buttonUpRunnables[buttonIndex - 1]
            handler.removeCallbacks(buttonUpRunnable)
            handler.postDelayed(buttonUpRunnable, BUTTON_RELEASE_DELAY)
        }
    }

    private fun startDragTimer() {
        cancelDragTimer()
        handler.postDelayed(dragTimerRunnable, DRAG_TIME_THRESHOLD)
    }

    private fun cancelDragTimer() {
        handler.removeCallbacks(dragTimerRunnable)
    }

    private fun checkForConfirmedMove(eventX: Int, eventY: Int) {
        // If we've already confirmed something, get out now
        if (confirmedMove || confirmedDrag) {
            return
        }

        // If it leaves the tap bounds before the drag time expires, it's a move.
        if (!isWithinTapBounds(eventX, eventY)) {
            confirmedMove = true
            cancelDragTimer()
            return
        }

        // Check if we've exceeded the maximum distance moved
        distanceMoved +=
            sqrt((eventX - lastTouchX).toDouble().pow(2) + (eventY - lastTouchY).toDouble().pow(2))
        if (distanceMoved >= TAP_DISTANCE_THRESHOLD) {
            confirmedMove = true
            cancelDragTimer()
        }
    }

    private fun checkForConfirmedScroll() {
        // Enter scrolling mode if we've already left the tap zone
        // and we have 2 fingers on screen. Leave scroll mode if
        // we no longer have 2 fingers on screen
        confirmedScroll = (actionIndex == 0 && pointerCount == 2 && confirmedMove)
    }

    override fun touchMoveEvent(eventX: Int, eventY: Int, eventTime: Long): Boolean {
        if (cancelled) {
            return true
        }

        if (eventX != lastTouchX || eventY != lastTouchY) {
            checkForConfirmedMove(eventX, eventY)
            checkForConfirmedScroll()

            // We only send moves and drags for the primary touch point
            if (actionIndex == 0) {
                var deltaX = eventX - lastTouchX
                var deltaY = eventY - lastTouchY

                // Scale the deltas based on the factors passed to our constructor
                deltaX = Math.round(abs(deltaX).toDouble() * xFactor).toInt()
                deltaY = Math.round(abs(deltaY).toDouble() * yFactor).toInt()

                // Fix up the signs
                if (eventX < lastTouchX) {
                    deltaX = -deltaX
                }
                if (eventY < lastTouchY) {
                    deltaY = -deltaY
                }

                if (pointerCount == 2) {
                    if (confirmedScroll) {
                        conn.sendMouseHighResScroll((deltaY * SCROLL_SPEED_FACTOR).toShort())
                    }
                } else {
                    if (prefConfig.absoluteMouseMode) {
                        conn.sendMouseMoveAsMousePosition(
                            deltaX.toShort(),
                            deltaY.toShort(),
                            targetView.width.toShort(),
                            targetView.height.toShort(),
                        )
                    } else {
                        conn.sendMouseMove(deltaX.toShort(), deltaY.toShort())
                    }
                }

                // If the scaling factor ended up rounding deltas to zero, wait until they are
                // non-zero to update lastTouch that way devices that report small touch events
                // often will work correctly
                if (deltaX != 0) {
                    lastTouchX = eventX
                }
                if (deltaY != 0) {
                    lastTouchY = eventY
                }
            } else {
                lastTouchX = eventX
                lastTouchY = eventY
            }
        }

        return true
    }

    override fun cancelTouch() {
        cancelled = true

        // Cancel the drag timer
        cancelDragTimer()

        // If it was a confirmed drag, we'll need to raise the button now
        if (confirmedDrag) {
            conn.sendMouseButtonUp(getMouseButtonIndex())
        }
    }

    override val isCancelled: Boolean
        get() = cancelled

    override fun setPointerCount(pointerCount: Int) {
        this.pointerCount = pointerCount

        if (pointerCount > maxPointerCountInGesture) {
            maxPointerCountInGesture = pointerCount
        }
    }
}
