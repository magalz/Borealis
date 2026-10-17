package io.github.magalz.borealis.settings.options

import android.os.Build
import io.github.magalz.borealis.settings.catalog.BooleanSetting
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.device.DecoderLimits
import io.github.magalz.borealis.settings.device.DeviceFacts
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * The [SettingsOptions] of the app: the rules that the inherited `StreamSettings` applied from the
 * device, in Kotlin. It holds no state: every answer comes from the arguments.
 *
 * Differences from `StreamSettings` that the minimum SDK of the app (28) makes safe: the branches
 * for Android below 6 (native size from the metrics), 7 (HDR) and 8 (remote desktop mouse,
 * picture-in-picture and vibration strength) are gone, because they cannot run.
 */
class DefaultSettingsOptions @Inject constructor() : SettingsOptions {
    override fun resolutionChoices(facts: DeviceFacts): List<ResolutionChoice> {
        val choices = STANDARD_RESOLUTIONS.toMutableList()
        val cutoutSafe = facts.cutoutSafeSize
        if (cutoutSafe != null) {
            choices.addNative(cutoutSafe.width, cutoutSafe.height, ResolutionKind.NATIVE)
        }
        // With a cutout, the full size is the fullscreen entry next to the cutout-safe one.
        val fullKind =
            if (cutoutSafe != null) ResolutionKind.NATIVE_FULLSCREEN else ResolutionKind.NATIVE
        var slot = 0
        for (mode in facts.displayModes) {
            // Some devices report their modes in portrait: put the long side first.
            val width = max(mode.width, mode.height)
            val height = min(mode.width, mode.height)
            // Some TVs report strange modes: leave native sizes out unless they exceed 4K.
            if (!facts.isTelevision || width > UHD_WIDTH || height > UHD_HEIGHT) {
                choices.addNative(width, height, fullKind)
            }
            slot = max(slot, displaySlot(width, height))
        }
        slot = max(slot, max(decoderSlot(facts.avcDecoder), decoderSlot(facts.hevcDecoder)))
        // A slot of 0 means that nothing was learned, so nothing is taken away. 720p stays always.
        if (slot != 0) {
            if (slot < SLOT_4K) choices.removeAll { it.key == RES_4K }
            if (slot < SLOT_1440P) choices.removeAll { it.key == RES_1440P }
            if (slot < SLOT_1080P) choices.removeAll { it.key == RES_1080P }
        }
        return choices
    }

    override fun frameRateChoices(
        facts: DeviceFacts,
        unlockAllFrameRates: Boolean,
    ): List<FrameRateChoice> {
        val rates = mutableListOf(30, 60)
        // A little room in case the display rounds its rate down.
        if (unlockAllFrameRates || facts.maxRefreshRate >= MIN_HZ_FOR_90) rates += 90
        if (unlockAllFrameRates || facts.maxRefreshRate >= MIN_HZ_FOR_120) rates += 120
        val choices = rates.map { FrameRateChoice(it, isNative = false) }.toMutableList()
        val native = Math.round(facts.maxRefreshRate)
        if (native != 0 && native !in rates) choices += FrameRateChoice(native, isNative = true)
        return choices
    }

    override fun rowState(
        setting: Setting<*>,
        facts: DeviceFacts,
        snapshot: SettingsSnapshot,
    ): RowState {
        val rule = rules[setting] ?: return RowState.Enabled
        val dependency = rule.dependency
        return when {
            rule.hiddenOn(facts) -> RowState.Hidden
            rule.blockedByFirmware(facts) ->
                RowState.Disabled(DisabledReason.HDR_BROKEN_ON_FIRMWARE)
            dependency != null && !snapshot[dependency] ->
                RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, dependency)
            else -> RowState.Enabled
        }
    }

    override fun onScreenControlsGroupState(facts: DeviceFacts): RowState =
        if (facts.hasTouchscreen) RowState.Enabled else RowState.Hidden

    override fun resetLayoutState(facts: DeviceFacts): RowState = onScreenControlsGroupState(facts)

    /**
     * What decides the state of one row: [hiddenOn] says when the device cannot use it,
     * [blockedByFirmware] when the firmware breaks it, and [dependency] is the switch that must be
     * on.
     */
    private class Rule(
        val hiddenOn: (DeviceFacts) -> Boolean = { false },
        val blockedByFirmware: (DeviceFacts) -> Boolean = { false },
        val dependency: BooleanSetting? = null,
    )

    private companion object {
        const val UHD_WIDTH = 3840
        const val UHD_HEIGHT = 2160
        const val QHD_WIDTH = 2560
        const val QHD_HEIGHT = 1440
        const val FHD_WIDTH = 1920
        const val FHD_HEIGHT = 1080
        const val HD_WIDTH = 1280
        const val SLOT_4K = UHD_WIDTH
        const val SLOT_1440P = QHD_WIDTH
        const val SLOT_1080P = FHD_WIDTH
        const val MIN_HZ_FOR_90 = 88f
        const val MIN_HZ_FOR_120 = 118f

        /** The long side over the short side below which a screen counts as nearly square. */
        const val SQUARISH_RATIO = 1.3f

        const val RES_4K = "3840x2160"
        const val RES_1440P = "2560x1440"
        const val RES_1080P = "1920x1080"

        val STANDARD_RESOLUTIONS =
            listOf(
                ResolutionChoice("640x360", 640, 360, ResolutionKind.STANDARD),
                ResolutionChoice("854x480", 854, 480, ResolutionKind.STANDARD),
                ResolutionChoice("1280x720", HD_WIDTH, 720, ResolutionKind.STANDARD),
                ResolutionChoice(RES_1080P, FHD_WIDTH, FHD_HEIGHT, ResolutionKind.STANDARD),
                ResolutionChoice(RES_1440P, QHD_WIDTH, QHD_HEIGHT, ResolutionKind.STANDARD),
                ResolutionChoice(RES_4K, UHD_WIDTH, UHD_HEIGHT, ResolutionKind.STANDARD),
            )

        /**
         * The largest standard width that a display mode of this size proves the device can show: 0
         * when the mode is below 1080p.
         */
        fun displaySlot(width: Int, height: Int): Int =
            when {
                width >= UHD_WIDTH || height >= UHD_HEIGHT -> SLOT_4K
                width >= QHD_WIDTH || height >= QHD_HEIGHT -> SLOT_1440P
                width >= FHD_WIDTH || height >= FHD_HEIGHT -> SLOT_1080P
                else -> 0
            }

        /**
         * The standard width that a decoder proves: 3840, 1920 or 1280, never 2560 (as upstream). A
         * decoder that does not reach 1280 reports nonsense, so it proves nothing.
         */
        fun decoderSlot(decoder: DecoderLimits?): Int =
            when {
                decoder == null || decoder.maxWidth < HD_WIDTH -> 0
                decoder.maxWidth >= UHD_WIDTH -> UHD_WIDTH
                decoder.maxWidth >= FHD_WIDTH -> FHD_WIDTH
                else -> HD_WIDTH
            }

        fun isSquarish(width: Int, height: Int): Boolean =
            max(width, height).toFloat() / min(width, height) < SQUARISH_RATIO

        /**
         * Adds the native entries of a [width] by [height] display (landscape): the portrait one
         * first when the screen is nearly square. A size that is already listed is not added again.
         */
        fun MutableList<ResolutionChoice>.addNative(width: Int, height: Int, kind: ResolutionKind) {
            val squarish = isSquarish(width, height)
            if (squarish) addNativeEntry(height, width, kind, EntryOrientation.PORTRAIT)
            addNativeEntry(width, height, kind, if (squarish) EntryOrientation.LANDSCAPE else null)
        }

        fun MutableList<ResolutionChoice>.addNativeEntry(
            width: Int,
            height: Int,
            kind: ResolutionKind,
            orientation: EntryOrientation?,
        ) {
            val key = "${width}x$height"
            if (none { it.key == key }) add(ResolutionChoice(key, width, height, kind, orientation))
        }

        val onScreenControls =
            Rule(hiddenOn = { !it.hasTouchscreen }, dependency = Settings.SHOW_ONSCREEN_CONTROLS)

        /** The rows that have a rule; a row that is not here is always enabled. */
        val rules: Map<Setting<*>, Rule> =
            mapOf(
                Settings.SHOW_ONSCREEN_CONTROLS to Rule(hiddenOn = { !it.hasTouchscreen }),
                Settings.VIBRATE_OSC to
                    Rule(
                        hiddenOn = { !it.hasTouchscreen || !it.hasVibrator },
                        dependency = Settings.SHOW_ONSCREEN_CONTROLS,
                    ),
                Settings.ONLY_L3_R3 to onScreenControls,
                Settings.SHOW_GUIDE_BUTTON to onScreenControls,
                Settings.OSC_OPACITY to onScreenControls,
                Settings.USB_DRIVER to Rule(hiddenOn = { !it.hasUsbHost }),
                Settings.USB_BIND_ALL to
                    Rule(hiddenOn = { !it.hasUsbHost }, dependency = Settings.USB_DRIVER),
                Settings.ANALOG_SCROLLING to Rule(dependency = Settings.MOUSE_EMULATION),
                Settings.ENABLE_PIP to
                    Rule(hiddenOn = { !it.supportsPictureInPicture || it.isFireOs }),
                Settings.VIBRATE_FALLBACK to Rule(hiddenOn = { !it.hasVibrator }),
                Settings.VIBRATE_FALLBACK_STRENGTH to
                    Rule(
                        hiddenOn = { !it.hasVibrator || !it.hasAmplitudeControl },
                        dependency = Settings.VIBRATE_FALLBACK,
                    ),
                // Motion sensors came with Android 12.
                Settings.GAMEPAD_MOTION_SENSORS to
                    Rule(hiddenOn = { it.sdkInt < Build.VERSION_CODES.S }),
                Settings.GAMEPAD_MOTION_FALLBACK to
                    Rule(hiddenOn = { !it.hasAccelerometer && !it.hasGyroscope }),
                // A SHIELD supports raw mouse input in pointer capture mode.
                Settings.ABSOLUTE_MOUSE_MODE to Rule(hiddenOn = { it.isNvidiaShield }),
                Settings.ENABLE_HDR to
                    Rule(
                        hiddenOn = { !it.supportsHdr10 },
                        blockedByFirmware = { it.hasBrokenHdrFirmware },
                    ),
            )
    }
}
