package io.github.magalz.borealis.stream.input

import io.github.magalz.borealis.stream.input.GamepadButtons.GUIDE
import io.github.magalz.borealis.stream.input.GamepadButtons.RIGHT_BUMPER
import io.github.magalz.borealis.stream.input.GamepadButtons.SELECT
import io.github.magalz.borealis.stream.input.GamepadButtons.START

/** The buttons a context holds and which of them are emulated, after a button event. */
data class ButtonState(val inputMap: Int, val emulatingFlags: Int)

/**
 * Makes the Guide button for pads that have none.
 *
 * Start and the right bumper are the Guide button on a pad without Select, as long as they are
 * held. On a pad with Select and no Mode button, Start and Select held together first count down to
 * the menu chord, so they become a Guide tap when released before it fires. A pad with a Mode
 * button sends its own Guide button. On every pad Start and Select held together are held back from
 * the PC while the chord counts down; released early they reach it as a short press (see
 * [tapButtons]). Start or Select alone, or with another button, reach it at once.
 */
object GuideChord {
    /** Set in the emulating flags while the Guide button is a combination of other buttons. */
    const val EMULATING_SPECIAL = 0x1

    /**
     * How soon after the right bumper goes up a lone Start still counts as Start and the bumper.
     */
    private const val MAXIMUM_BUMPER_UP_DELAY_MS = 100

    /** Whether Start and Select stand for the Guide button on this pad. */
    fun mapsStartSelect(hasSelect: Boolean, hasMode: Boolean): Boolean = hasSelect && !hasMode

    /**
     * The buttons to send the PC as a short press when [update] ended a Start and Select press
     * early (both were held back while the chord was pending): the Guide button on a pad where they
     * stand for it, Start and Select themselves on the others. None when the chord did not end
     * early.
     */
    fun tapButtons(update: MenuChordDetector.Update, hasSelect: Boolean, hasMode: Boolean): Int =
        when {
            !update.earlyRelease -> 0
            mapsStartSelect(hasSelect, hasMode) -> GUIDE
            else -> START or SELECT
        }

    /** A button went down. */
    fun onButtonDown(
        inputMap: Int,
        emulatingFlags: Int,
        hasSelect: Boolean,
        hasMode: Boolean,
        eventTime: Long,
        lastRightBumperUpTime: Long,
    ): ButtonState {
        // A pad with Select uses Start and Select, which are a tap rather than a held button.
        if (hasMode || hasSelect) return ButtonState(inputMap, emulatingFlags)
        val combination =
            inputMap == (START or RIGHT_BUMPER) ||
                (inputMap == START &&
                    eventTime - lastRightBumperUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS)
        if (!combination) return ButtonState(inputMap, emulatingFlags)
        return ButtonState(
            (inputMap and (START or RIGHT_BUMPER).inv()) or GUIDE,
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
