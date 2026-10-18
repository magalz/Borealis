package io.github.magalz.borealis.input

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.preference.PreferenceManager

/**
 * Keeps [UsbAttachActivity] enabled only while the USB driver setting (`checkbox_usb_driver`, on by
 * default) is on and the device is not a TV, once at startup and again on every change of that
 * setting.
 *
 * With the driver off, the system's attach dialog gives the user nothing. On Google TV it gives
 * nothing either way: it cannot remember a choice, so the driver's own permission dialog is the
 * only one left. The component is taken out of the system's choices in both cases.
 */
class UsbAttachGate
private constructor(private val context: Context, private val preferences: SharedPreferences) {
    // SharedPreferences keeps its listeners weakly, so this field keeps the registration alive for
    // as long as the gate is referenced.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == USB_DRIVER) sync()
    }

    private fun begin() {
        sync()
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun sync() {
        val packageManager = context.packageManager
        val component = ComponentName(context, UsbAttachActivity::class.java)
        val television = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val wanted = preferences.getBoolean(USB_DRIVER, DEFAULT_USB_DRIVER) && !television
        // The manifest declares the component enabled, so the default state counts as enabled.
        val current =
            when (packageManager.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                else -> false
            }
        if (wanted == current) return
        packageManager.setComponentEnabledSetting(
            component,
            if (wanted) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    companion object {
        // The key and the default of PreferenceConfiguration, which owns the setting.
        private const val USB_DRIVER = "checkbox_usb_driver"
        private const val DEFAULT_USB_DRIVER = true

        /**
         * Starts keeping the component in step with the setting. The caller must keep the result
         * referenced (for example in a field of the Application).
         */
        @Suppress("DEPRECATION") // android.preference.PreferenceManager: the inherited setting lives there.
        fun start(context: Context): UsbAttachGate =
            UsbAttachGate(context, PreferenceManager.getDefaultSharedPreferences(context)).also {
                it.begin()
            }
    }
}
