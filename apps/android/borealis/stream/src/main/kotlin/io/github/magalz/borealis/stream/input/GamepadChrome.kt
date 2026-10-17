package io.github.magalz.borealis.stream.input

/** What the gamepad handler needs from the chrome; `ControllerHandler` implements it. */
interface GamepadChrome {
    /** While [captured], the chrome owns the gamepad and the game gets no input from it. */
    fun setCaptured(captured: Boolean)

    /** Turns the gamepad-as-mouse mode on or off. */
    fun setMouseEmulation(enabled: Boolean)

    /** Scales the speed of the gamepad mouse for this session: 1 is the speed of the setting. */
    fun setPointerSpeed(factor: Float)
}

/** Told when the gamepad chord that opens the menu (Select and Start held) is recognised. */
fun interface MenuChordListener {
    fun onMenuChord()
}
