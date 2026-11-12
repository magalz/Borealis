package io.github.magalz.borealis.stream.game

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Looper
import android.os.Message
import android.view.InputDevice
import android.view.View
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.nvstream.http.NvApp
import com.limelight.preferences.GlPreferences
import com.limelight.ui.StreamView
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.host.video.FirstFrameWatchdog
import io.github.magalz.borealis.input.devices.connectPad
import io.github.magalz.borealis.input.devices.pad
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamPhase
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

/** What `Game.onCreate` builds and wires from the intent of a pinned shortcut. */
internal class GameCreateTest : GameTestBase() {
    private fun heldWifiLocks(): Int =
        shadowOf(rig.application.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .activeLockCount

    /**
     * The input classes that the activity handles: keyboards, gamepads, pointers, touchpads and
     * mice.
     */
    private val everyInputClass =
        InputDevice.SOURCE_CLASS_BUTTON or
            InputDevice.SOURCE_CLASS_JOYSTICK or
            InputDevice.SOURCE_CLASS_POINTER or
            InputDevice.SOURCE_CLASS_POSITION or
            InputDevice.SOURCE_CLASS_TRACKBALL

    private fun unbufferedSources(view: View): Int =
        ReflectionHelpers.getField(view, "mUnbufferedInputSource")

    /** The listener that a view of the activity holds in the field [name] of its listener info. */
    private fun listenerOf(view: View, name: String): Any? =
        ReflectionHelpers.getField<Any?>(
            ReflectionHelpers.getField<Any>(view, "mListenerInfo"),
            name,
        )

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
    fun `the Wi-Fi locks do not count their references`() {
        rig.create()

        for (name in listOf("highPerfWifiLock", "lowLatencyWifiLock")) {
            // The shadow of the lock keeps what the activity told the lock.
            val shadow = Shadow.extract<Any>(rig.field<WifiManager.WifiLock>(name))
            assertThat(ReflectionHelpers.getField<Boolean>(shadow, "refCounted")).isFalse()
        }
    }

    @Test
    fun `the two Wi-Fi locks are two locks`() {
        rig.create()

        val highPerf = rig.field<WifiManager.WifiLock>("highPerfWifiLock")
        assertThat(rig.field<WifiManager.WifiLock>("lowLatencyWifiLock"))
            .isNotSameInstanceAs(highPerf)
    }

    @Test
    fun `the decoder tombstone is kept in the DecoderTombstone preferences`() {
        rig.create()

        assertThat(rig.field<SharedPreferences>("tombstonePrefs"))
            .isSameInstanceAs(
                rig.application.getSharedPreferences("DecoderTombstone", Context.MODE_PRIVATE)
            )
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
                    StreamFailure(
                        FailureKind.GENERIC,
                        "No hardware accelerated H.264 decoder",
                        null,
                    )
                )
            )
        val view = rig.game.findViewById<StreamView>(R.id.surfaceView)
        assertThat(shadowOf(view).fakeSurfaceHolder.callbacks).isEmpty()
        assertThat(rig.game.isFinishing).isFalse()
    }

    @Test
    fun `the stream view asks for unbuffered input of every class that the activity handles`() {
        rig.create()

        val view = rig.game.findViewById<View>(R.id.surfaceView)
        assertThat(unbufferedSources(view)).isEqualTo(everyInputClass)
    }

    @Test
    fun `the background touch view asks for unbuffered input of every class too`() {
        rig.create()

        val view = rig.game.findViewById<View>(R.id.backgroundTouchView)
        assertThat(unbufferedSources(view)).isEqualTo(everyInputClass)
    }

    @Test
    fun `the stream view gives its keys to the activity through its key listener`() {
        rig.create()

        val view = rig.game.findViewById<View>(R.id.surfaceView)
        assertThat(listenerOf(view, "mOnKeyListener")).isSameInstanceAs(rig.game)
    }

    /** What the code run by [block] printed to the standard error stream. */
    private fun printedToStandardError(block: () -> Unit): String {
        val original = System.err
        val captured = ByteArrayOutputStream()
        System.setErr(PrintStream(captured, true))
        try {
            block()
        } finally {
            System.setErr(original)
        }
        return captured.toString()
    }

    @Test
    fun `the certificate is read before the app id ends the activity`() {
        val printed = printedToStandardError {
            rig.create(GameIntent(appId = null, serverCert = byteArrayOf(1, 2, 3)))
        }

        assertThat(printed).contains("CertificateException")
        assertThat(rig.game.isFinishing).isTrue()
    }

    @Test
    fun `a certificate that can be read prints nothing`() {
        val printed = printedToStandardError {
            rig.create(GameIntent(serverCert = GameIntent.certificate()))
        }

        assertThat(printed).doesNotContain("CertificateException")
    }

    @Test
    fun `the decoder is told the GL renderer that the GL preferences hold`() {
        GlPreferences.readPreferences(rig.application).apply {
            glRenderer = "Test GPU 1"
            writePreferences()
        }

        rig.create()

        assertThat(ReflectionHelpers.getField<String?>(rig.renderer, "glRenderer"))
            .isEqualTo("Test GPU 1")
    }

    private val gamepadMask: Int
        get() = rig.streamConfiguration.attachedGamepadMask

    private fun attachTwoGamepads() {
        connectPad(pad(40))
        connectPad(pad(7))
    }

    @Test
    fun `every gamepad that is attached takes a bit of the mask when multi-controller is on`() {
        attachTwoGamepads()

        rig.create()

        assertThat(gamepadMask).isEqualTo(0b11)
    }

    @Test
    fun `without multi-controller the mask is the first gamepad only, whatever is attached`() {
        attachTwoGamepads()
        rig.preference("checkbox_multi_controller", false)

        rig.create()

        assertThat(gamepadMask).isEqualTo(1)
    }

    @Test
    fun `the on-screen controller adds the first gamepad to the ones that are attached`() {
        connectPad(pad(40))
        connectPad(pad(7))
        connectPad(pad(9))
        rig.preference("checkbox_show_onscreen_controls", true)

        rig.create()

        assertThat(gamepadMask).isEqualTo(0b111)
    }

    @Test
    fun `without multi-controller the on-screen controller changes nothing`() {
        attachTwoGamepads()
        rig.preference("checkbox_multi_controller", false)
        rig.preference("checkbox_show_onscreen_controls", true)

        rig.create()

        assertThat(gamepadMask).isEqualTo(1)
    }

    /** How many messages of the USB driver service wait in the queue of the main looper. */
    private fun serviceMessagesPending(): Int {
        var pending = 0
        var message =
            ReflectionHelpers.getField<Message?>(Looper.getMainLooper().queue, "mMessages")
        while (message != null) {
            if (message.callback?.javaClass?.name?.contains("UsbDriverService") == true) pending++
            message = ReflectionHelpers.getField<Message?>(message, "next")
        }
        return pending
    }

    /** What the activity looked like at the moment the session heard of the first frame. */
    private data class AtFirstFrame(
        val firstFrameShown: Boolean,
        val watchdog: FirstFrameWatchdog.State,
        val serviceToldYet: Boolean,
    )

    @Test
    fun `at the first frame the activity knows it, the watchdog is satisfied and the service is told`() {
        rig.preference("checkbox_usb_driver", true)
        rig.usbDriverService()
        rig.create()
        rig.game.connectionStarted()
        ShadowLooper.idleMainLooper()
        val seen = mutableListOf<Pair<StreamPhase, AtFirstFrame>>()
        val watcher =
            CoroutineScope(UnconfinedTestDispatcher()).launch {
                rig.session.state.collect {
                    seen +=
                        it.phase to
                            AtFirstFrame(
                                firstFrameShown = rig.field("firstFrameShown"),
                                watchdog =
                                    ReflectionHelpers.getField<FirstFrameWatchdog>(
                                            rig.field<Any>("startupGuard"),
                                            "watchdog",
                                        )
                                        .state,
                                serviceToldYet = serviceMessagesPending() > 0,
                            )
                }
            }

        rig.firstFrame()
        watcher.cancel()

        val atStreaming = seen.single { it.first is StreamPhase.Streaming }.second
        assertThat(atStreaming)
            .isEqualTo(
                AtFirstFrame(
                    firstFrameShown = true,
                    watchdog = FirstFrameWatchdog.State.SATISFIED,
                    serviceToldYet = true,
                )
            )
    }
}
