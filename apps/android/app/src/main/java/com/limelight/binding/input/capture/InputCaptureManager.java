package com.limelight.binding.input.capture;

import android.app.Activity;

import com.limelight.LimeLog;
import com.limelight.R;

public class InputCaptureManager {
    public static InputCaptureProvider getInputCaptureProvider(Activity activity) {
        LimeLog.info("Using Android O+ native mouse capture");
        return new AndroidNativePointerCaptureProvider(activity, activity.findViewById(R.id.surfaceView));
    }
}
