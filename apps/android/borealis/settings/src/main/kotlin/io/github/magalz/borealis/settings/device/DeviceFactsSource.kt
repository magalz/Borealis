package io.github.magalz.borealis.settings.device

/** Provides the [DeviceFacts] of this device. The app implements it. */
fun interface DeviceFactsSource {
    /**
     * The facts of this device. They do not change while the app runs, so a caller may keep them.
     */
    fun facts(): DeviceFacts
}
