package io.github.magalz.borealis.stream.ui.overlay

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.SessionInfo
import java.util.Locale

private const val KBPS_PER_MBPS = 1000f

/** A [PerfFormat] for the language of the screen, made again when the configuration changes. */
@Composable
internal fun rememberPerfFormat(): PerfFormat {
    val resources = LocalResources.current
    return remember(resources) { PerfFormat(resources) }
}

/**
 * The text of the performance overlay and of the menu's Session info, from the numbers of a
 * [PerfSnapshot] and the [SessionInfo]. The words are string resources; the numbers are formatted
 * here, with a decimal point whatever the locale, and the units are the same in every language.
 */
internal class PerfFormat(private val resources: Resources) {
    /** The codec with its HDR format, for example "HEVC 10-bit HDR". */
    fun codec(info: SessionInfo): String =
        if (info.hdr) {
            resources.getString(R.string.stream_overlay_codec_hdr, info.codec)
        } else {
            info.codec
        }

    /** The video size and the frame rate, for example "1920×1080 · 60 fps". */
    fun video(info: SessionInfo): String =
        resources.getString(
            R.string.stream_overlay_video,
            info.width.toString(),
            info.height.toString(),
            info.frameRate.toString(),
        )

    /** The one line of the Compact overlay. */
    fun compact(perf: PerfSnapshot?, info: SessionInfo): String =
        resources.getString(
            R.string.stream_overlay_compact,
            whole(perf?.totalFps),
            tenths(perf?.hostLatency?.avg),
            perf?.rttMs?.toString() ?: NONE,
            codec(info),
        )

    /** The lines of the Detailed overlay, top to bottom. */
    fun detailed(perf: PerfSnapshot?, info: SessionInfo): List<String> = buildList {
        add(resources.getString(R.string.stream_overlay_video_codec, video(info), codec(info)))
        info.decoder?.let { add(resources.getString(R.string.stream_overlay_decoder, it)) }
        add(resources.getString(R.string.stream_overlay_host, hostProcessing(perf)))
        val network = listOfNotNull(network(perf), perf?.bitrateMbps?.let { "${tenths(it)} Mbps" })
        add(resources.getString(R.string.stream_overlay_network, network.joinToString(" · ")))
        add(resources.getString(R.string.stream_overlay_decode, tenths(perf?.decodeMs)))
    }

    /** The host processing time as min / avg / max, for example "1.0 / 2.0 / 5.0 ms". */
    fun hostProcessing(perf: PerfSnapshot?): String {
        val host = perf?.hostLatency ?: return "$NONE ms"
        return resources.getString(
            R.string.stream_overlay_host_range,
            tenths(host.min),
            tenths(host.avg),
            tenths(host.max),
        )
    }

    /** The network round trip with its variance and the packet loss. */
    fun network(perf: PerfSnapshot?): String {
        if (perf == null) return "$NONE ms"
        return resources.getString(
            R.string.stream_overlay_network_value,
            perf.rttMs.toString(),
            perf.rttVarianceMs.toString(),
            tenths(perf.lossPercent),
        )
    }

    /** The bitrate in use against the limit, for example "18.2 of 20 Mbps". */
    fun bitrate(perf: PerfSnapshot?, info: SessionInfo): String {
        val limit = tenths(info.bitrateLimitKbps / KBPS_PER_MBPS).removeSuffix(".0")
        return resources.getString(
            R.string.stream_overlay_bitrate,
            tenths(perf?.bitrateMbps),
            limit,
        )
    }

    private fun whole(value: Float?): String =
        if (value == null) NONE else String.format(Locale.ROOT, "%.0f", value)

    private fun tenths(value: Float?): String =
        if (value == null) NONE else String.format(Locale.ROOT, "%.1f", value)

    companion object {
        /** What stands for a figure that is not known yet. */
        const val NONE = "—"
    }
}
