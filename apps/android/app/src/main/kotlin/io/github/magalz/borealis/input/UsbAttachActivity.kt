package io.github.magalz.borealis.input

import android.app.Activity
import android.os.Bundle

/**
 * The target of the USB attach intent for the pads the drivers can claim (`usb_device_filter`).
 * Android asks the user once, when such a pad is attached, and can remember "use by default"; that
 * grant outlives the attachment, so no permission prompt appears at stream start. The dialog is the
 * system's: this activity only has to exist, show nothing and finish. It starts no stream and no
 * screen, and its theme is translucent so a stream running under it is paused for a moment but
 * never stopped.
 */
class UsbAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
