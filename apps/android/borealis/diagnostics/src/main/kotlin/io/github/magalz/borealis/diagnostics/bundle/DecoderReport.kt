package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
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

object DecoderCollector {
    fun collect(source: DecoderSource): DecodersReport = TODO()
}

/** [DecoderSource] backed by `MediaCodecList` and the default display. */
class SystemDecoderSource(private val context: Context) : DecoderSource {
    override fun decoders(): List<VideoDecoder> = TODO()

    override fun displayHdrTypes(): List<Int> = TODO()
}
