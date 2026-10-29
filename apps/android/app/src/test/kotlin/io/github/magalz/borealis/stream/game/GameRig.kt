package io.github.magalz.borealis.stream.game

import android.app.ActivityManager
import android.app.Application
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ConfigurationInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.preference.PreferenceManager
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import com.limelight.Game
import com.limelight.binding.input.driver.UsbDriverService
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.codec
import com.limelight.binding.video.installCodecs
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.host.launch.SessionAudit
import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditSink
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.logging.Redactor
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.android.controller.ServiceController
import org.robolectric.android.internal.DisplayConfig
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.util.ReflectionHelpers

/**
 * The extras of the intent that starts the stream, under the literal key names of the persistent
 * format (pinned shortcuts carry them), not under the constants of `Game`. A null value leaves its
 * extra out. A test changes one extra with `GameIntent().copy(appName = null)`, or by naming it in
 * the constructor.
 */
internal data class GameIntent(
    val host: String? = HOST,
    val port: Int? = PORT,
    val httpsPort: Int? = HTTPS_PORT,
    val appName: String? = APP_NAME,
    val appId: Int? = APP_ID,
    val uniqueId: String? = UNIQUE_ID,
    val uuid: String? = PC_UUID,
    val pcName: String? = PC_NAME,
    val hdr: Boolean? = false,
    val serverCert: ByteArray? = null,
    val resumeChosen: Boolean? = null,
) {
    fun build(application: Application): Intent =
        Intent().setClassName(application, Game::class.java.name).also { intent ->
            host?.let { intent.putExtra("Host", it) }
            port?.let { intent.putExtra("Port", it) }
            httpsPort?.let { intent.putExtra("HttpsPort", it) }
            appName?.let { intent.putExtra("AppName", it) }
            appId?.let { intent.putExtra("AppId", it) }
            uniqueId?.let { intent.putExtra("UniqueId", it) }
            uuid?.let { intent.putExtra("UUID", it) }
            pcName?.let { intent.putExtra("PcName", it) }
            hdr?.let { intent.putExtra("HDR", it) }
            serverCert?.let { intent.putExtra("ServerCert", it) }
            resumeChosen?.let { intent.putExtra("ResumeChosen", it) }
        }

    companion object {
        const val HOST = "192.168.1.5"
        const val PORT = 47989
        const val HTTPS_PORT = 47984
        const val APP_NAME = "Desktop"
        const val APP_ID = 881448767
        const val UNIQUE_ID = "0123456789ABCDEF"
        const val PC_UUID = "pc-uuid-1"
        const val PC_NAME = "Den PC"

        /** The DER form of a certificate, as `ServerHelper` writes it into the extras. */
        fun certificate(): ByteArray = TestIdentities.clientEc.certificate.encoded
    }
}

/**
 * A display mode: `Display.Mode` has no public constructor, and this is what the display shadow
 * takes.
 */
internal data class DisplayMode(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
) {
    fun toMode(): Display.Mode =
        Display.Mode::class
            .java
            .getDeclaredConstructor(
                Int::class.java,
                Int::class.java,
                Int::class.java,
                Float::class.java,
            )
            .apply { isAccessible = true }
            .newInstance(id, width, height, refreshRate)
}

/** The name of the hardware AVC decoder that the rig installs. */
internal const val AVC_DECODER = "c2.test.avc.decoder"

/** The connection starts and, this long after, the activity grabs the input. */
internal const val GRAB_DELAY_MS = 500L

/** The class names of the threads that `Game` starts: `Game$` and a number or a name. */
private const val STOP_THREAD = "com.limelight.Game$"

private const val STOP_WAIT_MS = 30_000L
private const val GL_3_2 = 0x30002
private const val HDR_NITS = 500f
private const val SHIELD_PACKAGE = "com.nvidia.blakepairing"
private const val SHIELD_SERVICE = "com.nvidia.blakepairing.AccessoryService"

/**
 * Builds the stream activity [Game] from the intent of a pinned shortcut and brings it to the
 * states that the lifecycle and input tests need. The rig never delivers `surfaceChanged`, which
 * starts the real `NvConnection` thread (HTTP to the host of the extras): the connection state
 * comes from the listener callbacks that `Game` implements, which the rig calls directly, the way
 * the native library does. `surfaceDestroyed` and `surfaceChanged` after a connection attempt are
 * reached with [markConnectionAttempted] instead.
 *
 * Use it from a test of [GameTestBase], which sets the Robolectric configuration and destroys the
 * rig after each test. The activity reads the default shared preferences of the application: a test
 * sets them with [preference] before [create]. The USB driver preference is off, because it binds a
 * service; the network is an unmetered Wi-Fi, because a metered one shows a toast; and a hardware
 * AVC decoder exists, because without one the activity stops after its wiring.
 *
 * The rig reads the private state of the activity by name ([field]) where the activity gives no
 * other way to see what it built. A conversion of `Game` keeps those names.
 */
@Suppress("DEPRECATION") // The preferences of the app are the default shared preferences.
internal class GameRig {
    val application: Application = ApplicationProvider.getApplicationContext()

    /** What the stream audit trail recorded, in order. */
    val audit = CopyOnWriteArrayList<AuditEvent>()

    private var controller: ActivityController<Game>? = null
    private var stage = Stage.RESUMED
    private var usbService: ServiceController<UsbDriverService>? = null
    private var pictureInPicture: Pair<Any, Any?>? = null
    private val pictureInPictureParams = CopyOnWriteArrayList<PictureInPictureParams>()

    /** The activity, once [create] ran. */
    val game: Game
        get() = checkNotNull(controller) { "the rig has no activity yet" }.get()

    init {
        BorealisAudit.install(AuditSink { audit += it }, Redactor(), "test")
        preference("checkbox_usb_driver", false)
        decoders(codec(AVC_DECODER))
        fixOpenGl()
        network(metered = false)
        // The SHIELD extension service does not exist here; without this the bind answers with no
        // binder and the extension manager of the controller handler fails.
        shadowOf(application)
            .declareComponentUnbindable(ComponentName(SHIELD_PACKAGE, SHIELD_SERVICE))
    }

    /** Puts a boolean in the default shared preferences that the activity reads. */
    fun preference(key: String, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(application)
            .edit()
            .putBoolean(key, value)
            .commit()
    }

    /** Puts a string in the default shared preferences that the activity reads. */
    fun preference(key: String, value: String) {
        PreferenceManager.getDefaultSharedPreferences(application)
            .edit()
            .putString(key, value)
            .commit()
    }

    /** Puts an int in the default shared preferences that the activity reads. */
    fun preference(key: String, value: Int) {
        PreferenceManager.getDefaultSharedPreferences(application)
            .edit()
            .putInt(key, value)
            .commit()
    }

    /** What the device lists as its video decoders, in this order, in place of any list before. */
    fun decoders(vararg decoders: MediaCodecInfo) {
        installCodecs(*decoders)
    }

    /**
     * The OpenGL ES version that the device reports. It is always 3.2, and the GL renderer name of
     * the GL preferences is empty (the one test that sets one uses a name no decoder rule reads).
     * `MediaCodecHelper` reads both once, the first time it runs in a sandbox, and keeps what it
     * read for every later test of the sandbox: a value that a test chose would count or not by the
     * order of the tests. Nothing that `Game` does with the activity depends on them.
     */
    private fun fixOpenGl() {
        val manager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(manager)
            .setDeviceConfigurationInfo(ConfigurationInfo().apply { reqGlEsVersion = GL_3_2 })
    }

    /**
     * The active network: a mobile one, which Android reports as metered, or an unmetered Wi-Fi.
     */
    fun network(metered: Boolean) {
        val manager = application.getSystemService(Context.CONNECTIVITY_SERVICE)
        val type = if (metered) ConnectivityManager.TYPE_MOBILE else ConnectivityManager.TYPE_WIFI
        val info =
            ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                type,
                0,
                true,
                NetworkInfo.State.CONNECTED,
            )
        shadowOf(manager as ConnectivityManager).setActiveNetworkInfo(info)
    }

    /**
     * The modes that the default display supports: [current], which the display is in, and
     * [others]. The display is in the mode with the id 0, whose refresh rate is the one that the
     * display reports.
     */
    fun displayModes(current: DisplayMode, vararg others: DisplayMode) {
        val modes = (listOf(current.copy(id = 0)) + others).map { it.toMode() }.toTypedArray()
        // The modes that apps see, and the modes that the system has, whose mode 0 is the current
        // one.
        ShadowDisplayManager.setSupportedModes(Display.DEFAULT_DISPLAY, *modes)
        val change =
            ShadowDisplayManager::class
                .java
                .getDeclaredMethod("changeDisplay", Int::class.java, Consumer::class.java)
                .apply { isAccessible = true }
        change.invoke(
            null,
            Display.DEFAULT_DISPLAY,
            Consumer<DisplayConfig> {
                it.supportedModes = modes
                it.modeId = 0
                it.defaultModeId = 0
            },
        )
        shadowOf(defaultDisplay()).setRefreshRate(current.refreshRate)
    }

    private fun defaultDisplay(): Display =
        application.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    /** What the default display can do with HDR: [types] are `Display.HdrCapabilities` types. */
    fun displayHdr(vararg types: Int) {
        shadowOf(defaultDisplay())
            .setDisplayHdrCapabilities(Display.DEFAULT_DISPLAY, HDR_NITS, HDR_NITS, 0.1f, *types)
    }

    /** Gives the device a system feature, such as television or picture in picture. */
    fun feature(name: String) {
        shadowOf(application.packageManager).setSystemFeature(name, true)
    }

    /**
     * Records the picture-in-picture parameters that the activity sets, in order. The framework
     * hands them to the activity manager, which a unit test does not have: the rig stands in for
     * the activity client of the process, and [destroy] puts the original back.
     */
    fun recordPictureInPicture(): List<PictureInPictureParams> {
        feature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        val client = Class.forName("android.app.ActivityClient")
        val singleton = ReflectionHelpers.getStaticField<Any>(client, "INTERFACE_SINGLETON")
        val contract = Class.forName("android.app.IActivityClientController")
        pictureInPicture = singleton to ReflectionHelpers.getField<Any?>(singleton, "mInstance")
        val stand =
            Proxy.newProxyInstance(contract.classLoader, arrayOf(contract)) { _, method, args ->
                if (method.name == "setPictureInPictureParams") {
                    pictureInPictureParams += args[1] as PictureInPictureParams
                }
                when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        ReflectionHelpers.setField(singleton, "mInstance", stand)
        return pictureInPictureParams
    }

    /**
     * A running USB driver service for the activity to bind: its binder answers the bind of [Game],
     * so that the activity connects to it as it does on a device.
     */
    fun usbDriverService(): UsbDriverService {
        val service = Robolectric.buildService(UsbDriverService::class.java).create()
        usbService = service
        val component = ComponentName(application, UsbDriverService::class.java)
        shadowOf(application)
            .setComponentNameAndServiceForBindService(component, service.get().onBind(Intent()))
        shadowOf(application).setBindServiceCallsOnServiceConnectedDirectly(true)
        return service.get()
    }

    /**
     * Builds the activity from [intent] and runs `onCreate`, `onStart` and `onResume`. The rig
     * holds the activity before `onCreate` runs, so that [destroy] ends it even when `onCreate`
     * throws.
     */
    fun create(intent: GameIntent = GameIntent()): Game {
        check(controller == null) { "the activity exists already" }
        val activity = Robolectric.buildActivity(Game::class.java, intent.build(application))
        controller = activity
        stage = Stage.RESUMED
        activity.setup()
        return game
    }

    /**
     * The state of a running stream: the connection started, the first frame on screen and the main
     * looper idled until the input grab. The grab waits for the first frame: until then the chrome
     * covers the stream and takes the input, and a grab is refused.
     */
    fun streaming(intent: GameIntent = GameIntent()): Game {
        val game = create(intent)
        game.connectionStarted()
        firstFrame()
        ShadowLooper.idleMainLooper(GRAB_DELAY_MS, TimeUnit.MILLISECONDS)
        return game
    }

    /** What the renderer does when its first frame is on screen. */
    fun firstFrame() {
        renderer.firstFrameSignal.frameRendered()
    }

    /**
     * What `surfaceChanged` leaves behind when it starts the connection, without starting it: the
     * activity believes the attempt was made, so that `surfaceChanged` does nothing more and
     * `surfaceDestroyed` stops what runs.
     */
    fun markConnectionAttempted() {
        ReflectionHelpers.setField(game, "attemptedConnection", true)
    }

    /** The decoder renderer that the activity built. */
    val renderer: MediaCodecDecoderRenderer
        get() = field("decoderRenderer")

    /** A private field of the activity, by name. */
    fun <T> field(name: String): T = ReflectionHelpers.getField(game, name)

    /** The activity goes out of the foreground, as when the user leaves: `onPause`. */
    fun pause() {
        if (stage == Stage.RESUMED) {
            checkNotNull(controller).pause()
            stage = Stage.PAUSED
        }
    }

    /**
     * The activity is no longer visible: `onPause` and `onStop`. `onStop` stops a running
     * connection on a thread of its own, and this waits for that thread, see [awaitStop].
     */
    fun stop() {
        pause()
        if (stage == Stage.PAUSED) {
            checkNotNull(controller).stop()
            stage = Stage.STOPPED
            awaitStop()
        }
    }

    /** The system ends the activity: `onPause`, `onStop` and `onDestroy`, if they did not run. */
    fun destroyActivity() {
        stop()
        if (stage == Stage.STOPPED) {
            checkNotNull(controller).destroy()
            stage = Stage.DESTROYED
        }
    }

    /**
     * Waits for the threads that the activity started to stop a connection. `Game.stopConnection`
     * stops it on a thread of its own (`NvConnection.stop` talks to the host), which `onStop`, a
     * surface that goes away and a terminated connection all start. A test that looks at what
     * reached the native library after one of them calls this first, and [destroy] calls it, so
     * that a late record never lands in the next test.
     */
    fun awaitStop() {
        val threads =
            Thread.getAllStackTraces().keys.filter { it.javaClass.name.startsWith(STOP_THREAD) }
        for (thread in threads) {
            thread.join(STOP_WAIT_MS)
            check(!thread.isAlive) { "the thread that stops the connection did not end" }
        }
    }

    /**
     * Ends the activity, if the test made one, and leaves nothing of the rig behind. Every step
     * runs even when an earlier one throws; the first failure is rethrown with the others added to
     * it.
     */
    fun destroy() {
        runAll(
            { if (controller != null) destroyActivity() },
            { controller = null },
            { usbService?.destroy() },
            { usbService = null },
            ::awaitStop,
            ::restoreActivityClient,
            ShadowRecordingBridge::reset,
            SessionAudit::stopped,
            BorealisAudit::uninstall,
        )
    }

    private fun restoreActivityClient() {
        pictureInPicture?.let { (singleton, original) ->
            ReflectionHelpers.setField(singleton, "mInstance", original)
        }
        pictureInPicture = null
    }

    /** Runs every step, whatever an earlier one threw; rethrows the first failure. */
    @Suppress("TooGenericExceptionCaught") // Any failure of a step must not skip the next steps.
    private fun runAll(vararg steps: () -> Any?) {
        var failure: Throwable? = null
        for (step in steps) {
            try {
                step()
            } catch (e: Throwable) {
                val first = failure
                if (first == null) failure = e else first.addSuppressed(e)
            }
        }
        failure?.let { throw it }
    }

    private enum class Stage {
        RESUMED,
        PAUSED,
        STOPPED,
        DESTROYED,
    }
}
