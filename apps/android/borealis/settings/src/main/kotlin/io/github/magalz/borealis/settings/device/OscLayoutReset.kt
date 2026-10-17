package io.github.magalz.borealis.settings.device

/** Resets the layout of the on-screen controls. The app implements it. */
fun interface OscLayoutReset {
    /** Deletes the saved layout, so the next stream starts from the default layout. */
    fun reset()
}
