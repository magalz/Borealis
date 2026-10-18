package io.github.magalz.borealis.host.caps

import android.content.Context
import io.github.magalz.borealis.host.R
import io.github.magalz.borealis.host.getFormattedString
import io.github.magalz.borealis.nativecore.HostCaps
import io.github.magalz.borealis.nativecore.HostFamily
import io.github.magalz.borealis.nativecore.PyroWaveProfile

/** The host capabilities block of the PC details dialog. */
object HostInfoText {
    /** [details] followed by a blank line and the block; [details] itself when [info] is null. */
    @JvmStatic
    fun appendTo(context: Context, details: String, info: BorealisHostInfo?): String =
        appendTo(context::getFormattedString, details, info)

    internal fun appendTo(
        getString: (Int, List<Any>) -> String,
        details: String,
        info: BorealisHostInfo?,
    ): String {
        if (info == null) return details
        val lines =
            listOf(
                getString(R.string.host_info_header, emptyList()),
                software(getString, info.caps),
                getString(R.string.host_info_codecs, listOf(codecs(getString, info.caps))),
                pyroWave(getString, info.caps),
                // Plain digits: a port is not a quantity, so no locale grouping.
                getString(
                    R.string.host_info_ports,
                    listOf(info.httpPort.toString(), info.httpsPort.toString()),
                ),
            )
        return details + "\n" + lines.joinToString("\n")
    }

    private fun software(getString: (Int, List<Any>) -> String, caps: HostCaps): String {
        val family =
            getString(
                when (caps.family) {
                    HostFamily.GFE -> R.string.host_info_family_gfe
                    HostFamily.SUNSHINE -> R.string.host_info_family_sunshine
                    HostFamily.UNKNOWN -> R.string.host_info_family_unknown
                },
                emptyList(),
            )
        // The core leaves the version at zero when it could not read it.
        return if (caps.family == HostFamily.UNKNOWN) {
            getString(R.string.host_info_software, listOf(family))
        } else {
            getString(
                R.string.host_info_software_version,
                listOf(family, caps.version.joinToString(".")),
            )
        }
    }

    private fun codecs(getString: (Int, List<Any>) -> String, caps: HostCaps): String {
        val ids =
            listOfNotNull(
                R.string.host_info_codec_hevc.takeIf { caps.hevc },
                R.string.host_info_codec_hevc_main10.takeIf { caps.hevcMain10 },
                R.string.host_info_codec_av1.takeIf { caps.av1 },
                R.string.host_info_codec_av1_main10.takeIf { caps.av1Main10 },
                R.string.host_info_codec_yuv444.takeIf { caps.yuv444 },
            )
        return if (ids.isEmpty()) {
            getString(R.string.host_info_codecs_h264_only, emptyList())
        } else {
            ids.joinToString(", ") { getString(it, emptyList()) }
        }
    }

    private fun pyroWave(getString: (Int, List<Any>) -> String, caps: HostCaps): String {
        if (caps.pyroWaveProfiles.isEmpty()) {
            return getString(R.string.host_info_pyrowave_off, emptyList())
        }
        val profiles =
            caps.pyroWaveProfiles.sorted().joinToString(", ") {
                getString(
                    when (it) {
                        PyroWaveProfile.SDR_420 -> R.string.host_info_pyrowave_sdr_420
                        PyroWaveProfile.SDR_444 -> R.string.host_info_pyrowave_sdr_444
                        PyroWaveProfile.HDR_420 -> R.string.host_info_pyrowave_hdr_420
                        PyroWaveProfile.HDR_444 -> R.string.host_info_pyrowave_hdr_444
                    },
                    emptyList(),
                )
            }
        return getString(R.string.host_info_pyrowave, listOf(profiles))
    }
}
