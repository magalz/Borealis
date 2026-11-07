package io.github.magalz.borealis.input.mouserelease

import android.view.KeyEvent.KEYCODE_BUTTON_START
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import io.github.magalz.borealis.input.ControllerHandlerRig
import io.github.magalz.borealis.input.FakeUsbController

/** A USB pad on controller 1, added with the mouse mode on for every pad. */
internal fun ControllerHandlerRig.usbPadInMouseMode() =
    FakeUsbController(1, handler).also {
        handler.deviceAdded(it)
        handler.setMouseEmulation(true)
    }

/** The system pad of [ControllerHandlerRig.useSystemPad], connected with the mouse mode on. */
internal fun ControllerHandlerRig.systemPadInMouseMode() {
    useSystemPad()
    handler.setMouseEmulation(true)
}

/**
 * Holds Start on top of what [pad] holds, long enough to flip its mouse mode, and lets Start go,
 * keeping the rest.
 */
internal fun ControllerHandlerRig.holdStartOnUsbPadAndLetGo(pad: FakeUsbController, held: Int = 0) {
    pad.report(held or PLAY_FLAG)
    advanceBy(800)
    pad.report(held)
}

/**
 * A Start hold on the system pad, or on the default context when no system pad is connected, long
 * enough to flip the mouse mode of the pad, and its release.
 */
internal fun ControllerHandlerRig.holdStartOnSystemPadAndLetGo() {
    press(KEYCODE_BUTTON_START)
    advanceBy(800)
    release(KEYCODE_BUTTON_START)
}
