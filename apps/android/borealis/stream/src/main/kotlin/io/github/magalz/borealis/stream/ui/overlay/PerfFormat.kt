package io.github.magalz.borealis.stream.ui.overlay

import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.SessionInfo

/**
 * The text of the performance overlay and of the menu's Session info, from the numbers of a
 * [PerfSnapshot] and the [SessionInfo]. Pure, so it is tested without a screen. The units and the
 * short labels of this readout are the same in every language and are not translated.
 */
internal object PerfFormat {
    /** What stands for a figure that is not known yet. */
    const val NONE = "—"

    /** The codec with its HDR format, for example "HEVC 10-bit HDR". */
    fun codec(info: SessionInfo): String = ""

    /** The video size and the frame rate, for example "1920×1080 · 60 fps". */
    fun video(info: SessionInfo): String = ""

    /** The one line of the Compact overlay. */
    fun compact(perf: PerfSnapshot?, info: SessionInfo): String = ""

    /** The lines of the Detailed overlay, top to bottom. */
    fun detailed(perf: PerfSnapshot?, info: SessionInfo): List<String> = emptyList()

    /** The host processing time as min / avg / max, for example "1.0 / 2.0 / 5.0 ms". */
    fun hostProcessing(perf: PerfSnapshot?): String = ""

    /** The network round trip with its variance and the packet loss. */
    fun network(perf: PerfSnapshot?): String = ""

    /** The bitrate in use against the limit, for example "18.2 of 20 Mbps". */
    fun bitrate(perf: PerfSnapshot?, info: SessionInfo): String = ""
}
