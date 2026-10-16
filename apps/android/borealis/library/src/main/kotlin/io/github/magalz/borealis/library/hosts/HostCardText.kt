package io.github.magalz.borealis.library.hosts

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.library.R
import io.github.magalz.borealis.library.domain.HostCapability

/**
 * The version and the address, or the note that the host was found on the network instead of the
 * address; only the part that exists when the other is missing; null when neither is.
 */
@Composable
internal fun HostItemUiState.detailLine(): String? {
    val found = stringResource(R.string.hosts_detail_found)
    val place = if (foundOnNetwork) found else address
    return when {
        version != null && place != null ->
            stringResource(R.string.hosts_detail_pair, version, place)
        version != null -> version
        foundOnNetwork -> found.replaceFirstChar { it.uppercase() }
        else -> place
    }
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
