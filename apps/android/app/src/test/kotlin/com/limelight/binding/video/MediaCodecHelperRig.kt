package com.limelight.binding.video

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.MediaCodecInfoBuilder
import org.robolectric.shadows.ShadowMediaCodecList
import org.robolectric.util.ReflectionHelpers

/** The `Build` facts that `MediaCodecHelper` reads while it loads and while it decides. */
data class DeviceBuild(
    val hardware: String = "qcom",
    val brand: String = "google",
    val device: String = "flame",
    val manufacturer: String = "Google",
    val sdk: Int = Build.VERSION_CODES.TIRAMISU,
    val performanceClass: Int = 0,
)

/** The OpenGL facts that `MediaCodecHelper.initialize` reads: the ES version and the renderer. */
data class GlFacts(val version: Int = GL_3_2, val renderer: String = "Adreno (TM) 640") {
    companion object {
        const val GL_2_0 = 0x20000
        const val GL_3_0 = 0x30000
        const val GL_3_2 = 0x30002
        const val UNDEFINED = ConfigurationInfo.GL_ES_VERSION_UNDEFINED
    }
}

/** Sets the `Build` fields that `MediaCodecHelper` reads and puts the original ones back. */
class BuildFacts : ExternalResource() {
    private var original = DeviceBuild()

    override fun before() {
        original =
            DeviceBuild(
                hardware = Build.HARDWARE,
                brand = Build.BRAND,
                device = Build.DEVICE,
                manufacturer = Build.MANUFACTURER,
                sdk = Build.VERSION.SDK_INT,
                performanceClass = Build.VERSION.MEDIA_PERFORMANCE_CLASS,
            )
    }

    override fun after() = set(original)

    fun set(build: DeviceBuild) {
        ReflectionHelpers.setStaticField(Build::class.java, "HARDWARE", build.hardware)
        ReflectionHelpers.setStaticField(Build::class.java, "BRAND", build.brand)
        ReflectionHelpers.setStaticField(Build::class.java, "DEVICE", build.device)
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", build.manufacturer)
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", build.sdk)
        ReflectionHelpers.setStaticField(
            Build.VERSION::class.java,
            "MEDIA_PERFORMANCE_CLASS",
            build.performanceClass,
        )
    }
}

/**
 * Loads `MediaCodecHelper` again, with a class loader of its own that owns only that class and
 * leaves the rest to the Robolectric sandbox. The copy runs its static initializer when it is
 * loaded, so the `Build` fields set before the load decide the class-load branches, and no test
 * leaves state behind for another.
 */
private class FreshClassLoader(private val original: Class<*>, sandbox: ClassLoader) :
    ClassLoader(sandbox) {
    private val bytes =
        checkNotNull(sandbox.getResourceAsStream(original.name.replace('.', '/') + ".class"))
            .use { it.readBytes() }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (name != original.name) return super.loadClass(name, resolve)
        synchronized(this) {
            return findLoadedClass(name)
                ?: defineClass(name, bytes, 0, bytes.size, original.protectionDomain)
        }
    }
}

/**
 * One copy of `MediaCodecHelper`, driven through its public static methods and its private static
 * fields by name. Everything goes through reflection, so the same test runs on the Java class and
 * on the Kotlin `object` that replaces it.
 */
class HelperHandle(private val type: Class<*>) {
    private fun method(name: String) =
        type.declaredMethods.single {
            it.name == name && Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers)
        }

    /** Calls a public static method and rethrows what it threw, not the reflection wrapper. */
    fun call(name: String, vararg args: Any?): Any? =
        try {
            method(name).invoke(null, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }

    fun field(name: String): Any? =
        type.getDeclaredField(name).apply { isAccessible = true }.get(null)

    fun strings(name: String): List<String> = (field(name) as List<*>).map { it as String }

    fun flag(name: String): Boolean = field(name) as Boolean

    fun setFlag(name: String, value: Boolean) {
        type.getDeclaredField(name).apply { isAccessible = true }.setBoolean(null, value)
    }

    /** What the private static state holds: every list and the flags. */
    class State(val lists: Map<String, List<String>>, val flags: Map<String, Boolean>)

    /** Copies the private static state, to put it back with [restore]. */
    fun snapshot() =
        State(
            LIST_FIELDS.associateWith { strings(it) },
            FLAG_FIELDS.associateWith { flag(it) },
        )

    fun restore(state: State) {
        state.lists.forEach { (name, entries) ->
            @Suppress("UNCHECKED_CAST") val list = field(name) as MutableList<String>
            list.clear()
            list.addAll(entries)
        }
        state.flags.forEach { (name, value) -> setFlag(name, value) }
    }

    /** What the lists gained while [block] ran, by field name, for the lists that gained any. */
    fun additionsDuring(block: () -> Unit): Map<String, List<String>> {
        val before = LIST_FIELDS.associateWith { strings(it) }
        block()
        return LIST_FIELDS.mapNotNull { name ->
                val old = before.getValue(name)
                val now = strings(name)
                check(now.take(old.size) == old) { "$name lost an entry" }
                now.drop(old.size).takeIf { it.isNotEmpty() }?.let { name to it }
            }
            .toMap()
    }

    fun initialize(context: Context, glRenderer: String) {
        call("initialize", context, glRenderer)
    }

    fun canDirectSubmit(name: String) = call("decoderCanDirectSubmit", name) as Boolean

    fun needsConstrainedHighProfile(name: String) =
        call("decoderNeedsConstrainedHighProfile", name) as Boolean

    fun needsBaselineSpsHack(name: String) = call("decoderNeedsBaselineSpsHack", name) as Boolean

    fun slicesPerFrame(name: String) = call("getDecoderOptimalSlicesPerFrame", name) as Byte

    fun refFrameInvalidationAvc(name: String, height: Int) =
        call("decoderSupportsRefFrameInvalidationAvc", name, height) as Boolean

    fun refFrameInvalidationHevc(info: MediaCodecInfo) =
        call("decoderSupportsRefFrameInvalidationHevc", info) as Boolean

    fun refFrameInvalidationAv1(info: MediaCodecInfo) =
        call("decoderSupportsRefFrameInvalidationAv1", info) as Boolean

    fun whitelistedForHevc(info: MediaCodecInfo) =
        call("decoderIsWhitelistedForHevc", info) as Boolean

    fun whitelistedForAv1(info: MediaCodecInfo) =
        call("isDecoderWhitelistedForAv1", info) as Boolean

    fun adaptivePlayback(info: MediaCodecInfo, mime: String) =
        call("decoderSupportsAdaptivePlayback", info, mime) as Boolean

    fun fusedIdrFrame(info: MediaCodecInfo, mime: String) =
        call("decoderSupportsFusedIdrFrame", info, mime) as Boolean

    fun lowLatencyOptions(format: MediaFormat, info: MediaCodecInfo, tryNumber: Int) =
        call("setDecoderLowLatencyOptions", format, info, tryNumber) as Boolean

    fun findFirstDecoder(mime: String) = call("findFirstDecoder", mime) as MediaCodecInfo?

    fun findProbableSafeDecoder(mime: String, requiredProfile: Int) =
        call("findProbableSafeDecoder", mime, requiredProfile) as MediaCodecInfo?

    val bypassesSoftwareBlock: Boolean
        get() = flag("SHOULD_BYPASS_SOFTWARE_BLOCK")

    companion object {
        /** Every private prefix list of the helper, by field name. */
        val LIST_FIELDS =
            listOf(
                "blacklistedDecoderPrefixes",
                "blacklistedAdaptivePlaybackPrefixes",
                "baselineProfileHackPrefixes",
                "directSubmitPrefixes",
                "constrainedHighProfilePrefixes",
                "whitelistedHevcDecoders",
                "refFrameInvalidationAvcPrefixes",
                "refFrameInvalidationHevcPrefixes",
                "useFourSlicesPrefixes",
                "qualcommDecoderPrefixes",
                "kirinDecoderPrefixes",
                "exynosDecoderPrefixes",
                "amlogicDecoderPrefixes",
                "knownVendorLowLatencyOptions",
            )

        /** Every private static flag of the helper, by field name. */
        val FLAG_FIELDS = listOf("isLowEndSnapdragon", "isAdreno620", "initialized")

        /** The message of the exception that a decision throws before `initialize` has run. */
        const val NOT_INITIALIZED = "MediaCodecHelper must be initialized before use"

        /** Loads a copy of the helper, running its static initializer now. */
        fun load(): HelperHandle {
            val original = Class.forName(HELPER_CLASS)
            val sandbox = checkNotNull(original.classLoader)
            return HelperHandle(
                Class.forName(HELPER_CLASS, true, FreshClassLoader(original, sandbox))
            )
        }

        private const val HELPER_CLASS = "com.limelight.binding.video.MediaCodecHelper"
    }
}

/** A table of rows: it collects every row that differs and reports them all at once. */
class Rows {
    private val failures = mutableListOf<String>()

    fun <T> check(label: String, expected: T, actual: () -> T) {
        try {
            val value = actual()
            if (value != expected) failures += "$label: expected <$expected> but was <$value>"
        } catch (e: Exception) {
            failures += "$label: expected <$expected> but threw <$e>"
        }
    }

    /** The row passes when [actual] throws [expected] with [message]. */
    fun <T : Throwable> checkThrows(
        label: String,
        expected: Class<T>,
        message: String?,
        actual: () -> Unit,
    ) {
        try {
            actual()
            failures += "$label: expected <${expected.simpleName}> but nothing was thrown"
        } catch (e: Exception) {
            if (e.javaClass != expected || e.message != message) {
                failures += "$label: expected <${expected.simpleName}: $message> but threw <$e>"
            }
        }
    }

    fun verify() {
        assertWithMessage("rows that differ").that(failures).isEmpty()
    }
}

/** What a test says about a decoder; [build] turns it into a `MediaCodecInfo` of the framework. */
class CodecBuilder(private val name: String) {
    var mimes: List<String> = listOf("video/avc")
    var encoder = false
    var softwareOnly = false
    var hardwareAccelerated = true
    var lowLatency = false
    var adaptivePlayback = false
    var profiles: List<Int>? = null
    var canonicalName: String? = null
    var brokenCapabilities = false

    /** The single mime type of the decoder. */
    var mime: String
        get() = mimes.single()
        set(value) {
            mimes = listOf(value)
        }

    private fun capabilities(mime: String): MediaCodecInfo.CodecCapabilities {
        val isHevc = mime == "video/hevc"
        // The highest level of the codec, so that the framework does not cap the sizes.
        val topLevel =
            if (isHevc) CodecProfileLevel.HEVCMainTierLevel62 else CodecProfileLevel.AVCLevel52
        val profileNumbers =
            profiles
                ?: listOf(
                    if (isHevc) CodecProfileLevel.HEVCProfileMain
                    else CodecProfileLevel.AVCProfileHigh
                )
        val levels = profileNumbers.map { number ->
            CodecProfileLevel().apply {
                profile = number
                level = topLevel
            }
        }
        val format =
            MediaFormat().apply {
                setString(MediaFormat.KEY_MIME, mime)
                setString("size-range", "64x64-4096x2304")
                setString("block-count-range", "1-100000")
                setString("blocks-per-second-range", "1-10000000")
                if (lowLatency) {
                    setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency, true)
                }
                if (adaptivePlayback) {
                    setFeatureEnabled(
                        MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback,
                        true,
                    )
                }
            }
        return MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
            .setMediaFormat(format)
            .setIsEncoder(encoder)
            .setProfileLevels(levels.toTypedArray())
            .setColorFormats(intArrayOf())
            .build()
    }

    fun build(): MediaCodecInfo {
        val info =
            MediaCodecInfoBuilder.newBuilder()
                .setName(name)
                .setIsEncoder(encoder)
                .setIsSoftwareOnly(softwareOnly)
                .setIsHardwareAccelerated(hardwareAccelerated)
                .setCapabilities(*mimes.map { capabilities(it) }.toTypedArray())
                .build()
        canonicalName?.let { ReflectionHelpers.setField(info, "mCanonicalName", it) }
        if (brokenCapabilities) {
            // A decoder that lists its types but throws when asked for their capabilities.
            val real =
                ReflectionHelpers.getField<Map<String, MediaCodecInfo.CodecCapabilities>>(
                    info,
                    "mCaps",
                )
            ReflectionHelpers.setField(
                info,
                "mCaps",
                object : HashMap<String, MediaCodecInfo.CodecCapabilities>(real) {
                    override fun get(key: String): MediaCodecInfo.CodecCapabilities? =
                        throw IllegalArgumentException("buggy decoder")
                },
            )
        }
        return info
    }
}

/** A decoder of the framework, from the little a test says about it. */
fun codec(name: String, configure: CodecBuilder.() -> Unit = {}): MediaCodecInfo =
    CodecBuilder(name).apply(configure).build()

/** What the device lists as its decoders, in this order, in place of any list before. */
fun installCodecs(vararg codecs: MediaCodecInfo) {
    ShadowMediaCodecList.reset()
    codecs.forEach(ShadowMediaCodecList::addCodec)
}

/**
 * The base of the `MediaCodecHelper` tests: a fresh copy of the helper per call of [helper], set up
 * for a device. [fresh] is the copy as the class loader leaves it, before `initialize`.
 */
open class MediaCodecHelperTestBase {
    @get:Rule val buildFacts = BuildFacts()

    protected val context: Context = ApplicationProvider.getApplicationContext()

    /** Loads the helper on [build], before `initialize`. */
    protected fun fresh(build: DeviceBuild = DeviceBuild()): HelperHandle {
        buildFacts.set(build)
        return HelperHandle.load()
    }

    /** The helper on [build], initialized as `Game` does for a device with [gl] and [fireTv]. */
    protected fun helper(
        build: DeviceBuild = DeviceBuild(),
        gl: GlFacts = GlFacts(),
        fireTv: Boolean = false,
    ): HelperHandle {
        val loaded = fresh(build)
        setGl(gl, fireTv)
        loaded.initialize(context, gl.renderer)
        return loaded
    }

    /** Tells the application context what the device reports about OpenGL and Fire TV. */
    protected fun setGl(gl: GlFacts, fireTv: Boolean) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(activityManager)
            .setDeviceConfigurationInfo(ConfigurationInfo().apply { reqGlEsVersion = gl.version })
        shadowOf(context.packageManager).setSystemFeature("amazon.hardware.fire_tv", fireTv)
    }
}
