package com.limelight.binding.video

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.media.MediaCodecInfo
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.discovery.LimeLogRecords
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers

/** A surface holder around a surface that a texture backs; only `getSurface` has an answer. */
class FakeSurfaceHolder(private val surface: Surface) : SurfaceHolder {
    override fun addCallback(callback: SurfaceHolder.Callback?) = Unit

    override fun removeCallback(callback: SurfaceHolder.Callback?) = Unit

    override fun isCreating() = false

    @Deprecated("The type is ignored since API 11.")
    override fun setType(type: Int) = Unit

    override fun setFixedSize(width: Int, height: Int) = Unit

    override fun setSizeFromLayout() = Unit

    override fun setFormat(format: Int) = Unit

    override fun setKeepScreenOn(screenOn: Boolean) = Unit

    override fun lockCanvas(): Canvas? = null

    override fun lockCanvas(dirty: Rect?): Canvas? = null

    override fun unlockCanvasAndPost(canvas: Canvas?) = Unit

    override fun getSurfaceFrame(): Rect = Rect()

    override fun getSurface(): Surface = surface
}

/**
 * The Robolectric setup that every test of `MediaCodecDecoderRenderer` shares: the native bridge
 * and the video codec are replaced by shadows (the canonical [ShadowRecordingBridge] and
 * [ShadowRecordingVideoCodec]), the decoders the device lists are what a test installs, and the
 * real `MediaCodecHelper` is put in the state that a device with [DeviceBuild] and [GlFacts] gives
 * it, whatever an earlier test of the sandbox left in it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class, ShadowRecordingVideoCodec::class])
// Abstract so that Gradle does not run the base as a test of its own.
@Suppress("AbstractClassCanBeConcreteClass")
abstract class RendererTestBase : MediaCodecHelperTestBase() {
    @get:Rule val log = LimeLogRecords()

    /** What the crash listener of the renderers was told, in order. */
    protected val crashes = CopyOnWriteArrayList<Exception>()

    private val realHelper = HelperHandle(MediaCodecHelper::class.java)
    private lateinit var helperBefore: HelperHandle.State
    private val socBefore = Build.SOC_MANUFACTURER to Build.SOC_MODEL
    private val texture = SurfaceTexture(0)
    protected val surface = Surface(texture)
    protected val holder = FakeSurfaceHolder(surface)
    private val renderers = CopyOnWriteArrayList<MediaCodecDecoderRenderer>()
    private val started = CopyOnWriteArrayList<MediaCodecDecoderRenderer>()
    private var screen: ActivityController<Activity>? = null

    /** An activity, which the renderer asks for the display; made on first use. */
    protected val activity: Activity
        get() =
            (screen ?: Robolectric.buildActivity(Activity::class.java).create().also { screen = it })
                .get()

    @Before
    fun freshDevice() {
        ShadowRecordingBridge.reset()
        ShadowRecordingVideoCodec.forgetEverything()
        helperBefore = realHelper.snapshot()
        // Robolectric runs the frame callbacks of a looper by itself, and moves the clock to do it,
        // unless they are paused: the tests call `doFrame` themselves.
        ShadowChoreographer.setPaused(true)
        device()
    }

    @After
    fun releaseDevice() {
        started.forEach { renderer ->
            renderer.prepareForStop()
            renderer.stop()
        }
        screen?.destroy()
        surface.release()
        texture.release()
        realHelper.restore(helperBefore)
        deviceSoc(socBefore.first, socBefore.second)
        ShadowRecordingBridge.reset()
        ShadowRecordingVideoCodec.forgetEverything()
    }

    /** Makes the device [build] with [gl]; the real helper is initialized as `Game` does it. */
    protected fun device(
        build: DeviceBuild = DeviceBuild(sdk = Build.VERSION_CODES.VANILLA_ICE_CREAM),
        gl: GlFacts = GlFacts(),
        fireTv: Boolean = false,
    ) {
        realHelper.restore(helper(build, gl, fireTv).snapshot())
    }

    /** Sets the SoC that a SDK 31 device reports; `BuildFacts` puts back what it replaced. */
    protected fun deviceSoc(manufacturer: String, model: String) {
        ReflectionHelpers.setStaticField(Build::class.java, "SOC_MANUFACTURER", manufacturer)
        ReflectionHelpers.setStaticField(Build::class.java, "SOC_MODEL", model)
    }

    /** Lists these decoders in this order, with a fake codec behind each. */
    protected fun install(vararg decoders: MediaCodecInfo) {
        installCodecs(*decoders)
        decoders.forEach { ShadowRecordingVideoCodec.register(it.name) }
    }

    /** The preferences of a 1080p60 stream, changed by [change]. */
    protected fun prefs(change: PreferenceConfiguration.() -> Unit = {}) =
        PreferenceConfiguration()
            .apply {
                width = 1920
                height = 1080
                fps = 60
                bitrate = 20_000
                videoFormat = PreferenceConfiguration.FormatOption.AUTO
                framePacing = PreferenceConfiguration.FRAME_PACING_MIN_LATENCY
            }
            .apply(change)

    /** A renderer for [prefs] whose crash listener collects into [crashes]. */
    protected fun renderer(
        prefs: PreferenceConfiguration = prefs(),
        crashCount: Int = 0,
        hdr: Boolean = false,
        glRenderer: String? = "Adreno (TM) 640",
    ): MediaCodecDecoderRenderer =
        MediaCodecDecoderRenderer(
                activity,
                prefs,
                CrashListener { crashes += it },
                crashCount,
                hdr,
                glRenderer,
            )
            .also { renderers += it }

    /** What happened to the fake codecs so far, in order. */
    protected val journal: List<CodecEvent>
        get() = ShadowRecordingVideoCodec.journal

    protected inline fun <reified T : CodecEvent> events(): List<T> =
        journal.filterIsInstance<T>()

    /** The formats that codecs were configured with, in order. */
    protected val configuredFormats: List<Map<String, Any>>
        get() = events<CodecEvent.Configured>().map { it.format }

    /** Waits until [condition] holds, which another thread of the renderer makes happen. */
    protected fun await(what: String, condition: () -> Boolean) {
        val limit = System.nanoTime() + AWAIT_NANOS
        while (!condition()) {
            check(System.nanoTime() < limit) { "Timed out waiting for $what; journal: $journal" }
            Thread.sleep(POLL_MS)
        }
    }

    /** Gives [this] its render target and sets it up for [format] at 1920x1080 and 60 FPS. */
    protected fun MediaCodecDecoderRenderer.prepare(
        format: Int,
        fps: Int = 60,
        width: Int = 1920,
        height: Int = 1080,
    ): Int {
        setRenderTarget(holder)
        return setup(format, width, height, fps)
    }

    /** The live thread with this name, or null. */
    protected fun threadNamed(name: String): Thread? =
        Thread.getAllStackTraces().keys.firstOrNull { it.name == name && it.isAlive }

    /** Moves the clock that `SystemClock.uptimeMillis()` reads forward. */
    protected fun advanceClock(millis: Long) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(millis))
    }

    /** Starts [this] and stops it again when the test ends. */
    protected fun MediaCodecDecoderRenderer.begin() {
        start()
        started += this
    }
}

private const val AWAIT_NANOS = 30_000_000_000L
private const val POLL_MS = 2L
