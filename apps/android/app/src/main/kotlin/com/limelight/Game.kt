package com.limelight

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.hardware.input.InputManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Rational
import android.view.Display
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.limelight.binding.PlatformBinding
import com.limelight.binding.audio.AndroidAudioRenderer
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.binding.input.capture.InputCaptureManager
import com.limelight.binding.input.capture.InputCaptureProvider
import com.limelight.binding.input.driver.UsbDriverService
import com.limelight.binding.input.touch.TouchContext
import com.limelight.binding.input.virtual_controller.VirtualController
import com.limelight.binding.video.CrashListener
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.GlPreferences
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.GameGestures
import com.limelight.ui.StreamView
import com.limelight.utils.ServerHelper
import com.limelight.utils.ShortcutHelper
import com.limelight.utils.UiHelper
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.host.launch.SessionAudit
import io.github.magalz.borealis.host.video.StreamStartupGuard
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.stream.ChromeMode
import io.github.magalz.borealis.stream.GameStreamActions
import io.github.magalz.borealis.stream.GamepadMouseSync
import io.github.magalz.borealis.stream.ImeKeyGate
import io.github.magalz.borealis.stream.PerfGate
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamControls
import io.github.magalz.borealis.stream.StreamFailures
import io.github.magalz.borealis.stream.StreamKeyboards
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.StreamSessionFactory
import io.github.magalz.borealis.stream.TouchContexts
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.host.HeldKeys
import io.github.magalz.borealis.stream.host.InputRoute
import io.github.magalz.borealis.stream.host.StreamChromeHost
import java.io.ByteArrayInputStream
import java.lang.reflect.InvocationTargetException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
@Suppress("DEPRECATION", "LargeClass", "OVERRIDE_DEPRECATION", "TooManyFunctions")
class Game :
    ComponentActivity(),
    SurfaceHolder.Callback,
    View.OnGenericMotionListener,
    View.OnTouchListener,
    NvConnectionListener,
    View.OnSystemUiVisibilityChangeListener,
    GameGestures,
    StreamView.InputCallbacks,
    UsbDriverService.UsbDriverStateListener,
    View.OnKeyListener {
    private var lastButtonState = 0

    // BOREALIS: the Back button of the mouse, held for BACK_HOLD_MS, opens the in-game menu and
    // sends nothing to the PC; a shorter press is X1, sent when the button comes up (stream)
    private var backHoldOpenedMenu = false
    private val openMenuFromBackHold = Runnable {
        backHoldOpenedMenu = true
        streamSession.openMenu()
    }

    // Only 2 touches are supported
    private val touchContextMap = arrayOfNulls<TouchContext>(2)
    private var threeFingerDownTime = 0L

    // STYLUS_DOWN_DEAD_ZONE_DELAY, STYLUS_DOWN_DEAD_ZONE_RADIUS, STYLUS_UP_DEAD_ZONE_DELAY,
    // STYLUS_UP_DEAD_ZONE_RADIUS and THREE_FINGER_TAP_THRESHOLD are in the companion object.

    private lateinit var controllerHandler: ControllerHandler
    private lateinit var keyboardTranslator: KeyboardTranslator
    private var virtualController: VirtualController? = null

    private lateinit var prefConfig: PreferenceConfiguration
    // BOREALIS: the frame rate negotiated with the host, for the audit trail (host.launch)
    private var streamFrameRate = 0
    private lateinit var tombstonePrefs: SharedPreferences

    private lateinit var conn: NvConnection
    // BOREALIS: the stream chrome (stream) shows the start, the conflict and the failures
    private lateinit var streamSession: StreamSession
    private lateinit var streamActions: GameStreamActions
    private lateinit var chromeHost: StreamChromeHost
    private val heldKeys = HeldKeys()
    private val chromeInput = InputRoute(::currentChromeMode)
    // BOREALIS: while the Android keyboard shows, a gamepad's D-pad, Enter and Back are for it
    // (stream)
    private val imeKeys = ImeKeyGate { ImeKeyGate.isImeVisible(window.decorView) }
    private var displayedFailureDialog = false
    private var connected = false
    // BOREALIS: the first frame was on screen, so an ending stream "stopped" and did not fail to
    // start (stream)
    @Volatile private var firstFrameShown = false
    // BOREALIS: the last fixed text of a failed start, shown on the error screen instead of as a
    // toast (stream)
    @Volatile private var startNote: String? = null
    private var autoEnterPip = false
    private var surfaceCreated = false
    private var attemptedConnection = false
    private var suppressPipRefCount = 0
    private var pcName: String? = null
    private var appName: String? = null
    private lateinit var app: NvApp
    private var desiredRefreshRate = 0f

    private lateinit var inputCaptureProvider: InputCaptureProvider
    private var modifierFlags = 0
    private var grabbedInput = true
    // BOREALIS: whether the input was grabbed when the chrome took it
    private var grabbedBeforeCapture = true
    private var cursorVisible = false
    private var waitingForAllModifiersUp = false
    private var specialKeyCode = KeyEvent.KEYCODE_UNKNOWN
    private lateinit var streamView: StreamView
    private var lastAbsTouchUpTime = 0L
    private var lastAbsTouchDownTime = 0L
    private var lastAbsTouchUpX = 0f
    private var lastAbsTouchUpY = 0f
    private var lastAbsTouchDownX = 0f
    private var lastAbsTouchDownY = 0f

    private var isHidingOverlays = false
    private lateinit var notificationOverlayView: TextView
    private var requestedNotificationOverlayVisibility = View.GONE

    private lateinit var decoderRenderer: MediaCodecDecoderRenderer

    // BOREALIS: first-frame watchdog (host.video)
    private val startupGuard =
        StreamStartupGuard({ connectionTerminated(MoonBridge.ML_ERROR_NO_VIDEO_FRAME) })
    private var reportedCrash = false

    private var highPerfWifiLock: WifiManager.WifiLock? = null
    private var lowLatencyWifiLock: WifiManager.WifiLock? = null

    private var connectedToUsbDriverService = false
    // BOREALIS: the driver service waits for the first frame before it shows a permission dialog
    @Volatile private var usbDriverBinder: UsbDriverService.UsbDriverBinder? = null
    private val usbDriverServiceConnection: ServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                val binder = iBinder as UsbDriverService.UsbDriverBinder
                binder.setListener(controllerHandler)
                binder.setStateListener(this@Game)
                binder.start()
                connectedToUsbDriverService = true
                // BOREALIS: set the binder first, then read the flag: the first-frame listener does
                // the reverse
                usbDriverBinder = binder
                if (firstFrameShown) {
                    binder.onStreamReady()
                }
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                connectedToUsbDriverService = false
                usbDriverBinder = null // BOREALIS
            }
        }

    // The EXTRA_ constants are the persistent format of the intent that starts the stream.
    companion object {
        const val EXTRA_HOST = "Host"
        const val EXTRA_PORT = "Port"
        const val EXTRA_HTTPS_PORT = "HttpsPort"
        const val EXTRA_APP_NAME = "AppName"
        const val EXTRA_APP_ID = "AppId"
        const val EXTRA_UNIQUEID = "UniqueId"
        const val EXTRA_PC_UUID = "UUID"
        const val EXTRA_PC_NAME = "PcName"
        const val EXTRA_APP_HDR = "HDR"
        const val EXTRA_SERVER_CERT = "ServerCert"

        // BOREALIS: true when the user chose Resume, Continue or Try again for the app that
        // runs; absent, as in an older shortcut, it is false (stream)
        const val EXTRA_RESUME_CHOSEN = "ResumeChosen"

        private const val STYLUS_DOWN_DEAD_ZONE_DELAY = 100
        private const val STYLUS_DOWN_DEAD_ZONE_RADIUS = 20

        private const val STYLUS_UP_DEAD_ZONE_DELAY = 150
        private const val STYLUS_UP_DEAD_ZONE_RADIUS = 50

        private const val THREE_FINGER_TAP_THRESHOLD = 300

        // The named values of numbers that the Java wrote inline.
        private const val THREE_FINGER_COUNT = 3
        private const val SAMSUNG_DEX_MOUSE_SOURCE = 12290
        private const val HIGH_RES_SCROLL_UNIT = 120
        private const val FULL_CIRCLE_DEGREES = 360
        private const val VIRTUAL_KEY_MASK = 0xFF
        private const val CONNECTION_TEST_PORT = 443
        private const val MAX_PACKET_SIZE = 1392
        private const val X100 = 100
        private const val SLOW_CONNECTION_BITRATE_KBPS = 5000
        private const val REFRESH_RATE_TOLERANCE = 3
        private const val BOGUS_REFRESH_RATE_LIMIT = 49
        private const val MAX_SAFE_DISPLAY_WIDTH = 4096
        private const val UHD_WIDTH = 3840
        private const val HIGH_FPS = 60
        private const val QUARTER_TURN_DIVISOR = 4
        private const val HIDE_UI_AFTER_MULTI_WINDOW_MS = 50
        private const val HIDE_UI_AFTER_CONNECT_MS = 1000
        private const val HIDE_UI_AFTER_UI_CHANGE_MS = 2000
        private const val TOGGLE_GRAB_DELAY_MS = 250L
        private const val INPUT_GRAB_DELAY_MS = 500L
        private const val BACK_HOLD_MS = 1000L
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("CyclomaticComplexMethod", "LongMethod", "PrintStackTrace")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We don't want a title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // Full-screen
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // If we're going to use immersive mode, we want to have
        // the entire screen
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)

        // Listen for UI visibility events
        window.decorView.setOnSystemUiVisibilityChangeListener(this)

        // Change volume button behavior
        volumeControlStream = AudioManager.STREAM_MUSIC

        // Inflate the content
        setContentView(R.layout.activity_game)

        // Read the stream preferences
        prefConfig = PreferenceConfiguration.readPreferences(this)
        tombstonePrefs = getSharedPreferences("DecoderTombstone", 0)

        // Enter landscape unless we're on a square screen
        setPreferredOrientationForCurrentDisplay()

        if (
            prefConfig.stretchVideo ||
                shouldIgnoreInsetsForResolution(prefConfig.width, prefConfig.height)
        ) {
            // Allow the activity to layout under notches if the fill-screen option
            // was turned on by the user or it's a full-screen native resolution
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Listen for non-touch events on the game surface
        streamView = findViewById(R.id.surfaceView)
        streamView.setOnGenericMotionListener(this)
        streamView.setOnKeyListener(this)
        streamView.setInputCallbacks(this)

        // Listen for touch events on the background touch view to enable trackpad mode
        // to work on areas outside of the StreamView itself. We use a separate View
        // for this rather than just handling it at the Activity level, because that
        // allows proper touch splitting, which the OSC relies upon.
        val backgroundTouchView = findViewById<View>(R.id.backgroundTouchView)
        backgroundTouchView.setOnTouchListener(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Request unbuffered input event dispatching for all input classes we handle here.
            // Without this, input events are buffered to be delivered in lock-step with VBlank,
            // artificially increasing input latency while streaming.
            streamView.requestUnbufferedDispatch(
                InputDevice.SOURCE_CLASS_BUTTON or // Keyboards
                    InputDevice.SOURCE_CLASS_JOYSTICK or // Gamepads
                    InputDevice
                        .SOURCE_CLASS_POINTER or // Touchscreens and mice (w/o pointer capture)
                    InputDevice.SOURCE_CLASS_POSITION or // Touchpads
                    InputDevice.SOURCE_CLASS_TRACKBALL // Mice (pointer capture)
            )
            backgroundTouchView.requestUnbufferedDispatch(
                InputDevice.SOURCE_CLASS_BUTTON or // Keyboards
                    InputDevice.SOURCE_CLASS_JOYSTICK or // Gamepads
                    InputDevice
                        .SOURCE_CLASS_POINTER or // Touchscreens and mice (w/o pointer capture)
                    InputDevice.SOURCE_CLASS_POSITION or // Touchpads
                    InputDevice.SOURCE_CLASS_TRACKBALL // Mice (pointer capture)
            )
        }

        notificationOverlayView = findViewById(R.id.notificationOverlay)

        inputCaptureProvider = InputCaptureManager.getInputCaptureProvider(this)

        streamView.setOnCapturedPointerListener { view, motionEvent ->
            handleMotionEvent(view, motionEvent)
        }

        // Warn the user if they're on a metered connection
        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connMgr.isActiveNetworkMetered) {
            displayTransientMessage(resources.getString(R.string.conn_metered))
        }

        // Make sure Wi-Fi is fully powered up
        val wifiMgr = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        try {
            val highPerf =
                wifiMgr.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "Moonlight High Perf Lock",
                )
            highPerfWifiLock = highPerf
            highPerf.setReferenceCounted(false)
            highPerf.acquire()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val lowLatency =
                    wifiMgr.createWifiLock(
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                        "Moonlight Low Latency Lock",
                    )
                lowLatencyWifiLock = lowLatency
                lowLatency.setReferenceCounted(false)
                lowLatency.acquire()
            }
        } catch (e: SecurityException) {
            // Some Samsung Galaxy S10+/S10e devices throw a SecurityException from
            // WifiLock.acquire() even though we have android.permission.WAKE_LOCK in our manifest.
            e.printStackTrace()
        }

        appName = intent.getStringExtra(EXTRA_APP_NAME)
        pcName = intent.getStringExtra(EXTRA_PC_NAME)

        val host = intent.getStringExtra(EXTRA_HOST)
        val port = intent.getIntExtra(EXTRA_PORT, NvHTTP.DEFAULT_HTTP_PORT)
        val httpsPort = intent.getIntExtra(EXTRA_HTTPS_PORT, 0) // 0 is treated as unknown
        val appId = intent.getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID)
        val uniqueId = intent.getStringExtra(EXTRA_UNIQUEID)
        val appSupportsHdr = intent.getBooleanExtra(EXTRA_APP_HDR, false)
        val derCertData = intent.getByteArrayExtra(EXTRA_SERVER_CERT)
        // BOREALIS: absent, as in a shortcut of an older version, it is false (stream)
        val resumeChosen = intent.getBooleanExtra(EXTRA_RESUME_CHOSEN, false)

        app = NvApp(appName ?: "app", appId, appSupportsHdr)

        var serverCert: X509Certificate? = null
        try {
            if (derCertData != null) {
                serverCert =
                    CertificateFactory.getInstance("X.509")
                        .generateCertificate(ByteArrayInputStream(derCertData)) as X509Certificate
            }
        } catch (e: CertificateException) {
            e.printStackTrace()
        }

        if (appId == StreamConfiguration.INVALID_APP_ID) {
            finish()
            return
        }

        // Initialize the MediaCodec helper before creating the decoder
        val glPrefs = GlPreferences.readPreferences(this)
        MediaCodecHelper.initialize(this, glPrefs.glRenderer)

        // Check if the user has enabled HDR
        var willStreamHdr = false
        if (prefConfig.enableHdr) {
            // Start our HDR checklist
            val display = windowManager.defaultDisplay
            val hdrCaps = display.hdrCapabilities

            // We must now ensure our display is compatible with HDR10
            if (hdrCaps != null) {
                // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android
                // 8.0
                for (hdrType in hdrCaps.supportedHdrTypes) {
                    if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                        willStreamHdr = true
                        break
                    }
                }
            }

            if (!willStreamHdr) {
                // Nope, no HDR for us :(
                Toast.makeText(this, "Display does not support HDR10", Toast.LENGTH_LONG).show()
            }
        }

        decoderRenderer =
            MediaCodecDecoderRenderer(
                this,
                prefConfig,
                CrashListener {
                    // The MediaCodec instance is going down due to a crash
                    // let's tell the user something when they open the app again

                    // We must use commit because the app will crash when we return from this
                    // function
                    tombstonePrefs.edit(commit = true) {
                        putInt("CrashCount", tombstonePrefs.getInt("CrashCount", 0) + 1)
                    }
                    reportedCrash = true
                },
                tombstonePrefs.getInt("CrashCount", 0),
                willStreamHdr,
                glPrefs.glRenderer,
            )
        // BOREALIS: first rendered frame ends the watchdog
        decoderRenderer.firstFrameSignal.listener = Runnable {
            firstFrameShown = true // BOREALIS
            val usbBinder = usbDriverBinder // BOREALIS
            if (usbBinder != null) {
                usbBinder.onStreamReady()
            }
            startupGuard.onFirstFrame()
            streamSession.onFirstFrame()
        }

        // Don't stream HDR if the decoder can't support it
        if (
            willStreamHdr &&
                !decoderRenderer.isHevcMain10Hdr10Supported() &&
                !decoderRenderer.isAv1Main10Supported()
        ) {
            willStreamHdr = false
            Toast.makeText(this, "Decoder does not support HDR10 profile", Toast.LENGTH_LONG).show()
        }

        // Display a message to the user if HEVC was forced on but we still didn't find a decoder
        if (
            prefConfig.videoFormat == PreferenceConfiguration.FormatOption.FORCE_HEVC &&
                !decoderRenderer.isHevcSupported()
        ) {
            Toast.makeText(this, "No HEVC decoder found", Toast.LENGTH_LONG).show()
        }

        // Display a message to the user if AV1 was forced on but we still didn't find a decoder
        if (
            prefConfig.videoFormat == PreferenceConfiguration.FormatOption.FORCE_AV1 &&
                !decoderRenderer.isAv1Supported()
        ) {
            Toast.makeText(this, "No AV1 decoder found", Toast.LENGTH_LONG).show()
        }

        // H.264 is always supported
        var supportedVideoFormats = MoonBridge.VIDEO_FORMAT_H264
        if (decoderRenderer.isHevcSupported()) {
            supportedVideoFormats = supportedVideoFormats or MoonBridge.VIDEO_FORMAT_H265
            if (willStreamHdr && decoderRenderer.isHevcMain10Hdr10Supported()) {
                supportedVideoFormats = supportedVideoFormats or MoonBridge.VIDEO_FORMAT_H265_MAIN10
            }
        }
        if (decoderRenderer.isAv1Supported()) {
            supportedVideoFormats = supportedVideoFormats or MoonBridge.VIDEO_FORMAT_AV1_MAIN8
            if (willStreamHdr && decoderRenderer.isAv1Main10Supported()) {
                supportedVideoFormats = supportedVideoFormats or MoonBridge.VIDEO_FORMAT_AV1_MAIN10
            }
        }

        var gamepadMask = ControllerHandler.getAttachedControllerMask(this).toInt()
        if (!prefConfig.multiController) {
            // Always set gamepad 1 present for when multi-controller is
            // disabled for games that don't properly support detection
            // of gamepads removed and replugged at runtime.
            gamepadMask = 1
        }
        if (prefConfig.onscreenController) {
            // If we're using OSC, always set at least gamepad 1.
            gamepadMask = gamepadMask or 1
        }

        // Set to the optimal mode for streaming
        val displayRefreshRate = prepareDisplayForRendering()
        LimeLog.info("Display refresh rate: $displayRefreshRate")

        // If the user requested frame pacing using a capped FPS, we will need to change our
        // desired FPS setting here in accordance with the active display refresh rate.
        val roundedRefreshRate = Math.round(displayRefreshRate)
        var chosenFrameRate = prefConfig.fps
        if (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS) {
            if (prefConfig.fps >= roundedRefreshRate) {
                if (prefConfig.fps > roundedRefreshRate + REFRESH_RATE_TOLERANCE) {
                    // Use frame drops when rendering above the screen frame rate
                    prefConfig.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED
                    LimeLog.info("Using drop mode for FPS > Hz")
                } else if (roundedRefreshRate <= BOGUS_REFRESH_RATE_LIMIT) {
                    // Let's avoid clearly bogus refresh rates and fall back to legacy rendering
                    prefConfig.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED
                    LimeLog.info("Bogus refresh rate: $roundedRefreshRate")
                } else {
                    chosenFrameRate = roundedRefreshRate - 1
                    LimeLog.info("Adjusting FPS target for screen to $chosenFrameRate")
                }
            }
        }

        // BOREALIS: audited as negotiated, not as requested (host.launch)
        streamFrameRate = chosenFrameRate
        val config =
            StreamConfiguration.Builder()
                .setResolution(prefConfig.width, prefConfig.height)
                .setLaunchRefreshRate(prefConfig.fps)
                .setRefreshRate(chosenFrameRate)
                .setApp(app)
                .setBitrate(prefConfig.bitrate)
                .setEnableSops(prefConfig.enableSops)
                .enableLocalAudioPlayback(prefConfig.playHostAudio)
                .setMaxPacketSize(MAX_PACKET_SIZE)
                // NvConnection will perform LAN and VPN detection
                .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
                .setSupportedVideoFormats(supportedVideoFormats)
                .setAttachedGamepadMask(gamepadMask)
                .setClientRefreshRateX100((displayRefreshRate * X100).toInt())
                .setAudioConfiguration(prefConfig.audioConfiguration)
                .setColorSpace(decoderRenderer.getPreferredColorSpace())
                .setColorRange(decoderRenderer.getPreferredColorRange())
                .setPersistGamepadsAfterDisconnect(!prefConfig.multiController)
                .setResumeChosen(resumeChosen) // BOREALIS: host.launch
                .build()

        // Initialize the connection
        conn =
            NvConnection(
                applicationContext,
                ComputerDetails.AddressTuple(host, port),
                httpsPort,
                uniqueId,
                config,
                PlatformBinding.getCryptoProvider(this),
                serverCert,
            )
        controllerHandler = ControllerHandler(this, conn, this, prefConfig)
        keyboardTranslator = KeyboardTranslator()

        val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(keyboardTranslator, null)

        // Initialize touch contexts
        // BOREALIS: the contexts of the touch mode the settings start with; the menu can change it
        // (stream)
        System.arraycopy(
            TouchContexts.build(
                TouchContexts.initialMode(prefConfig),
                conn,
                streamView,
                prefConfig,
            ) ?: throw NullPointerException("The initial touch mode has no touch contexts"),
            0,
            touchContextMap,
            0,
            touchContextMap.size,
        )

        if (prefConfig.onscreenController) {
            // create virtual onscreen controller
            val onscreenController =
                VirtualController(controllerHandler, streamView.parent as FrameLayout?, this)
            virtualController = onscreenController
            onscreenController.refreshLayout()
            onscreenController.show()
        }

        if (prefConfig.usbDriver) {
            // Start the USB driver
            bindService(
                Intent(this, UsbDriverService::class.java),
                usbDriverServiceConnection,
                Service.BIND_AUTO_CREATE,
            )
        }

        // BOREALIS: the stream chrome over the stream view shows the start, the conflict and the
        // failures (stream)
        streamActions =
            GameStreamActions(
                this,
                conn,
                StreamControls(
                    controllerHandler,
                    touchContextMap,
                    streamView,
                    { streamSession.closePanel() },
                    { mode -> TouchContexts.build(mode, conn, streamView, prefConfig) },
                    { done -> stopConnectionThen(done) },
                ),
                heldKeys,
            )
        streamSession =
            StreamSessionFactory.create(
                this,
                prefConfig,
                appName,
                pcName,
                streamActions,
                controllerHandler::hasUsbPad,
            )
        chromeHost =
            StreamChromeHost(
                this,
                streamSession,
                streamView,
                ::chromeCaptured,
                ::chromeReleased,
                StreamSessionFactory.look(this),
            )
        addContentView(
            chromeHost,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        // BOREALIS: the controller opens the menu and navigates the chrome; the decoder numbers and
        // the gamepad mouse follow the session (stream)
        controllerHandler.setMenuChordListener(streamSession::openMenu)
        controllerHandler.setChromeKeySink(chromeHost::dispatchUsbPadKey)
        decoderRenderer.setPerfSink(streamSession::onPerf)
        PerfGate.bind(lifecycleScope, streamSession, decoderRenderer::setPerfEnabled)
        GamepadMouseSync.bind(lifecycleScope, streamSession) {
            controllerHandler.isMouseEmulationOnAnyPad
        }

        if (!decoderRenderer.isAvcSupported()) {
            // If we can't find an AVC decoder, we can't proceed
            streamSession.onFailed(
                StreamFailure(FailureKind.GENERIC, "No hardware accelerated H.264 decoder", null)
            )
            return
        }

        // The connection will be started when the surface gets created
        streamView.holder.addCallback(this)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @SuppressLint("SourceLockedOrientationActivity")
    private fun setPreferredOrientationForCurrentDisplay() {
        val display = windowManager.defaultDisplay

        // For semi-square displays, we use more complex logic to determine which orientation to use
        // (if any)
        if (PreferenceConfiguration.isSquarishScreen(display)) {
            var desiredOrientation = Configuration.ORIENTATION_UNDEFINED

            // OSC doesn't properly support portrait displays, so don't use it in portrait mode by
            // default
            if (prefConfig.onscreenController) {
                desiredOrientation = Configuration.ORIENTATION_LANDSCAPE
            }

            // For native resolution, we will lock the orientation to the one that matches the
            // specified resolution
            if (PreferenceConfiguration.isNativeResolution(prefConfig.width, prefConfig.height)) {
                desiredOrientation =
                    if (prefConfig.width > prefConfig.height) {
                        Configuration.ORIENTATION_LANDSCAPE
                    } else {
                        Configuration.ORIENTATION_PORTRAIT
                    }
            }

            if (desiredOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            } else if (desiredOrientation == Configuration.ORIENTATION_PORTRAIT) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            } else {
                // If we don't have a reason to lock to portrait or landscape, allow any orientation
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            }
        } else {
            // For regular displays, we always request landscape
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Set requested orientation for possible new screen size
        setPreferredOrientationForCurrentDisplay()

        // Refresh layout of OSC for possible new screen size
        virtualController?.refreshLayout()

        // Hide on-screen overlays in PiP mode
        if (isInPictureInPictureMode) {
            isHidingOverlays = true

            virtualController?.hide()

            notificationOverlayView.visibility = View.GONE

            // Disable sensors while in PiP mode
            controllerHandler.disableSensors()

            // Update GameManager state to indicate we're in PiP (still gaming, but interruptible)
            UiHelper.notifyStreamEnteringPiP(this)
        } else {
            isHidingOverlays = false

            // Restore overlays to previous state when leaving PiP

            virtualController?.show()

            notificationOverlayView.visibility = requestedNotificationOverlayVisibility

            // Enable sensors again after exiting PiP
            controllerHandler.enableSensors()

            // Update GameManager state to indicate we're out of PiP (gaming, non-interruptible)
            UiHelper.notifyStreamExitingPiP(this)
        }
    }

    private fun getPictureInPictureParams(autoEnter: Boolean): PictureInPictureParams {
        val builder =
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(prefConfig.width, prefConfig.height))
                .setSourceRectHint(
                    Rect(streamView.left, streamView.top, streamView.right, streamView.bottom)
                )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (appName != null) {
                builder.setTitle(appName)
                if (pcName != null) {
                    builder.setSubtitle(pcName)
                }
            } else if (pcName != null) {
                builder.setTitle(pcName)
            }
        }

        return builder.build()
    }

    private fun updatePipAutoEnter() {
        if (!prefConfig.enablePip) {
            return
        }

        val autoEnter = connected && suppressPipRefCount == 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(getPictureInPictureParams(autoEnter))
        } else {
            autoEnterPip = autoEnter
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("PrintStackTrace")
    fun setMetaKeyCaptureState(enabled: Boolean) {
        // Android has native keyboard capture support starting in API 36.1
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
                Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
        ) {
            val windowLayoutParams = window.attributes
            windowLayoutParams.setKeyboardCaptureEnabled(enabled)
            window.attributes = windowLayoutParams
        } else {
            // This uses custom APIs present on some Samsung devices to allow capture of
            // meta key events while streaming.
            try {
                val semWindowManager = Class.forName("com.samsung.android.view.SemWindowManager")
                val getInstanceMethod = semWindowManager.getMethod("getInstance")
                val manager = getInstanceMethod.invoke(null)

                if (manager != null) {
                    val requestMetaKeyEventMethod =
                        semWindowManager.getDeclaredMethod(
                            "requestMetaKeyEvent",
                            ComponentName::class.java,
                            java.lang.Boolean.TYPE,
                        )
                    requestMetaKeyEventMethod.invoke(manager, this.componentName, enabled)
                } else {
                    LimeLog.warning("SemWindowManager.getInstance() returned null")
                }
            } catch (ignored: ClassNotFoundException) {} catch (e: NoSuchMethodException) {
                e.printStackTrace()
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("PrintStackTrace", "TooGenericExceptionCaught")
    public override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        // PiP is only supported on Oreo and later, and we don't need to manually enter PiP on
        // Android S and later. On Android R, we will use onPictureInPictureRequested() instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (autoEnterPip) {
                try {
                    // This has thrown all sorts of weird exceptions on Samsung devices
                    // running Oreo. Just eat them and close gracefully on leave, rather
                    // than crashing.
                    enterPictureInPictureMode(getPictureInPictureParams(false))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onPictureInPictureRequested(): Boolean {
        // Enter PiP when requested unless we're on Android 12 which supports auto-enter.
        if (autoEnterPip && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPictureInPictureMode(getPictureInPictureParams(false))
        }
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        // We can't guarantee the state of modifiers keys which may have
        // lifted while focus was not on us. Clear the modifier state.
        this.modifierFlags = 0

        // BOREALIS: the release of the mouse's Back button may come while another window has the
        // focus (stream)
        if (!hasFocus) cancelBackHold()

        // With Android native pointer capture, capture is lost when focus is lost,
        // so it must be requested again when focus is regained.
        inputCaptureProvider.onWindowFocusChanged(hasFocus)
    }

    private fun isRefreshRateEqualMatch(refreshRate: Float): Boolean {
        return refreshRate >= prefConfig.fps &&
            refreshRate <= prefConfig.fps + REFRESH_RATE_TOLERANCE
    }

    private fun isRefreshRateGoodMatch(refreshRate: Float): Boolean {
        return refreshRate >= prefConfig.fps &&
            Math.round(refreshRate) % prefConfig.fps <= REFRESH_RATE_TOLERANCE
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("ComplexCondition")
    private fun shouldIgnoreInsetsForResolution(width: Int, height: Int): Boolean {
        // Never ignore insets for non-native resolutions
        if (!PreferenceConfiguration.isNativeResolution(width, height)) {
            return false
        }

        val display = windowManager.defaultDisplay
        for (candidate in display.supportedModes) {
            // Ignore insets if this is an exact match for the display resolution
            if (
                (width == candidate.physicalWidth && height == candidate.physicalHeight) ||
                    (height == candidate.physicalWidth && width == candidate.physicalHeight)
            ) {
                return true
            }
        }

        return false
    }

    private fun mayReduceRefreshRate(): Boolean {
        return prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS ||
            prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS ||
            (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_BALANCED &&
                prefConfig.reduceRefreshRate)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress(
        "CyclomaticComplexMethod",
        "ForbiddenComment",
        "LongMethod",
        "LoopWithTooManyJumpStatements",
    )
    private fun prepareDisplayForRendering(): Float {
        val display = windowManager.defaultDisplay
        val windowLayoutParams = window.attributes

        // Pick the optimal display mode explicitly
        var bestMode = display.mode
        val isNativeResolutionStream =
            PreferenceConfiguration.isNativeResolution(prefConfig.width, prefConfig.height)
        var refreshRateIsGood = isRefreshRateGoodMatch(bestMode.refreshRate)
        var refreshRateIsEqual = isRefreshRateEqualMatch(bestMode.refreshRate)

        LimeLog.info(
            "Current display mode: ${bestMode.physicalWidth}x${bestMode.physicalHeight}x${bestMode.refreshRate}"
        )

        for (candidate in display.supportedModes) {
            val refreshRateReduced = candidate.refreshRate < bestMode.refreshRate
            val resolutionReduced =
                candidate.physicalWidth < bestMode.physicalWidth ||
                    candidate.physicalHeight < bestMode.physicalHeight
            val resolutionFitsStream =
                candidate.physicalWidth >= prefConfig.width &&
                    candidate.physicalHeight >= prefConfig.height

            LimeLog.info(
                "Examining display mode: ${candidate.physicalWidth}x" +
                    "${candidate.physicalHeight}x${candidate.refreshRate}"
            )

            if (
                candidate.physicalWidth > MAX_SAFE_DISPLAY_WIDTH &&
                    prefConfig.width <= MAX_SAFE_DISPLAY_WIDTH
            ) {
                // Avoid resolutions options above 4K to be safe
                continue
            }

            // On non-4K streams, we force the resolution to never change unless it's above
            // 60 FPS, which may require a resolution reduction due to HDMI bandwidth limitations,
            // or it's a native resolution stream.
            if (
                prefConfig.width < UHD_WIDTH &&
                    prefConfig.fps <= HIGH_FPS &&
                    !isNativeResolutionStream
            ) {
                if (
                    display.mode.physicalWidth != candidate.physicalWidth ||
                        display.mode.physicalHeight != candidate.physicalHeight
                ) {
                    continue
                }
            }

            // Make sure the resolution doesn't regress unless if it's over 60 FPS
            // where we may need to reduce resolution to achieve the desired refresh rate.
            if (resolutionReduced && !(prefConfig.fps > HIGH_FPS && resolutionFitsStream)) {
                continue
            }

            if (
                mayReduceRefreshRate() &&
                    refreshRateIsEqual &&
                    !isRefreshRateEqualMatch(candidate.refreshRate)
            ) {
                // If we had an equal refresh rate and this one is not, skip it. In min latency
                // mode, we want to always prefer the highest frame rate even though it may cause
                // microstuttering.
                continue
            } else if (refreshRateIsGood) {
                // We've already got a good match, so if this one isn't also good, it's not
                // worth considering at all.
                if (!isRefreshRateGoodMatch(candidate.refreshRate)) {
                    continue
                }

                if (mayReduceRefreshRate()) {
                    // User asked for the lowest possible refresh rate, so don't raise it if we
                    // have a good match already
                    if (candidate.refreshRate > bestMode.refreshRate) {
                        continue
                    }
                } else {
                    // User asked for the highest possible refresh rate, so don't reduce it if we
                    // have a good match already
                    if (refreshRateReduced) {
                        continue
                    }
                }
            } else if (!isRefreshRateGoodMatch(candidate.refreshRate)) {
                // We didn't have a good match and this match isn't good either, so just don't
                // reduce the refresh rate.
                if (refreshRateReduced) {
                    continue
                }
            } else {
                // We didn't have a good match and this match is good. Prefer this refresh rate
                // even if it reduces the refresh rate. Lowering the refresh rate can be beneficial
                // when streaming a 60 FPS stream on a 90 Hz device. We want to select 60 Hz to
                // match the frame rate even if the active display mode is 90 Hz.
            }

            bestMode = candidate
            refreshRateIsGood = isRefreshRateGoodMatch(candidate.refreshRate)
            refreshRateIsEqual = isRefreshRateEqualMatch(candidate.refreshRate)
        }

        LimeLog.info(
            "Best display mode: ${bestMode.physicalWidth}x${bestMode.physicalHeight}x${bestMode.refreshRate}"
        )

        // Only apply new window layout parameters if we've actually changed the display mode
        if (display.mode.modeId != bestMode.modeId) {
            // If we only changed refresh rate and we're on an OS that supports
            // Surface.setFrameRate() use that instead of using preferredDisplayModeId to avoid the
            // possibility of triggering bugs that can cause the system to switch from 4K60 to 4K24
            // on Chromecast 4K.
            if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    display.mode.physicalWidth != bestMode.physicalWidth ||
                    display.mode.physicalHeight != bestMode.physicalHeight
            ) {
                // Apply the display mode change
                windowLayoutParams.preferredDisplayModeId = bestMode.modeId
                window.attributes = windowLayoutParams
            } else {
                LimeLog.info(
                    "Using setFrameRate() instead of preferredDisplayModeId due to matching resolution"
                )
            }
        } else {
            LimeLog.info("Current display mode is already the best display mode")
        }

        val displayRefreshRate = bestMode.refreshRate

        if (prefConfig.stretchVideo) {
            // Set the surface to the size of the video
            streamView.holder.setFixedSize(prefConfig.width, prefConfig.height)
        } else {
            // Set the surface to scale based on the aspect ratio of the stream
            streamView.setDesiredAspectRatio(
                prefConfig.width.toDouble() / prefConfig.height.toDouble()
            )
        }

        // Set the desired refresh rate that will get passed into setFrameRate() later
        desiredRefreshRate = displayRefreshRate

        if (
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        ) {
            // TVs may take a few moments to switch refresh rates, and we can probably assume
            // it will be eventually activated.
            // TODO: Improve this
            return displayRefreshRate
        } else {
            // Use the lower of the current refresh rate and the selected refresh rate.
            // The preferred refresh rate may not actually be applied (ex: Battery Saver mode).
            return min(windowManager.defaultDisplay.refreshRate, displayRefreshRate)
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("ForbiddenComment")
    @SuppressLint("InlinedApi")
    private val hideSystemUi = Runnable {
        // TODO: Do we want to use WindowInsetsController here on R+ instead of
        // SYSTEM_UI_FLAG_IMMERSIVE_STICKY? They seem to do the same thing as of S...

        // In multi-window mode on N+, we need to drop our layout flags or we'll
        // be drawing underneath the system UI.
        if (isInMultiWindowMode) {
            this.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            // Use immersive mode
            this.window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun hideSystemUi(delay: Int) {
        val h = window.decorView.handler
        if (h != null) {
            h.removeCallbacks(hideSystemUi)
            h.postDelayed(hideSystemUi, delay.toLong())
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)

        // In multi-window, we don't want to use the full-screen layout
        // flag. It will cause us to collide with the system UI.
        // This function will also be called for PiP so we can cover
        // that case here too.
        if (isInMultiWindowMode) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        // Correct the system UI visibility flags
        hideSystemUi(HIDE_UI_AFTER_MULTI_WINDOW_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        startupGuard.cancel() // BOREALIS: first-frame watchdog
        // BOREALIS: end the launch retries (host.launch)
        if (::conn.isInitialized) {
            conn.cancelLaunch()
        }

        if (::controllerHandler.isInitialized) {
            controllerHandler.destroy()
        }
        if (::keyboardTranslator.isInitialized) {
            val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
            inputManager.unregisterInputDeviceListener(keyboardTranslator)
        }

        lowLatencyWifiLock?.release()
        highPerfWifiLock?.release()

        if (connectedToUsbDriverService) {
            // Unbind from the discovery service
            unbindService(usbDriverServiceConnection)
        }

        // Destroy the capture provider
        inputCaptureProvider.destroy()
    }

    override fun onPause() {
        if (isFinishing) {
            // Stop any further input device notifications before we lose focus (and pointer
            // capture)
            if (::controllerHandler.isInitialized) {
                controllerHandler.stop()
            }

            // Ungrab input to prevent further input device notifications
            setInputGrabState(false)
        }

        super.onPause()
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun onStop() {
        super.onStop()
        startupGuard.cancel() // BOREALIS: first-frame watchdog
        cancelBackHold() // BOREALIS: the release of the mouse's Back button may never come (stream)
        // BOREALIS: a pending "app already running" question is a cancel (host.launch)
        if (::streamSession.isInitialized) {
            streamSession.answerConflict(ConflictChoice.CANCEL)
        }

        virtualController?.hide()

        if (::conn.isInitialized) {
            val videoFormat = decoderRenderer.getActiveVideoFormat()

            conn.cancelLaunch() // BOREALIS: end the launch retries (host.launch)
            displayedFailureDialog = true
            stopConnection()

            if (prefConfig.enableLatencyToast) {
                val averageEndToEndLat = decoderRenderer.getAverageEndToEndLatency()
                val averageDecoderLat = decoderRenderer.getAverageDecoderLatency()
                var message: String? = null
                if (averageEndToEndLat > 0) {
                    message =
                        resources.getString(R.string.conn_client_latency) +
                            " " +
                            averageEndToEndLat +
                            " ms"
                    if (averageDecoderLat > 0) {
                        message +=
                            " (" +
                                resources.getString(R.string.conn_client_latency_hw) +
                                " " +
                                averageDecoderLat +
                                " ms)"
                    }
                } else if (averageDecoderLat > 0) {
                    message =
                        resources.getString(R.string.conn_hardware_latency) +
                            " " +
                            averageDecoderLat +
                            " ms"
                }

                // Add the video codec to the post-stream toast
                if (message != null) {
                    message += " ["

                    if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H264) != 0) {
                        message += "H.264"
                    } else if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H265) != 0) {
                        message += "HEVC"
                    } else if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_AV1) != 0) {
                        message += "AV1"
                    } else {
                        message += "UNKNOWN"
                    }

                    if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0) {
                        message += " HDR"
                    }

                    message += "]"
                }

                if (message != null) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }

            // Clear the tombstone count if we terminated normally
            if (!reportedCrash && tombstonePrefs.getInt("CrashCount", 0) != 0) {
                tombstonePrefs.edit {
                    putInt("CrashCount", 0)
                    putInt("LastNotifiedCrashCount", 0)
                }
            }
        }

        // BOREALIS: every exit that finished the activity already asks once (leave)
        if (!isFinishing) finish()
    }

    // BOREALIS: who gets the input, from the stream session; the stream gets it until the session
    // exists
    private fun currentChromeMode(): ChromeMode {
        return if (::streamSession.isInitialized) streamSession.chromeMode else ChromeMode.IDLE
    }

    // BOREALIS: the chrome takes the input: release what is held and drop the pointer capture
    private fun chromeCaptured() {
        // The gamepad is zeroed and the game gets nothing from it
        controllerHandler.setCaptured(true)
        grabbedBeforeCapture = grabbedInput
        // The PC is never left holding X1: Back is sent as a press and a release when it comes up
        streamActions.releaseHeldInput(lastButtonState and MotionEvent.BUTTON_BACK.inv())
        lastButtonState = 0
        // A modifier that comes up while the chrome has the keys is never seen here
        modifierFlags = 0
        specialKeyCode = KeyEvent.KEYCODE_UNKNOWN
        waitingForAllModifiersUp = false
        setInputGrabState(false)
    }

    // BOREALIS: the chrome gives the input back: capture the pointer again once the stream is up,
    // unless the user had released it (Ctrl+Alt+Shift+Z) before the chrome took the input
    private fun chromeReleased() {
        controllerHandler.setCaptured(false)
        if (connected && grabbedBeforeCapture) {
            setInputGrabState(true)
        }
    }

    private fun setInputGrabState(grab: Boolean) {
        // BOREALIS: no grab while the chrome is modal
        if (grab && currentChromeMode() == ChromeMode.MODAL) {
            return
        }

        // Grab/ungrab the mouse cursor
        if (grab) {
            inputCaptureProvider.enableCapture()

            // Enabling capture may hide the cursor again, so
            // we will need to show it again.
            if (cursorVisible) {
                inputCaptureProvider.showCursor()
            }
        } else {
            inputCaptureProvider.disableCapture()
            cancelBackHold()
        }

        // Grab/ungrab system keyboard shortcuts
        setMetaKeyCaptureState(grab)

        grabbedInput = grab
    }

    // BOREALIS: the Back button of the mouse stops waiting to open the menu: the pointer is no
    // longer ours, so the release of the button may never be seen (stream)
    private fun cancelBackHold() {
        window.decorView.removeCallbacks(openMenuFromBackHold)
        lastButtonState = lastButtonState and MotionEvent.BUTTON_BACK.inv()
    }

    private val toggleGrab = Runnable { setInputGrabState(!grabbedInput) }

    // Returns true if the key stroke was consumed
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    private fun handleSpecialKeys(androidKeyCode: Int, down: Boolean): Boolean {
        var modifierMask = 0
        var nonModifierKeyCode = KeyEvent.KEYCODE_UNKNOWN

        if (
            androidKeyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
                androidKeyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        ) {
            modifierMask = KeyboardPacket.MODIFIER_CTRL.toInt()
        } else if (
            androidKeyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                androidKeyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
        ) {
            modifierMask = KeyboardPacket.MODIFIER_SHIFT.toInt()
        } else if (
            androidKeyCode == KeyEvent.KEYCODE_ALT_LEFT ||
                androidKeyCode == KeyEvent.KEYCODE_ALT_RIGHT
        ) {
            modifierMask = KeyboardPacket.MODIFIER_ALT.toInt()
        } else if (
            androidKeyCode == KeyEvent.KEYCODE_META_LEFT ||
                androidKeyCode == KeyEvent.KEYCODE_META_RIGHT
        ) {
            modifierMask = KeyboardPacket.MODIFIER_META.toInt()
        } else {
            nonModifierKeyCode = androidKeyCode
        }

        if (down) {
            this.modifierFlags = this.modifierFlags or modifierMask
        } else {
            this.modifierFlags = this.modifierFlags and modifierMask.inv()
        }

        // Handle the special combos on the key up
        if (waitingForAllModifiersUp || specialKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
            if (specialKeyCode == androidKeyCode) {
                // If this is a key up for the special key itself, eat that because the host never
                // saw the original key down
                return true
            } else if (modifierFlags != 0) {
                // While we're waiting for modifiers to come up, eat all key downs and allow all key
                // ups to pass
                return down
            } else {
                // When all modifiers are up, perform the special action
                when (specialKeyCode) {
                    // Toggle input grab
                    KeyEvent.KEYCODE_Z -> {
                        val h = window.decorView.handler
                        if (h != null) {
                            h.postDelayed(toggleGrab, TOGGLE_GRAB_DELAY_MS)
                        }
                    }

                    // Disconnect (BOREALIS: the flow of the menu's Disconnect, with its screen and
                    // its notice, not a finish)
                    KeyEvent.KEYCODE_Q -> streamSession.disconnect()

                    // BOREALIS: open the in-game menu
                    KeyEvent.KEYCODE_M -> streamSession.openMenu()

                    // Toggle cursor visibility
                    KeyEvent.KEYCODE_C -> {
                        if (!grabbedInput) {
                            inputCaptureProvider.enableCapture()
                            grabbedInput = true
                        }
                        cursorVisible = !cursorVisible
                        if (cursorVisible) {
                            inputCaptureProvider.showCursor()
                        } else {
                            inputCaptureProvider.hideCursor()
                        }
                    }

                    else -> {}
                }

                // Reset special key state
                specialKeyCode = KeyEvent.KEYCODE_UNKNOWN
                waitingForAllModifiersUp = false
            }
        }
        // Check if Ctrl+Alt+Shift is down when a non-modifier key is pressed
        else if (
            (modifierFlags and
                (KeyboardPacket.MODIFIER_CTRL.toInt() or
                    KeyboardPacket.MODIFIER_ALT.toInt() or
                    KeyboardPacket.MODIFIER_SHIFT.toInt())) ==
                (KeyboardPacket.MODIFIER_CTRL.toInt() or
                    KeyboardPacket.MODIFIER_ALT.toInt() or
                    KeyboardPacket.MODIFIER_SHIFT.toInt()) &&
                (down && nonModifierKeyCode != KeyEvent.KEYCODE_UNKNOWN)
        ) {
            when (androidKeyCode) {
                KeyEvent.KEYCODE_Z,
                KeyEvent.KEYCODE_Q,
                KeyEvent.KEYCODE_C,
                KeyEvent.KEYCODE_M -> {
                    // Remember that a special key combo was activated, so we can consume all key
                    // events until the modifiers come up
                    specialKeyCode = androidKeyCode
                    waitingForAllModifiersUp = true
                    return true
                }

                else -> {
                    // This isn't a special combo that we consume on the client side
                    return false
                }
            }
        }

        // Not a special combo
        return false
    }

    // We cannot simply use modifierFlags for all key event processing, because
    // some IMEs will not generate real key events for pressing Shift. Instead
    // they will simply send key events with isShiftPressed() returning true,
    // and we will need to send the modifier flag ourselves.
    private fun getModifierState(event: KeyEvent): Byte {
        // Start with the global modifier state to ensure we cover the case
        // detailed in https://github.com/moonlight-stream/moonlight-android/issues/840
        var modifier = getModifierState()
        if (event.isShiftPressed) {
            modifier = (modifier.toInt() or KeyboardPacket.MODIFIER_SHIFT.toInt()).toByte()
        }
        if (event.isCtrlPressed) {
            modifier = (modifier.toInt() or KeyboardPacket.MODIFIER_CTRL.toInt()).toByte()
        }
        if (event.isAltPressed) {
            modifier = (modifier.toInt() or KeyboardPacket.MODIFIER_ALT.toInt()).toByte()
        }
        if (event.isMetaPressed) {
            modifier = (modifier.toInt() or KeyboardPacket.MODIFIER_META.toInt()).toByte()
        }
        return applyKeySpecificModifiers(event.keyCode, modifier)
    }

    private fun applyKeySpecificModifiers(keyCode: Int, modifier: Byte): Byte {
        var result = modifier
        if (keyCode == KeyEvent.KEYCODE_PLUS) {
            // The host protocol has a single US =/+ virtual key, so Android's semantic plus key
            // needs Shift.
            result = (result.toInt() or KeyboardPacket.MODIFIER_SHIFT.toInt()).toByte()
        }
        return result
    }

    private fun getModifierState(): Byte {
        return modifierFlags.toByte()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // BOREALIS: the hardware Escape key acts as Back while the chrome is modal
        if (chromeInput.actsAsBack(event)) return true
        return handleKeyDown(event) || super.onKeyDown(keyCode, event)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override fun handleKeyDown(event: KeyEvent): Boolean {
        // BOREALIS: the chrome takes the keys while it is modal
        if (chromeInput.toChrome(event)) return false
        // BOREALIS: the Android keyboard keeps a gamepad's D-pad, Enter and Back
        if (imeKeys.toIme(event)) return false
        // Pass-through virtual navigation keys
        if ((event.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
            return false
        }

        // Handle a synthetic back button event that some Android OS versions
        // create as a result of a right-click. This event WILL repeat if
        // the right mouse button is held down, so we ignore those.
        val eventSource = event.source
        if (
            (eventSource == InputDevice.SOURCE_MOUSE ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE) &&
                event.keyCode == KeyEvent.KEYCODE_BACK
        ) {
            // Send the right mouse button event if mouse back and forward
            // are disabled. If they are enabled, handleMotionEvent() will take
            // care of this.
            if (!prefConfig.mouseNavButtons) {
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
            }

            // Always return true, otherwise the back press will be propagated
            // up to the parent and finish the activity.
            return true
        }

        var handled = false

        if (ControllerHandler.isGameControllerDevice(event.device)) {
            // Always try the controller handler first, unless it's an alphanumeric keyboard device.
            // Otherwise, controller handler will eat keyboard d-pad events.
            handled = controllerHandler.handleButtonDown(event)
        }

        // Try the keyboard handler if it wasn't handled as a game controller
        if (!handled) {
            // Let this method take duplicate key down events
            if (handleSpecialKeys(event.keyCode, true)) {
                return true
            }

            // Pass through keyboard input if we're not grabbing
            if (!grabbedInput) {
                return false
            }

            // We'll send it as a raw key event if we have a key mapping, otherwise we'll send it
            // as UTF-8 text (if it's a printable character).
            val translated = keyboardTranslator.translate(event.keyCode, event.deviceId)
            if (translated.toInt() == 0) {
                // Make sure it has a valid Unicode representation and it's not a dead character
                // (which we don't support). If those are true, we can send it as UTF-8 text.
                //
                // NB: We need to be sure this happens before the getRepeatCount() check because
                // UTF-8 events don't auto-repeat on the host side.
                val unicodeChar = event.unicodeChar
                if (
                    (unicodeChar and KeyCharacterMap.COMBINING_ACCENT) == 0 &&
                        (unicodeChar and KeyCharacterMap.COMBINING_ACCENT_MASK) != 0
                ) {
                    conn.sendUtf8Text("" + unicodeChar.toChar())
                    return true
                }

                return false
            }

            // Eat repeat down events
            if (event.repeatCount > 0) {
                return true
            }

            conn.sendKeyboardInput(
                translated,
                KeyboardPacket.KEY_DOWN,
                getModifierState(event),
                if (keyboardTranslator.hasNormalizedMapping(event.keyCode, event.deviceId)) 0
                else MoonBridge.SS_KBE_FLAG_NON_NORMALIZED,
            )
            // BOREALIS: keys held down are released when the chrome takes the input
            heldKeys.down(translated.toInt() and VIRTUAL_KEY_MASK)
        }

        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // BOREALIS: the hardware Escape key acts as Back while the chrome is modal
        if (chromeInput.actsAsBack(event)) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return handleKeyUp(event) || super.onKeyUp(keyCode, event)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override fun handleKeyUp(event: KeyEvent): Boolean {
        // BOREALIS: the chrome takes the keys while it is modal
        if (chromeInput.toChrome(event)) return false
        // BOREALIS: the Android keyboard keeps a gamepad's D-pad, Enter and Back
        if (imeKeys.toIme(event)) return false
        // Pass-through virtual navigation keys
        if ((event.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
            return false
        }

        // Handle a synthetic back button event that some Android OS versions
        // create as a result of a right-click.
        val eventSource = event.source
        if (
            (eventSource == InputDevice.SOURCE_MOUSE ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE) &&
                event.keyCode == KeyEvent.KEYCODE_BACK
        ) {
            // Send the right mouse button event if mouse back and forward
            // are disabled. If they are enabled, handleMotionEvent() will take
            // care of this.
            if (!prefConfig.mouseNavButtons) {
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
            }

            // Always return true, otherwise the back press will be propagated
            // up to the parent and finish the activity.
            return true
        }

        var handled = false
        if (ControllerHandler.isGameControllerDevice(event.device)) {
            // Always try the controller handler first, unless it's an alphanumeric keyboard device.
            // Otherwise, controller handler will eat keyboard d-pad events.
            handled = controllerHandler.handleButtonUp(event)
        }

        // Try the keyboard handler if it wasn't handled as a game controller
        if (!handled) {
            if (handleSpecialKeys(event.keyCode, false)) {
                return true
            }

            // Pass through keyboard input if we're not grabbing
            if (!grabbedInput) {
                return false
            }

            val translated = keyboardTranslator.translate(event.keyCode, event.deviceId)
            if (translated.toInt() == 0) {
                // If we sent this event as UTF-8 on key down, also report that it was handled
                // when we get the key up event for it.
                val unicodeChar = event.unicodeChar
                return (unicodeChar and KeyCharacterMap.COMBINING_ACCENT) == 0 &&
                    (unicodeChar and KeyCharacterMap.COMBINING_ACCENT_MASK) != 0
            }

            conn.sendKeyboardInput(
                translated,
                KeyboardPacket.KEY_UP,
                getModifierState(event),
                if (keyboardTranslator.hasNormalizedMapping(event.keyCode, event.deviceId)) 0
                else MoonBridge.SS_KBE_FLAG_NON_NORMALIZED,
            )
            // BOREALIS: keys held down are released when the chrome takes the input
            heldKeys.up(translated.toInt() and VIRTUAL_KEY_MASK)
        }

        return true
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        return handleKeyMultiple(event) || super.onKeyMultiple(keyCode, repeatCount, event)
    }

    private fun handleKeyMultiple(event: KeyEvent): Boolean {
        // BOREALIS: the chrome takes the keys while it is modal
        if (chromeInput.toChrome(event)) return false
        // We can receive keys from a software keyboard that don't correspond to any existing
        // KEYCODE value. Android will give those to us as an ACTION_MULTIPLE KeyEvent.
        //
        // Despite the fact that the Android docs say this is unused since API level 29, these
        // events are still sent as of Android 13 for the above case.
        //
        // For other cases of ACTION_MULTIPLE, we will not report those as handled so hopefully
        // they will be passed to us again as regular singular key events.
        if (event.keyCode != KeyEvent.KEYCODE_UNKNOWN || event.characters == null) {
            return false
        }

        conn.sendUtf8Text(event.characters)
        return true
    }

    private fun getTouchContext(actionIndex: Int): TouchContext? {
        return if (actionIndex < touchContextMap.size) {
            touchContextMap[actionIndex]
        } else {
            null
        }
    }

    override fun toggleKeyboard() {
        LimeLog.info("Toggling keyboard overlay")
        // BOREALIS: hides the Android keyboard when it shows, else toggles the Borealis keyboard;
        // the Android one opens from it
        StreamKeyboards.toggle(
            streamSession,
            { ImeKeyGate.isImeVisible(window.decorView) },
            { ImeKeyGate.hideIme(window.decorView) },
        )
    }

    private fun getLiTouchTypeFromEvent(event: MotionEvent): Byte {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> MoonBridge.LI_TOUCH_EVENT_DOWN

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                if ((event.flags and MotionEvent.FLAG_CANCELED) != 0) {
                    MoonBridge.LI_TOUCH_EVENT_CANCEL
                } else {
                    MoonBridge.LI_TOUCH_EVENT_UP
                }
            }

            MotionEvent.ACTION_MOVE -> MoonBridge.LI_TOUCH_EVENT_MOVE

            // ACTION_CANCEL applies to *all* pointers in the gesture, so it maps to CANCEL_ALL
            // rather than CANCEL. For a single pointer cancellation, that's indicated via
            // FLAG_CANCELED on a ACTION_POINTER_UP.
            // https://developer.android.com/develop/ui/views/touch-and-input/gestures/multi
            MotionEvent.ACTION_CANCEL -> MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL

            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE -> MoonBridge.LI_TOUCH_EVENT_HOVER

            MotionEvent.ACTION_HOVER_EXIT -> MoonBridge.LI_TOUCH_EVENT_HOVER_LEAVE

            MotionEvent.ACTION_BUTTON_PRESS,
            MotionEvent.ACTION_BUTTON_RELEASE -> MoonBridge.LI_TOUCH_EVENT_BUTTON_ONLY

            else -> -1
        }
    }

    private fun getStreamViewRelativeNormalizedXY(
        view: View,
        event: MotionEvent,
        pointerIndex: Int,
    ): FloatArray {
        var normalizedX = event.getX(pointerIndex)
        var normalizedY = event.getY(pointerIndex)

        // For the containing background view, we must subtract the origin
        // of the StreamView to get video-relative coordinates.
        if (view != streamView) {
            normalizedX -= streamView.x
            normalizedY -= streamView.y
        }

        normalizedX = max(normalizedX, 0.0f)
        normalizedY = max(normalizedY, 0.0f)

        normalizedX = min(normalizedX, streamView.width.toFloat())
        normalizedY = min(normalizedY, streamView.height.toFloat())

        normalizedX /= streamView.width
        normalizedY /= streamView.height

        return floatArrayOf(normalizedX, normalizedY)
    }

    private fun normalizeValueInRange(value: Float, range: InputDevice.MotionRange): Float {
        return (value - range.min) / range.range
    }

    private fun getPressureOrDistance(event: MotionEvent, pointerIndex: Int): Float {
        val dev = event.device
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_HOVER_EXIT -> {
                // Hover events report distance
                if (dev != null) {
                    val distanceRange = dev.getMotionRange(MotionEvent.AXIS_DISTANCE, event.source)
                    if (distanceRange != null) {
                        return normalizeValueInRange(
                            event.getAxisValue(MotionEvent.AXIS_DISTANCE, pointerIndex),
                            distanceRange,
                        )
                    }
                }
                return 0.0f
            }

            // Other events report pressure
            else -> return event.getPressure(pointerIndex)
        }
    }

    private fun getRotationDegrees(event: MotionEvent, pointerIndex: Int): Short {
        val dev = event.device
        if (dev != null) {
            if (dev.getMotionRange(MotionEvent.AXIS_ORIENTATION, event.source) != null) {
                var rotationDegrees =
                    Math.toDegrees(event.getOrientation(pointerIndex).toDouble()).toInt().toShort()
                if (rotationDegrees < 0) {
                    rotationDegrees = (rotationDegrees + FULL_CIRCLE_DEGREES).toShort()
                }
                return rotationDegrees
            }
        }
        return MoonBridge.LI_ROT_UNKNOWN
    }

    private fun polarToCartesian(r: Float, theta: Float): FloatArray {
        return floatArrayOf(
            (r * cos(theta.toDouble())).toFloat(),
            (r * sin(theta.toDouble())).toFloat(),
        )
    }

    private fun cartesianToR(point: FloatArray): Float {
        return sqrt(point[0].toDouble().pow(2) + point[1].toDouble().pow(2)).toFloat()
    }

    private fun getStreamViewNormalizedContactArea(
        event: MotionEvent,
        pointerIndex: Int,
    ): FloatArray {
        val orientation: Float

        // If the orientation is unknown, we'll just assume it's at a 45 degree angle and scale it
        // by X and Y scaling factors evenly.
        if (
            event.device == null ||
                event.device.getMotionRange(MotionEvent.AXIS_ORIENTATION, event.source) == null
        ) {
            orientation = (Math.PI / QUARTER_TURN_DIVISOR).toFloat()
        } else {
            orientation = event.getOrientation(pointerIndex)
        }

        val contactAreaMajor: Float
        val contactAreaMinor: Float
        when (event.actionMasked) {
            // Hover events report the tool size
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_HOVER_EXIT -> {
                contactAreaMajor = event.getToolMajor(pointerIndex)
                contactAreaMinor = event.getToolMinor(pointerIndex)
            }

            // Other events report contact area
            else -> {
                contactAreaMajor = event.getTouchMajor(pointerIndex)
                contactAreaMinor = event.getTouchMinor(pointerIndex)
            }
        }

        // The contact area major axis is parallel to the orientation, so we simply convert
        // polar to cartesian coordinates using the orientation as theta.
        val contactAreaMajorCartesian = polarToCartesian(contactAreaMajor, orientation)

        // The contact area minor axis is perpendicular to the contact area major axis (and thus
        // the orientation), so rotate the orientation angle by 90 degrees.
        val contactAreaMinorCartesian =
            polarToCartesian(contactAreaMinor, (orientation + (Math.PI / 2)).toFloat())

        // Normalize the contact area to the stream view size
        contactAreaMajorCartesian[0] =
            min(abs(contactAreaMajorCartesian[0]), streamView.width.toFloat()) / streamView.width
        contactAreaMinorCartesian[0] =
            min(abs(contactAreaMinorCartesian[0]), streamView.width.toFloat()) / streamView.width
        contactAreaMajorCartesian[1] =
            min(abs(contactAreaMajorCartesian[1]), streamView.height.toFloat()) / streamView.height
        contactAreaMinorCartesian[1] =
            min(abs(contactAreaMinorCartesian[1]), streamView.height.toFloat()) / streamView.height

        // Convert the normalized values back into polar coordinates
        return floatArrayOf(
            cartesianToR(contactAreaMajorCartesian),
            cartesianToR(contactAreaMinorCartesian),
        )
    }

    private fun sendPenEventForPointer(
        view: View,
        event: MotionEvent,
        eventType: Byte,
        toolType: Byte,
        pointerIndex: Int,
    ): Boolean {
        var penButtons: Byte = 0
        if ((event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0) {
            penButtons = (penButtons.toInt() or MoonBridge.LI_PEN_BUTTON_PRIMARY.toInt()).toByte()
        }
        if ((event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0) {
            penButtons = (penButtons.toInt() or MoonBridge.LI_PEN_BUTTON_SECONDARY.toInt()).toByte()
        }

        var tiltDegrees = MoonBridge.LI_TILT_UNKNOWN
        val dev = event.device
        if (dev != null) {
            if (dev.getMotionRange(MotionEvent.AXIS_TILT, event.source) != null) {
                tiltDegrees =
                    Math.toDegrees(
                            event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex).toDouble()
                        )
                        .toInt()
                        .toByte()
            }
        }

        val normalizedCoords = getStreamViewRelativeNormalizedXY(view, event, pointerIndex)
        val normalizedContactArea = getStreamViewNormalizedContactArea(event, pointerIndex)
        return conn.sendPenEvent(
            eventType,
            toolType,
            penButtons,
            normalizedCoords[0],
            normalizedCoords[1],
            getPressureOrDistance(event, pointerIndex),
            normalizedContactArea[0],
            normalizedContactArea[1],
            getRotationDegrees(event, pointerIndex),
            tiltDegrees,
        ) != MoonBridge.LI_ERR_UNSUPPORTED
    }

    private fun convertToolTypeToStylusToolType(event: MotionEvent, pointerIndex: Int): Byte {
        return when (event.getToolType(pointerIndex)) {
            MotionEvent.TOOL_TYPE_ERASER -> MoonBridge.LI_TOOL_TYPE_ERASER
            MotionEvent.TOOL_TYPE_STYLUS -> MoonBridge.LI_TOOL_TYPE_PEN
            else -> MoonBridge.LI_TOOL_TYPE_UNKNOWN
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("ReturnCount")
    private fun trySendPenEvent(view: View, event: MotionEvent): Boolean {
        val eventType = getLiTouchTypeFromEvent(event)
        if (eventType < 0) {
            return false
        }

        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            // Move events may impact all active pointers
            var handledStylusEvent = false
            for (i in 0 until event.pointerCount) {
                val toolType = convertToolTypeToStylusToolType(event, i)
                if (toolType == MoonBridge.LI_TOOL_TYPE_UNKNOWN) {
                    // Not a stylus pointer, so skip it
                    continue
                } else {
                    // This pointer is a stylus, so we'll report that we handled this event
                    handledStylusEvent = true
                }

                if (!sendPenEventForPointer(view, event, eventType, toolType, i)) {
                    // Pen events aren't supported by the host
                    return false
                }
            }
            return handledStylusEvent
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            // Cancel impacts all active pointers
            return conn.sendPenEvent(
                MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL,
                MoonBridge.LI_TOOL_TYPE_UNKNOWN,
                0,
                0f,
                0f,
                0f,
                0f,
                0f,
                MoonBridge.LI_ROT_UNKNOWN,
                MoonBridge.LI_TILT_UNKNOWN,
            ) != MoonBridge.LI_ERR_UNSUPPORTED
        } else {
            // Up, Down, and Hover events are specific to the action index
            val toolType = convertToolTypeToStylusToolType(event, event.actionIndex)
            if (toolType == MoonBridge.LI_TOOL_TYPE_UNKNOWN) {
                // Not a stylus event
                return false
            }
            return sendPenEventForPointer(view, event, eventType, toolType, event.actionIndex)
        }
    }

    private fun sendTouchEventForPointer(
        view: View,
        event: MotionEvent,
        eventType: Byte,
        pointerIndex: Int,
    ): Boolean {
        val normalizedCoords = getStreamViewRelativeNormalizedXY(view, event, pointerIndex)
        val normalizedContactArea = getStreamViewNormalizedContactArea(event, pointerIndex)
        return conn.sendTouchEvent(
            eventType,
            event.getPointerId(pointerIndex),
            normalizedCoords[0],
            normalizedCoords[1],
            getPressureOrDistance(event, pointerIndex),
            normalizedContactArea[0],
            normalizedContactArea[1],
            getRotationDegrees(event, pointerIndex),
        ) != MoonBridge.LI_ERR_UNSUPPORTED
    }

    // Nothing calls it since the Java commented out the call in handleMotionEvent; the owner keeps
    // the native touch path.
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("ReturnCount", "UnusedPrivateFunction")
    private fun trySendTouchEvent(view: View, event: MotionEvent): Boolean {
        val eventType = getLiTouchTypeFromEvent(event)
        if (eventType < 0) {
            return false
        }

        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            // Move events may impact all active pointers
            for (i in 0 until event.pointerCount) {
                if (!sendTouchEventForPointer(view, event, eventType, i)) {
                    return false
                }
            }
            return true
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            // Cancel impacts all active pointers
            return conn.sendTouchEvent(
                MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL,
                0,
                0f,
                0f,
                0f,
                0f,
                0f,
                MoonBridge.LI_ROT_UNKNOWN,
            ) != MoonBridge.LI_ERR_UNSUPPORTED
        } else {
            // Up, Down, and Hover events are specific to the action index
            return sendTouchEventForPointer(view, event, eventType, event.actionIndex)
        }
    }

    // Returns true if the event was consumed
    // NB: View is only present if called from a view callback
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress(
        "ComplexCondition",
        "CyclomaticComplexMethod",
        "ForbiddenComment",
        "LongMethod",
        "NestedBlockDepth",
        "ReturnCount",
    )
    private fun handleMotionEvent(view: View?, event: MotionEvent): Boolean {
        // BOREALIS: the chrome takes the input while it is modal
        if (chromeInput.toChrome(event)) return false
        // Pass through mouse/touch/joystick input if we're not grabbing
        if (!grabbedInput) {
            return false
        }

        val eventSource = event.source
        val deviceSources = if (event.device != null) event.device.sources else 0
        if ((eventSource and InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
            if (controllerHandler.handleMotionEvent(event)) {
                return true
            }
        } else if (
            (deviceSources and InputDevice.SOURCE_CLASS_JOYSTICK) != 0 &&
                controllerHandler.tryHandleTouchpadEvent(event)
        ) {
            return true
        } else if (
            (eventSource and InputDevice.SOURCE_CLASS_POINTER) != 0 ||
                (eventSource and InputDevice.SOURCE_CLASS_POSITION) != 0 ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE
        ) {
            // This case is for mice and non-finger touch devices
            if (
                eventSource == InputDevice.SOURCE_MOUSE ||
                    (eventSource and InputDevice.SOURCE_CLASS_POSITION) != 0 || // SOURCE_TOUCHPAD
                    eventSource == InputDevice.SOURCE_MOUSE_RELATIVE ||
                    (event.pointerCount >= 1 &&
                        (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE ||
                            event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                            event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER)) ||
                    eventSource == SAMSUNG_DEX_MOUSE_SOURCE // Samsung DeX mode desktop mouse
            ) {
                var buttonState = event.buttonState
                var changedButtons = buttonState xor lastButtonState

                // The DeX touchpad on the Fold 4 sends proper right click events using
                // BUTTON_SECONDARY, but doesn't send BUTTON_PRIMARY for a regular click. Instead it
                // sends ACTION_DOWN/UP, so we need to fix that up to look like a sane input event
                // to process it correctly.
                if (eventSource == SAMSUNG_DEX_MOUSE_SOURCE) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        buttonState = buttonState or MotionEvent.BUTTON_PRIMARY
                    } else if (event.action == MotionEvent.ACTION_UP) {
                        buttonState = buttonState and MotionEvent.BUTTON_PRIMARY.inv()
                    } else {
                        // We may be faking the primary button down from a previous event,
                        // so be sure to add that bit back into the button state.
                        buttonState =
                            buttonState or (lastButtonState and MotionEvent.BUTTON_PRIMARY)
                    }

                    changedButtons = buttonState xor lastButtonState
                }

                // Ignore mouse input if we're not capturing from our input source
                if (!inputCaptureProvider.isCapturingActive()) {
                    // We return true here because otherwise the events may end up causing
                    // Android to synthesize d-pad events.
                    return true
                }

                // Always update the position before sending any button events. If we're
                // dealing with a stylus without hover support, our position might be
                // significantly different than before.
                if (inputCaptureProvider.eventHasRelativeMouseAxes(event)) {
                    // Send the deltas straight from the motion event
                    val deltaX = inputCaptureProvider.getRelativeAxisX(event).toInt().toShort()
                    val deltaY = inputCaptureProvider.getRelativeAxisY(event).toInt().toShort()

                    if (deltaX.toInt() != 0 || deltaY.toInt() != 0) {
                        if (prefConfig.absoluteMouseMode) {
                            // NB: view may be null, but we can unconditionally use streamView
                            // because we don't need to adjust relative axis deltas for the position
                            // of the streamView within the parent's coordinate system.
                            conn.sendMouseMoveAsMousePosition(
                                deltaX,
                                deltaY,
                                streamView.width.toShort(),
                                streamView.height.toShort(),
                            )
                        } else {
                            conn.sendMouseMove(deltaX, deltaY)
                        }
                    }
                } else if ((eventSource and InputDevice.SOURCE_CLASS_POSITION) != 0) {
                    // If this input device is not associated with the view itself (like a
                    // trackpad), we'll convert the device-specific coordinates to use to send the
                    // cursor position. This really isn't ideal but it's probably better than
                    // nothing.
                    //
                    // Trackpad on newer versions of Android (Oreo and later) should be caught by
                    // the relative axes case above. If we get here, we're on an older version that
                    // doesn't support pointer capture.
                    val device = event.device
                    if (device != null) {
                        val xRange = device.getMotionRange(MotionEvent.AXIS_X, eventSource)
                        val yRange = device.getMotionRange(MotionEvent.AXIS_Y, eventSource)

                        // All touchpads coordinate planes should start at (0, 0)
                        if (
                            xRange != null && yRange != null && xRange.min == 0f && yRange.min == 0f
                        ) {
                            val xMax = xRange.max.toInt()
                            val yMax = yRange.max.toInt()

                            // Touchpads must be smaller than (65535, 65535)
                            if (xMax <= Short.MAX_VALUE && yMax <= Short.MAX_VALUE) {
                                conn.sendMousePosition(
                                    event.x.toInt().toShort(),
                                    event.y.toInt().toShort(),
                                    xMax.toShort(),
                                    yMax.toShort(),
                                )
                            }
                        }
                    }
                } else if (view != null && trySendPenEvent(view, event)) {
                    // If our host supports pen events, send it directly
                    return true
                } else if (view != null) {
                    // Otherwise send absolute position based on the view for SOURCE_CLASS_POINTER
                    updateMousePosition(view, event)
                }

                if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
                    // Send the vertical scroll packet
                    conn.sendMouseHighResScroll(
                        (event.getAxisValue(MotionEvent.AXIS_VSCROLL) * HIGH_RES_SCROLL_UNIT)
                            .toInt()
                            .toShort()
                    )
                    conn.sendMouseHighResHScroll(
                        (event.getAxisValue(MotionEvent.AXIS_HSCROLL) * HIGH_RES_SCROLL_UNIT)
                            .toInt()
                            .toShort()
                    )
                }

                if ((changedButtons and MotionEvent.BUTTON_PRIMARY) != 0) {
                    if ((buttonState and MotionEvent.BUTTON_PRIMARY) != 0) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                    } else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                    }
                }

                // Mouse secondary or stylus primary is right click (stylus down is left click)
                if (
                    (changedButtons and
                        (MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_PRIMARY)) != 0
                ) {
                    if (
                        (buttonState and
                            (MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_PRIMARY)) !=
                            0
                    ) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                    } else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                    }
                }

                // Mouse tertiary or stylus secondary is middle click
                if (
                    (changedButtons and
                        (MotionEvent.BUTTON_TERTIARY or MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0
                ) {
                    if (
                        (buttonState and
                            (MotionEvent.BUTTON_TERTIARY or MotionEvent.BUTTON_STYLUS_SECONDARY)) !=
                            0
                    ) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE)
                    } else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE)
                    }
                }

                if (prefConfig.mouseNavButtons) {
                    if ((changedButtons and MotionEvent.BUTTON_BACK) != 0) {
                        if ((buttonState and MotionEvent.BUTTON_BACK) != 0) {
                            // BOREALIS: Back waits to see whether it is held (stream)
                            backHoldOpenedMenu = false
                            window.decorView.postDelayed(openMenuFromBackHold, BACK_HOLD_MS)
                        } else {
                            window.decorView.removeCallbacks(openMenuFromBackHold)
                            // BOREALIS: a short press is X1, sent now; a hold sent nothing (stream)
                            if (!backHoldOpenedMenu) {
                                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X1)
                                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1)
                            }
                        }
                    }

                    if ((changedButtons and MotionEvent.BUTTON_FORWARD) != 0) {
                        if ((buttonState and MotionEvent.BUTTON_FORWARD) != 0) {
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X2)
                        } else {
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2)
                        }
                    }
                }

                // Handle stylus presses
                if (event.pointerCount == 1 && event.actionIndex == 0) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                            lastAbsTouchDownTime = event.eventTime
                            lastAbsTouchDownX = event.getX(0)
                            lastAbsTouchDownY = event.getY(0)

                            // Stylus is left click
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                        } else if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) {
                            lastAbsTouchDownTime = event.eventTime
                            lastAbsTouchDownX = event.getX(0)
                            lastAbsTouchDownY = event.getY(0)

                            // Eraser is right click
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                        }
                    } else if (
                        event.actionMasked == MotionEvent.ACTION_UP ||
                            event.actionMasked == MotionEvent.ACTION_CANCEL
                    ) {
                        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                            lastAbsTouchUpTime = event.eventTime
                            lastAbsTouchUpX = event.getX(0)
                            lastAbsTouchUpY = event.getY(0)

                            // Stylus is left click
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                        } else if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) {
                            lastAbsTouchUpTime = event.eventTime
                            lastAbsTouchUpX = event.getX(0)
                            lastAbsTouchUpY = event.getY(0)

                            // Eraser is right click
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                        }
                    }
                }

                lastButtonState = buttonState
            }
            // This case is for fingers
            else {
                val controllerMode = virtualController?.controllerMode
                if (
                    controllerMode == VirtualController.ControllerMode.MoveButtons ||
                        controllerMode == VirtualController.ControllerMode.ResizeButtons
                ) {
                    // Ignore presses when the virtual controller is being configured
                    return true
                }

                // BOREALIS: the touch mode of the stream session; Off ignores the touch screen
                val touchMode = TouchContexts.mode(streamSession, prefConfig)
                if (touchMode == TouchMode.OFF) {
                    return true
                }

                // If this is the parent view, we'll offset our coordinates to appear as if they
                // are relative to the StreamView like our StreamView touch events are.
                val xOffset: Float
                val yOffset: Float
                if (view != streamView && touchMode != TouchMode.TRACKPAD) {
                    xOffset = -streamView.x
                    yOffset = -streamView.y
                } else {
                    xOffset = 0f
                    yOffset = 0f
                }

                val actionIndex = event.actionIndex

                val eventX = (event.getX(actionIndex) + xOffset).toInt()
                val eventY = (event.getY(actionIndex) + yOffset).toInt()

                // Special handling for 3 finger gesture
                if (
                    event.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
                        event.pointerCount == THREE_FINGER_COUNT
                ) {
                    // Three fingers down
                    threeFingerDownTime = event.eventTime

                    // Cancel the first and second touches to avoid
                    // erroneous events
                    for (aTouchContext in touchContextMap) {
                        touchContextEntry(aTouchContext).cancelTouch()
                    }

                    return true
                }

                // TODO: Re-enable native touch when have a better solution for handling
                // cancelled touches from Android gestures and 3 finger taps to activate
                // the software keyboard.
                /*if (touchMode == TouchMode.DIRECT && trySendTouchEvent(view, event)) {
                    // If this host supports touch events and absolute touch is enabled,
                    // send it directly as a touch event.
                    return true;
                }*/

                val context = getTouchContext(actionIndex)
                if (context == null) {
                    return false
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_DOWN -> {
                        for (touchContext in touchContextMap) {
                            touchContextEntry(touchContext).setPointerCount(event.pointerCount)
                        }
                        context.touchDownEvent(eventX, eventY, event.eventTime, true)
                    }

                    MotionEvent.ACTION_POINTER_UP,
                    MotionEvent.ACTION_UP -> {
                        if (
                            event.pointerCount == 1 &&
                                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                    (event.flags and MotionEvent.FLAG_CANCELED) == 0)
                        ) {
                            // All fingers up
                            if (
                                event.eventTime - threeFingerDownTime < THREE_FINGER_TAP_THRESHOLD
                            ) {
                                // This is a 3 finger tap to bring up the keyboard
                                toggleKeyboard()
                                return true
                            }
                        }

                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                (event.flags and MotionEvent.FLAG_CANCELED) != 0
                        ) {
                            context.cancelTouch()
                        } else {
                            context.touchUpEvent(eventX, eventY, event.eventTime)
                        }

                        for (touchContext in touchContextMap) {
                            touchContextEntry(touchContext).setPointerCount(event.pointerCount - 1)
                        }
                        if (actionIndex == 0 && event.pointerCount > 1 && !context.isCancelled) {
                            // The original secondary touch now becomes primary
                            context.touchDownEvent(
                                (event.getX(1) + xOffset).toInt(),
                                (event.getY(1) + yOffset).toInt(),
                                event.eventTime,
                                false,
                            )
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // ACTION_MOVE is special because it always has actionIndex == 0
                        // We'll call the move handlers for all indexes manually

                        // First process the historical events
                        for (i in 0 until event.historySize) {
                            for (aTouchContextMap in touchContextMap) {
                                val touch = touchContextEntry(aTouchContextMap)
                                if (touch.actionIndex < event.pointerCount) {
                                    touch.touchMoveEvent(
                                        (event.getHistoricalX(touch.actionIndex, i) + xOffset)
                                            .toInt(),
                                        (event.getHistoricalY(touch.actionIndex, i) + yOffset)
                                            .toInt(),
                                        event.getHistoricalEventTime(i),
                                    )
                                }
                            }
                        }

                        // Now process the current values
                        for (aTouchContextMap in touchContextMap) {
                            val touch = touchContextEntry(aTouchContextMap)
                            if (touch.actionIndex < event.pointerCount) {
                                touch.touchMoveEvent(
                                    (event.getX(touch.actionIndex) + xOffset).toInt(),
                                    (event.getY(touch.actionIndex) + yOffset).toInt(),
                                    event.eventTime,
                                )
                            }
                        }
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        for (aTouchContext in touchContextMap) {
                            touchContextEntry(aTouchContext).cancelTouch()
                            touchContextEntry(aTouchContext).setPointerCount(0)
                        }
                    }

                    else -> return false
                }
            }

            // Handled a known source
            return true
        }

        // Unknown class
        return false
    }

    // The Java dereferenced the entries of the touch context array without a check: an entry that
    // was never filled is a NullPointerException at the call.
    private fun touchContextEntry(entry: TouchContext?): TouchContext {
        return entry ?: throw NullPointerException("The touch contexts have not been built")
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return handleMotionEvent(null, event) || super.onGenericMotionEvent(event)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("ComplexCondition")
    private fun updateMousePosition(touchedView: View, event: MotionEvent) {
        // X and Y are already relative to the provided view object
        var eventX: Float
        var eventY: Float

        // For our StreamView itself, we can use the coordinates unmodified.
        if (touchedView == streamView) {
            eventX = event.getX(0)
            eventY = event.getY(0)
        } else {
            // For the containing background view, we must subtract the origin
            // of the StreamView to get video-relative coordinates.
            eventX = event.getX(0) - streamView.x
            eventY = event.getY(0) - streamView.y
        }

        if (
            event.pointerCount == 1 &&
                event.actionIndex == 0 &&
                (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER ||
                    event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS)
        ) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_HOVER_ENTER,
                MotionEvent.ACTION_HOVER_EXIT,
                MotionEvent.ACTION_HOVER_MOVE -> {
                    if (
                        event.eventTime - lastAbsTouchUpTime <= STYLUS_UP_DEAD_ZONE_DELAY &&
                            sqrt(
                                (eventX - lastAbsTouchUpX).toDouble().pow(2) +
                                    (eventY - lastAbsTouchUpY).toDouble().pow(2)
                            ) <= STYLUS_UP_DEAD_ZONE_RADIUS
                    ) {
                        // Enforce a small deadzone between touch up and hover or touch down to
                        // allow more precise double-clicking
                        return
                    }
                }

                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP -> {
                    if (
                        event.eventTime - lastAbsTouchDownTime <= STYLUS_DOWN_DEAD_ZONE_DELAY &&
                            sqrt(
                                (eventX - lastAbsTouchDownX).toDouble().pow(2) +
                                    (eventY - lastAbsTouchDownY).toDouble().pow(2)
                            ) <= STYLUS_DOWN_DEAD_ZONE_RADIUS
                    ) {
                        // Enforce a small deadzone between touch down and move or touch up to allow
                        // more precise double-clicking
                        return
                    }
                }
            }
        }

        // We may get values slightly outside our view region on ACTION_HOVER_ENTER and
        // ACTION_HOVER_EXIT. Normalize these to the view size. We can't just drop them because we
        // won't always get an event right at the boundary of the view, so dropping them would
        // result in our cursor never really reaching the sides of the screen.
        eventX = min(max(eventX, 0f), streamView.width.toFloat())
        eventY = min(max(eventY, 0f), streamView.height.toFloat())

        conn.sendMousePosition(
            eventX.toInt().toShort(),
            eventY.toInt().toShort(),
            streamView.width.toShort(),
            streamView.height.toShort(),
        )
    }

    override fun onGenericMotion(view: View, event: MotionEvent): Boolean {
        return handleMotionEvent(view, event)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // Tell the OS not to buffer input events for us
            //
            // NB: This is still needed even when we call the newer requestUnbufferedDispatch()!
            view.requestUnbufferedDispatch(event)
        }

        return handleMotionEvent(view, event)
    }

    override fun stageStarting(stage: String?) {
        // BOREALIS: the chrome shows the stage
        runOnUiThread {
            streamSession.onStage(
                StreamSessionFactory.startStage(
                    stage ?: throw NullPointerException("The stage has no name"),
                    app.appName ?: throw NullPointerException("The app has no name"),
                )
            )
        }
    }

    // BOREALIS: /launch or /resume is about to be requested: the chrome shows the "starting app"
    // stage (host.failure)
    override fun launchStarting(resume: Boolean) {
        runOnUiThread { streamSession.onStage(StartStage.STARTING_APP) }
    }

    override fun stageComplete(stage: String?) {
        // The inherited Java did nothing when a stage completed
    }

    // BOREALIS: another app is running on the host: Join, Quit and relaunch or Cancel (host.launch)
    override fun launchConflict(
        runningAppId: Int,
        runningAppName: String?,
        requestedAppId: Int,
        requestedAppName: String?,
        answer: ConflictAnswer,
    ) {
        val requested = requestedAppName ?: appName.orEmpty()
        runOnUiThread {
            streamSession.onConflict(
                runningAppName,
                requested,
                runningAppId == requestedAppId,
                answer,
            )
        }
    }

    // BOREALIS: retry progress of a LuminalShine 409/503 answer on the chrome (host.launch)
    override fun launchRetrying(category: Int, attempt: Int, maxAttempts: Int, delayMs: Int) {
        runOnUiThread {
            streamSession.onLaunchRetry(
                StreamSessionFactory.launchRetry(category, attempt, maxAttempts, delayMs)
            )
        }
    }

    // BOREALIS: stops the connection and runs [done] once it has stopped, from the thread that
    // stopped it; at once when no connection runs (leave)
    internal fun stopConnectionThen(done: Runnable) = stopConnection(done)

    private fun stopConnection(done: Runnable? = null) {
        startupGuard.cancel() // BOREALIS: first-frame watchdog
        if (connected) {
            connected = false
            SessionAudit.stopped() // BOREALIS: audit trail
            updatePipAutoEnter()

            controllerHandler.stop()

            // Update GameManager state to indicate we're no longer in game
            UiHelper.notifyStreamEnded(this)

            // Stop may take a few hundred ms to do some network I/O to tell
            // the server we're going away and clean up. Let it run in a separate
            // thread to keep things smooth for the UI. Inside moonlight-common,
            // we prevent another thread from starting a connection before and
            // during the process of stopping this one.
            object : Thread() {
                    override fun run() {
                        val stopStarted = System.nanoTime() // BOREALIS: leave log
                        conn.stop()
                        BorealisLog.i(
                            LogTag.APP,
                            "connection stop done",
                            "ms" to TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStarted),
                        )
                        tellConnectionStopped()
                        done?.run()
                    }
                }
                .start()
        } else {
            // BOREALIS: a start that failed after the decoder stage holds a decoder and no
            // connection (stream)
            tellConnectionStopped()
            done?.run() // BOREALIS: nothing runs, so nothing to wait for (leave)
        }
    }

    // BOREALIS: the stream's decoder is released with the connection, so a clip of the background
    // plays again; from any thread, run on the main one (stream)
    private fun tellConnectionStopped() {
        if (::streamSession.isInitialized) runOnUiThread { streamSession.onConnectionStopped() }
    }

    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
        // Perform a connection test if the failure could be due to a blocked port
        // This does network I/O, so don't do it on the main thread.
        val portTestResult =
            MoonBridge.testClientConnectivity(
                ServerHelper.CONNECTION_TEST_SERVER,
                CONNECTION_TEST_PORT,
                portFlags,
            )

        runOnUiThread {
            if (!displayedFailureDialog) {
                displayedFailureDialog = true
                LimeLog.severe("$stage failed: $errorCode")

                // If video initialization failed and the surface is still valid, display extra
                // information for the user
                if (stage.contains("video") && streamView.holder.surface.isValid) {
                    Toast.makeText(
                            this,
                            resources.getText(R.string.video_decoder_init_failed),
                            Toast.LENGTH_LONG,
                        )
                        .show()
                }

                // BOREALIS: the chrome shows the classified failure (stream)
                val portBlocked =
                    portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0
                streamSession.onFailed(
                    StreamFailures.failure(
                        this,
                        StreamFailures.facts(
                            stage,
                            errorCode,
                            portFlags,
                            StreamFailures.SessionState(firstFrameShown, startNote, conn),
                        ),
                        portBlocked,
                    )
                )
            }
        }
    }

    override fun connectionTerminated(errorCode: Int) {
        // BOREALIS: the host ends the stream when it gets the Quit that the user confirmed, and the
        // control stream reports the ENet disconnect (-1); that end is the quit's, so no
        // connectivity test and no error. Any other code is a network drop (leave)
        val endedByQuit = ::streamSession.isInitialized && streamSession.endedByQuit(errorCode)

        // Perform a connection test if the failure could be due to a blocked port
        // This does network I/O, so don't do it on the main thread.
        val portFlags = MoonBridge.getPortFlagsFromTerminationErrorCode(errorCode)
        val portTestResult =
            if (endedByQuit) {
                MoonBridge.ML_TEST_RESULT_INCONCLUSIVE
            } else {
                MoonBridge.testClientConnectivity(
                    ServerHelper.CONNECTION_TEST_SERVER,
                    CONNECTION_TEST_PORT,
                    portFlags,
                )
            }

        runOnUiThread {
            startupGuard.cancel() // BOREALIS: first-frame watchdog
            SessionAudit.terminated(errorCode) // BOREALIS: audit trail

            // Let the display go to sleep now
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Stop processing controller input
            controllerHandler.stop()

            // Ungrab input
            setInputGrabState(false)

            if (!displayedFailureDialog) {
                displayedFailureDialog = true
                if (!endedByQuit) LimeLog.severe("Connection terminated: $errorCode")
                stopConnection()

                // Display the error dialog if it was an unexpected termination.
                // Otherwise, just finish the activity immediately.
                if (errorCode != MoonBridge.ML_ERROR_GRACEFUL_TERMINATION) {
                    // BOREALIS: the chrome shows the classified failure (stream)
                    val portBlocked =
                        portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE &&
                            portTestResult != 0
                    streamSession.onFailed(
                        StreamFailures.failure(
                            this,
                            StreamFailures.facts(
                                null,
                                errorCode,
                                portFlags,
                                StreamFailures.SessionState(firstFrameShown, startNote, conn),
                            ),
                            portBlocked,
                        )
                    )
                } else {
                    // BOREALIS: another exit may have finished the activity already (leave)
                    if (!isFinishing) finish()
                }
            }
        }
    }

    override fun connectionStatusUpdate(connectionStatus: Int) {
        runOnUiThread {
            if (prefConfig.disableWarnings) {
                return@runOnUiThread
            }

            if (connectionStatus == MoonBridge.CONN_STATUS_POOR) {
                if (prefConfig.bitrate > SLOW_CONNECTION_BITRATE_KBPS) {
                    notificationOverlayView.text = resources.getString(R.string.slow_connection_msg)
                } else {
                    notificationOverlayView.text = resources.getString(R.string.poor_connection_msg)
                }

                requestedNotificationOverlayVisibility = View.VISIBLE
            } else if (connectionStatus == MoonBridge.CONN_STATUS_OKAY) {
                requestedNotificationOverlayVisibility = View.GONE
            }

            if (!isHidingOverlays) {
                notificationOverlayView.visibility = requestedNotificationOverlayVisibility
            }
        }
    }

    override fun connectionStarted() {
        runOnUiThread {
            connected = true
            updatePipAutoEnter()
            // BOREALIS: the chrome waits for the first frame
            streamSession.onConnectionStarted(
                StreamSessionFactory.sessionInfo(
                    prefConfig,
                    streamFrameRate,
                    decoderRenderer.getActiveVideoFormat(),
                    decoderRenderer.getDecoderName(),
                )
            )
            startupGuard.onConnectionStarted() // BOREALIS: first-frame watchdog
            // BOREALIS: audit trail
            SessionAudit.started(
                pcName,
                intent.getStringExtra(EXTRA_HOST),
                decoderRenderer.getActiveVideoFormat(),
                prefConfig.width,
                prefConfig.height,
                streamFrameRate,
            )

            // Hide the mouse cursor now after a short delay.
            // Doing it before dismissing the spinner seems to be undone
            // when the spinner gets displayed. On Android Q, even now
            // is too early to capture. We will delay 500 ms to allow
            // the spinner to dismiss before capturing.
            val h = Handler()
            h.postDelayed({ setInputGrabState(true) }, INPUT_GRAB_DELAY_MS)

            // Keep the display on
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Update GameManager state to indicate we're in game
            UiHelper.notifyStreamConnected(this)

            hideSystemUi(HIDE_UI_AFTER_CONNECT_MS)
        }

        // Report this shortcut being used (off the main thread to prevent ANRs)
        val computer = ComputerDetails()
        computer.name = pcName
        computer.uuid = intent.getStringExtra(EXTRA_PC_UUID)
        val shortcutHelper = ShortcutHelper(this)
        shortcutHelper.reportComputerShortcutUsed(computer)
        if (appName != null) {
            // This may be null if launched from the "Resume Session" PC context menu item
            shortcutHelper.reportGameLaunched(computer, app)
        }
    }

    override fun displayMessage(message: String) {
        // BOREALIS: the error screen shows it under the raw code, not a toast over the screen
        startNote = message
    }

    override fun displayTransientMessage(message: String) {
        if (!prefConfig.disableWarnings) {
            runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
        }
    }

    override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) {
        LimeLog.info(
            String.format(
                null as Locale?,
                "Rumble on gamepad %d: %04x %04x",
                controllerNumber,
                lowFreqMotor,
                highFreqMotor,
            )
        )

        controllerHandler.handleRumble(controllerNumber, lowFreqMotor, highFreqMotor)
    }

    override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) {
        LimeLog.info(
            String.format(
                null as Locale?,
                "Rumble on gamepad triggers %d: %04x %04x",
                controllerNumber,
                leftTrigger,
                rightTrigger,
            )
        )

        controllerHandler.handleRumbleTriggers(controllerNumber, leftTrigger, rightTrigger)
    }

    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        LimeLog.info("Display HDR mode: " + (if (enabled) "enabled" else "disabled"))
        decoderRenderer.setHdrMode(enabled, hdrMetadata)
    }

    override fun setMotionEventState(
        controllerNumber: Short,
        motionType: Byte,
        reportRateHz: Short,
    ) {
        controllerHandler.handleSetMotionEventState(controllerNumber, motionType, reportRateHz)
    }

    override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) {
        controllerHandler.handleSetControllerLED(controllerNumber, r, g, b)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        check(surfaceCreated) { "Surface changed before creation!" }

        if (!attemptedConnection) {
            attemptedConnection = true

            // Update GameManager state to indicate we're "loading" while connecting
            UiHelper.notifyStreamConnecting(this)

            decoderRenderer.setRenderTarget(holder)
            conn.start(AndroidAudioRenderer(this, prefConfig.enableAudioFx), decoderRenderer, this)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val desiredFrameRate: Float

        surfaceCreated = true

        // Android will pick the lowest matching refresh rate for a given frame rate value, so we
        // want to report the true FPS value if refresh rate reduction is enabled. We also report
        // the true FPS value if there's no suitable matching refresh rate. In that case, Android
        // could try to select a lower refresh rate that avoids uneven pull-down (ex: 30 Hz for a 60
        // FPS stream on a display that maxes out at 50 Hz).
        if (mayReduceRefreshRate() || desiredRefreshRate < prefConfig.fps) {
            desiredFrameRate = prefConfig.fps.toFloat()
        } else {
            // Otherwise, we will pretend that our frame rate matches the refresh rate we picked in
            // prepareDisplayForRendering(). This will usually be the highest refresh rate that our
            // frame rate evenly divides into, which ensures the lowest possible display latency.
            desiredFrameRate = desiredRefreshRate
        }

        // Tell the OS about our frame rate to allow it to adapt the display refresh rate
        // appropriately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // We want to change frame rate even if it's not seamless, since
            // prepareDisplayForRendering() will not set the display mode on S+ if it only differs
            // by the refresh rate. It depends on us to trigger the frame rate switch here.
            holder.surface.setFrameRate(
                desiredFrameRate,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            holder.surface.setFrameRate(
                desiredFrameRate,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
            )
        }

        // Disable producer throttling on the underlying surface for reduced latency
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            holder.surface.setProducerThrottlingEnabled(false)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        check(surfaceCreated) { "Surface destroyed before creation!" }

        if (attemptedConnection) {
            // Let the decoder know immediately that the surface is gone
            decoderRenderer.prepareForStop()

            if (connected) {
                stopConnection()
            }
        }
    }

    override fun onSystemUiVisibilityChange(visibility: Int) {
        // Don't do anything if we're not connected
        if (!connected) {
            return
        }

        // This flag is set for all devices
        if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            hideSystemUi(HIDE_UI_AFTER_UI_CHANGE_MS)
        } else if ((visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
            hideSystemUi(HIDE_UI_AFTER_UI_CHANGE_MS)
        }
    }

    override fun onUsbPermissionPromptStarting() {
        // Disable PiP auto-enter while the USB permission prompt is on-screen. This prevents
        // us from entering PiP while the user is interacting with the OS permission dialog.
        suppressPipRefCount++
        updatePipAutoEnter()
    }

    override fun onUsbPermissionPromptCompleted() {
        suppressPipRefCount--
        updatePipAutoEnter()
    }

    override fun onKey(view: View, keyCode: Int, keyEvent: KeyEvent): Boolean {
        return when (keyEvent.action) {
            KeyEvent.ACTION_DOWN -> handleKeyDown(keyEvent)
            KeyEvent.ACTION_UP -> handleKeyUp(keyEvent)
            KeyEvent.ACTION_MULTIPLE -> handleKeyMultiple(keyEvent)
            else -> false
        }
    }
}
