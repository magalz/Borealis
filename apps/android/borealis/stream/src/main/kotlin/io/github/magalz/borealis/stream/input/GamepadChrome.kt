package io.github.magalz.borealis.stream.input

/** What the gamepad handler needs from the chrome; `ControllerHandler` implements it. */
interface GamepadChrome {
    /** While [captured], the chrome owns the gamepad and the game gets no input from it. */
    fun setCaptured(captured: Boolean)

    /** Turns the gamepad-as-mouse mode on or off. */
    fun setMouseEmulation(enabled: Boolean)
}

/** Told when the gamepad chord that opens the menu (Select and Start held) is recognised. */
fun interface MenuChordListener {
    fun onMenuChord()
}
