package io.github.magalz.borealis.input.devices

import com.limelight.preferences.PreferenceConfiguration
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The base of the tests that make controller contexts from Android input devices. A context
 * reads the native `moonlight-core` library and the vibrators, lights and sensors of the device,
 * which Robolectric does not have, so the shadows of this package stand in for them. The rigs
 * a test makes are closed after it, which ends the handler's background thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowDevicesMoonBridge::class,
            ShadowDeviceHardware::class,
            ShadowDeviceVibratorManager::class,
            ShadowDeviceVibrator::class,
            ShadowDeviceLightsManager::class,
            ShadowSystemVibratorManager::class,
        ]
)
// Abstract so that Gradle does not run the base as a test of its own.
@Suppress("AbstractClassCanBeConcreteClass")
abstract class DevicesTestBase {
    private val rigs = mutableListOf<DevicesRig>()

    @Before
    fun resetShadows() {
        ShadowDevicesMoonBridge.reset()
        DeviceHardware.reset()
    }

    @After
    fun closeRigs() {
        rigs.forEach { it.close() }
    }

    /** A handler over [devices], with [configure] applied to its preferences. */
    protected fun rig(
        multiController: Boolean = false,
        devices: List<Pad> = emptyList(),
        configure: PreferenceConfiguration.() -> Unit = {},
    ): DevicesRig = DevicesRig(multiController, devices, configure).also(rigs::add)
}
