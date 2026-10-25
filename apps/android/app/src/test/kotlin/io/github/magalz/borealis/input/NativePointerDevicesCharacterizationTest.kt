package io.github.magalz.borealis.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * While the cursor is hidden the provider listens to the devices: a mouse that appears gets the
 * pointer capture, and the capture goes when the last mouse does. A device that changes counts as
 * one that was removed and added. [CaptureRig]'s view says what [hasCapture] tells it.
 */
@RunWith(RobolectricTestRunner::class)
class NativePointerDevicesCharacterizationTest {
    private val rig = CaptureRig()

    @Test
    fun `a device that was added asks for capture when there is a mouse and none is held`() {
        rig.connect(MOUSE, Sources.MOUSE)

        rig.provider.onInputDeviceAdded(MOUSE)

        assertThat(rig.view.requests).isEqualTo(1)
        assertThat(rig.view.releases).isEqualTo(0)
    }

    @Test
    fun `a device that was added asks for nothing when the capture is held`() {
        rig.connect(MOUSE, Sources.MOUSE)
        rig.view.hasCapture = true

        rig.provider.onInputDeviceAdded(MOUSE)

        assertThat(rig.view.requests).isEqualTo(0)
    }

    @Test
    fun `a device that was added asks for nothing when no device can capture`() {
        rig.connect(KEYBOARD, Sources.KEYBOARD)

        rig.provider.onInputDeviceAdded(KEYBOARD)

        assertThat(rig.view.requests).isEqualTo(0)
    }

    @Test
    fun `a device that was removed releases the capture when no device can capture`() {
        rig.view.hasCapture = true

        rig.provider.onInputDeviceRemoved(MOUSE)

        assertThat(rig.view.releases).isEqualTo(1)
        assertThat(rig.view.requests).isEqualTo(0)
    }

    @Test
    fun `a device that was removed keeps the capture while another mouse is there`() {
        rig.connect(MOUSE, Sources.MOUSE)
        rig.view.hasCapture = true

        rig.provider.onInputDeviceRemoved(OTHER_MOUSE)

        assertThat(rig.view.releases).isEqualTo(0)
    }

    @Test
    fun `a device that was removed releases nothing when the capture is not held`() {
        rig.provider.onInputDeviceRemoved(MOUSE)

        assertThat(rig.view.releases).isEqualTo(0)
    }

    @Test
    fun `a device that changed asks for capture when there is a mouse and none is held`() {
        rig.connect(MOUSE, Sources.MOUSE)

        rig.provider.onInputDeviceChanged(MOUSE)

        assertThat(rig.view.requests).isEqualTo(1)
        assertThat(rig.view.releases).isEqualTo(0)
    }

    @Test
    fun `a device that changed leaves a held capture alone while a mouse is there`() {
        rig.connect(MOUSE, Sources.MOUSE)
        rig.view.hasCapture = true

        rig.provider.onInputDeviceChanged(MOUSE)

        assertThat(rig.view.requests).isEqualTo(0)
        assertThat(rig.view.releases).isEqualTo(0)
    }

    @Test
    fun `a device that changed releases a held capture when no device can capture any more`() {
        rig.connect(KEYBOARD, Sources.KEYBOARD)
        rig.view.hasCapture = true

        rig.provider.onInputDeviceChanged(KEYBOARD)

        assertThat(rig.view.releases).isEqualTo(1)
        assertThat(rig.view.requests).isEqualTo(0)
    }

    @Test
    fun `a device that changed with no capture and no mouse does nothing`() {
        rig.provider.onInputDeviceChanged(KEYBOARD)

        assertThat(rig.view.requests).isEqualTo(0)
        assertThat(rig.view.releases).isEqualTo(0)
    }

    private companion object {
        const val MOUSE = 3
        const val OTHER_MOUSE = 5
        const val KEYBOARD = 4
    }
}
