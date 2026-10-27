package io.github.magalz.borealis.stream.game

import android.content.Context
import android.net.wifi.WifiManager
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.nvstream.http.NvApp
import com.limelight.ui.StreamView
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamPhase
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

/** What `Game.onCreate` builds and wires from the intent of a pinned shortcut. */
internal class GameCreateTest : GameTestBase() {
    private fun heldWifiLocks(): Int =
        shadowOf(rig.application.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .activeLockCount

    private fun nothingWasBuilt() {
        for (name in listOf("conn", "decoderRenderer", "streamSession", "controllerHandler")) {
            assertThat(ReflectionHelpers.getField<Any?>(rig.game, name)).isNull()
        }
    }

    @Test
    fun `an intent without an app id ends the activity before the stream is built`() {
        rig.create(GameIntent(appId = null))

        assertThat(rig.game.isFinishing).isTrue()
        nothingWasBuilt()
    }

    @Test
    fun `the app id 0 is the invalid id`() {
        rig.create(GameIntent(appId = 0))

        assertThat(rig.game.isFinishing).isTrue()
        nothingWasBuilt()
    }

    @Test
    fun `the Wi-Fi locks are taken before the app id is checked, and released with the activity`() {
        rig.create(GameIntent(appId = null))
        assertThat(heldWifiLocks()).isEqualTo(2)

        rig.destroyActivity()

        assertThat(heldWifiLocks()).isEqualTo(0)
    }

    @Test
    fun `the host, the ports and the unique id of the extras reach the connection`() {
        rig.create()

        val address = checkNotNull(rig.connectionContext.serverAddress)
        assertThat(address.address).isEqualTo("192.168.1.5")
        assertThat(address.port).isEqualTo(47989)
        assertThat(rig.connectionContext.httpsPort).isEqualTo(47984)
        assertThat(ReflectionHelpers.getField<String>(rig.connection, "uniqueId"))
            .isEqualTo("0123456789ABCDEF")
    }

    @Test
    fun `the ports default to 47989 and to unknown`() {
        rig.create(GameIntent(port = null, httpsPort = null))

        assertThat(checkNotNull(rig.connectionContext.serverAddress).port).isEqualTo(47989)
        assertThat(rig.connectionContext.httpsPort).isEqualTo(0)
    }

    @Test
    fun `the app of the extras is the one that the stream asks the host for`() {
        rig.create(GameIntent(hdr = true))

        val app = checkNotNull(rig.streamConfiguration.app)
        assertThat(app.appName).isEqualTo("Desktop")
        assertThat(app.appId).isEqualTo(881448767)
        assertThat(app.isHdrSupported).isTrue()
        assertThat(rig.field<NvApp>("app")).isSameInstanceAs(app)
    }

    @Test
    fun `an app without a name is called app`() {
        rig.create(GameIntent(appName = null))

        assertThat(checkNotNull(rig.streamConfiguration.app).appName).isEqualTo("app")
        assertThat(rig.field<String?>("appName")).isNull()
    }

    @Test
    fun `the server certificate of the extras reaches the connection`() {
        rig.create(GameIntent(serverCert = GameIntent.certificate()))

        assertThat(checkNotNull(rig.connectionContext.serverCert).encoded)
            .isEqualTo(GameIntent.certificate())
    }

    @Test
    fun `a certificate that cannot be read is left out and the stream goes on`() {
        rig.create(GameIntent(serverCert = byteArrayOf(1, 2, 3)))

        assertThat(rig.connectionContext.serverCert).isNull()
        assertThat(rig.game.isFinishing).isFalse()
        assertThat(rig.phase).isEqualTo(StreamPhase.Starting(StartStage.CONNECTING))
    }

    @Test
    fun `the activity is full screen and keeps the media volume keys`() {
        rig.create()

        val flags = rig.game.window.attributes.flags
        assertThat(flags and WindowManager.LayoutParams.FLAG_FULLSCREEN).isNotEqualTo(0)
        assertThat(flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN).isNotEqualTo(0)
        assertThat(rig.game.volumeControlStream).isEqualTo(android.media.AudioManager.STREAM_MUSIC)
    }

    @Test
    fun `the stream view takes the callbacks of the activity`() {
        rig.create()

        val view = rig.game.findViewById<StreamView>(R.id.surfaceView)
        assertThat(shadowOf(view).fakeSurfaceHolder.callbacks).containsExactly(rig.game)
    }

    @Test
    fun `without a hardware AVC decoder the session fails and the surface is not watched`() {
        rig.decoders()

        rig.create()

        assertThat(rig.phase)
            .isEqualTo(
                StreamPhase.Failed(
                    StreamFailure(FailureKind.GENERIC, "No hardware accelerated H.264 decoder", null)
                )
            )
        val view = rig.game.findViewById<StreamView>(R.id.surfaceView)
        assertThat(shadowOf(view).fakeSurfaceHolder.callbacks).isEmpty()
        assertThat(rig.game.isFinishing).isFalse()
    }
}
