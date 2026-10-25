package com.limelight.binding.input.capture

import android.app.Activity
import android.hardware.input.InputManager
import android.os.Handler
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

/** How long after focus is regained the pointer is captured again. */
private const val RECAPTURE_DELAY_MS = 500L

// We extend AndroidPointerIconCaptureProvider because we want to also get the
// pointer icon hiding behavior over our stream view just in case pointer capture
// is unavailable on this system (ex: DeX, ChromeOS)
class AndroidNativePointerCaptureProvider(activity: Activity, private val targetView: View) :
    AndroidPointerIconCaptureProvider(activity, targetView), InputManager.InputDeviceListener {
    private val inputManager = activity.getSystemService(InputManager::class.java)

    // We only capture the pointer if we have a compatible InputDevice
    // present. This is a workaround for an Android 12 regression causing
    // incorrect mouse input when using the SPen.
    // https://github.com/moonlight-stream/moonlight-android/issues/1030
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("LoopWithTooManyJumpStatements")
    private fun hasCaptureCompatibleInputDevice(): Boolean {
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue

            // Skip touchscreens when considering compatible capture devices.
            // Samsung devices on Android 12 will report a sec_touchpad device
            // with SOURCE_TOUCHSCREEN, SOURCE_KEYBOARD, and SOURCE_MOUSE.
            // Upon enabling pointer capture, that device will switch to
            // SOURCE_KEYBOARD and SOURCE_TOUCHPAD.
            // Only skip on non ChromeOS devices cause the ChromeOS pointer else
            // gets disabled removing relative mouse capabilities
            // on Chromebooks with touchscreens
            if (
                device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN) &&
                    !targetView.context.packageManager.hasSystemFeature(
                        "org.chromium.arc.device_management"
                    )
            ) {
                continue
            }

            if (
                device.supportsSource(InputDevice.SOURCE_MOUSE) ||
                    device.supportsSource(InputDevice.SOURCE_MOUSE_RELATIVE) ||
                    device.supportsSource(InputDevice.SOURCE_TOUCHPAD)
            ) {
                return true
            }
        }

        return false
    }

    override fun showCursor() {
        super.showCursor()

        // It is important to unregister the listener *before* releasing pointer capture,
        // because releasing pointer capture can cause an onInputDeviceChanged() callback
        // for devices with a touchpad (like a DS4 controller).
        inputManager.unregisterInputDeviceListener(this)
        targetView.releasePointerCapture()
    }

    override fun hideCursor() {
        super.hideCursor()

        // Listen for device events to enable/disable capture
        inputManager.registerInputDeviceListener(this, null)

        // Capture now if we have a capture-compatible device
        if (hasCaptureCompatibleInputDevice()) {
            targetView.requestPointerCapture()
        }
    }

    override fun onWindowFocusChanged(focusActive: Boolean) {
        // NB: We have to check cursor visibility here because Android pointer capture
        // doesn't support capturing the cursor while it's visible. Enabling pointer
        // capture implicitly hides the cursor.
        if (!focusActive || !isCapturing || isCursorVisible) {
            return
        }

        // Recapture the pointer if focus was regained. On Android Q,
        // we have to delay a bit before requesting capture because otherwise
        // we'll hit the "requestPointerCapture called for a window that has no focus"
        // error and it will not actually capture the cursor.
        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
        @Suppress("DEPRECATION") val h = Handler()
        h.postDelayed(
            {
                if (hasCaptureCompatibleInputDevice()) {
                    targetView.requestPointerCapture()
                }
            },
            RECAPTURE_DELAY_MS,
        )
    }

    override fun eventHasRelativeMouseAxes(event: MotionEvent): Boolean {
        // SOURCE_MOUSE_RELATIVE is how SOURCE_MOUSE appears when our view has pointer capture.
        // SOURCE_TOUCHPAD will have relative axes populated iff our view has pointer capture.
        // See https://developer.android.com/reference/android/view/View#requestPointerCapture()
        val eventSource = event.source
        return (eventSource == InputDevice.SOURCE_MOUSE_RELATIVE &&
            event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) ||
            (eventSource == InputDevice.SOURCE_TOUCHPAD && targetView.hasPointerCapture())
    }

    override fun getRelativeAxisX(event: MotionEvent): Float {
        val axis =
            if (event.source == InputDevice.SOURCE_MOUSE_RELATIVE) {
                MotionEvent.AXIS_X
            } else {
                MotionEvent.AXIS_RELATIVE_X
            }
        var x = event.getAxisValue(axis)
        for (i in 0 until event.historySize) {
            x += event.getHistoricalAxisValue(axis, i)
        }
        return x
    }

    override fun getRelativeAxisY(event: MotionEvent): Float {
        val axis =
            if (event.source == InputDevice.SOURCE_MOUSE_RELATIVE) {
                MotionEvent.AXIS_Y
            } else {
                MotionEvent.AXIS_RELATIVE_Y
            }
        var y = event.getAxisValue(axis)
        for (i in 0 until event.historySize) {
            y += event.getHistoricalAxisValue(axis, i)
        }
        return y
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        // Check if we've added a capture-compatible device
        if (!targetView.hasPointerCapture() && hasCaptureCompatibleInputDevice()) {
            targetView.requestPointerCapture()
        }
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        // Check if the capture-compatible device was removed
        if (targetView.hasPointerCapture() && !hasCaptureCompatibleInputDevice()) {
            targetView.releasePointerCapture()
        }
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        // Emulating a remove+add should be sufficient for our purposes.
        //
        // Note: This callback must be handled carefully because it can happen as a result of
        // calling requestPointerCapture(). This can cause trackpad devices to gain
        // SOURCE_MOUSE_RELATIVE and re-enter this callback.
        onInputDeviceRemoved(deviceId)
        onInputDeviceAdded(deviceId)
    }
}
