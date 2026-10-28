package io.github.magalz.borealis.input

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Looper
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.lang.ref.Reference
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The attach dialog of Google TV has no "use by default", so a user who turned the USB driver off
 * gets nothing from it, and neither does anybody on a TV: the activity is offered to the system
 * only while the driver is on and the device is not a TV.
 */
@RunWith(RobolectricTestRunner::class)
class UsbAttachGateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val component = ComponentName(context, UsbAttachActivity::class.java)

    @Suppress("DEPRECATION")
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    private fun setUsbDriver(enabled: Boolean) {
        preferences.edit().putBoolean(USB_DRIVER, enabled).commit()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun state() = context.packageManager.getComponentEnabledSetting(component)

    private fun attachHandlers(): List<String> =
        context.packageManager
            .queryIntentActivities(
                Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).setPackage(context.packageName),
                0,
            )
            .map { it.activityInfo.name }

    @Test
    fun `with the USB driver off the attach intent resolves to no activity`() {
        setUsbDriver(false)

        UsbAttachGate.start(context)

        assertThat(state()).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        assertThat(attachHandlers()).isEmpty()
    }

    @Test
    fun `with the USB driver on the attach intent resolves to the activity`() {
        setUsbDriver(true)

        UsbAttachGate.start(context)

        assertThat(attachHandlers()).containsExactly(UsbAttachActivity::class.java.name)
    }

    @Test
    fun `an absent setting counts as on`() {
        context.packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )

        UsbAttachGate.start(context)

        assertThat(state()).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        assertThat(attachHandlers()).containsExactly(UsbAttachActivity::class.java.name)
    }

    @Test
    fun `toggling the setting flips the component without a restart`() {
        setUsbDriver(true)
        // SharedPreferences holds its listeners weakly: the gate must stay referenced until the
        // end.
        val gate = UsbAttachGate.start(context)

        setUsbDriver(false)
        assertThat(attachHandlers()).isEmpty()

        setUsbDriver(true)
        assertThat(attachHandlers()).containsExactly(UsbAttachActivity::class.java.name)
        Reference.reachabilityFence(gate)
    }

    @Test
    fun `it writes only when the state changes`() {
        // The manifest default is enabled: with the driver on, nothing is written.
        setUsbDriver(true)
        UsbAttachGate.start(context)
        assertThat(state()).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

        // A component that is already off for another reason is left as it is.
        context.packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.DONT_KILL_APP,
        )
        setUsbDriver(false)
        UsbAttachGate.start(context)
        assertThat(state()).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
    }

    private fun asTelevision() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
    }

    @Test
    fun `on a TV the attach intent resolves to no activity even with the USB driver on`() {
        asTelevision()
        setUsbDriver(true)

        UsbAttachGate.start(context)

        assertThat(state()).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        assertThat(attachHandlers()).isEmpty()
    }

    @Test
    fun `on a TV an absent setting does not bring the activity back`() {
        asTelevision()

        UsbAttachGate.start(context)

        assertThat(attachHandlers()).isEmpty()
    }

    @Test
    fun `on a TV toggling the setting leaves the activity out`() {
        asTelevision()
        setUsbDriver(false)
        // SharedPreferences holds its listeners weakly: the gate must stay referenced until the
        // end.
        val gate = UsbAttachGate.start(context)

        setUsbDriver(true)

        assertThat(attachHandlers()).isEmpty()
        Reference.reachabilityFence(gate)
    }

    private companion object {
        const val USB_DRIVER = "checkbox_usb_driver"
    }
}
