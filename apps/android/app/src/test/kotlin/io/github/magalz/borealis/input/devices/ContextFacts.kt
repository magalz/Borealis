package io.github.magalz.borealis.input.devices

import android.view.MotionEvent

/** The axis a context reads for something it has none for. */
const val NO_AXIS = -1

/** The axes a context reads for the sticks, the triggers and the hat. */
data class Axes(
    val leftStickX: Int = MotionEvent.AXIS_X,
    val leftStickY: Int = MotionEvent.AXIS_Y,
    val rightStickX: Int = NO_AXIS,
    val rightStickY: Int = NO_AXIS,
    val leftTrigger: Int = NO_AXIS,
    val rightTrigger: Int = NO_AXIS,
    val hatX: Int = NO_AXIS,
    val hatY: Int = NO_AXIS,
    val triggersIdleNegative: Boolean = false,
)

/** What the handler decided about a device from its vendor, its name and its key layout. */
data class Quirks(
    val isNonStandardDualShock4: Boolean = false,
    val usesLinuxGamepadStandardFaceButtons: Boolean = false,
    val isNonStandardXboxBtController: Boolean = false,
    val isServal: Boolean = false,
    val backIsStart: Boolean = false,
    val modeIsSelect: Boolean = false,
    val searchIsMode: Boolean = false,
    val isDualShockStandaloneTouchpad: Boolean = false,
    val hasSelect: Boolean = false,
    val hasMode: Boolean = false,
    val hasPaddles: Boolean = false,
    val hasShare: Boolean = false,
)

/** The axes of this context, by the field names of the Java. */
fun ContextView.axes() =
    Axes(
        leftStickX = int("leftStickXAxis"),
        leftStickY = int("leftStickYAxis"),
        rightStickX = int("rightStickXAxis"),
        rightStickY = int("rightStickYAxis"),
        leftTrigger = int("leftTriggerAxis"),
        rightTrigger = int("rightTriggerAxis"),
        hatX = int("hatXAxis"),
        hatY = int("hatYAxis"),
        triggersIdleNegative = bool("triggersIdleNegative"),
    )

/** The quirks of this context, by the field names of the Java. */
fun ContextView.quirks() =
    Quirks(
        isNonStandardDualShock4 = bool("isNonStandardDualShock4"),
        usesLinuxGamepadStandardFaceButtons = bool("usesLinuxGamepadStandardFaceButtons"),
        isNonStandardXboxBtController = bool("isNonStandardXboxBtController"),
        isServal = bool("isServal"),
        backIsStart = bool("backIsStart"),
        modeIsSelect = bool("modeIsSelect"),
        searchIsMode = bool("searchIsMode"),
        isDualShockStandaloneTouchpad = bool("isDualShockStandaloneTouchpad"),
        hasSelect = bool("hasSelect"),
        hasMode = bool("hasMode"),
        hasPaddles = bool("hasPaddles"),
        hasShare = bool("hasShare"),
    )
