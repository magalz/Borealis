package io.github.magalz.borealis.settings.options

import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.device.DeviceFacts
import io.github.magalz.borealis.settings.repository.SettingsSnapshot

/**
 * The device-specific options of the settings screen (ADR 0055, decision 9): which resolutions and
 * frame rates to offer, and which rows to show, enable or hide. Every answer is a pure function of
 * the [DeviceFacts] and the [SettingsSnapshot] that the caller passes in.
 *
 * The rules are those of UC-009 and of the inherited `StreamSettings` screen. The real
 * implementation is separate from this contract.
 */
interface SettingsOptions {
    /**
     * The choices of the Resolution list, in display order: the standard sizes that the display and
     * the decoders can handle, then the native ones. 720p is always offered. A native entry is left
     * out when the same `WxH` is already a standard entry.
     */
    fun resolutionChoices(facts: DeviceFacts): List<ResolutionChoice>

    /**
     * The choices of the Frame rate list, in display order: 30 and 60 always, 90 when the display
     * reaches 88 Hz, 120 when it reaches 118 Hz, then the native rate when it is not in the list.
     * When [unlockAllFrameRates] is on, 90 and 120 are offered whatever the display.
     */
    fun frameRateChoices(facts: DeviceFacts, unlockAllFrameRates: Boolean): List<FrameRateChoice>

    /**
     * The state of the row of [setting]: [RowState.Hidden] when the device cannot use it,
     * [RowState.Disabled] when another setting or the firmware blocks it, otherwise
     * [RowState.Enabled]. A setting with no rule is always [RowState.Enabled].
     */
    fun rowState(setting: Setting<*>, facts: DeviceFacts, snapshot: SettingsSnapshot): RowState

    /** The state of the whole On-screen controls group: hidden without a touchscreen. */
    fun onScreenControlsGroupState(facts: DeviceFacts): RowState

    /**
     * The state of the Reset layout action of the On-screen controls group: hidden when the group
     * is hidden, otherwise enabled.
     */
    fun resetLayoutState(facts: DeviceFacts): RowState
}

/**
 * One entry of the Resolution list.
 *
 * @property key the stored value, `WxH`, for example `2400x1080`.
 * @property width the width in pixels. It is smaller than [height] only for the portrait entry of a
 *   screen that is nearly square.
 * @property height the height in pixels.
 * @property isNative whether this is a native resolution of the display; choosing it shows the
 *   warning that the PC must support it.
 * @property isCutoutSafe whether this native entry is the display size without its cutout.
 */
data class ResolutionChoice(
    val key: String,
    val width: Int,
    val height: Int,
    val isNative: Boolean,
    val isCutoutSafe: Boolean,
)

/**
 * One entry of the Frame rate list.
 *
 * @property fps the frame rate; the stored value is its decimal string.
 * @property isNative whether this is the native refresh rate of the display, added because it is
 *   not one of the standard rates; choosing it shows the same warning as a native resolution.
 */
data class FrameRateChoice(val fps: Int, val isNative: Boolean)

/** Whether a row is shown, and whether it can be changed. */
sealed interface RowState {
    /** The device cannot use the row: it is not shown. */
    data object Hidden : RowState

    /** The row is shown and can be changed. */
    data object Enabled : RowState

    /**
     * The row is shown, focusable and cannot be changed. It shows a note for [reason].
     *
     * @property dependency the switch that must be on, set for [DisabledReason.DEPENDS_ON_SETTING]
     *   and `null` otherwise.
     */
    data class Disabled(
        val reason: DisabledReason,
        val dependency: Setting<Boolean>? = null,
    ) : RowState
}

/** Why a row is disabled. */
enum class DisabledReason {
    /** Another setting must be on first; see [RowState.Disabled.dependency]. */
    DEPENDS_ON_SETTING,

    /** HDR crashes on this NVIDIA SHIELD firmware. Update the firmware to use it. */
    HDR_BROKEN_ON_FIRMWARE,
}
