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
import io.github.magalz.borealis.testing.LimeLogCapture
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.fail
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

    @Deprecated("The type is ignored since API 11.") override fun setType(type: Int) = Unit

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
 * What `LimeLog` wrote, line by line, and the thread that wrote it. [LimeLogCapture] keeps no
 * thread, and a recovery that waits is told by who waits and for whom.
 *
 * A line that says a thread waits to quiesce also keeps the bit of that thread in the flags it
 * prints (the role it plays in the recovery), which is known by the renderer function that it waits
 * in (the test thread plays the input thread when it submits, and the choreographer when it calls
 * `doFrame`), and for a thread in none of them by the name of the renderer thread, of the
 * choreographer thread and of an [InputCall].
 */
class ThreadedLog : Handler() {
    class Line(val thread: String, val message: String, val bit: Int? = null) {
        override fun toString() = "[$thread] $message"
    }

    private val written = CopyOnWriteArrayList<Line>()
    private val waits = LinkedBlockingQueue<Int>()

    val lines: List<Line>
        get() = written.toList()

    override fun publish(record: LogRecord) {
        val flags = quiesceFlags(record.message)
        val line = Line(Thread.currentThread().name, record.message, flags?.let { ownBit() })
        written += line
        flags?.let { waits.add(it) }
    }

    /**
     * The bit of the role that the current thread plays in a recovery, or null if it is unknown.
     */
    private fun ownBit(): Int? =
        Thread.currentThread().stackTrace.firstNotNullOfOrNull { ROLE_FUNCTIONS[it.methodName] }
            ?: OWN_BITS[Thread.currentThread().name]

    override fun flush() = Unit

    override fun close() = Unit

    /** Forgets every line and every wait. */
    fun clear() {
        written.clear()
        waits.clear()
    }

    /** Forgets [lines], which a test made up, and nothing else. */
    fun forget(lines: List<Line>) {
        written.removeAll(lines.toSet())
    }

    /** Forgets the waits seen so far: the next [awaitWaiting] sees only the ones that come now. */
    fun forgetWaits() = waits.clear()

    /**
     * Blocks until a thread logs that it waits to quiesce with exactly [flags] (the bits of the
     * threads that have come), and fails if none does within [WAIT_BOUND_SECONDS].
     */
    fun awaitWaiting(flags: Int) {
        while (true) {
            val seen =
                checkNotNull(waits.poll(WAIT_BOUND_SECONDS, TimeUnit.SECONDS)) {
                    "No thread waited to quiesce with $flags; log: ${written.takeLast(TAIL)}"
                }
            if (seen == flags) return
        }
    }

    /**
     * The lines of a thread that waits for a recovery whose bit is not among those it printed, so
     * that nobody counts it as arrived: such a thread waits for ever (BORE-121).
     */
    fun waitsWithoutOwnBit(): List<Line> = written.filter { line ->
        val bit = line.bit ?: return@filter false
        quiesceFlags(line.message)?.let { it and bit == 0 } ?: false
    }

    private fun quiesceFlags(message: String): Int? =
        message.takeIf { it.startsWith(QUIESCE_PREFIX) }?.substringAfterLast(": ")?.toIntOrNull()

    private companion object {
        const val QUIESCE_PREFIX = "Waiting to quiesce decoder threads: "
        const val WAIT_BOUND_SECONDS = 30L
        const val TAIL = 30
        val OWN_BITS =
            mapOf(
                INPUT_CALL_THREAD to INPUT_THREAD,
                RENDERER_THREAD_NAME to RENDERER_THREAD,
                CHOREOGRAPHER_THREAD_NAME to CHOREOGRAPHER,
            )

        /** The functions of the renderer that a thread is in when it comes to the recovery. */
        val ROLE_FUNCTIONS =
            mapOf(
                "fetchNextInputBuffer" to INPUT_THREAD,
                "queueNextInputBuffer" to INPUT_THREAD,
                "doFrame" to CHOREOGRAPHER,
            )
    }
}

/**
 * A call that runs on a thread of its own, the input thread of the renderer (the caller of
 * `submitDecodeUnit`), so that the test thread can play the others and a test that goes wrong ends
 * by itself.
 */
class InputCall<T>(block: () -> T) {
    /** What the call ended with, and whether that thread was interrupted then. */
    class Outcome<T>(val result: Result<T>, val interrupted: Boolean)

    private val outcome = AtomicReference<Outcome<T>>()
    private val input =
        thread(name = INPUT_CALL_THREAD) {
            val result = runCatching(block)
            outcome.set(Outcome(result, Thread.currentThread().isInterrupted))
        }

    fun interrupt() = input.interrupt()

    /** Whether the call has ended, so that a test can wait for it with another condition. */
    val isFinished: Boolean
        get() = outcome.get() != null

    /** Waits for the call to end; fails if it does not end within half a minute. */
    fun outcome(): Outcome<T> {
        input.join(JOIN_MS)
        check(!input.isAlive) { "The input thread is still blocked" }
        return outcome.get()
    }

    fun result(): T = outcome().result.getOrThrow()

    fun failure(): Throwable = checkNotNull(outcome().result.exceptionOrNull())

    private companion object {
        const val JOIN_MS = 30_000L
    }
}

/**
 * The Robolectric setup that every test of `MediaCodecDecoderRenderer` shares: the native bridge
 * and the video codec are replaced by shadows (the canonical [ShadowRecordingBridge] and
 * [ShadowRecordingVideoCodec]), the decoders the device lists are what a test installs, and the
 * real `MediaCodecHelper` is put in the state that a device with [DeviceBuild] and [GlFacts] gives
 * it, whatever an earlier test of the sandbox left in it.
 *
 * A watchdog bounds every test. The renderer waits for its threads to stop using the codec before
 * it recovers it, and a regression in that wait would hang the test worker for ever. After
 * [WATCHDOG_SECONDS] seconds the watchdog tells every renderer of the test to stop, which wakes the
 * threads that wait, and the test fails when it ends. JUnit's `Timeout` rule is not used: it runs
 * the test off the main thread of Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class, ShadowRecordingVideoCodec::class])
// Abstract so that Gradle does not run the base as a test of its own.
@Suppress("AbstractClassCanBeConcreteClass")
abstract class RendererTestBase : MediaCodecHelperTestBase() {
    @get:Rule val log = LimeLogCapture()

    /** The same lines with the thread that wrote each, and the waits of the recoveries. */
    protected val threadLog = ThreadedLog()

    /** What the crash listener of the renderers was told, in order. */
    protected val crashes = CopyOnWriteArrayList<Exception>()

    private val realHelper = HelperHandle(MediaCodecHelper::class.java)
    private lateinit var helperBefore: HelperHandle.State
    private val limeLog = Logger.getLogger("com.limelight.LimeLog")
    private val socBefore = Build.SOC_MANUFACTURER to Build.SOC_MODEL
    private val texture = SurfaceTexture(0)
    protected val surface = Surface(texture)
    protected val holder = FakeSurfaceHolder(surface)
    private val renderers = CopyOnWriteArrayList<MediaCodecDecoderRenderer>()
    private val started = CopyOnWriteArrayList<MediaCodecDecoderRenderer>()
    private var screen: ActivityController<Activity>? = null
    private val watchdog = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "renderer test watchdog").apply { isDaemon = true }
    }
    private var alarm: ScheduledFuture<*>? = null
    private val overran = AtomicBoolean()

    private var watchdogSeconds = WATCHDOG_SECONDS

    /** An activity, which the renderer asks for the display; made on first use. */
    protected val activity: Activity
        get() =
            (screen
                    ?: Robolectric.buildActivity(Activity::class.java).create().also {
                        screen = it
                    })
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
        limeLog.addHandler(threadLog)
        alarm = watchdog.schedule(::overrun, watchdogSeconds, TimeUnit.SECONDS)
    }

    /** Moves the watchdog to [seconds] from now, for a test that loops over many passes. */
    protected fun lengthenWatchdog(seconds: Long) {
        alarm?.cancel(false)
        watchdogSeconds = seconds
        alarm = watchdog.schedule(::overrun, seconds, TimeUnit.SECONDS)
    }

    /** What the watchdog does: it lets go every thread that waits for the renderers. */
    private fun overrun() {
        overran.set(true)
        ShadowRecordingVideoCodec.starveInput = false
        renderers.forEach { it.prepareForStop() }
    }

    @After
    fun releaseDevice() {
        alarm?.cancel(false)
        try {
            stopRenderers()
            screen?.destroy()
            surface.release()
            texture.release()
            realHelper.restore(helperBefore)
            deviceSoc(socBefore.first, socBefore.second)
            ShadowRecordingBridge.reset()
            ShadowRecordingVideoCodec.forgetEverything()
        } finally {
            limeLog.removeHandler(threadLog)
            watchdog.shutdownNow()
        }
        if (overran.get()) {
            fail(
                "The test ran for more than $watchdogSeconds s: a thread of the renderer was " +
                    "probably waiting for another, and the watchdog told the renderers to stop." +
                    raceEvidence()
            )
        }
        failOnLostArrival()
    }

    /**
     * Fails if a thread waited to quiesce without its own bit in the flags it printed: a recovery
     * that ended while it waited had cleared the bit and the thread did not count itself in again
     * for the next recovery, so that one could never see every thread (BORE-121). The rule runs
     * this after the test, once [stopRenderers] has stopped its renderers; the entry that loops
     * over many passes runs it after each pass's case, before [endPass] stops them.
     */
    protected fun failOnLostArrival() {
        val lines = threadLog.waitsWithoutOwnBit()
        if (lines.isNotEmpty()) {
            fail(
                "A thread waited to quiesce without its own bit in the flags, the race of " +
                    "BORE-121: $lines"
            )
        }
    }

    private fun stopRenderers() {
        started.forEach { renderer ->
            renderer.prepareForStop()
            renderer.stop()
        }
        started.clear()
        renderers.clear()
    }

    /**
     * Ends a pass of a test that makes many, so that the next one begins as a test does: the
     * renderers stopped, and the fake codecs, the bridge and the crashes forgotten.
     */
    protected fun endPass() {
        stopRenderers()
        ShadowRecordingVideoCodec.forgetEverything()
        ShadowRecordingBridge.reset()
        crashes.clear()
        threadLog.clear()
    }

    /** What to add to a failure when a thread waited for a recovery without its own bit. */
    protected fun raceEvidence(): String =
        threadLog.waitsWithoutOwnBit().let { lines ->
            if (lines.isEmpty()) {
                ""
            } else {
                "\nA thread waited to quiesce without its own bit in the flags, the race of " +
                    "BORE-121: $lines"
            }
        }

    /**
     * The threads that have come to wait for a recovery, as the bits that the renderer prints in
     * its "Waiting to quiesce" lines: [INPUT_THREAD], [RENDERER_THREAD] and [CHOREOGRAPHER]. The
     * lines repeat every second for a thread that waits, so the bits of all of them are joined and
     * a thread is never counted twice. A renderer without a choreographer sets its bit itself.
     */
    protected fun quiesced(): Int =
        log.lines()
            .filter { it.startsWith("INFO: Waiting to quiesce decoder threads: ") }
            .fold(0) { threads, line -> threads or line.substringAfterLast(": ").toInt() }

    /** Waits until all of [threads] wait for the others, so that the next arrival is the last. */
    protected fun awaitQuiesced(threads: Int) {
        await("the threads $threads to wait to quiesce") { quiesced() and threads == threads }
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

    protected inline fun <reified T : CodecEvent> events(): List<T> = journal.filterIsInstance<T>()

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

    /**
     * Starts [this] with a renderer thread that makes a pass only when
     * `ShadowRecordingVideoCodec.stepOutput` says so, and waits until that thread is parked at the
     * codec, before its first pass.
     */
    protected fun MediaCodecDecoderRenderer.beginStepped() {
        ShadowRecordingVideoCodec.manualOutput = true
        begin()
        ShadowRecordingVideoCodec.awaitParked()
    }

    /**
     * Runs [call] on the input thread, which meets a failure of the codec there and waits for the
     * renderer thread; lets the parked renderer thread make its pass, which comes last and does the
     * recovery; and waits until the input thread is released and the renderer thread is parked
     * again. Nothing waits for the clock, and no other thread can ask for a recovery meanwhile, so
     * every thread meets the one recovery of the round. Returns what the call returned.
     */
    protected fun <T> recoveredByRendererThread(call: () -> T): T {
        threadLog.forgetWaits()
        val input = InputCall(call)
        // The input thread is the only one that has come; the renderer has no choreographer.
        threadLog.awaitWaiting(INPUT_THREAD or CHOREOGRAPHER)
        ShadowRecordingVideoCodec.stepOutput()
        // Only a call that does not end is the race; what the call itself throws is its own.
        val outcome =
            try {
                input.outcome()
            } catch (e: IllegalStateException) {
                throw IllegalStateException(e.message + raceEvidence(), e)
            }
        val result = outcome.result.getOrThrow()
        ShadowRecordingVideoCodec.awaitParked()
        return result
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

/** The names of the threads of the renderer that a test can tell apart in the log. */
internal const val INPUT_CALL_THREAD = "input"
internal const val RENDERER_THREAD_NAME = "Video - Renderer (MediaCodec)"
internal const val CHOREOGRAPHER_THREAD_NAME = "Video - Choreographer"
private const val AWAIT_NANOS = 30_000_000_000L
private const val POLL_MS = 2L

/** The bits of the threads that touch the codec, which the renderer prints in its log. */
internal const val INPUT_THREAD = 1
internal const val RENDERER_THREAD = 2
internal const val CHOREOGRAPHER = 4

/**
 * How long a test may run, ten times the slowest test of the renderer: it takes six seconds, most
 * of them the start of the sandbox in the first test of a worker, and the next one under a second.
 */
private const val WATCHDOG_SECONDS = 60L
