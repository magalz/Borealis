package com.limelight.binding.video

import android.app.Activity
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Range
import android.view.Choreographer
import android.view.SurfaceHolder
import com.limelight.BuildConfig
import com.limelight.LimeLog
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.host.video.FirstFrameSignal
import io.github.magalz.borealis.host.video.HdrRestartReuse
import io.github.magalz.borealis.host.video.HevcSpsMonitor
import io.github.magalz.borealis.stream.DecodeTimeCounts
import io.github.magalz.borealis.stream.DecodeTimeLog
import io.github.magalz.borealis.stream.DecodeTimeTracker
import io.github.magalz.borealis.stream.PerfSnapshotSink
import io.github.magalz.borealis.stream.PerfSnapshots
import io.github.magalz.borealis.stream.PerfWindow
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import org.jcodec.codecs.h264.H264Utils
import org.jcodec.codecs.h264.io.model.SeqParameterSet
import org.jcodec.codecs.h264.io.model.VUIParameters

// The markers of the import groups of the Java, which ktfmt sorts together:
// BOREALIS: first-frame signal for the stream startup guard (host.video)
// BOREALIS: keeps the first IDR across the HDR decoder restart (host.video)
// BOREALIS: HEVC SPS diagnostics (host.video)
// BOREALIS: stream.perf
// BOREALIS: logs what the decoder did per window (stream.perf)
// BOREALIS: pairs outputs with inputs by timestamp (stream.perf)
// BOREALIS: structured performance numbers (stream.perf)

private const val CR_MAX_TRIES = 10
private const val CR_RECOVERY_TYPE_NONE = 0
private const val CR_RECOVERY_TYPE_RESTART = 2
private const val CR_RECOVERY_TYPE_RESET = 3

// Each thread that touches the MediaCodec object or any associated buffers must have a flag
// here and must call doCodecRecoveryIfRequired() on a regular basis.
private const val CR_FLAG_INPUT_THREAD = 0x1
private const val CR_FLAG_RENDER_THREAD = 0x2
private const val CR_FLAG_CHOREOGRAPHER = 0x4
private const val CR_FLAG_ALL =
    CR_FLAG_INPUT_THREAD or CR_FLAG_RENDER_THREAD or CR_FLAG_CHOREOGRAPHER

private const val EXCEPTION_REPORT_DELAY_MS = 3000
private const val OUTPUT_BUFFER_QUEUE_LIMIT = 2

private const val MAX_AVC_DIMENSION = 4096
private const val HDR_STATIC_INFO_SIZE = 25
private const val INPUT_BUFFER_TIMEOUT_US = 10000L
private const val OUTPUT_BUFFER_TIMEOUT_US = 50000L
private const val QUIESCE_WAIT_MS = 1000L
private const val LONG_DEQUEUE_MS = 20
private const val DECODER_HUNG_MS = 5000
private const val STATS_WINDOW_MS = 1000
private const val MICROS_PER_MILLI = 1000
private const val HALF_MILLI_US = 500
private const val MAX_DECODE_TIME_US = 1000000L

// Within 80% of the next frame, in nanoseconds of a second.
private const val FRAME_DUE_NUMERATOR_NS = 800000000
private const val SHORT_START_SEQUENCE_LENGTH = 3
private const val LONG_START_SEQUENCE_LENGTH = 4
private const val PROFILE_BASELINE = 66
private const val PROFILE_HIGH = 100
private const val LOG2_MAX_MV_LENGTH = 16
private const val REPLAYED_SPS_SIZE = 128
private const val SPS_NAL_HEADER: Byte = 0x67
private const val EARLY_OUTPUT_SECONDS = 30

// What initializeDecoder answers when it cannot set the decoder up: no usable AVC decoder, no HEVC
// or AV1 decoder, a format that no decoder handles, and a decoder that no format configures.
private const val ERROR_AVC_UNAVAILABLE = -1
private const val ERROR_DECODER_UNAVAILABLE = -2
private const val ERROR_UNKNOWN_FORMAT = -3
private const val ERROR_CONFIGURATION_FAILED = -5

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
@Suppress("LargeClass", "TooManyFunctions")
class MediaCodecDecoderRenderer(
    private val activity: Activity,
    private val prefs: PreferenceConfiguration,
    private val crashListener: CrashListener,
    private val consecutiveCrashCount: Int,
    requestedHdr: Boolean,
    private val glRenderer: String?,
) : VideoDecoderRenderer(), Choreographer.FrameCallback {
    // BOREALIS: raised once, when the first output buffer is released for rendering
    @JvmField val firstFrameSignal = FirstFrameSignal()
    // BOREALIS: logs the stream profile, never affects decoding
    private val hevcSpsMonitor: HevcSpsMonitor
    // BOREALIS: set by setHdrMode, used once by the IDR CSD fetch
    private val hdrRestartReuse = HdrRestartReuse()

    private var avcDecoder: MediaCodecInfo? = null
    private var hevcDecoder: MediaCodecInfo? = null
    private var av1Decoder: MediaCodecInfo? = null

    private val vpsBuffers = ArrayList<ByteArray>()
    private val spsBuffers = ArrayList<ByteArray>()
    private val ppsBuffers = ArrayList<ByteArray>()
    private var submittedCsd = false
    private var currentHdrMetadata: ByteArray? = null

    private var nextInputBufferIndex = -1
    private var nextInputBuffer: ByteBuffer? = null

    private var videoDecoder: MediaCodec? = null
    private var rendererThread: Thread? = null
    private var adaptivePlayback = false
    private var directSubmit = false
    private var fusedIdrFrame = false
    private var constrainedHighProfile = false
    private var refFrameInvalidationAvc = false
    private var refFrameInvalidationHevc = false
    private var refFrameInvalidationAv1 = false
    private var optimalSlicesPerFrame: Byte = 0
    private var refFrameInvalidationActive = false
    private var initialWidth = 0
    private var initialHeight = 0
    private var videoFormat = 0
    private var renderTarget: SurfaceHolder? = null
    @Volatile private var stopping = false
    private var reportedCrash = false

    // BOREALIS: the in-game overlay asks for structured numbers at runtime, whatever the preference
    // says (stream.perf)
    @Volatile private var perfEnabled = false
    @Volatile private var perfSink: PerfSnapshotSink? = null

    private val codecRecoveryType = AtomicInteger(CR_RECOVERY_TYPE_NONE)
    private val codecRecoveryMonitor = Any()

    private var codecRecoveryThreadQuiescedFlags = 0
    private var codecRecoveryAttempts = 0

    private var inputFormat: MediaFormat? = null
    private var outputFormat: MediaFormat? = null
    private var configuredFormat: MediaFormat? = null

    private var needsBaselineSpsHack = false
    private var savedSps: SeqParameterSet? = null

    private var initialException: RendererException? = null
    private var initialExceptionTimestamp = 0L

    private val activeWindowVideoStats = VideoStats()
    private val lastWindowVideoStats = VideoStats()
    private val globalVideoStats = VideoStats()

    private var lastTimestampUs = 0L
    private var lastFrameNumber = 0
    private var baseTimestampUs = 0L
    private var refreshRate = 0

    private val outputBufferQueue = LinkedBlockingQueue<Int>()
    // BOREALIS: input timestamps of the frames in the decoder, guarded by its own monitor
    // (stream.perf)
    private val decodeTimeTracker = DecodeTimeTracker()
    private var lastRenderedFrameTimeNanos = 0L
    private var choreographerHandlerThread: HandlerThread? = null
    private var choreographerHandler: Handler? = null

    private var numSpsIn = 0
    private var numPpsIn = 0
    private var numVpsIn = 0
    private var numFramesIn = 0
    private var numFramesOut = 0

    init {
        val avc = findAvcDecoder()
        avcDecoder = avc
        if (avc != null) {
            LimeLog.info("Selected AVC decoder: " + avc.name)
        } else {
            LimeLog.warning("No AVC decoder found")
        }

        val hevc = findHevcDecoder(prefs, requestedHdr)
        hevcDecoder = hevc
        if (hevc != null) {
            LimeLog.info("Selected HEVC decoder: " + hevc.name)
        } else {
            LimeLog.info("No HEVC decoder found")
        }
        // BOREALIS: HEVC SPS diagnostics
        hevcSpsMonitor = HevcSpsMonitor.forDecoder(hevc, requestedHdr, BuildConfig.DEBUG)

        val av1 = findAv1Decoder(prefs)
        av1Decoder = av1
        if (av1 != null) {
            LimeLog.info("Selected AV1 decoder: " + av1.name)
        } else {
            LimeLog.info("No AV1 decoder found")
        }

        // Set attributes that are queried in getCapabilities(). This must be done here
        // because getCapabilities() may be called before setup() in current versions of the common
        // library. The limitation of this is that we don't know whether we're using HEVC or AVC.
        var avcOptimalSlicesPerFrame = 0
        var hevcOptimalSlicesPerFrame = 0
        if (avc != null) {
            directSubmit = MediaCodecHelper.decoderCanDirectSubmit(avc.name)
            refFrameInvalidationAvc =
                MediaCodecHelper.decoderSupportsRefFrameInvalidationAvc(avc.name, prefs.height)
            avcOptimalSlicesPerFrame =
                MediaCodecHelper.getDecoderOptimalSlicesPerFrame(avc.name).toInt()

            if (directSubmit) {
                LimeLog.info("Decoder " + avc.name + " will use direct submit")
            }
            if (refFrameInvalidationAvc) {
                LimeLog.info(
                    "Decoder " + avc.name + " will use reference frame invalidation for AVC"
                )
            }
            LimeLog.info(
                "Decoder " + avc.name + " wants " + avcOptimalSlicesPerFrame + " slices per frame"
            )
        }

        if (hevc != null) {
            refFrameInvalidationHevc =
                MediaCodecHelper.decoderSupportsRefFrameInvalidationHevc(hevc)
            hevcOptimalSlicesPerFrame =
                MediaCodecHelper.getDecoderOptimalSlicesPerFrame(hevc.name).toInt()

            if (refFrameInvalidationHevc) {
                LimeLog.info(
                    "Decoder " + hevc.name + " will use reference frame invalidation for HEVC"
                )
            }

            LimeLog.info(
                "Decoder " + hevc.name + " wants " + hevcOptimalSlicesPerFrame + " slices per frame"
            )
        }

        if (av1 != null) {
            refFrameInvalidationAv1 = MediaCodecHelper.decoderSupportsRefFrameInvalidationAv1(av1)

            if (refFrameInvalidationAv1) {
                LimeLog.info(
                    "Decoder " + av1.name + " will use reference frame invalidation for AV1"
                )
            }
        }

        // Use the larger of the two slices per frame preferences
        optimalSlicesPerFrame = maxOf(avcOptimalSlicesPerFrame, hevcOptimalSlicesPerFrame).toByte()
        LimeLog.info("Requesting $optimalSlicesPerFrame slices per frame")

        if (consecutiveCrashCount % 2 == 1) {
            refFrameInvalidationAvc = false
            refFrameInvalidationHevc = false
            LimeLog.warning("Disabling RFI due to previous crash")
        }
    }

    // The inherited Java used these without a check, so a call before the field was set failed
    // with a NullPointerException at that point. Keep the exception.
    private fun requireDecoder(): MediaCodec = videoDecoder.orNpe("The video decoder")

    private fun requireInputBuffer(): ByteBuffer = nextInputBuffer.orNpe("The input buffer")

    private fun findAvcDecoder(): MediaCodecInfo? =
        MediaCodecHelper.findProbableSafeDecoder(
            "video/avc",
            MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
        ) ?: MediaCodecHelper.findFirstDecoder("video/avc")

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("ReturnCount")
    private fun decoderCanMeetPerformancePoint(
        caps: MediaCodecInfo.VideoCapabilities,
        prefs: PreferenceConfiguration,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val targetPerfPoint =
                MediaCodecInfo.VideoCapabilities.PerformancePoint(
                    prefs.width,
                    prefs.height,
                    prefs.fps,
                )
            val perfPoints = caps.supportedPerformancePoints
            if (perfPoints != null) {
                for (perfPoint in perfPoints) {
                    // If we find a performance point that covers our target, we're good to go
                    if (perfPoint.covers(targetPerfPoint)) {
                        return true
                    }
                }

                // We had performance point data but none met the specified streaming settings
                return false
            }

            // Fall-through to try the Android M API if there's no performance point data
        }

        try {
            // We'll ask the decoder what it can do for us at this resolution and see if our
            // requested frame rate falls below or inside the range of achievable frame rates.
            val fpsRange: Range<Double>? =
                caps.getAchievableFrameRatesFor(prefs.width, prefs.height)
            if (fpsRange != null) {
                return prefs.fps <= fpsRange.upper
            }

            // Fall-through to try the Android L API if there's no performance point data
        } catch (ignored: IllegalArgumentException) {
            // Video size not supported at any frame rate
            return false
        }

        // As a last resort, we will use areSizeAndRateSupported() which is explicitly NOT a
        // performance metric, but it can work at least for the purpose of determining if
        // the codec is going to die when given a stream with the specified settings.
        return caps.areSizeAndRateSupported(prefs.width, prefs.height, prefs.fps.toDouble())
    }

    private fun decoderCanMeetPerformancePointWithHevcAndNotAvc(
        hevcDecoderInfo: MediaCodecInfo,
        avcDecoderInfo: MediaCodecInfo,
        prefs: PreferenceConfiguration,
    ): Boolean {
        val avcCaps =
            avcDecoderInfo
                .getCapabilitiesForType("video/avc")
                .videoCapabilities
                .orNpe("The AVC video capabilities")
        val hevcCaps =
            hevcDecoderInfo
                .getCapabilitiesForType("video/hevc")
                .videoCapabilities
                .orNpe("The HEVC video capabilities")

        return !decoderCanMeetPerformancePoint(avcCaps, prefs) &&
            decoderCanMeetPerformancePoint(hevcCaps, prefs)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    private fun findHevcDecoder(
        prefs: PreferenceConfiguration,
        requestedHdr: Boolean,
    ): MediaCodecInfo? {
        // Don't return anything if H.264 is forced
        if (prefs.videoFormat == PreferenceConfiguration.FormatOption.FORCE_H264) {
            return null
        }

        // We don't try the first HEVC decoder. We'd rather fall back to hardware accelerated AVC
        // instead
        //
        // We need HEVC Main profile, so we could pass that constant to findProbableSafeDecoder,
        // however some decoders (at least Qualcomm's Snapdragon 805) don't properly report support
        // for even required levels of HEVC.
        val hevcDecoderInfo = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1)
        if (hevcDecoderInfo != null) {
            if (!MediaCodecHelper.decoderIsWhitelistedForHevc(hevcDecoderInfo)) {
                LimeLog.info(
                    "Found HEVC decoder, but it's not whitelisted - " + hevcDecoderInfo.name
                )

                val avc = avcDecoder
                if (prefs.videoFormat == PreferenceConfiguration.FormatOption.FORCE_HEVC) {
                    // Force HEVC enabled if the user asked for it
                    LimeLog.info("Forcing HEVC enabled despite non-whitelisted decoder")
                } else if (requestedHdr) {
                    // HDR implies HEVC forced on, since HEVCMain10HDR10 is required for HDR.
                    LimeLog.info("Forcing HEVC enabled for HDR streaming")
                } else if (prefs.width > MAX_AVC_DIMENSION || prefs.height > MAX_AVC_DIMENSION) {
                    // > 4K streaming also requires HEVC, so force it on there too.
                    LimeLog.info("Forcing HEVC enabled for over 4K streaming")
                } else if (
                    avc != null &&
                        decoderCanMeetPerformancePointWithHevcAndNotAvc(hevcDecoderInfo, avc, prefs)
                ) {
                    // Use HEVC if the H.264 decoder is unable to meet the performance point
                    LimeLog.info("Using non-whitelisted HEVC decoder to meet performance point")
                } else {
                    return null
                }
            }
        }

        return hevcDecoderInfo
    }

    private fun findAv1Decoder(prefs: PreferenceConfiguration): MediaCodecInfo? {
        // For now, don't use AV1 unless explicitly requested
        if (prefs.videoFormat != PreferenceConfiguration.FormatOption.FORCE_AV1) {
            return null
        }

        val decoderInfo = MediaCodecHelper.findProbableSafeDecoder("video/av01", -1)
        if (decoderInfo != null) {
            if (!MediaCodecHelper.isDecoderWhitelistedForAv1(decoderInfo)) {
                LimeLog.info("Found AV1 decoder, but it's not whitelisted - " + decoderInfo.name)

                // Force HEVC enabled if the user asked for it
                if (prefs.videoFormat == PreferenceConfiguration.FormatOption.FORCE_AV1) {
                    LimeLog.info("Forcing AV1 enabled despite non-whitelisted decoder")
                } else {
                    return null
                }
            }
        }

        return decoderInfo
    }

    fun setRenderTarget(renderTarget: SurfaceHolder?) {
        this.renderTarget = renderTarget
    }

    fun isHevcSupported(): Boolean = hevcDecoder != null

    fun isAvcSupported(): Boolean = avcDecoder != null

    fun isHevcMain10Hdr10Supported(): Boolean {
        val decoder = hevcDecoder ?: return false

        for (profileLevel in decoder.getCapabilitiesForType("video/hevc").profileLevels) {
            if (profileLevel.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10) {
                LimeLog.info("HEVC decoder " + decoder.name + " supports HEVC Main10 HDR10")
                return true
            }
        }

        return false
    }

    fun isAv1Supported(): Boolean = av1Decoder != null

    fun isAv1Main10Supported(): Boolean {
        val decoder = av1Decoder ?: return false

        for (profileLevel in decoder.getCapabilitiesForType("video/av01").profileLevels) {
            if (profileLevel.profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10) {
                LimeLog.info("AV1 decoder " + decoder.name + " supports AV1 Main 10 HDR10")
                return true
            }
        }

        return false
    }

    // Game and the tests call this as a function, as they did in Java.
    fun getPreferredColorSpace(): Int {
        // Default to Rec 709 which is probably better supported on modern devices.
        return MoonBridge.COLORSPACE_REC_709
    }

    fun getPreferredColorRange(): Int {
        return if (prefs.fullRange) {
            MoonBridge.COLOR_RANGE_FULL
        } else {
            MoonBridge.COLOR_RANGE_LIMITED
        }
    }

    // BOREALIS: turns the per-second performance window on or off at runtime (stream.perf)
    fun setPerfEnabled(enabled: Boolean) {
        perfEnabled = enabled
    }

    // BOREALIS: receives a PerfSnapshot about once a second while the window is on (stream.perf)
    fun setPerfSink(sink: PerfSnapshotSink?) {
        perfSink = sink
    }

    // BOREALIS: the name of the decoder of the active video format, for Session info (stream.perf)
    fun getDecoderName(): String? {
        var decoder: MediaCodecInfo? = null
        if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H264) != 0) {
            decoder = avcDecoder
        } else if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H265) != 0) {
            decoder = hevcDecoder
        } else if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_AV1) != 0) {
            decoder = av1Decoder
        }
        return decoder?.name
    }

    // BOREALIS: builds the snapshot of the last two windows, the same numbers the text overlay
    // shows (stream.perf)
    private fun pushPerfSnapshot() {
        val sink = perfSink ?: return
        val lastTwo = VideoStats()
        lastTwo.add(lastWindowVideoStats)
        lastTwo.add(activeWindowVideoStats)
        val elapsedMs = SystemClock.uptimeMillis() - lastTwo.measurementStartTimestamp
        sink.onPerf(
            PerfSnapshots.from(
                PerfWindow(
                    lastTwo.totalFrames,
                    lastTwo.totalFramesReceived,
                    lastTwo.totalFramesRendered,
                    lastTwo.framesLost,
                    lastTwo.decoderTimeUs, // BOREALIS: stream.perf
                    lastTwo.framesWithHostProcessingLatency,
                    lastTwo.minHostProcessingLatency.code,
                    lastTwo.maxHostProcessingLatency.code,
                    lastTwo.totalHostProcessingLatency,
                    lastTwo.totalBytes,
                    elapsedMs,
                ),
                MoonBridge.getEstimatedRttInfo(),
            )
        )
    }

    fun getActiveVideoFormat(): Int {
        return videoFormat
    }

    private fun createBaseMediaFormat(mimeType: String): MediaFormat {
        val format = MediaFormat.createVideoFormat(mimeType, initialWidth, initialHeight)

        format.setInteger(MediaFormat.KEY_FRAME_RATE, refreshRate)

        // Populate keys for adaptive playback
        if (adaptivePlayback) {
            format.setInteger(MediaFormat.KEY_MAX_WIDTH, initialWidth)
            format.setInteger(MediaFormat.KEY_MAX_HEIGHT, initialHeight)
        }

        format.setInteger(
            MediaFormat.KEY_COLOR_RANGE,
            if (getPreferredColorRange() == MoonBridge.COLOR_RANGE_FULL) {
                MediaFormat.COLOR_RANGE_FULL
            } else {
                MediaFormat.COLOR_RANGE_LIMITED
            },
        )

        // If the stream is HDR-capable, the decoder will detect transitions in color standards
        // rather than us hardcoding them into the MediaFormat.
        if ((getActiveVideoFormat() and MoonBridge.VIDEO_FORMAT_MASK_10BIT) == 0) {
            // Set color format keys when not in HDR mode, since we know they won't change
            format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            when (getPreferredColorSpace()) {
                MoonBridge.COLORSPACE_REC_709 ->
                    format.setInteger(
                        MediaFormat.KEY_COLOR_STANDARD,
                        MediaFormat.COLOR_STANDARD_BT709,
                    )
            }
        }

        return format
    }

    private fun configureAndStartDecoder(format: MediaFormat) {
        // Set HDR metadata if present
        val hdrMetadataBytes = currentHdrMetadata
        if (hdrMetadataBytes != null) {
            val hdrStaticInfo =
                ByteBuffer.allocate(HDR_STATIC_INFO_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val hdrMetadata = ByteBuffer.wrap(hdrMetadataBytes).order(ByteOrder.LITTLE_ENDIAN)

            // Create a HDMI Dynamic Range and Mastering InfoFrame as defined by CTA-861.3
            hdrStaticInfo.put(0.toByte()) // Metadata type
            hdrStaticInfo.putShort(hdrMetadata.getShort()) // RX
            hdrStaticInfo.putShort(hdrMetadata.getShort()) // RY
            hdrStaticInfo.putShort(hdrMetadata.getShort()) // GX
            hdrStaticInfo.putShort(hdrMetadata.getShort()) // GY
            hdrStaticInfo.putShort(hdrMetadata.getShort()) // BX
            hdrStaticInfo.putShort(hdrMetadata.getShort()) // BY
            hdrStaticInfo.putShort(hdrMetadata.getShort()) // White X
            hdrStaticInfo.putShort(hdrMetadata.getShort()) // White Y
            hdrStaticInfo.putShort(hdrMetadata.getShort()) // Max mastering luminance
            hdrStaticInfo.putShort(hdrMetadata.getShort()) // Min mastering luminance
            hdrStaticInfo.putShort(hdrMetadata.getShort()) // Max content luminance
            hdrStaticInfo.putShort(hdrMetadata.getShort()) // Max frame average luminance

            hdrStaticInfo.rewind()
            format.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, hdrStaticInfo)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            format.removeKey(MediaFormat.KEY_HDR_STATIC_INFO)
        }

        LimeLog.info("Configuring with format: $format")

        requireDecoder().configure(format, renderTarget.orNpe("The render target").surface, null, 0)

        configuredFormat = format

        // After reconfiguration, we must resubmit CSD buffers
        submittedCsd = false
        vpsBuffers.clear()
        spsBuffers.clear()
        ppsBuffers.clear()

        // This will contain the actual accepted input format attributes
        inputFormat = requireDecoder().inputFormat
        LimeLog.info("Input format: $inputFormat")

        requireDecoder().setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)

        // Start the decoder
        requireDecoder().start()
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("PrintStackTrace", "ThrowsCount", "TooGenericExceptionThrown")
    private fun tryConfigureDecoder(
        selectedDecoderInfo: MediaCodecInfo,
        format: MediaFormat,
        throwOnCodecError: Boolean,
    ): Boolean {
        var configured = false
        try {
            videoDecoder = MediaCodec.createByCodecName(selectedDecoderInfo.name)
            configureAndStartDecoder(format)
            LimeLog.info(
                "Using codec " +
                    selectedDecoderInfo.name +
                    " for hardware decoding " +
                    format.getString(MediaFormat.KEY_MIME)
            )
            configured = true
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            if (throwOnCodecError) {
                throw e
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            if (throwOnCodecError) {
                throw e
            }
        } catch (e: IOException) {
            e.printStackTrace()
            if (throwOnCodecError) {
                throw RuntimeException(e)
            }
        } finally {
            val decoder = videoDecoder
            if (!configured && decoder != null) {
                decoder.release()
                videoDecoder = null
            }
        }
        return configured
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun initializeDecoder(throwOnCodecError: Boolean): Int {
        val mimeType: String
        val selectedDecoderInfo: MediaCodecInfo

        if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H264) != 0) {
            mimeType = "video/avc"
            val avc = avcDecoder

            if (avc == null) {
                LimeLog.severe("No available AVC decoder!")
                return ERROR_AVC_UNAVAILABLE
            }
            selectedDecoderInfo = avc

            if (initialWidth > MAX_AVC_DIMENSION || initialHeight > MAX_AVC_DIMENSION) {
                LimeLog.severe("> 4K streaming only supported on HEVC")
                return ERROR_AVC_UNAVAILABLE
            }

            // These fixups only apply to H264 decoders
            needsBaselineSpsHack =
                MediaCodecHelper.decoderNeedsBaselineSpsHack(selectedDecoderInfo.name)
            constrainedHighProfile =
                MediaCodecHelper.decoderNeedsConstrainedHighProfile(selectedDecoderInfo.name)
            if (needsBaselineSpsHack) {
                LimeLog.info("Decoder " + selectedDecoderInfo.name + " needs baseline SPS hack")
            }
            if (constrainedHighProfile) {
                LimeLog.info(
                    "Decoder " + selectedDecoderInfo.name + " needs constrained high profile"
                )
            }

            refFrameInvalidationActive = refFrameInvalidationAvc
        } else if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H265) != 0) {
            mimeType = "video/hevc"
            val hevc = hevcDecoder

            if (hevc == null) {
                LimeLog.severe("No available HEVC decoder!")
                return ERROR_DECODER_UNAVAILABLE
            }
            selectedDecoderInfo = hevc

            refFrameInvalidationActive = refFrameInvalidationHevc
        } else if ((videoFormat and MoonBridge.VIDEO_FORMAT_MASK_AV1) != 0) {
            mimeType = "video/av01"
            val av1 = av1Decoder

            if (av1 == null) {
                LimeLog.severe("No available AV1 decoder!")
                return ERROR_DECODER_UNAVAILABLE
            }
            selectedDecoderInfo = av1

            refFrameInvalidationActive = refFrameInvalidationAv1
        } else {
            // Unknown format
            LimeLog.severe("Unknown format")
            return ERROR_UNKNOWN_FORMAT
        }

        adaptivePlayback =
            MediaCodecHelper.decoderSupportsAdaptivePlayback(selectedDecoderInfo, mimeType)
        fusedIdrFrame = MediaCodecHelper.decoderSupportsFusedIdrFrame(selectedDecoderInfo, mimeType)

        var tryNumber = 0
        while (true) {
            LimeLog.info("Decoder configuration try: $tryNumber")

            val mediaFormat = createBaseMediaFormat(mimeType)

            // This will try low latency options until we find one that works (or we give up).
            val newFormat =
                MediaCodecHelper.setDecoderLowLatencyOptions(
                    mediaFormat,
                    selectedDecoderInfo,
                    tryNumber,
                )

            // Throw the underlying codec exception on the last attempt if the caller requested it
            if (
                tryConfigureDecoder(
                    selectedDecoderInfo,
                    mediaFormat,
                    !newFormat && throwOnCodecError,
                )
            ) {
                // Success!
                break
            }

            if (!newFormat) {
                // We couldn't even configure a decoder without any low latency options
                return ERROR_CONFIGURATION_FAILED
            }
            tryNumber++
        }

        return 0
    }

    override fun setup(format: Int, width: Int, height: Int, redrawRate: Int): Int {
        initialWidth = width
        initialHeight = height
        videoFormat = format
        refreshRate = redrawRate

        return initializeDecoder(false)
    }

    // All threads that interact with the MediaCodec instance must call this function regularly!
    // Object.wait and Object.notifyAll are not members of Kotlin's Any, so the monitor is cast to
    // java.lang.Object, as the Java used it.
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress(
        "CyclomaticComplexMethod",
        "LongMethod",
        "PLATFORM_CLASS_MAPPED_TO_KOTLIN",
        "PrintStackTrace",
    )
    private fun doCodecRecoveryIfRequired(quiescenceFlag: Int): Boolean {
        // NB: We cannot check 'stopping' here because we could end up bailing in a partially
        // quiesced state that will cause the quiesced threads to never wake up.
        if (codecRecoveryType.get() == CR_RECOVERY_TYPE_NONE) {
            // Common case
            return false
        }

        // We need some sort of recovery, so quiesce all threads before starting that
        synchronized(codecRecoveryMonitor) {
            if (choreographerHandlerThread == null) {
                // If we have no choreographer thread, we can just mark that as quiesced right now.
                codecRecoveryThreadQuiescedFlags =
                    codecRecoveryThreadQuiescedFlags or CR_FLAG_CHOREOGRAPHER
            }

            codecRecoveryThreadQuiescedFlags = codecRecoveryThreadQuiescedFlags or quiescenceFlag

            // This is the final thread to quiesce, so let's perform the codec recovery now.
            if (codecRecoveryThreadQuiescedFlags == CR_FLAG_ALL) {
                // Input and output buffers are invalidated by stop() and reset().
                nextInputBuffer = null
                nextInputBufferIndex = -1
                outputBufferQueue.clear()
                // BOREALIS: stream.perf
                synchronized(decodeTimeTracker) { decodeTimeTracker.clear() }

                // Count the attempt, unless prepareForStop() cancelled the recovery meanwhile
                if (codecRecoveryType.get() != CR_RECOVERY_TYPE_NONE) {
                    codecRecoveryAttempts++
                    LimeLog.info("Codec recovery attempt: $codecRecoveryAttempts")
                }

                // For "recoverable" exceptions, we can just stop, reconfigure, and restart.
                if (codecRecoveryType.get() == CR_RECOVERY_TYPE_RESTART) {
                    LimeLog.warning("Trying to restart decoder after CodecException")
                    try {
                        requireDecoder().stop()
                        configureAndStartDecoder(configuredFormat.orNpe("The configured format"))
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()

                        // Our Surface is probably invalid, so just stop
                        stopping = true
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()

                        // Something went wrong during the restart, let's use a bigger hammer
                        // and try a reset instead.
                        codecRecoveryType.set(CR_RECOVERY_TYPE_RESET)
                    }
                }

                // For "non-recoverable" exceptions on L+, we can call reset() to recover
                // without having to recreate the entire decoder again.
                if (codecRecoveryType.get() == CR_RECOVERY_TYPE_RESET) {
                    LimeLog.warning("Trying to reset decoder after CodecException")
                    try {
                        requireDecoder().reset()
                        configureAndStartDecoder(configuredFormat.orNpe("The configured format"))
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()

                        // Our Surface is probably invalid, so just stop
                        stopping = true
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()

                        // Something went wrong during the reset, we'll have to resort to
                        // releasing and recreating the decoder now.
                    }
                }

                // If we _still_ haven't managed to recover, go for the nuclear option and just
                // throw away the old decoder and reinitialize a new one from scratch.
                if (codecRecoveryType.get() == CR_RECOVERY_TYPE_RESET) {
                    LimeLog.warning("Trying to recreate decoder after CodecException")
                    requireDecoder().release()

                    try {
                        initializeDecoder(true)
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()

                        // Our Surface is probably invalid, so just stop
                        stopping = true
                        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
                    } catch (e: IllegalStateException) {
                        // If we failed to recover after all of these attempts, just crash
                        if (!reportedCrash) {
                            reportedCrash = true
                            crashListener.notifyCrash(e)
                        }
                        throw RendererException(this, e)
                    }
                }

                // Wake all quiesced threads and allow them to begin work again
                codecRecoveryThreadQuiescedFlags = 0
                (codecRecoveryMonitor as Object).notifyAll()
            } else {
                // If we haven't quiesced all threads yet, wait to be signalled after recovery.
                // The final thread to be quiesced will handle the codec recovery.
                while (codecRecoveryType.get() != CR_RECOVERY_TYPE_NONE) {
                    try {
                        LimeLog.info(
                            "Waiting to quiesce decoder threads: $codecRecoveryThreadQuiescedFlags"
                        )
                        (codecRecoveryMonitor as Object).wait(QUIESCE_WAIT_MS)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()

                        // InterruptedException clears the thread's interrupt status. Since we can't
                        // handle that here, we will re-interrupt the thread to set the interrupt
                        // status back to true.
                        Thread.currentThread().interrupt()

                        break
                    }
                }
            }
        }

        return true
    }

    // Returns true if the exception is transient
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    private fun handleDecoderException(e: IllegalStateException): Boolean {
        // Eat decoder exceptions if we're in the process of stopping
        if (stopping) {
            return false
        }

        if (e is MediaCodec.CodecException) {
            if (e.isTransient) {
                // We'll let transient exceptions go
                LimeLog.warning(e.diagnosticInfo)
                return true
            }

            LimeLog.severe(e.diagnosticInfo)

            // We can attempt a recovery or reset at this stage to try to start decoding again
            if (codecRecoveryAttempts < CR_MAX_TRIES) {
                // If the exception is non-recoverable or we already require a reset, perform a
                // reset. If we have no prior unrecoverable failure, we will try a restart instead.
                if (e.isRecoverable) {
                    if (
                        codecRecoveryType.compareAndSet(
                            CR_RECOVERY_TYPE_NONE,
                            CR_RECOVERY_TYPE_RESTART,
                        )
                    ) {
                        LimeLog.info("Decoder requires restart for recoverable CodecException")
                        e.printStackTrace()
                    } else if (
                        codecRecoveryType.get() != CR_RECOVERY_TYPE_RESET &&
                            codecRecoveryType.get() != CR_RECOVERY_TYPE_RESTART
                    ) {
                        error("Unexpected codec recovery type: ${codecRecoveryType.get()}")
                    }
                } else if (!e.isRecoverable) {
                    if (
                        codecRecoveryType.compareAndSet(
                            CR_RECOVERY_TYPE_NONE,
                            CR_RECOVERY_TYPE_RESET,
                        )
                    ) {
                        LimeLog.info("Decoder requires reset for non-recoverable CodecException")
                        e.printStackTrace()
                    } else if (
                        codecRecoveryType.compareAndSet(
                            CR_RECOVERY_TYPE_RESTART,
                            CR_RECOVERY_TYPE_RESET,
                        )
                    ) {
                        LimeLog.info(
                            "Decoder restart promoted to reset for non-recoverable CodecException"
                        )
                        e.printStackTrace()
                    } else if (codecRecoveryType.get() != CR_RECOVERY_TYPE_RESET) {
                        error("Unexpected codec recovery type: ${codecRecoveryType.get()}")
                    }
                }

                // The recovery will take place when all threads reach doCodecRecoveryIfRequired().
                return false
            }
        } else {
            // IllegalStateException was primarily used prior to the introduction of CodecException.
            // Recovery from this requires a full decoder reset.
            //
            // NB: CodecException is an IllegalStateException, so we must check for it first.
            if (codecRecoveryAttempts < CR_MAX_TRIES) {
                if (
                    codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_NONE, CR_RECOVERY_TYPE_RESET)
                ) {
                    LimeLog.info("Decoder requires reset for IllegalStateException")
                    e.printStackTrace()
                } else if (
                    codecRecoveryType.compareAndSet(
                        CR_RECOVERY_TYPE_RESTART,
                        CR_RECOVERY_TYPE_RESET,
                    )
                ) {
                    LimeLog.info("Decoder restart promoted to reset for IllegalStateException")
                    e.printStackTrace()
                } else if (codecRecoveryType.get() != CR_RECOVERY_TYPE_RESET) {
                    error("Unexpected codec recovery type: ${codecRecoveryType.get()}")
                }

                return false
            }
        }

        // Only throw if we're not in the middle of codec recovery
        if (codecRecoveryType.get() == CR_RECOVERY_TYPE_NONE) {
            //
            // There seems to be a race condition with decoder/surface teardown causing some
            // decoders to to throw IllegalStateExceptions even before 'stopping' is set.
            // To workaround this while allowing real exceptions to propagate, we will eat the
            // first exception. If we are still receiving exceptions 3 seconds later, we will
            // throw the original exception again.
            //
            val firstException = initialException
            if (firstException != null) {
                // This isn't the first time we've had an exception processing video
                if (
                    SystemClock.uptimeMillis() - initialExceptionTimestamp >=
                        EXCEPTION_REPORT_DELAY_MS
                ) {
                    // It's been over 3 seconds and we're still getting exceptions. Throw the
                    // original now.
                    if (!reportedCrash) {
                        reportedCrash = true
                        crashListener.notifyCrash(firstException)
                    }
                    throw firstException
                }
            } else {
                // This is the first exception we've hit
                initialException = RendererException(this, e)
                initialExceptionTimestamp = SystemClock.uptimeMillis()
            }
        }

        // Not transient
        return false
    }

    // WindowManager.getDefaultDisplay is deprecated since API 30; the Java used it.
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("DEPRECATION", "PrintStackTrace")
    override fun doFrame(frameTimeNanos: Long) {
        // Do nothing if we're stopping
        if (stopping) {
            return
        }

        val frameTime = frameTimeNanos - activity.windowManager.defaultDisplay.appVsyncOffsetNanos

        // Don't render unless a new frame is due. This prevents microstutter when streaming
        // at a frame rate that doesn't match the display (such as 60 FPS on 120 Hz).
        val actualFrameTimeDeltaNs = frameTime - lastRenderedFrameTimeNanos
        val expectedFrameTimeDeltaNs = (FRAME_DUE_NUMERATOR_NS / refreshRate).toLong()
        if (actualFrameTimeDeltaNs >= expectedFrameTimeDeltaNs) {
            // Render up to one frame when in frame pacing mode.
            //
            // NB: Since the queue limit is 2, we won't starve the decoder of output buffers
            // by holding onto them for too long. This also ensures we will have that 1 extra
            // frame of buffer to smooth over network/rendering jitter.
            val nextOutputBuffer = outputBufferQueue.poll()
            if (nextOutputBuffer != null) {
                try {
                    requireDecoder().releaseOutputBuffer(nextOutputBuffer, frameTime)

                    lastRenderedFrameTimeNanos = frameTime
                    activeWindowVideoStats.totalFramesRendered++
                    // BOREALIS: first-frame signal
                    firstFrameSignal.frameRendered()
                } catch (ignored: IllegalStateException) {
                    try {
                        // Try to avoid leaking the output buffer by releasing it without rendering
                        requireDecoder().releaseOutputBuffer(nextOutputBuffer, false)
                    } catch (e: IllegalStateException) {
                        // This will leak nextOutputBuffer, but there's really nothing else we can
                        // do
                        e.printStackTrace()
                        handleDecoderException(e)
                    }
                }
            }
        }

        // Attempt codec recovery even if we have nothing to render right now. Recovery can still
        // be required even if the codec died before giving any output.
        doCodecRecoveryIfRequired(CR_FLAG_CHOREOGRAPHER)

        // Request another callback for next frame
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun startChoreographerThread() {
        if (prefs.framePacing != PreferenceConfiguration.FRAME_PACING_BALANCED) {
            // Not using Choreographer in this pacing mode
            return
        }

        // We use a separate thread to avoid any main thread delays from delaying rendering
        val thread =
            HandlerThread(
                "Video - Choreographer",
                Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_MORE_FAVORABLE,
            )
        choreographerHandlerThread = thread
        thread.start()

        // Start the frame callbacks
        val handler = Handler(thread.looper)
        choreographerHandler = handler
        handler.post { Choreographer.getInstance().postFrameCallback(this) }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("LongMethod", "NestedBlockDepth")
    private fun startRendererThread() {
        val thread =
            object : Thread() {
                override fun run() {
                    val info = MediaCodec.BufferInfo()
                    while (!stopping) {
                        try {
                            // Try to output a frame
                            val outIndex =
                                requireDecoder().dequeueOutputBuffer(info, OUTPUT_BUFFER_TIMEOUT_US)
                            if (outIndex >= 0) {
                                var presentationTimeUs = info.presentationTimeUs
                                // BOREALIS: stream.perf
                                var matchedDecodeUs = matchOutput(presentationTimeUs)
                                var lastIndex = outIndex

                                numFramesOut++

                                // Render the latest frame now if frame pacing isn't in balanced
                                // mode
                                if (
                                    prefs.framePacing !=
                                        PreferenceConfiguration.FRAME_PACING_BALANCED
                                ) {
                                    // Get the last output buffer in the queue
                                    while (true) {
                                        val nextIndex =
                                            requireDecoder().dequeueOutputBuffer(info, 0)
                                        if (nextIndex < 0) {
                                            break
                                        }
                                        requireDecoder().releaseOutputBuffer(lastIndex, false)

                                        numFramesOut++

                                        lastIndex = nextIndex
                                        presentationTimeUs = info.presentationTimeUs
                                        // BOREALIS: stream.perf
                                        matchedDecodeUs = matchOutput(presentationTimeUs)
                                    }

                                    if (
                                        prefs.framePacing ==
                                            PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS ||
                                            prefs.framePacing ==
                                                PreferenceConfiguration.FRAME_PACING_CAP_FPS
                                    ) {
                                        // In max smoothness or cap FPS mode, we want to never drop
                                        // frames. Use a PTS that will cause this frame to never be
                                        // dropped
                                        requireDecoder().releaseOutputBuffer(lastIndex, 0L)
                                    } else {
                                        // Use a PTS that will cause this frame to be dropped if
                                        // another comes in within the same V-sync period
                                        requireDecoder()
                                            .releaseOutputBuffer(lastIndex, System.nanoTime())
                                    }

                                    activeWindowVideoStats.totalFramesRendered++
                                    // BOREALIS: first-frame signal
                                    firstFrameSignal.frameRendered()
                                } else {
                                    // For balanced frame pacing case, the Choreographer callback
                                    // will handle rendering. We just put all frames into the output
                                    // buffer queue and let it handle things.

                                    // Discard the oldest buffer if we've exceeded our limit.
                                    //
                                    // NB: We have to do this on the producer side because the
                                    // consumer may not run for a while (if there is a huge mismatch
                                    // between stream FPS and display refresh rate).
                                    if (outputBufferQueue.size == OUTPUT_BUFFER_QUEUE_LIMIT) {
                                        try {
                                            requireDecoder()
                                                .releaseOutputBuffer(
                                                    outputBufferQueue.take(),
                                                    false,
                                                )
                                        } catch (e: InterruptedException) {
                                            // We're shutting down, so we can just drop this buffer
                                            // on the floor and it will be reclaimed when the codec
                                            // is released.
                                            return
                                        }
                                    }

                                    // Add this buffer
                                    outputBufferQueue.add(lastIndex)
                                }

                                // Add delta time to the totals (excluding probable outliers)
                                // BOREALIS: stream.perf: from the queue time when the output
                                // matched an input, else from the timestamp as before
                                val decodeUs =
                                    if (matchedDecodeUs != DecodeTimeTracker.NO_MATCH) {
                                        matchedDecodeUs
                                    } else {
                                        (SystemClock.uptimeMillis() -
                                            (presentationTimeUs / MICROS_PER_MILLI)) *
                                            MICROS_PER_MILLI
                                    }
                                if (decodeUs >= 0 && decodeUs < MAX_DECODE_TIME_US) {
                                    val delta = (decodeUs + HALF_MILLI_US) / MICROS_PER_MILLI
                                    activeWindowVideoStats.decoderTimeUs += decodeUs
                                    activeWindowVideoStats.decoderTimeMs += delta
                                    activeWindowVideoStats.totalTimeMs += delta
                                }
                            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                // INFO_TRY_AGAIN_LATER and any other negative index need nothing.
                                LimeLog.info("Output format changed")
                                outputFormat = requireDecoder().outputFormat
                                LimeLog.info("New output format: $outputFormat")
                            }
                        } catch (e: IllegalStateException) {
                            handleDecoderException(e)
                        } finally {
                            doCodecRecoveryIfRequired(CR_FLAG_RENDER_THREAD)
                        }
                    }
                }
            }
        rendererThread = thread
        thread.name = "Video - Renderer (MediaCodec)"
        thread.priority = Thread.NORM_PRIORITY + 2
        thread.start()
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("ReturnCount")
    private fun fetchNextInputBuffer(): Boolean {
        if (nextInputBuffer != null) {
            // We already have an input buffer
            return true
        }

        val startTime = SystemClock.uptimeMillis()

        val codecRecovered: Boolean
        try {
            // If we don't have an input buffer index yet, fetch one now
            while (nextInputBufferIndex < 0 && !stopping) {
                nextInputBufferIndex = requireDecoder().dequeueInputBuffer(INPUT_BUFFER_TIMEOUT_US)
            }

            // Get the backing ByteBuffer for the input buffer index
            if (nextInputBufferIndex >= 0) {
                // Using the new getInputBuffer() API on Lollipop allows
                // the framework to do some performance optimizations for us
                nextInputBuffer = requireDecoder().getInputBuffer(nextInputBufferIndex)
                if (nextInputBuffer == null) {
                    // According to the Android docs, getInputBuffer() can return null "if the
                    // index is not a dequeued input buffer". I don't think this ever should
                    // happen but if it does, let's try to get a new input buffer next time.
                    nextInputBufferIndex = -1
                }
            }
        } catch (e: IllegalStateException) {
            handleDecoderException(e)
            return false
        } finally {
            codecRecovered = doCodecRecoveryIfRequired(CR_FLAG_INPUT_THREAD)
        }

        // If codec recovery is required, always return false to ensure the caller will request
        // an IDR frame to complete the codec recovery.
        if (codecRecovered) {
            return false
        }

        val deltaMs = (SystemClock.uptimeMillis() - startTime).toInt()

        if (deltaMs >= LONG_DEQUEUE_MS) {
            LimeLog.warning("Dequeue input buffer ran long: $deltaMs ms")
        }

        if (nextInputBuffer == null) {
            // We've been hung for 5 seconds and no other exception was reported,
            // so generate a decoder hung exception
            if (deltaMs >= DECODER_HUNG_MS && initialException == null) {
                val decoderHungException = DecoderHungException(deltaMs)
                if (!reportedCrash) {
                    reportedCrash = true
                    crashListener.notifyCrash(decoderHungException)
                }
                throw RendererException(this, decoderHungException)
            }

            return false
        }

        return true
    }

    override fun start() {
        startRendererThread()
        startChoreographerThread()
    }

    // !!! May be called even if setup()/start() fails !!!
    // Object.notifyAll is not a member of Kotlin's Any, so the monitor is cast to java.lang.Object,
    // as the Java used it.
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    fun prepareForStop() {
        // Let the decoding code know to ignore codec exceptions now
        stopping = true

        // Halt the rendering thread
        rendererThread?.interrupt()

        // Stop any active codec recovery operations
        synchronized(codecRecoveryMonitor) {
            codecRecoveryType.set(CR_RECOVERY_TYPE_NONE)
            (codecRecoveryMonitor as Object).notifyAll()
        }

        // Post a quit message to the Choreographer looper (if we have one)
        choreographerHandler?.post {
            // Don't allow any further messages to be queued
            choreographerHandlerThread.orNpe("The choreographer thread").quit()

            // Deregister the frame callback (if registered)
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("PrintStackTrace")
    override fun stop() {
        // May be called already, but we'll call it now to be safe
        prepareForStop()

        // Wait for the Choreographer looper to shut down (if we have one)
        choreographerHandlerThread?.let { thread ->
            try {
                thread.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()

                // InterruptedException clears the thread's interrupt status. Since we can't
                // handle that here, we will re-interrupt the thread to set the interrupt
                // status back to true.
                Thread.currentThread().interrupt()
            }
        }

        // Wait for the renderer thread to shut down
        try {
            rendererThread.orNpe("The renderer thread").join()
        } catch (e: InterruptedException) {
            e.printStackTrace()

            // InterruptedException clears the thread's interrupt status. Since we can't
            // handle that here, we will re-interrupt the thread to set the interrupt
            // status back to true.
            Thread.currentThread().interrupt()
        }
    }

    override fun cleanup() {
        requireDecoder().release()
    }

    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        if (currentHdrMetadata != null && (!enabled || hdrMetadata == null)) {
            currentHdrMetadata = null
        } else if (
            enabled && hdrMetadata != null && !currentHdrMetadata.contentEquals(hdrMetadata)
        ) {
            currentHdrMetadata = hdrMetadata
        } else {
            // Nothing to do
            return
        }

        // If we reach this point, we need to restart the MediaCodec instance to
        // pick up the HDR metadata change. This will happen on the next input
        // or output buffer.

        // HACK: Reset codec recovery attempt counter, since this is an expected "recovery"
        codecRecoveryAttempts = 0

        // BOREALIS: before the promotion, so a recovery cannot run ahead of the mark
        hdrRestartReuse.requested()

        // Promote None to Restart and leave Restart and Reset alone
        codecRecoveryType.compareAndSet(CR_RECOVERY_TYPE_NONE, CR_RECOVERY_TYPE_RESTART)
    }

    // BOREALIS: the tracker is not thread-safe, and the input and the output run on different
    // threads (stream.perf)
    private fun drainDecodeWindow(): DecodeTimeCounts =
        synchronized(decodeTimeTracker) { decodeTimeTracker.drainWindow() }

    // BOREALIS: stream.perf
    private fun matchOutput(presentationTimeUs: Long): Long =
        synchronized(decodeTimeTracker) {
            decodeTimeTracker.onOutput(presentationTimeUs, System.nanoTime())
        }

    private fun queueNextInputBuffer(timestampUs: Long, codecFlags: Int): Boolean {
        val codecRecovered: Boolean

        try {
            // BOREALIS: stream.perf
            if ((codecFlags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                // BOREALIS: before the queue, so a fast output finds it (stream.perf)
                synchronized(decodeTimeTracker) {
                    decodeTimeTracker.onInput(timestampUs, System.nanoTime())
                }
            }
            requireDecoder()
                .queueInputBuffer(
                    nextInputBufferIndex,
                    0,
                    requireInputBuffer().position(),
                    timestampUs,
                    codecFlags,
                )

            // We need a new buffer now
            nextInputBufferIndex = -1
            nextInputBuffer = null
        } catch (e: IllegalStateException) {
            if (handleDecoderException(e)) {
                // We encountered a transient error. In this case, just hold onto the buffer
                // (to avoid leaking it), clear it, and keep it for the next frame. We'll return
                // false to trigger an IDR frame to recover.
                requireInputBuffer().clear()
            } else {
                // We encountered a non-transient error. In this case, we will simply leak the
                // buffer because we cannot be sure we will ever succeed in queuing it.
                nextInputBufferIndex = -1
                nextInputBuffer = null
            }
            return false
        } finally {
            codecRecovered = doCodecRecoveryIfRequired(CR_FLAG_INPUT_THREAD)
        }

        // If codec recovery is required, always return false to ensure the caller will request
        // an IDR frame to complete the codec recovery.
        if (codecRecovered) {
            return false
        }

        // Fetch a new input buffer now while we have some time between frames
        // to have it ready immediately when the next frame arrives.
        //
        // We must propagate the return value here in order to properly handle
        // codec recovery happening in fetchNextInputBuffer(). If we don't, we'll
        // never get an IDR frame to complete the recovery process.
        return fetchNextInputBuffer()
    }

    private fun doProfileSpecificSpsPatching(sps: SeqParameterSet) {
        // Some devices benefit from setting constraint flags 4 & 5 to make this Constrained
        // High Profile which allows the decoder to assume there will be no B-frames and
        // reduce delay and buffering accordingly. Some devices (Marvell, Exynos 4) don't
        // like it so we only set them on devices that are confirmed to benefit from it.
        if (sps.profileIdc == PROFILE_HIGH && constrainedHighProfile) {
            LimeLog.info("Setting constraint set flags for constrained high profile")
            sps.constraintSet4Flag = true
            sps.constraintSet5Flag = true
        }
    }

    // MediaCodec.BUFFER_FLAG_SYNC_FRAME is deprecated since API 21 in favour of
    // BUFFER_FLAG_KEY_FRAME, which has the same value; the Java used the old name.
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress(
        "CyclomaticComplexMethod",
        "DEPRECATION",
        "LongMethod",
        "NestedBlockDepth",
        "ReturnCount",
    )
    override fun submitDecodeUnit(
        decodeUnitData: ByteArray?,
        decodeUnitLength: Int,
        decodeUnitType: Int,
        frameNumber: Int,
        frameType: Int,
        frameHostProcessingLatency: Char,
        receiveTimeUs: Long,
        enqueueTimeUs: Long,
    ): Int {
        if (stopping) {
            // Don't bother if we're stopping
            return MoonBridge.DR_OK
        }

        if (lastFrameNumber == 0) {
            activeWindowVideoStats.measurementStartTimestamp = SystemClock.uptimeMillis()
        } else if (frameNumber != lastFrameNumber && frameNumber != lastFrameNumber + 1) {
            // We can receive the same "frame" multiple times if it's an IDR frame.
            // In that case, each frame start NALU is submitted independently.
            activeWindowVideoStats.framesLost += frameNumber - lastFrameNumber - 1
            activeWindowVideoStats.totalFrames += frameNumber - lastFrameNumber - 1
            activeWindowVideoStats.frameLossEvents++
        }

        // Reset CSD data for each IDR frame
        if (lastFrameNumber != frameNumber && frameType == MoonBridge.FRAME_TYPE_IDR) {
            vpsBuffers.clear()
            spsBuffers.clear()
            ppsBuffers.clear()
        }

        lastFrameNumber = frameNumber
        // BOREALIS: stream.perf
        activeWindowVideoStats.totalBytes += decodeUnitLength

        // Flip stats windows roughly every second
        if (
            SystemClock.uptimeMillis() >=
                activeWindowVideoStats.measurementStartTimestamp + STATS_WINDOW_MS
        ) {
            if (perfEnabled) {
                // BOREALIS: stream.perf
                pushPerfSnapshot()
            }
            // BOREALIS: stream.perf
            DecodeTimeLog.window(getDecoderName(), drainDecodeWindow())

            globalVideoStats.add(activeWindowVideoStats)
            lastWindowVideoStats.copy(activeWindowVideoStats)
            activeWindowVideoStats.clear()
            activeWindowVideoStats.measurementStartTimestamp = SystemClock.uptimeMillis()
        }

        var csdSubmittedForThisFrame = false

        // IDR frames require special handling for CSD buffer submission
        if (frameType == MoonBridge.FRAME_TYPE_IDR) {
            // H264 SPS
            if (
                decodeUnitType == MoonBridge.BUFFER_TYPE_SPS &&
                    (videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H264) != 0
            ) {
                numSpsIn++

                val data = decodeUnitData.orNpe("The decode unit data")
                val spsBuf = ByteBuffer.wrap(data)
                val startSeqLen =
                    if (data[2].toInt() == 0x01) {
                        SHORT_START_SEQUENCE_LENGTH
                    } else {
                        LONG_START_SEQUENCE_LENGTH
                    }

                // Skip to the start of the NALU data
                spsBuf.position(startSeqLen + 1)

                // The H264Utils.readSPS function safely handles
                // Annex B NALUs (including NALUs with escape sequences)
                val sps = H264Utils.readSPS(spsBuf)

                // TI OMAP4 requires a reference frame count of 1 to decode successfully. Exynos 4
                // also requires this fixup.
                //
                // I'm doing this fixup for all devices because I haven't seen any devices that
                // this causes issues for. At worst, it seems to do nothing and at best it fixes
                // issues with video lag, hangs, and crashes.
                //
                // It does break reference frame invalidation, so we will not do that for decoders
                // where we've enabled reference frame invalidation.
                if (!refFrameInvalidationActive) {
                    LimeLog.info("Patching num_ref_frames in SPS")
                    sps.numRefFrames = 1
                }

                // The SPS that comes in the current H264 bytestream doesn't set
                // bitstream_restriction_flag or max_dec_frame_buffering which increases decoding
                // latency on Tegra.

                // If the encoder didn't include VUI parameters in the SPS, add them now
                if (sps.vuiParams == null) {
                    LimeLog.info("Adding VUI parameters")
                    sps.vuiParams = VUIParameters()
                }

                // GFE 2.5.11 started sending bitstream restrictions
                if (sps.vuiParams.bitstreamRestriction == null) {
                    LimeLog.info("Adding bitstream restrictions")
                    val restriction = VUIParameters.BitstreamRestriction()
                    sps.vuiParams.bitstreamRestriction = restriction
                    restriction.motionVectorsOverPicBoundariesFlag = true
                    restriction.maxBytesPerPicDenom = 2
                    restriction.maxBitsPerMbDenom = 1
                    restriction.log2MaxMvLengthHorizontal = LOG2_MAX_MV_LENGTH
                    restriction.log2MaxMvLengthVertical = LOG2_MAX_MV_LENGTH
                    restriction.numReorderFrames = 0
                } else {
                    LimeLog.info("Patching bitstream restrictions")
                }

                // Some devices throw errors if maxDecFrameBuffering < numRefFrames
                sps.vuiParams.bitstreamRestriction.maxDecFrameBuffering = sps.numRefFrames

                // log2_max_mv_length_horizontal and log2_max_mv_length_vertical are set to more
                // conservative values by GFE 2.5.11. We'll let those values stand.

                // If we need to hack this SPS to say we're baseline, do so now
                if (needsBaselineSpsHack) {
                    LimeLog.info("Hacking SPS to baseline")
                    sps.profileIdc = PROFILE_BASELINE
                    savedSps = sps
                }

                // Patch the SPS constraint flags
                doProfileSpecificSpsPatching(sps)

                // The H264Utils.writeSPS function safely handles
                // Annex B NALUs (including NALUs with escape sequences)
                val escapedNalu = H264Utils.writeSPS(sps, decodeUnitLength)

                // Construct the patched SPS
                val naluBuffer = ByteArray(startSeqLen + 1 + escapedNalu.limit())
                System.arraycopy(data, 0, naluBuffer, 0, startSeqLen + 1)
                escapedNalu.get(naluBuffer, startSeqLen + 1, escapedNalu.limit())

                // Batch this to submit together with other CSD per AOSP docs
                spsBuffers.add(naluBuffer)
                return MoonBridge.DR_OK
            } else if (decodeUnitType == MoonBridge.BUFFER_TYPE_VPS) {
                numVpsIn++

                // Batch this to submit together with other CSD per AOSP docs
                val naluBuffer = ByteArray(decodeUnitLength)
                System.arraycopy(
                    decodeUnitData.orNpe("The decode unit data"),
                    0,
                    naluBuffer,
                    0,
                    decodeUnitLength,
                )
                vpsBuffers.add(naluBuffer)
                return MoonBridge.DR_OK
            } else if (decodeUnitType == MoonBridge.BUFFER_TYPE_SPS) {
                // Only the HEVC SPS hits this path (H.264 is handled above)
                numSpsIn++

                // Batch this to submit together with other CSD per AOSP docs
                val naluBuffer = ByteArray(decodeUnitLength)
                System.arraycopy(
                    decodeUnitData.orNpe("The decode unit data"),
                    0,
                    naluBuffer,
                    0,
                    decodeUnitLength,
                )
                // BOREALIS: HEVC SPS diagnostics
                hevcSpsMonitor.onSps(naluBuffer, 0, naluBuffer.size)
                spsBuffers.add(naluBuffer)
                return MoonBridge.DR_OK
            } else if (decodeUnitType == MoonBridge.BUFFER_TYPE_PPS) {
                numPpsIn++

                // Batch this to submit together with other CSD per AOSP docs
                val naluBuffer = ByteArray(decodeUnitLength)
                System.arraycopy(
                    decodeUnitData.orNpe("The decode unit data"),
                    0,
                    naluBuffer,
                    0,
                    decodeUnitLength,
                )
                ppsBuffers.add(naluBuffer)
                return MoonBridge.DR_OK
            } else if (
                (videoFormat and
                    (MoonBridge.VIDEO_FORMAT_MASK_H264 or MoonBridge.VIDEO_FORMAT_MASK_H265)) != 0
            ) {
                // If this is the first CSD blob or we aren't supporting fused IDR frames, we will
                // submit the CSD blob in a separate input buffer for each IDR frame.
                if (!submittedCsd || !fusedIdrFrame) {
                    // BOREALIS: an HDR restart clears the CSD in configureAndStartDecoder; restore
                    // it and fetch once more to keep this IDR
                    hdrRestartReuse.snapshot(vpsBuffers, spsBuffers, ppsBuffers)
                    if (
                        !fetchNextInputBuffer() &&
                            !(hdrRestartReuse.restoreForRetry(
                                vpsBuffers,
                                spsBuffers,
                                ppsBuffers,
                            ) && fetchNextInputBuffer())
                    ) {
                        return MoonBridge.DR_NEED_IDR
                    }

                    // Submit all CSD when we receive the first non-CSD blob in an IDR frame
                    for (vpsBuffer in vpsBuffers) {
                        requireInputBuffer().put(vpsBuffer)
                    }
                    for (spsBuffer in spsBuffers) {
                        requireInputBuffer().put(spsBuffer)
                    }
                    for (ppsBuffer in ppsBuffers) {
                        requireInputBuffer().put(ppsBuffer)
                    }

                    if (!queueNextInputBuffer(0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)) {
                        return MoonBridge.DR_NEED_IDR
                    }

                    // Remember that we already submitted CSD for this frame, so we don't do it
                    // again in the fused IDR case below.
                    csdSubmittedForThisFrame = true

                    // Remember that we submitted CSD globally for this MediaCodec instance
                    submittedCsd = true

                    if (needsBaselineSpsHack) {
                        needsBaselineSpsHack = false

                        if (!replaySps()) {
                            return MoonBridge.DR_NEED_IDR
                        }

                        LimeLog.info("SPS replay complete")
                    }
                }
            }
        }

        if (frameHostProcessingLatency.code != 0) {
            if (activeWindowVideoStats.minHostProcessingLatency.code != 0) {
                activeWindowVideoStats.minHostProcessingLatency =
                    minOf(
                            activeWindowVideoStats.minHostProcessingLatency.code,
                            frameHostProcessingLatency.code,
                        )
                        .toChar()
            } else {
                activeWindowVideoStats.minHostProcessingLatency = frameHostProcessingLatency
            }
            activeWindowVideoStats.framesWithHostProcessingLatency += 1
        }
        activeWindowVideoStats.maxHostProcessingLatency =
            maxOf(
                    activeWindowVideoStats.maxHostProcessingLatency.code,
                    frameHostProcessingLatency.code,
                )
                .toChar()
        activeWindowVideoStats.totalHostProcessingLatency += frameHostProcessingLatency.code

        activeWindowVideoStats.totalFramesReceived++
        activeWindowVideoStats.totalFrames++

        // Count time from first packet received to enqueue time as receive time
        // We will count DU queue time as part of decoding, because it is directly
        // caused by a slow decoder.
        activeWindowVideoStats.totalTimeMs += (enqueueTimeUs - receiveTimeUs) / MICROS_PER_MILLI

        if (!fetchNextInputBuffer()) {
            return MoonBridge.DR_NEED_IDR
        }

        var codecFlags = 0

        if (frameType == MoonBridge.FRAME_TYPE_IDR) {
            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_SYNC_FRAME

            // If we are using fused IDR frames, submit the CSD with each IDR frame
            if (fusedIdrFrame && !csdSubmittedForThisFrame) {
                for (vpsBuffer in vpsBuffers) {
                    requireInputBuffer().put(vpsBuffer)
                }
                for (spsBuffer in spsBuffers) {
                    requireInputBuffer().put(spsBuffer)
                }
                for (ppsBuffer in ppsBuffers) {
                    requireInputBuffer().put(ppsBuffer)
                }
            }
        }

        // The frame timestamps use an undefined epoch, so normalize them to uptime microseconds.
        if (baseTimestampUs == 0L) {
            baseTimestampUs = (SystemClock.uptimeMillis() * MICROS_PER_MILLI) - enqueueTimeUs
        }

        var timestampUs = baseTimestampUs + enqueueTimeUs
        if (timestampUs <= lastTimestampUs) {
            // We can't submit multiple buffers with the same timestamp
            // so bump it up by one before queuing
            timestampUs = lastTimestampUs + 1
        }
        lastTimestampUs = timestampUs

        numFramesIn++

        if (decodeUnitLength > requireInputBuffer().limit() - requireInputBuffer().position()) {
            val exception =
                IllegalArgumentException(
                    "Decode unit length $decodeUnitLength too large for input buffer " +
                        requireInputBuffer().limit()
                )
            if (!reportedCrash) {
                reportedCrash = true
                crashListener.notifyCrash(exception)
            }
            throw RendererException(this, exception)
        }

        // Copy data from our buffer list into the input buffer
        requireInputBuffer().put(decodeUnitData.orNpe("The decode unit data"), 0, decodeUnitLength)

        if (!queueNextInputBuffer(timestampUs, codecFlags)) {
            return MoonBridge.DR_NEED_IDR
        }

        return MoonBridge.DR_OK
    }

    private fun replaySps(): Boolean {
        fetchNextInputBuffer()

        // Write the Annex B header
        requireInputBuffer().put(byteArrayOf(0x00, 0x00, 0x00, 0x01, SPS_NAL_HEADER))

        // Switch the H264 profile back to high
        val sps = savedSps.orNpe("The saved SPS")
        sps.profileIdc = PROFILE_HIGH

        // Patch the SPS constraint flags
        doProfileSpecificSpsPatching(sps)

        // The H264Utils.writeSPS function safely handles
        // Annex B NALUs (including NALUs with escape sequences)
        val escapedNalu = H264Utils.writeSPS(sps, REPLAYED_SPS_SIZE)
        requireInputBuffer().put(escapedNalu)

        // No need for the SPS anymore
        savedSps = null

        // Queue the new SPS
        return queueNextInputBuffer(0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
    }

    override fun getCapabilities(): Int {
        var capabilities = 0

        // Request the optimal number of slices per frame for this decoder
        capabilities = capabilities or MoonBridge.CAPABILITY_SLICES_PER_FRAME(optimalSlicesPerFrame)

        // Enable reference frame invalidation on supported hardware
        if (refFrameInvalidationAvc) {
            capabilities = capabilities or MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AVC
        }
        if (refFrameInvalidationHevc) {
            capabilities = capabilities or MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_HEVC
        }
        if (refFrameInvalidationAv1) {
            capabilities = capabilities or MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AV1
        }

        // Enable direct submit on supported hardware
        if (directSubmit) {
            capabilities = capabilities or MoonBridge.CAPABILITY_DIRECT_SUBMIT
        }

        return capabilities
    }

    fun getAverageEndToEndLatency(): Int {
        if (globalVideoStats.totalFramesReceived == 0) {
            return 0
        }
        return (globalVideoStats.totalTimeMs / globalVideoStats.totalFramesReceived).toInt()
    }

    fun getAverageDecoderLatency(): Int {
        if (globalVideoStats.totalFramesReceived == 0) {
            return 0
        }
        return (globalVideoStats.decoderTimeMs / globalVideoStats.totalFramesReceived).toInt()
    }

    private class DecoderHungException(private val hangTimeMs: Int) : RuntimeException() {
        override fun toString(): String {
            var str = ""

            str += "Hang time: $hangTimeMs ms${RendererException.DELIMITER}"
            str += super.toString()

            return str
        }
    }

    private class RendererException(renderer: MediaCodecDecoderRenderer, e: Exception) :
        RuntimeException() {
        private val text: String = generateText(renderer, e)

        override fun toString(): String {
            return text
        }

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
        @Suppress("CyclomaticComplexMethod", "LongMethod")
        private fun generateText(
            renderer: MediaCodecDecoderRenderer,
            originalException: Exception,
        ): String {
            var str =
                if (renderer.numVpsIn == 0 && renderer.numSpsIn == 0 && renderer.numPpsIn == 0) {
                    "PreSPSError"
                } else if (renderer.numSpsIn > 0 && renderer.numPpsIn == 0) {
                    "PrePPSError"
                } else if (renderer.numPpsIn > 0 && renderer.numFramesIn == 0) {
                    "PreIFrameError"
                } else if (renderer.numFramesIn > 0 && renderer.outputFormat == null) {
                    "PreOutputConfigError"
                } else if (renderer.outputFormat != null && renderer.numFramesOut == 0) {
                    "PreOutputError"
                } else if (renderer.numFramesOut <= renderer.refreshRate * EARLY_OUTPUT_SECONDS) {
                    "EarlyOutputError"
                } else {
                    "ErrorWhileStreaming"
                }

            val avcDecoder = renderer.avcDecoder
            val hevcDecoder = renderer.hevcDecoder
            val av1Decoder = renderer.av1Decoder

            str += "Format: " + "%x".format(Locale.ROOT, renderer.videoFormat) + DELIMITER
            str += "AVC Decoder: " + (avcDecoder?.name ?: "(none)") + DELIMITER
            str += "HEVC Decoder: " + (hevcDecoder?.name ?: "(none)") + DELIMITER
            str += "AV1 Decoder: " + (av1Decoder?.name ?: "(none)") + DELIMITER
            if (avcDecoder != null) {
                val avcCaps =
                    avcDecoder
                        .getCapabilitiesForType("video/avc")
                        .videoCapabilities
                        .orNpe("The AVC video capabilities")
                val avcWidthRange: Range<Int> = avcCaps.supportedWidths
                str += "AVC supported width range: $avcWidthRange$DELIMITER"
                try {
                    val avcFpsRange: Range<Double>? =
                        avcCaps.getAchievableFrameRatesFor(
                            renderer.initialWidth,
                            renderer.initialHeight,
                        )
                    str += "AVC achievable FPS range: $avcFpsRange$DELIMITER"
                } catch (ignored: IllegalArgumentException) {
                    str += "AVC achievable FPS range: UNSUPPORTED!$DELIMITER"
                }
            }
            if (hevcDecoder != null) {
                val hevcCaps =
                    hevcDecoder
                        .getCapabilitiesForType("video/hevc")
                        .videoCapabilities
                        .orNpe("The HEVC video capabilities")
                val hevcWidthRange: Range<Int> = hevcCaps.supportedWidths
                str += "HEVC supported width range: $hevcWidthRange$DELIMITER"
                try {
                    val hevcFpsRange: Range<Double>? =
                        hevcCaps.getAchievableFrameRatesFor(
                            renderer.initialWidth,
                            renderer.initialHeight,
                        )
                    str += "HEVC achievable FPS range: $hevcFpsRange$DELIMITER"
                } catch (ignored: IllegalArgumentException) {
                    str += "HEVC achievable FPS range: UNSUPPORTED!$DELIMITER"
                }
            }
            if (av1Decoder != null) {
                val av1Caps =
                    av1Decoder
                        .getCapabilitiesForType("video/av01")
                        .videoCapabilities
                        .orNpe("The AV1 video capabilities")
                val av1WidthRange: Range<Int> = av1Caps.supportedWidths
                str += "AV1 supported width range: $av1WidthRange$DELIMITER"
                try {
                    val av1FpsRange: Range<Double>? =
                        av1Caps.getAchievableFrameRatesFor(
                            renderer.initialWidth,
                            renderer.initialHeight,
                        )
                    str += "AV1 achievable FPS range: $av1FpsRange$DELIMITER"
                } catch (ignored: IllegalArgumentException) {
                    str += "AV1 achievable FPS range: UNSUPPORTED!$DELIMITER"
                }
            }
            str += "Configured format: ${renderer.configuredFormat}$DELIMITER"
            str += "Input format: ${renderer.inputFormat}$DELIMITER"
            str += "Output format: ${renderer.outputFormat}$DELIMITER"
            str += "Adaptive playback: ${renderer.adaptivePlayback}$DELIMITER"
            str += "GL Renderer: ${renderer.glRenderer}$DELIMITER"
            // str += "Build fingerprint: " + Build.FINGERPRINT + DELIMITER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                str += "SOC: ${Build.SOC_MANUFACTURER} - ${Build.SOC_MODEL}$DELIMITER"
                str += "Performance class: ${Build.VERSION.MEDIA_PERFORMANCE_CLASS}$DELIMITER"
                // The Java had the vendor parameters of the decoder here, commented out.
            }
            str += "Consecutive crashes: ${renderer.consecutiveCrashCount}$DELIMITER"
            str += "RFI active: ${renderer.refFrameInvalidationActive}$DELIMITER"
            str += "Using modern SPS patching: true$DELIMITER"
            str += "Fused IDR frames: ${renderer.fusedIdrFrame}$DELIMITER"
            str += "Video dimensions: ${renderer.initialWidth}x${renderer.initialHeight}$DELIMITER"
            str += "FPS target: ${renderer.refreshRate}$DELIMITER"
            str += "Bitrate: ${renderer.prefs.bitrate} Kbps$DELIMITER"
            str +=
                "CSD stats: ${renderer.numVpsIn}, ${renderer.numSpsIn}, ${renderer.numPpsIn}$DELIMITER"
            str += "Frames in-out: ${renderer.numFramesIn}, ${renderer.numFramesOut}$DELIMITER"
            str +=
                "Total frames received: ${renderer.globalVideoStats.totalFramesReceived}$DELIMITER"
            str +=
                "Total frames rendered: ${renderer.globalVideoStats.totalFramesRendered}$DELIMITER"
            str +=
                "Frame losses: ${renderer.globalVideoStats.framesLost} in " +
                    "${renderer.globalVideoStats.frameLossEvents} loss events$DELIMITER"
            str +=
                "Average end-to-end client latency: ${renderer.getAverageEndToEndLatency()}ms" +
                    DELIMITER
            str +=
                "Average hardware decoder latency: ${renderer.getAverageDecoderLatency()}ms" +
                    DELIMITER
            str += "Frame pacing mode: ${renderer.prefs.framePacing}$DELIMITER"

            if (originalException is MediaCodec.CodecException) {
                str += "Diagnostic Info: ${originalException.diagnosticInfo}$DELIMITER"
                str += "Recoverable: ${originalException.isRecoverable}$DELIMITER"
                str += "Transient: ${originalException.isTransient}$DELIMITER"

                str += "Codec Error Code: ${originalException.errorCode}$DELIMITER"
            }

            str += originalException.toString()

            return str
        }

        companion object {
            private const val serialVersionUID = 8985937536997012406L

            // BuildConfig.DEBUG is not a constant, so this is set when the class is first used.
            val DELIMITER: String = if (BuildConfig.DEBUG) "\n" else " | "
        }
    }
}

private fun <T : Any> T?.orNpe(what: String): T =
    this ?: throw NullPointerException("$what is not available")
