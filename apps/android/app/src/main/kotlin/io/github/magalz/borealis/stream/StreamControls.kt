package io.github.magalz.borealis.stream

import android.view.View
import com.limelight.binding.input.touch.TouchContext
import io.github.magalz.borealis.stream.input.GamepadChrome

/**
 * The parts of the running stream that [GameStreamActions] drives besides the connection: the
 * gamepad handler, the touch contexts the activity reads, the stream view and the chrome's panel.
 */
class StreamControls(
    val controller: GamepadChrome,
    /** The array the activity reads its touches from; touch mode changes replace its entries. */
    val touchContexts: Array<TouchContext?>,
    val streamView: View,
    /** Closes the panel that is open over the stream. */
    val closePanel: Runnable,
    /** The contexts for a touch mode; null when the mode has none (the touchscreen is off). */
    val buildTouch: (TouchMode) -> Array<TouchContext>?,
    /**
     * Stops the connection and runs the [Runnable] when it has stopped, on the thread that stopped
     * it; at once when no connection runs.
     */
    val stopConnection: (Runnable) -> Unit,
)
