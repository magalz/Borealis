package io.github.magalz.borealis.stream.game.session

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.driver.UsbDriverService
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.phase
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

/**
 * The USB driver service that the activity binds when the setting is on: it waits for the first
 * frame of the stream before it shows a permission dialog, so the activity tells it when the
 * picture is up, whichever of the two comes first. The service also tells the activity when a
 * permission dialog opens and closes.
 */
internal class GameUsbServiceTest : GameTestBase() {
    private val component: ComponentName
        get() = ComponentName(rig.application, UsbDriverService::class.java)

    private val connection: ServiceConnection
        get() = rig.field("usbDriverServiceConnection")

    /** Whether the service shows the permission dialogs that it held back. */
    private fun asksNow(service: UsbDriverService): Boolean {
        val requests = ReflectionHelpers.getField<Any>(service, "permissionRequests")
        return ReflectionHelpers.getField(requests, "ready")
    }

    private fun boundService(): UsbDriverService {
        rig.preference("checkbox_usb_driver", true)
        return rig.usbDriverService()
    }

    // The first frame and the service

    @Test
    fun `the first frame tells a connected service that the picture is up`() {
        val service = boundService()
        rig.create()
        rig.game.connectionStarted()
        assertThat(asksNow(service)).isFalse()

        rig.firstFrame()
        ShadowLooper.idleMainLooper()

        assertThat(asksNow(service)).isTrue()
    }

    @Test
    fun `a service that connects before the first frame is not told that the picture is up`() {
        val service = boundService()

        rig.create()
        rig.game.connectionStarted()
        ShadowLooper.idleMainLooper()

        assertThat(asksNow(service)).isFalse()
    }

    @Test
    fun `a service that connects after the first frame is told at once`() {
        rig.create()
        rig.game.connectionStarted()
        rig.firstFrame()
        val service = rig.usbDriverService()

        connection.onServiceConnected(component, service.onBind(Intent()))
        ShadowLooper.idleMainLooper()

        assertThat(asksNow(service)).isTrue()
        assertThat(rig.field<Boolean>("connectedToUsbDriverService")).isTrue()
    }

    @Test
    fun `a service that connects to an activity that has no first frame yet is held back`() {
        rig.create()
        val service = rig.usbDriverService()

        connection.onServiceConnected(component, service.onBind(Intent()))
        ShadowLooper.idleMainLooper()

        assertThat(asksNow(service)).isFalse()
        assertThat(ReflectionHelpers.getField<Any?>(service, "listener"))
            .isSameInstanceAs(rig.field<Any>("controllerHandler"))
        assertThat(ReflectionHelpers.getField<Any?>(service, "stateListener"))
            .isSameInstanceAs(rig.game)
    }

    @Test
    fun `a first frame with no service to tell goes on to the session`() {
        rig.create()
        rig.game.connectionStarted()

        rig.firstFrame()

        assertThat(rig.phase).isInstanceOf(StreamPhase.Streaming::class.java)
    }

    // A service that goes away

    @Test
    fun `a service that goes away is forgotten and is not told about the first frame`() {
        val service = boundService()
        rig.create()
        rig.game.connectionStarted()
        assertThat(rig.field<Boolean>("connectedToUsbDriverService")).isTrue()

        connection.onServiceDisconnected(component)
        rig.firstFrame()
        ShadowLooper.idleMainLooper()

        assertThat(rig.field<Boolean>("connectedToUsbDriverService")).isFalse()
        assertThat(rig.field<Any?>("usbDriverBinder")).isNull()
        assertThat(asksNow(service)).isFalse()
    }

    @Test
    fun `a service that went away is not unbound when the activity is destroyed`() {
        boundService()
        rig.create()
        val bound = shadowOf(rig.application).boundServiceConnections.single()
        connection.onServiceDisconnected(component)

        rig.destroyActivity()

        assertThat(shadowOf(rig.application).unboundServiceConnections).doesNotContain(bound)
    }

    // The permission dialog of the service

    @Test
    fun `a permission dialog that is up keeps the window from entering picture in picture`() {
        rig.preference("checkbox_enable_pip", true)
        val parameters = rig.recordPictureInPicture()
        rig.create()

        rig.game.onUsbPermissionPromptStarting()
        rig.game.connectionStarted()
        rig.game.onUsbPermissionPromptCompleted()

        // The prompt: no. Connected with the prompt up: no. The prompt closed: yes.
        assertThat(parameters.map { it.isAutoEnterEnabled })
            .containsExactly(false, false, true)
            .inOrder()
    }

    @Test
    fun `a dialog that closes without having opened leaves the count below zero`() {
        rig.preference("checkbox_enable_pip", true)
        val parameters = rig.recordPictureInPicture()
        rig.streaming()

        rig.game.onUsbPermissionPromptCompleted()

        // The window may not enter on its own until the count is back at zero: the next dialog
        // that opens does that.
        assertThat(parameters.map { it.isAutoEnterEnabled }).containsExactly(true, false).inOrder()
        rig.game.onUsbPermissionPromptStarting()
        assertThat(parameters.map { it.isAutoEnterEnabled })
            .containsExactly(true, false, true)
            .inOrder()
    }
}
