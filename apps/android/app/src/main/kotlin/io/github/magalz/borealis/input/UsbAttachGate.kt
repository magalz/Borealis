package io.github.magalz.borealis.input

import android.content.Context
import android.content.SharedPreferences

/** Keeps [UsbAttachActivity] enabled only while the USB driver setting is on. */
class UsbAttachGate private constructor(private val preferences: SharedPreferences) {
    companion object {
        fun start(context: Context): UsbAttachGate =
            start(context, context.getSharedPreferences("", 0))

        internal fun start(context: Context, preferences: SharedPreferences): UsbAttachGate =
            UsbAttachGate(preferences)
    }
}
