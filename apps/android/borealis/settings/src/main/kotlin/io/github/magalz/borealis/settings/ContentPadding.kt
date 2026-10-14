package io.github.magalz.borealis.settings

import android.app.Activity

/** Padding of the settings content: system bars and cutouts on handhelds, overscan on TV. */
internal object ContentPadding {
    data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun compute(isTelevision: Boolean, density: Float, systemInsets: Padding): Padding =
        systemInsets

    fun install(activity: Activity) = Unit
}
