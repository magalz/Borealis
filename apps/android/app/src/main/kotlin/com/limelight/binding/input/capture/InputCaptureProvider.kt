package com.limelight.binding.input.capture

import android.view.MotionEvent

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
@Suppress("AbstractClassCanBeConcreteClass")
abstract class InputCaptureProvider {
    @JvmField protected var isCapturing = false

    @JvmField protected var isCursorVisible = false

    open fun enableCapture() {
        isCapturing = true
        hideCursor()
    }

    open fun disableCapture() {
        isCapturing = false
        showCursor()
    }

    open fun destroy() {}

    open fun isCapturingActive(): Boolean {
        return isCapturing
    }

    open fun showCursor() {
        isCursorVisible = true
    }

    open fun hideCursor() {
        isCursorVisible = false
    }

    open fun eventHasRelativeMouseAxes(event: MotionEvent): Boolean {
        return false
    }

    open fun getRelativeAxisX(event: MotionEvent): Float {
        return 0f
    }

    open fun getRelativeAxisY(event: MotionEvent): Float {
        return 0f
    }

    open fun onWindowFocusChanged(focusActive: Boolean) {}
}
