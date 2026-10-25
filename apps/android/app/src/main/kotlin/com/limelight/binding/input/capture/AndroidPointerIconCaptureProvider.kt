package com.limelight.binding.input.capture

import android.app.Activity
import android.content.Context
import android.view.PointerIcon
import android.view.View

open class AndroidPointerIconCaptureProvider(activity: Activity, private val targetView: View) :
    InputCaptureProvider() {
    private val context: Context = activity

    override fun hideCursor() {
        super.hideCursor()
        targetView.pointerIcon = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
    }

    override fun showCursor() {
        super.showCursor()
        targetView.pointerIcon = null
    }
}
