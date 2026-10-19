package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.components.settings.ChoiceOption
import io.github.magalz.borealis.settings.R
import io.github.magalz.borealis.settings.options.DisabledReason
import io.github.magalz.borealis.settings.options.EntryOrientation
import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.ResolutionChoice
import io.github.magalz.borealis.settings.options.ResolutionKind
import io.github.magalz.borealis.settings.options.RowState
import java.util.Locale

private const val KBPS_PER_MBPS = 1000
private const val UHD_HEIGHT = 2160

/** The label of a resolution: `1080p (1920x1080)`, or `Native (2400x1080)` for a native one. */
@Composable
internal fun resolutionLabel(choice: ResolutionChoice): String =
    if (choice.kind == ResolutionKind.STANDARD) {
        val name =
            if (choice.height == UHD_HEIGHT) {
                stringResource(R.string.settings_resolution_name_4k)
            } else {
                stringResource(R.string.settings_resolution_name_p, choice.height)
            }
        stringResource(
            R.string.settings_value_resolution_standard,
            name,
            choice.width,
            choice.height,
        )
    } else {
        stringResource(nativeResolutionRes(choice), choice.width, choice.height)
    }

private fun nativeResolutionRes(choice: ResolutionChoice): Int {
    val fullscreen = choice.kind == ResolutionKind.NATIVE_FULLSCREEN
    return when (choice.orientation) {
        null ->
            if (fullscreen) {
                R.string.settings_value_resolution_fullscreen
            } else {
                R.string.settings_value_resolution_native
            }
        EntryOrientation.PORTRAIT ->
            if (fullscreen) {
                R.string.settings_value_resolution_fullscreen_portrait
            } else {
                R.string.settings_value_resolution_native_portrait
            }
        EntryOrientation.LANDSCAPE ->
            if (fullscreen) {
                R.string.settings_value_resolution_fullscreen_landscape
            } else {
                R.string.settings_value_resolution_native_landscape
            }
    }
}

/** The label of a frame rate: `60 fps`, or `Native (144 fps)` for the native rate. */
@Composable
internal fun frameRateLabel(choice: FrameRateChoice): String =
    stringResource(
        if (choice.isNative) {
            R.string.settings_value_frame_rate_native
        } else {
            R.string.settings_value_frame_rate
        },
        choice.fps,
    )

/** The label of a bitrate: `20 Mbps`, or `Automatic (20 Mbps)` while it is computed. */
@Composable
internal fun bitrateLabel(kbps: Int, automatic: Boolean): String =
    stringResource(
        if (automatic) R.string.settings_value_bitrate_automatic
        else R.string.settings_value_bitrate,
        megabits(kbps),
    )

/** [kbps] in megabits per second with no decimals when it is a whole number: `20` or `12.5`. */
internal fun megabits(kbps: Int): String =
    if (kbps % KBPS_PER_MBPS == 0) {
        (kbps / KBPS_PER_MBPS).toString()
    } else {
        String.format(Locale.ROOT, "%.1f", kbps.toDouble() / KBPS_PER_MBPS)
    }

/** The note of a disabled row: why it cannot be changed. */
@Composable
internal fun disabledNote(disabled: RowState.Disabled?): String? =
    when (disabled?.reason) {
        null -> null
        DisabledReason.HDR_BROKEN_ON_FIRMWARE -> stringResource(R.string.settings_note_hdr_firmware)
        DisabledReason.DEPENDS_ON_SETTING ->
            disabled.dependency
                ?.let { SettingsItem.forSetting(it) }
                ?.let {
                    stringResource(
                        R.string.settings_note_turn_on_first,
                        stringResource(it.titleRes),
                    )
                }
    }

/** What the row of the list [item] shows as its value, for its [value]. */
@Composable
internal fun choiceValue(item: SettingsItem, value: RowValue): String {
    val key = value.choiceKey
    return when (item) {
        SettingsItem.RESOLUTION ->
            value.resolutionChoices.firstOrNull { it.key == key }?.let { resolutionLabel(it) }
        SettingsItem.FRAME_RATE ->
            value.frameRateChoices
                .firstOrNull { it.fps.toString() == key }
                ?.let { frameRateLabel(it) }
                ?: key.toIntOrNull()?.let { frameRateLabel(FrameRateChoice(it, isNative = false)) }
        else ->
            item.input.fixedChoices
                .firstOrNull { it.key == key }
                ?.let { stringResource(it.labelRes) }
    } ?: key
}

/** The entries of the choice sheet of the list [item], for [state]. */
@Composable
internal fun choiceOptions(item: SettingsItem, state: SettingsUiState): List<ChoiceOption<String>> =
    when (item) {
        SettingsItem.RESOLUTION ->
            state.resolutionChoices.map { ChoiceOption(it.key, resolutionLabel(it)) }
        SettingsItem.FRAME_RATE ->
            state.frameRateChoices.map { ChoiceOption(it.fps.toString(), frameRateLabel(it)) }
        else ->
            item.input.fixedChoices.map {
                ChoiceOption(
                    it.key,
                    stringResource(it.labelRes),
                    it.summaryRes?.let { summary -> stringResource(summary) },
                )
            }
    }
