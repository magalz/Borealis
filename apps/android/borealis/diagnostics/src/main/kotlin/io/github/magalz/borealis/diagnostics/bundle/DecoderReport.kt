package io.github.magalz.borealis.diagnostics.bundle

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel as P
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import android.view.Display.HdrCapabilities as Hdr
import kotlinx.serialization.Serializable

/** One decoder of a video MIME type, as the platform reports it. */
data class VideoDecoder(
    val name: String,
    val mimeType: String,
    val hardware: Boolean,
    /** `MediaCodecInfo.CodecProfileLevel` profile constants. */
    val profiles: List<Int>,
)

/** Where [DecoderCollector] gets its facts; the real one asks the platform. */
interface DecoderSource {
    fun decoders(): List<VideoDecoder>

    /** `Display.HdrCapabilities.HDR_TYPE_*` constants of the default display. */
    fun displayHdrTypes(): List<Int>
}

@Serializable
data class DecoderEntry(
    val name: String,
    val mimeType: String,
    val hardware: Boolean,
    val profiles: List<String>,
)

/** `decoders.json`. */
@Serializable
data class DecodersReport(val decoders: List<DecoderEntry>, val displayHdrTypes: List<String>)

/** Builds [DecodersReport]: H.264, HEVC and AV1 decoders with named profiles, and display HDR. */
@SuppressLint("InlinedApi")
object DecoderCollector {
    private const val AVC = "video/avc"
    private const val HEVC = "video/hevc"
    private const val AV1 = "video/av01"
    private val CODEC_ORDER = listOf(AVC, HEVC, AV1)

    private val AVC_PROFILES =
        mapOf(
            P.AVCProfileBaseline to "Baseline",
            P.AVCProfileMain to "Main",
            P.AVCProfileExtended to "Extended",
            P.AVCProfileHigh to "High",
            P.AVCProfileHigh10 to "High10",
            P.AVCProfileHigh422 to "High422",
            P.AVCProfileHigh444 to "High444",
            P.AVCProfileConstrainedBaseline to "Constrained Baseline",
            P.AVCProfileConstrainedHigh to "Constrained High",
        )
    private val HEVC_PROFILES =
        mapOf(
            P.HEVCProfileMain to "Main",
            P.HEVCProfileMain10 to "Main10",
            P.HEVCProfileMainStill to "Main Still Picture",
            P.HEVCProfileMain10HDR10 to "Main10 HDR10",
            P.HEVCProfileMain10HDR10Plus to "Main10 HDR10+",
        )
    private val AV1_PROFILES =
        mapOf(
            P.AV1ProfileMain8 to "Main8",
            P.AV1ProfileMain10 to "Main10",
            P.AV1ProfileMain10HDR10 to "Main10 HDR10",
            P.AV1ProfileMain10HDR10Plus to "Main10 HDR10+",
        )
    private val PROFILE_NAMES =
        mapOf(AVC to AVC_PROFILES, HEVC to HEVC_PROFILES, AV1 to AV1_PROFILES)

    private val HDR_NAMES =
        mapOf(
            Hdr.HDR_TYPE_DOLBY_VISION to "Dolby Vision",
            Hdr.HDR_TYPE_HDR10 to "HDR10",
            Hdr.HDR_TYPE_HLG to "HLG",
            Hdr.HDR_TYPE_HDR10_PLUS to "HDR10+",
        )

    fun collect(source: DecoderSource): DecodersReport {
        val entries =
            source
                .decoders()
                .filter { it.mimeType in CODEC_ORDER }
                .sortedWith(
                    compareBy<VideoDecoder>(
                        { CODEC_ORDER.indexOf(it.mimeType) },
                        { !it.hardware },
                        { it.name },
                    )
                )
                .map { decoder ->
                    val names = PROFILE_NAMES.getValue(decoder.mimeType)
                    DecoderEntry(
                        name = decoder.name,
                        mimeType = decoder.mimeType,
                        hardware = decoder.hardware,
                        profiles = decoder.profiles.distinct().map { names[it] ?: "profile-$it" },
                    )
                }
        return DecodersReport(entries, source.displayHdrTypes().map { HDR_NAMES[it] ?: "type-$it" })
    }
}

/** [DecoderSource] backed by `MediaCodecList` and the default display. */
class SystemDecoderSource(private val context: Context) : DecoderSource {
    override fun decoders(): List<VideoDecoder> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .filterNot { it.isEncoder }
            .flatMap { info ->
                info.supportedTypes
                    .filter { it.startsWith("video/") }
                    .mapNotNull { mime -> runCatching { describe(info, mime) }.getOrNull() }
            }
    }
        .getOrDefault(emptyList())

    override fun displayHdrTypes(): List<Int> = runCatching {
        val display =
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            display.mode.supportedHdrTypes.toList()
        } else {
            legacyHdrTypes(display)
        }
    }
        .getOrDefault(emptyList())

    // Display.getHdrCapabilities() is deprecated from API 34; it can return null on old devices.
    @Suppress("DEPRECATION")
    private fun legacyHdrTypes(display: Display): List<Int> =
        display.hdrCapabilities?.supportedHdrTypes?.toList().orEmpty()

    private fun describe(info: MediaCodecInfo, mime: String) =
        VideoDecoder(
            name = info.name,
            mimeType = mime,
            hardware = isHardware(info),
            profiles = info.getCapabilitiesForType(mime).profileLevels.map { it.profile },
        )

    // isHardwareAccelerated needs API 29; before that, the software codecs are the stock ones.
    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
        }
}
