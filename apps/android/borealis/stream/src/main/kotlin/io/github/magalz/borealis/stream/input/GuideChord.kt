package io.github.magalz.borealis.stream.input

import io.github.magalz.borealis.stream.input.GamepadButtons.GUIDE
import io.github.magalz.borealis.stream.input.GamepadButtons.RIGHT_BUMPER
import io.github.magalz.borealis.stream.input.GamepadButtons.SELECT
import io.github.magalz.borealis.stream.input.GamepadButtons.START

/** The buttons a context holds and which of them are emulated, after a button event. */
data class ButtonState(val inputMap: Int, val emulatingFlags: Int)

/** Turns button combinations into the Guide button for pads that have no Guide button. */
object GuideChord {
    /** Set in the emulating flags while the Guide button is a combination of other buttons. */
    const val EMULATING_SPECIAL = 0x1

    /**
     * How soon after the right bumper goes up a lone Start still counts as Start and the bumper.
     */
    private const val MAXIMUM_BUMPER_UP_DELAY_MS = 100

    /** A button went down. A pad with a Mode button sends its own Guide button. */
    fun onButtonDown(
        inputMap: Int,
        emulatingFlags: Int,
        hasSelect: Boolean,
        hasMode: Boolean,
        eventTime: Long,
        lastRightBumperUpTime: Long,
    ): ButtonState {
        if (hasMode) return ButtonState(inputMap, emulatingFlags)
        // With a physical Select button Start and Select are the combination, otherwise Start and
        // the right bumper.
        val combination =
            if (hasSelect) {
                inputMap == (START or SELECT)
            } else {
                inputMap == (START or RIGHT_BUMPER) ||
                    (inputMap == START &&
                        eventTime - lastRightBumperUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS)
            }
        if (!combination) return ButtonState(inputMap, emulatingFlags)
        return ButtonState(
            (inputMap and (START or SELECT or RIGHT_BUMPER).inv()) or GUIDE,
            emulatingFlags or EMULATING_SPECIAL,
        )
    }

    /** A button went up. The emulated Guide button ends with its combination. */
    fun onButtonUp(inputMap: Int, emulatingFlags: Int): ButtonState {
        if (emulatingFlags and EMULATING_SPECIAL == 0) return ButtonState(inputMap, emulatingFlags)
        val startUp = inputMap and START == 0
        val partnerUp = inputMap and SELECT == 0 && inputMap and RIGHT_BUMPER == 0
        if (!startUp && !partnerUp) return ButtonState(inputMap, emulatingFlags)
        return ButtonState(inputMap and GUIDE.inv(), emulatingFlags and EMULATING_SPECIAL.inv())
    }
}
