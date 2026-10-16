package io.github.magalz.borealis.stream.input

/** The buttons a context holds and which of them are emulated, after a button event. */
data class ButtonState(val inputMap: Int, val emulatingFlags: Int)

/** Turns button combinations into the Guide button for pads that have no Guide button. */
object GuideChord {
    /** Set in the emulating flags while the Guide button is a combination of other buttons. */
    const val EMULATING_SPECIAL = 0x1

    fun onButtonDown(
        inputMap: Int,
        emulatingFlags: Int,
        hasSelect: Boolean,
        hasMode: Boolean,
        eventTime: Long,
        lastRightBumperUpTime: Long,
    ): ButtonState = ButtonState(inputMap, emulatingFlags)

    fun onButtonUp(inputMap: Int, emulatingFlags: Int): ButtonState =
        ButtonState(inputMap, emulatingFlags)
}
