package com.limelight.binding.input.capture

import android.app.Activity
import com.limelight.LimeLog
import com.limelight.R

object InputCaptureManager {
    @JvmStatic
    fun getInputCaptureProvider(activity: Activity): InputCaptureProvider {
        LimeLog.info("Using Android O+ native mouse capture")
        return AndroidNativePointerCaptureProvider(
            activity,
            activity.findViewById(R.id.surfaceView),
        )
    }
}
