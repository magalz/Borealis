package io.github.magalz.borealis.settings.ui

import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.ResolutionChoice

/**
 * The key of the offered resolution to use for a stored [key]: [key] itself when it is offered,
 * otherwise the offered one with the most pixels that is not larger than it, or the smallest
 * offered one when none is that small. A [key] that is not a size counts as the default resolution.
 */
internal fun nearestLowerResolution(key: String, offered: List<ResolutionChoice>): String {
    if (offered.isEmpty() || offered.any { it.key == key }) return key
    val pixels = pixelsOf(key) ?: pixelsOf(Settings.RESOLUTION.default) ?: return key
    val lower = offered.filter { it.width * it.height <= pixels }
    val choice =
        lower.maxByOrNull { it.width * it.height } ?: offered.minBy { it.width * it.height }
    return choice.key
}

/**
 * The stored value to use for a stored frame rate [key]: [key] itself when it is offered, otherwise
 * the highest offered rate that is not above it, or the lowest offered one when none is that low. A
 * [key] that is not a number counts as the default frame rate.
 */
internal fun nearestLowerFrameRate(key: String, offered: List<FrameRateChoice>): String {
    if (offered.isEmpty() || offered.any { it.fps.toString() == key }) return key
    val fps = key.toIntOrNull() ?: Settings.FRAME_RATE.default.toInt()
    val choice = offered.filter { it.fps <= fps }.maxByOrNull { it.fps } ?: offered.minBy { it.fps }
    return choice.fps.toString()
}

/** The pixel count of a stored resolution such as `1920x1080`, or `null` when it is not one. */
private fun pixelsOf(key: String): Int? {
    val parts = key.split("x")
    val width = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val height = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return width * height
}
