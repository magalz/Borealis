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
private const val TAP_TIME_THRESHOLD = 250L

private const val SCROLL_SPEED_FACTOR = 5

// The mouse button of a tap goes up this long after it went down.
private const val BUTTON_RELEASE_DELAY = 100L

/**
 * What the two touch contexts of the trackpad share about a two-finger tap, which neither sees
 * alone: when the first finger lifts first, its context decides at the last lift and never sees the
 * second finger go down or slide, and the context of the second finger never sees the first one
 * slide. The tap time runs from the second finger's down in both lift orders, and a move of either
 * finger past the tap distance since then is no tap. `TouchContexts` builds one for the contexts of
 * a stream.
 */
class TwoFingerTap {
    private var secondFingerDownTime: Long? = null
    private var moved = false

    /** The second finger went down at [eventTime]: the tap of the two fingers starts. */
    fun secondFingerDown(eventTime: Long) {
        secondFingerDownTime = eventTime
        moved = false
    }

    /** A finger moved past the tap distance since the second one landed: no tap, whoever lifts. */
    fun fingerMoved() {
        moved = true
    }

    /** Whether a lift at [eventTime] ends a tap of the two fingers: in time, no finger moved. */
    fun isTapAt(eventTime: Long): Boolean {
        val downTime = secondFingerDownTime ?: return false
        return !moved && eventTime - downTime <= TAP_TIME_THRESHOLD
    }
}

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
// The parameters are the connection, the pointer, the reference screen, the view, the settings and
// the two-finger tap that the contexts of a stream share.
@Suppress("TooManyFunctions", "LongParameterList")
class RelativeTouchContext(
    private val conn: NvConnection,
    override val actionIndex: Int,
    private val referenceWidth: Int,
    private val referenceHeight: Int,
    private val targetView: View,
    private val prefConfig: PreferenceConfiguration,
    private val twoFingerTap: TwoFingerTap,
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

    // Where the first finger was when the second one landed and how far it walked since. Its move
    // before that is confirmedMove, which does not count for the two-finger tap; its jitter after
    // the landing does not either, only a move past the tap box or distance (twoFingerTap).
    private var landingX = 0
    private var landingY = 0
    private var distanceSinceLanding = 0.0
    private var xFactor = 0.0
    private var yFactor = 0.0
    private var pointerCount = 0
    private var maxPointerCountInGesture = 0

    // The first finger of a two-finger tap lifted before the second: the tap is a right click when
    // the second finger, which takes its place as this context's finger, lifts (touchUpEvent).
    private var twoFingerTapPending = false

    // When the last left click lifted, if it was a tap: a press within the tap time after it is the
    // second press of a double tap (touchDownEvent).
    private var lastTapUpTime: Long? = null

    // A press that follows a tap within the tap time: held past the tap time or moved past the tap
    // distance, it is a drag (startDrag), and if it lifts quickly it is a second click.
    private var secondPress = false

    // A view with no size gives a factor of 0 (touchDownEvent): there is no scale to send with.
    private val hasScale: Boolean
        get() = xFactor != 0.0 && yFactor != 0.0

    private val handler = Handler(Looper.getMainLooper())

    private val dragTimerRunnable = Runnable { startDrag() }

    // The release of a left click is posted and not sent yet. Handler.hasCallbacks would tell, but
    // it needs API 29 and the app supports 28.
    private var leftReleasePending = false

    // A tap gives only the left or the right button (getMouseButtonIndex).
    private val leftButtonUpRunnable = Runnable {
        leftReleasePending = false
        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
    }
    private val rightButtonUpRunnable = Runnable {
        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
    }

    private fun followsTap(eventTime: Long): Boolean {
        val tapUpTime = lastTapUpTime ?: return false
        return eventTime - tapUpTime in 0..TAP_TIME_THRESHOLD
    }

    private fun isWithinTapBounds(touchX: Int, touchY: Int): Boolean {
        val xDelta = abs(touchX - originalTouchX)
        val yDelta = abs(touchY - originalTouchY)
        return xDelta <= TAP_MOVEMENT_THRESHOLD && yDelta <= TAP_MOVEMENT_THRESHOLD
    }

    // Whether the motion of this finger so far, whatever the time, is still the one of a tap.
    private fun isTapMotion(): Boolean {
        if (confirmedDrag || confirmedMove || confirmedScroll) {
            return false
        }

        return isWithinTapBounds(lastTouchX, lastTouchY)
    }

    // The touch of this finger, ended at eventTime, is a tap: it runs from this finger's down.
    private fun isTapTouch(eventTime: Long): Boolean =
        isTapMotion() && eventTime - originalTouchTime <= TAP_TIME_THRESHOLD

    // The touch of a finger of two, ended at eventTime, is a tap of the two fingers: it runs from
    // the second finger's down, whichever finger lifts first, and neither finger moved past the tap
    // distance (twoFingerTap).
    private fun isTwoFingerTapTouch(eventTime: Long): Boolean =
        isTapMotion() && twoFingerTap.isTapAt(eventTime)

    private fun isTap(eventTime: Long): Boolean {
        // If this input wasn't the last finger down, do not report
        // a tap. This ensures we don't report duplicate taps for each
        // finger on a multi-finger tap gesture
        if (actionIndex + 1 != maxPointerCountInGesture) {
            return false
        }

        return if (actionIndex == 1) isTwoFingerTapTouch(eventTime) else isTapTouch(eventTime)
    }

    // The first of two fingers lifts and its touch is still a tap: the two fingers are a right
    // click if the other finger lifts as a tap too.
    private fun isFirstOfTwoFingersTap(eventTime: Long): Boolean =
        actionIndex == 0 && pointerCount == 2 && isTwoFingerTapTouch(eventTime)

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
        // Get the view dimensions to scale inputs on this touch. A view with no size has no
        // scale (a factor of 0): touchMoveEvent sends nothing until a touch down finds a size.
        xFactor = if (targetView.width > 0) referenceWidth / targetView.width.toDouble() else 0.0
        yFactor = if (targetView.height > 0) referenceHeight / targetView.height.toDouble() else 0.0

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
            twoFingerTapPending = false
            distanceMoved = 0.0

            // The second finger starts the tap time of the two fingers. At index 0 it is the first
            // finger of this context: its move counts from where it landed.
            if (pointerCount == 2) {
                twoFingerTap.secondFingerDown(eventTime)
                markLanding()
            }

            // Only the first finger of a gesture drags, and only right after a tap
            clearSecondPress()
            secondPress = actionIndex == 0 && pointerCount == 1 && followsTap(eventTime)
            lastTapUpTime = null
            if (secondPress) {
                handler.postDelayed(dragTimerRunnable, TAP_TIME_THRESHOLD)
            }
        }

        return true
    }

    override fun touchUpEvent(eventX: Int, eventY: Int, eventTime: Long) {
        if (cancelled) {
            return
        }

        clearSecondPress()

        val buttonIndex = getMouseButtonIndex()
        val otherFingerLiftedFirst = twoFingerTapPending
        twoFingerTapPending = false

        if (confirmedDrag) {
            // Raise the button after a drag
            conn.sendMouseButtonUp(buttonIndex)
        } else if (otherFingerLiftedFirst && pointerCount == 1 && isTwoFingerTapTouch(eventTime)) {
            // The second finger of a two-finger tap lifts, the first one lifted before it
            clickButton(MouseButtonPacket.BUTTON_RIGHT)
        } else if (isTap(eventTime)) {
            clickButton(buttonIndex)
            if (buttonIndex == MouseButtonPacket.BUTTON_LEFT) {
                lastTapUpTime = eventTime
            }
        } else if (isFirstOfTwoFingersTap(eventTime)) {
            twoFingerTapPending = true
        }
    }

    private fun clickButton(buttonIndex: Byte) {
        // Lower the mouse button
        conn.sendMouseButtonDown(buttonIndex)

        // Release the mouse button in 100ms to allow for apps that use polling
        // to detect mouse button presses.
        val buttonUpRunnable =
            if (buttonIndex == MouseButtonPacket.BUTTON_RIGHT) {
                rightButtonUpRunnable
            } else {
                leftButtonUpRunnable
            }
        handler.removeCallbacks(buttonUpRunnable)
        handler.postDelayed(buttonUpRunnable, BUTTON_RELEASE_DELAY)
        if (buttonIndex == MouseButtonPacket.BUTTON_LEFT) {
            leftReleasePending = true
        }
    }

    // The press of a double tap is held or moved: the left button goes down until the lift.
    private fun startDrag() {
        clearSecondPress()
        confirmedDrag = true

        // The release of the click that came first may still be due: it goes out now, or it would
        // end the drag
        if (leftReleasePending) {
            handler.removeCallbacks(leftButtonUpRunnable)
            leftButtonUpRunnable.run()
        }
        conn.sendMouseButtonDown(getMouseButtonIndex())
    }

    // The press is not a second press any more: it lifted, it was cancelled, another finger joined
    // it, or it started its drag.
    private fun clearSecondPress() {
        secondPress = false
        handler.removeCallbacks(dragTimerRunnable)
    }

    // The second finger landed: the move of the finger of this context counts from here.
    private fun markLanding() {
        landingX = lastTouchX
        landingY = lastTouchY
        distanceSinceLanding = 0.0
    }

    // This context follows the first of two fingers: the second finger's context judges its own
    // moves, and the first finger's moves count from the landing (checkForTwoFingerMove).
    private val isFirstFingerOfTwo: Boolean
        get() = actionIndex == 0 && pointerCount == 2

    // The first finger moved past the tap box or the tap distance since the second one landed: no
    // two-finger tap.
    private fun checkForTwoFingerMove(eventX: Int, eventY: Int) {
        if (!isFirstFingerOfTwo) {
            return
        }

        distanceSinceLanding +=
            sqrt((eventX - lastTouchX).toDouble().pow(2) + (eventY - lastTouchY).toDouble().pow(2))
        if (
            abs(eventX - landingX) > TAP_MOVEMENT_THRESHOLD ||
                abs(eventY - landingY) > TAP_MOVEMENT_THRESHOLD ||
                distanceSinceLanding >= TAP_DISTANCE_THRESHOLD
        ) {
            twoFingerTap.fingerMoved()
        }
    }

    private fun confirmMove() {
        if (secondPress) {
            startDrag()
        } else {
            confirmedMove = true

            // The move of the first of two fingers is judged from the landing of the second, not
            // from the touch down of the first (checkForTwoFingerMove)
            if (!isFirstFingerOfTwo) {
                twoFingerTap.fingerMoved()
            }
        }
    }

    private fun checkForConfirmedMove(eventX: Int, eventY: Int) {
        // If we've already confirmed something, get out now
        if (confirmedMove || confirmedDrag) {
            return
        }

        // If it leaves the tap bounds, it's a move.
        if (!isWithinTapBounds(eventX, eventY)) {
            confirmMove()
            return
        }

        // Check if we've exceeded the maximum distance moved
        distanceMoved +=
            sqrt((eventX - lastTouchX).toDouble().pow(2) + (eventY - lastTouchY).toDouble().pow(2))
        if (distanceMoved >= TAP_DISTANCE_THRESHOLD) {
            confirmMove()
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
            checkForTwoFingerMove(eventX, eventY)

            // We only send moves and drags for the primary touch point
            if (actionIndex == 0) {
                if (!hasScale) {
                    return true
                }

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
                    // A move along x alone, or a y delta that scaled to zero, scrolls nothing
                    if (confirmedScroll && deltaY != 0) {
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
        twoFingerTapPending = false
        clearSecondPress()

        // If it was a confirmed drag, we'll need to raise the button now
        if (confirmedDrag) {
            conn.sendMouseButtonUp(getMouseButtonIndex())
        }
    }

    override val isCancelled: Boolean
        get() = cancelled

    override fun setPointerCount(pointerCount: Int) {
        // A new finger (the count rises) ends the two-finger tap that waited for the last finger
        // and takes the drag away from a second press
        if (pointerCount > this.pointerCount) {
            twoFingerTapPending = false
            clearSecondPress()
            markLanding()
        }
        this.pointerCount = pointerCount

        if (pointerCount > maxPointerCountInGesture) {
            maxPointerCountInGesture = pointerCount
        }
    }
}
