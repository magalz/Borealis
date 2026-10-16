package io.github.magalz.borealis.input

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.w3c.dom.Element

/**
 * A supported USB pad is offered to Borealis when it is attached, so Android asks once and can
 * remember "use by default" instead of prompting at every stream start.
 */
@RunWith(RobolectricTestRunner::class)
class UsbAttachActivityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun attachHandlers(): List<ActivityInfo> =
        context.packageManager
            .queryIntentActivities(
                Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).setPackage(context.packageName),
                PackageManager.GET_META_DATA,
            )
            .map { it.activityInfo }

    private fun handler(): ActivityInfo {
        assertThat(attachHandlers()).hasSize(1)
        return attachHandlers().first()
    }

    @Test
    fun `the attach intent resolves to the activity and to nothing else`() {
        assertThat(attachHandlers().map { it.name })
            .containsExactly(UsbAttachActivity::class.java.name)
    }

    @Test
    fun `the activity is exported and shows nothing`() {
        val info = handler()

        assertThat(info.exported).isTrue()
        val theme = context.resources.newTheme().apply { applyStyle(info.theme, true) }
        val noDisplay = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.windowNoDisplay, noDisplay, true)
        assertThat(noDisplay.data).isNotEqualTo(0)
    }

    @Test
    fun `the attach intent names the device filter`() {
        val info = handler()

        assertThat(info.metaData).isNotNull()
        val resource = info.metaData.getInt(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        assertThat(context.resources.getResourceEntryName(resource)).isEqualTo("usb_device_filter")
    }

    @Test
    fun `the filter holds the class, subclass and protocol of every claimable pad`() {
        val devices =
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(File("src/main/res/xml/usb_device_filter.xml"))
                .getElementsByTagName("usb-device")
        val triples =
            (0 until devices.length).map {
                val device = devices.item(it) as Element
                listOf("class", "subclass", "protocol").map(device::getAttribute)
            }

        assertThat(triples)
            .containsExactly(
                listOf("255", "93", "1"), // Xbox 360 wired and XInput dongles
                listOf("255", "93", "129"), // Xbox 360 wireless receiver
                listOf("255", "71", "208"), // Xbox One
            )
    }

    @Test
    fun `an attach finishes the activity without starting another`() {
        val controller =
            Robolectric.buildActivity(
                    UsbAttachActivity::class.java,
                    Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED),
                )
                .setup()

        assertThat(controller.get().isFinishing).isTrue()
        assertThat(shadowOf(controller.get()).nextStartedActivity).isNull()
    }
}
