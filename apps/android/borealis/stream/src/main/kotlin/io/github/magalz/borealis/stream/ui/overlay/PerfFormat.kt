package io.github.magalz.borealis.stream.ui.overlay

import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.SessionInfo
import java.util.Locale

private const val KBPS_PER_MBPS = 1000f

/**
 * The text of the performance overlay and of the menu's Session info, from the numbers of a
 * [PerfSnapshot] and the [SessionInfo]. Pure, so it is tested without a screen. The units and the
 * short labels of this readout are the same in every language and are not translated.
 */
internal object PerfFormat {
    /** What stands for a figure that is not known yet. */
    const val NONE = "—"

    /** The codec with its HDR format, for example "HEVC 10-bit HDR". */
    fun codec(info: SessionInfo): String = if (info.hdr) "${info.codec} 10-bit HDR" else info.codec

    /** The video size and the frame rate, for example "1920×1080 · 60 fps". */
    fun video(info: SessionInfo): String = "${info.width}×${info.height} · ${info.frameRate} fps"

    /** The one line of the Compact overlay. */
    fun compact(perf: PerfSnapshot?, info: SessionInfo): String =
        "${whole(perf?.totalFps)} fps · host ${hostAverage(perf)} ms · " +
            "net ${whole(perf?.rttMs)} ms · ${codec(info)}"

    /** The lines of the Detailed overlay, top to bottom. */
    fun detailed(perf: PerfSnapshot?, info: SessionInfo): List<String> = buildList {
        add("${video(info)} · ${codec(info)}")
        info.decoder?.let { add("Decoder $it") }
        add("Host processing ${hostProcessing(perf)} (min/avg/max)")
        val bitrate = perf?.bitrateMbps?.let { " · ${tenths(it)} Mbps" }.orEmpty()
        add("Network ${network(perf)}$bitrate")
        add("Decode ${tenths(perf?.decodeMs)} ms")
    }

    /** The host processing time as min / avg / max, for example "1.0 / 2.0 / 5.0 ms". */
    fun hostProcessing(perf: PerfSnapshot?): String {
        val host = perf?.hostLatency ?: return "$NONE ms"
        return "${tenths(host.min)} / ${tenths(host.avg)} / ${tenths(host.max)} ms"
    }

    /** The network round trip with its variance and the packet loss. */
    fun network(perf: PerfSnapshot?): String {
        if (perf == null) return "$NONE ms"
        return "${perf.rttMs} ms ± ${perf.rttVarianceMs} · loss ${tenths(perf.lossPercent)} %"
    }

    /** The bitrate in use against the limit, for example "18.2 of 20 Mbps". */
    fun bitrate(perf: PerfSnapshot?, info: SessionInfo): String {
        val limit = tenths(info.bitrateLimitKbps / KBPS_PER_MBPS).removeSuffix(".0")
        return "${tenths(perf?.bitrateMbps)} of $limit Mbps"
    }

    private fun hostAverage(perf: PerfSnapshot?) = tenths(perf?.hostLatency?.avg)

    private fun whole(value: Number?): String =
        if (value == null) NONE else String.format(Locale.ROOT, "%.0f", value.toDouble())

    private fun tenths(value: Float?): String =
        if (value == null) NONE else String.format(Locale.ROOT, "%.1f", value)
}
