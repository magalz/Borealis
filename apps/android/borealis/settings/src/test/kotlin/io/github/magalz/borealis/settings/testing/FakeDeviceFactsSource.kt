package io.github.magalz.borealis.settings.testing

import io.github.magalz.borealis.settings.device.DeviceFacts
import io.github.magalz.borealis.settings.device.DeviceFactsSource

/** A [DeviceFactsSource] that returns the [facts] it holds; a test may replace them. */
class FakeDeviceFactsSource(var facts: DeviceFacts = deviceFacts()) : DeviceFactsSource {
    /** How many times [facts] was asked for. */
    var calls: Int = 0
        private set

    override fun facts(): DeviceFacts {
        calls++
        return facts
    }
}
