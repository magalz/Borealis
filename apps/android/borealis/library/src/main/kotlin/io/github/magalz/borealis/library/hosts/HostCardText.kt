package io.github.magalz.borealis.library.hosts

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.library.R
import io.github.magalz.borealis.library.domain.HostCapability

/**
 * The address, or the note that the host was found on the network instead of it; null when neither
 * applies. An online host that is not paired shows the note, because the address is not what the
 * user acts on yet.
 */
@Composable
internal fun HostItemUiState.detailLine(): String? {
    val found = stringResource(R.string.hosts_detail_found)
    return if (foundOnNetwork) found else address
}

@StringRes
internal fun HostCapability.labelRes(): Int =
    when (this) {
        HostCapability.HEVC -> R.string.hosts_tag_hevc
        HostCapability.HEVC_10_BIT -> R.string.hosts_tag_hevc_10_bit
        HostCapability.AV1 -> R.string.hosts_tag_av1
        HostCapability.AV1_10_BIT -> R.string.hosts_tag_av1_10_bit
        HostCapability.YUV_444 -> R.string.hosts_tag_yuv_444
        HostCapability.HDR -> R.string.hosts_tag_hdr
    }
