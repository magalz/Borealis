package io.github.magalz.borealis.settings.options

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.BooleanSetting
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.device.DecoderLimits
import io.github.magalz.borealis.settings.device.DeviceFacts
import io.github.magalz.borealis.settings.device.DisplayMode
import io.github.magalz.borealis.settings.device.PixelSize
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.testing.DeviceFactsBuilder
import io.github.magalz.borealis.settings.testing.deviceFacts
import org.junit.Test

/** The Resolution list of the upstream stream settings screen, now in Kotlin. */
class DefaultSettingsOptionsResolutionTest {
    private val options = DefaultSettingsOptions()

    private fun keys(facts: DeviceFacts): List<String> =
        options.resolutionChoices(facts).map { it.key }

    /** A 720p display with no decoder information: nothing but the standard sizes to judge by. */
    private fun bare(configure: DeviceFactsBuilder.() -> Unit) = deviceFacts {
        displayModes = listOf(DisplayMode(1280, 720, 60f))
        avcDecoder = null
        hevcDecoder = null
        configure()
    }

    @Test
    fun `a 4K phone offers every standard size and then its native size`() {
        val choices = options.resolutionChoices(deviceFacts())

        assertThat(choices.map { it.key })
            .containsExactly(
                "640x360",
                "854x480",
                "1280x720",
                "1920x1080",
                "2560x1440",
                "3840x2160",
                "2400x1080",
            )
            .inOrder()
        assertThat(choices.take(STANDARD_COUNT).map { it.kind }.toSet())
            .containsExactly(ResolutionKind.STANDARD)
        assertThat(choices.last())
            .isEqualTo(ResolutionChoice("2400x1080", 2400, 1080, ResolutionKind.NATIVE))
        assertThat(choices.last().isNative).isTrue()
    }

    @Test
    fun `720p is offered even when the decoders report only 1280`() {
        val result = keys(bare { avcDecoder = DecoderLimits(1280, 720) })

        assertThat(result).containsExactly("640x360", "854x480", "1280x720").inOrder()
    }

    @Test
    fun `a decoder that reaches 1920 removes 1440p and 4K`() {
        val result = keys(bare { hevcDecoder = DecoderLimits(1920, 1088) })

        assertThat(result).containsExactly("640x360", "854x480", "1280x720", "1920x1080").inOrder()
    }

    @Test
    fun `a decoder that reaches 3840 keeps every standard size`() {
        val result = keys(bare { avcDecoder = DecoderLimits(4096, 2304) })

        assertThat(result).hasSize(STANDARD_COUNT)
    }

    @Test
    fun `a decoder that reaches only 2560 counts as 1920, as upstream does`() {
        val result = keys(bare { avcDecoder = DecoderLimits(2560, 1440) })

        assertThat(result).doesNotContain("2560x1440")
        assertThat(result).contains("1920x1080")
    }

    @Test
    fun `the better of the two decoders wins`() {
        val result =
            keys(
                bare {
                    avcDecoder = DecoderLimits(1280, 720)
                    hevcDecoder = DecoderLimits(3840, 2160)
                }
            )

        assertThat(result).hasSize(STANDARD_COUNT)
    }

    @Test
    fun `nothing is removed when no decoder and no display mode reaches 720p`() {
        // Upstream removes sizes only when it found some evidence. Keep that.
        val result = keys(bare { avcDecoder = DecoderLimits(384, 216) })

        assertThat(result).hasSize(STANDARD_COUNT)
    }

    @Test
    fun `the display alone can lift the limit of the decoders`() {
        val result =
            keys(
                bare {
                    displayModes = listOf(DisplayMode(2560, 1440, 60f))
                    avcDecoder = DecoderLimits(1280, 720)
                }
            )

        assertThat(result)
            .containsExactly("640x360", "854x480", "1280x720", "1920x1080", "2560x1440")
            .inOrder()
    }

    @Test
    fun `a 4K display offers every standard size without a duplicate native entry`() {
        val result =
            keys(
                bare {
                    displayModes = listOf(DisplayMode(3840, 2160, 60f))
                    avcDecoder = DecoderLimits(1280, 720)
                }
            )

        assertThat(result).hasSize(STANDARD_COUNT)
    }

    @Test
    fun `a portrait display mode is normalised and listed once for every refresh rate`() {
        val result =
            keys(
                deviceFacts {
                    displayModes =
                        listOf(
                            DisplayMode(1080, 2400, 60f),
                            DisplayMode(1080, 2400, 120f),
                            DisplayMode(2400, 1080, 90f),
                        )
                }
            )

        assertThat(result.filter { it == "2400x1080" }).hasSize(1)
        assertThat(result).doesNotContain("1080x2400")
    }

    @Test
    fun `a display with a cutout offers the cutout-safe size, then the full size as fullscreen`() {
        val choices =
            options.resolutionChoices(deviceFacts { cutoutSafeSize = PixelSize(2316, 1080) })

        assertThat(choices.drop(STANDARD_COUNT))
            .containsExactly(
                ResolutionChoice("2316x1080", 2316, 1080, ResolutionKind.NATIVE),
                ResolutionChoice("2400x1080", 2400, 1080, ResolutionKind.NATIVE_FULLSCREEN),
            )
            .inOrder()
        assertThat(choices.last().isNative).isTrue()
    }

    @Test
    fun `a nearly square display offers a portrait and a landscape entry of each native size`() {
        val choices =
            options.resolutionChoices(
                deviceFacts { displayModes = listOf(DisplayMode(1800, 1600, 60f)) }
            )

        assertThat(choices.drop(STANDARD_COUNT))
            .containsExactly(
                ResolutionChoice(
                    "1600x1800",
                    1600,
                    1800,
                    ResolutionKind.NATIVE,
                    EntryOrientation.PORTRAIT,
                ),
                ResolutionChoice(
                    "1800x1600",
                    1800,
                    1600,
                    ResolutionKind.NATIVE,
                    EntryOrientation.LANDSCAPE,
                ),
            )
            .inOrder()
    }

    @Test
    fun `a display that is not nearly square has no orientation`() {
        val choices = options.resolutionChoices(deviceFacts())

        assertThat(choices.map { it.orientation }.toSet()).containsExactly(null)
    }

    @Test
    fun `a nearly square display with a cutout offers both orientations of both native sizes`() {
        val choices =
            options.resolutionChoices(
                deviceFacts {
                    displayModes = listOf(DisplayMode(1800, 1600, 60f))
                    cutoutSafeSize = PixelSize(1750, 1600)
                }
            )

        assertThat(choices.drop(STANDARD_COUNT).map { it.key to it.kind })
            .containsExactly(
                "1600x1750" to ResolutionKind.NATIVE,
                "1750x1600" to ResolutionKind.NATIVE,
                "1600x1800" to ResolutionKind.NATIVE_FULLSCREEN,
                "1800x1600" to ResolutionKind.NATIVE_FULLSCREEN,
            )
            .inOrder()
    }

    @Test
    fun `a television drops native sizes that are not above 4K`() {
        val result =
            keys(
                deviceFacts {
                    isTelevision = true
                    displayModes = listOf(DisplayMode(1366, 768, 60f), DisplayMode(1920, 1200, 60f))
                }
            )

        assertThat(result).hasSize(STANDARD_COUNT)
    }

    @Test
    fun `a television keeps a native size that is wider than 4K`() {
        val result =
            keys(
                deviceFacts {
                    isTelevision = true
                    displayModes = listOf(DisplayMode(4096, 2160, 60f))
                }
            )

        assertThat(result.last()).isEqualTo("4096x2160")
    }

    @Test
    fun `a television keeps a native size that is taller than 4K`() {
        val result =
            keys(
                deviceFacts {
                    isTelevision = true
                    displayModes = listOf(DisplayMode(3840, 2400, 60f))
                }
            )

        assertThat(result.last()).isEqualTo("3840x2400")
    }

    @Test
    fun `a television does not drop the cutout-safe size`() {
        val result =
            keys(
                deviceFacts {
                    isTelevision = true
                    cutoutSafeSize = PixelSize(2316, 1080)
                }
            )

        assertThat(result).contains("2316x1080")
    }

    private companion object {
        const val STANDARD_COUNT = 6
    }
}

/** The Frame rate list. */
class DefaultSettingsOptionsFrameRateTest {
    private val options = DefaultSettingsOptions()

    private fun rates(maxHz: Float, unlock: Boolean = false): List<Int> =
        options.frameRateChoices(deviceFacts { maxRefreshRate = maxHz }, unlock).map { it.fps }

    @Test
    fun `30 and 60 are always offered`() {
        assertThat(rates(24f)).containsAtLeast(30, 60)
        assertThat(rates(60f)).containsExactly(30, 60).inOrder()
    }

    @Test
    fun `90 is offered from 88 Hz`() {
        assertThat(rates(88f)).contains(90)
        assertThat(rates(87.9f)).doesNotContain(90)
    }

    @Test
    fun `120 is offered from 118 Hz`() {
        assertThat(rates(118f)).contains(120)
        assertThat(rates(117.9f)).doesNotContain(120)
    }

    @Test
    fun `a 120 Hz display offers every standard rate and no native one`() {
        val choices = options.frameRateChoices(deviceFacts(), unlockAllFrameRates = false)

        assertThat(choices.map { it.fps }).containsExactly(30, 60, 90, 120).inOrder()
        assertThat(choices.map { it.isNative }.toSet()).containsExactly(false)
    }

    @Test
    fun `unlock all frame rates offers 90 and 120 whatever the display`() {
        assertThat(rates(60f, unlock = true)).containsExactly(30, 60, 90, 120).inOrder()
    }

    @Test
    fun `a rate that is not in the list is added as native`() {
        val choices =
            options.frameRateChoices(
                deviceFacts { maxRefreshRate = 144f },
                unlockAllFrameRates = false,
            )

        assertThat(choices.last()).isEqualTo(FrameRateChoice(144, isNative = true))
        assertThat(choices.map { it.fps }).containsExactly(30, 60, 90, 120, 144).inOrder()
    }

    @Test
    fun `a native rate is added also while 90 and 120 are filtered out`() {
        val choices =
            options.frameRateChoices(
                deviceFacts { maxRefreshRate = 48f },
                unlockAllFrameRates = false,
            )

        assertThat(choices.map { it.fps to it.isNative })
            .containsExactly(30 to false, 60 to false, 48 to true)
            .inOrder()
    }

    @Test
    fun `a rate that rounds to a standard rate is not added`() {
        assertThat(rates(119.88f)).containsExactly(30, 60, 90, 120).inOrder()
    }

    @Test
    fun `a rate just above the 90 threshold is added as native next to 90`() {
        assertThat(rates(88f)).containsExactly(30, 60, 90, 88).inOrder()
    }

    @Test
    fun `a display that reports no rate has no native entry`() {
        assertThat(rates(0f)).containsExactly(30, 60).inOrder()
    }

    @Test
    fun `unlock all frame rates still adds the native rate`() {
        assertThat(rates(165f, unlock = true)).containsExactly(30, 60, 90, 120, 165).inOrder()
    }
}

/** Which rows are hidden, disabled or enabled (UC-009). */
class DefaultSettingsOptionsRowStateTest {
    private val options = DefaultSettingsOptions()
    private val defaults = SettingsSnapshot.DEFAULTS

    private fun state(
        setting: Setting<*>,
        snapshot: SettingsSnapshot = defaults,
        configure: DeviceFactsBuilder.() -> Unit = {},
    ): RowState = options.rowState(setting, deviceFacts(configure), snapshot)

    private fun SettingsSnapshot.on(setting: BooleanSetting) = with(setting, true)

    private fun SettingsSnapshot.off(setting: BooleanSetting) = with(setting, false)

    private fun dependsOn(setting: BooleanSetting) =
        RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, setting)

    @Test
    fun `a setting with no rule is always enabled`() {
        assertThat(state(Settings.RESOLUTION)).isEqualTo(RowState.Enabled)
        assertThat(state(Settings.FRAME_RATE)).isEqualTo(RowState.Enabled)
        assertThat(state(Settings.UNLOCK_FPS)).isEqualTo(RowState.Enabled)
        assertThat(state(Settings.STRETCH_VIDEO) { hasTouchscreen = false })
            .isEqualTo(RowState.Enabled)
    }

    @Test
    fun `every row is enabled on a device that has everything`() {
        val snapshot = defaults.on(Settings.SHOW_ONSCREEN_CONTROLS).on(Settings.VIBRATE_FALLBACK)
        val facts = deviceFacts()

        val states = Settings.all.map { options.rowState(it, facts, snapshot) }

        assertThat(states.toSet()).containsExactly(RowState.Enabled)
        assertThat(options.onScreenControlsGroupState(facts)).isEqualTo(RowState.Enabled)
        assertThat(options.resetLayoutState(facts)).isEqualTo(RowState.Enabled)
    }

    // On-screen controls

    @Test
    fun `the on-screen controls group is hidden without a touchscreen`() {
        val facts = deviceFacts { hasTouchscreen = false }

        assertThat(options.onScreenControlsGroupState(facts)).isEqualTo(RowState.Hidden)
        assertThat(options.resetLayoutState(facts)).isEqualTo(RowState.Hidden)
    }

    @Test
    fun `every on-screen controls row is hidden without a touchscreen`() {
        val rows =
            listOf(
                Settings.SHOW_ONSCREEN_CONTROLS,
                Settings.VIBRATE_OSC,
                Settings.ONLY_L3_R3,
                Settings.SHOW_GUIDE_BUTTON,
                Settings.OSC_OPACITY,
            )
        val shown = defaults.on(Settings.SHOW_ONSCREEN_CONTROLS)

        for (row in rows) {
            assertThat(state(row, shown) { hasTouchscreen = false }).isEqualTo(RowState.Hidden)
        }
    }

    @Test
    fun `the on-screen controls sub-rows are disabled while Show is off`() {
        val rows =
            listOf(
                Settings.VIBRATE_OSC,
                Settings.ONLY_L3_R3,
                Settings.SHOW_GUIDE_BUTTON,
                Settings.OSC_OPACITY,
            )

        for (row in rows) {
            assertThat(state(row)).isEqualTo(dependsOn(Settings.SHOW_ONSCREEN_CONTROLS))
            assertThat(state(row, defaults.on(Settings.SHOW_ONSCREEN_CONTROLS)))
                .isEqualTo(RowState.Enabled)
        }
        assertThat(state(Settings.SHOW_ONSCREEN_CONTROLS)).isEqualTo(RowState.Enabled)
    }

    @Test
    fun `the on-screen vibration row is hidden without a vibrator`() {
        val shown = defaults.on(Settings.SHOW_ONSCREEN_CONTROLS)

        assertThat(state(Settings.VIBRATE_OSC, shown) { hasVibrator = false })
            .isEqualTo(RowState.Hidden)
        assertThat(state(Settings.ONLY_L3_R3, shown) { hasVibrator = false })
            .isEqualTo(RowState.Enabled)
    }

    // USB

    @Test
    fun `the USB rows are hidden without USB host`() {
        assertThat(state(Settings.USB_DRIVER) { hasUsbHost = false }).isEqualTo(RowState.Hidden)
        assertThat(state(Settings.USB_BIND_ALL) { hasUsbHost = false }).isEqualTo(RowState.Hidden)
        assertThat(
                state(Settings.USB_BIND_ALL, defaults.off(Settings.USB_DRIVER)) {
                    hasUsbHost = false
                }
            )
            .isEqualTo(RowState.Hidden)
    }

    @Test
    fun `all controllers is disabled while the USB driver is off`() {
        assertThat(state(Settings.USB_BIND_ALL, defaults.off(Settings.USB_DRIVER)))
            .isEqualTo(dependsOn(Settings.USB_DRIVER))
        assertThat(state(Settings.USB_BIND_ALL)).isEqualTo(RowState.Enabled)
        assertThat(state(Settings.USB_DRIVER, defaults.off(Settings.USB_DRIVER)))
            .isEqualTo(RowState.Enabled)
    }

    // Picture-in-picture

    @Test
    fun `picture-in-picture is hidden without the feature`() {
        assertThat(state(Settings.ENABLE_PIP) { supportsPictureInPicture = false })
            .isEqualTo(RowState.Hidden)
    }

    @Test
    fun `picture-in-picture is hidden on Fire OS`() {
        assertThat(state(Settings.ENABLE_PIP) { isFireOs = true }).isEqualTo(RowState.Hidden)
    }

    // Vibration

    @Test
    fun `the vibration rows are hidden without a vibrator`() {
        assertThat(state(Settings.VIBRATE_FALLBACK) { hasVibrator = false })
            .isEqualTo(RowState.Hidden)
        assertThat(state(Settings.VIBRATE_FALLBACK_STRENGTH) { hasVibrator = false })
            .isEqualTo(RowState.Hidden)
    }

    @Test
    fun `the vibration strength is hidden without amplitude control`() {
        assertThat(state(Settings.VIBRATE_FALLBACK_STRENGTH) { hasAmplitudeControl = false })
            .isEqualTo(RowState.Hidden)
        assertThat(state(Settings.VIBRATE_FALLBACK) { hasAmplitudeControl = false })
            .isEqualTo(RowState.Enabled)
    }

    @Test
    fun `the vibration strength is disabled while the fallback is off`() {
        assertThat(state(Settings.VIBRATE_FALLBACK_STRENGTH))
            .isEqualTo(dependsOn(Settings.VIBRATE_FALLBACK))
        assertThat(
                state(Settings.VIBRATE_FALLBACK_STRENGTH, defaults.on(Settings.VIBRATE_FALLBACK))
            )
            .isEqualTo(RowState.Enabled)
    }

    // HDR

    @Test
    fun `HDR is hidden without an HDR10 display`() {
        assertThat(state(Settings.ENABLE_HDR) { supportsHdr10 = false }).isEqualTo(RowState.Hidden)
    }

    @Test
    fun `HDR is disabled on a SHIELD firmware that crashes with it`() {
        assertThat(state(Settings.ENABLE_HDR) { hasBrokenHdrFirmware = true })
            .isEqualTo(RowState.Disabled(DisabledReason.HDR_BROKEN_ON_FIRMWARE))
    }

    @Test
    fun `HDR stays hidden on a broken firmware without an HDR10 display`() {
        assertThat(
                state(Settings.ENABLE_HDR) {
                    supportsHdr10 = false
                    hasBrokenHdrFirmware = true
                }
            )
            .isEqualTo(RowState.Hidden)
    }

    // Motion

    @Test
    fun `motion sensors are hidden below Android 12`() {
        assertThat(state(Settings.GAMEPAD_MOTION_SENSORS) { sdkInt = 30 })
            .isEqualTo(RowState.Hidden)
        assertThat(state(Settings.GAMEPAD_MOTION_SENSORS) { sdkInt = 31 })
            .isEqualTo(RowState.Enabled)
    }

    @Test
    fun `the motion fallback is hidden without an accelerometer and without a gyroscope`() {
        assertThat(
                state(Settings.GAMEPAD_MOTION_FALLBACK) {
                    hasAccelerometer = false
                    hasGyroscope = false
                }
            )
            .isEqualTo(RowState.Hidden)
    }

    @Test
    fun `the motion fallback is shown when one of the two sensors exists`() {
        assertThat(state(Settings.GAMEPAD_MOTION_FALLBACK) { hasAccelerometer = false })
            .isEqualTo(RowState.Enabled)
        assertThat(state(Settings.GAMEPAD_MOTION_FALLBACK) { hasGyroscope = false })
            .isEqualTo(RowState.Enabled)
    }

    // Mouse

    @Test
    fun `remote desktop mouse is hidden on a SHIELD`() {
        assertThat(state(Settings.ABSOLUTE_MOUSE_MODE) { isNvidiaShield = true })
            .isEqualTo(RowState.Hidden)
        assertThat(state(Settings.ABSOLUTE_MOUSE_MODE)).isEqualTo(RowState.Enabled)
    }

    @Test
    fun `analog scrolling is disabled while Gamepad mouse is off`() {
        assertThat(state(Settings.ANALOG_SCROLLING, defaults.off(Settings.MOUSE_EMULATION)))
            .isEqualTo(dependsOn(Settings.MOUSE_EMULATION))
        assertThat(state(Settings.ANALOG_SCROLLING)).isEqualTo(RowState.Enabled)
        assertThat(state(Settings.MOUSE_EMULATION, defaults.off(Settings.MOUSE_EMULATION)))
            .isEqualTo(RowState.Enabled)
    }

    @Test
    fun `gamepad mouse speed is disabled while Gamepad mouse is off`() {
        assertThat(state(Settings.GAMEPAD_MOUSE_SPEED, defaults.off(Settings.MOUSE_EMULATION)))
            .isEqualTo(dependsOn(Settings.MOUSE_EMULATION))
        assertThat(state(Settings.GAMEPAD_MOUSE_SPEED)).isEqualTo(RowState.Enabled)
    }

    // Glass

    private fun SettingsSnapshot.transparency(id: String) = with(Settings.TRANSPARENCY, id)

    private val glassRows = listOf(Settings.GLASS_TINT, Settings.GLASS_BLUR)

    @Test
    fun `the glass rows are enabled with Standard transparency and no high contrast`() {
        for (row in glassRows) {
            assertThat(state(row)).isEqualTo(RowState.Enabled)
            assertThat(state(row, defaults.transparency("standard"))).isEqualTo(RowState.Enabled)
        }
    }

    @Test
    fun `the glass rows are disabled while Transparency is Reduced`() {
        for (row in glassRows) {
            assertThat(state(row, defaults.transparency("reduced")))
                .isEqualTo(RowState.Disabled(DisabledReason.TRANSPARENCY_REDUCED))
        }
    }

    @Test
    fun `the glass rows are disabled while Transparency is Off`() {
        for (row in glassRows) {
            assertThat(state(row, defaults.transparency("off")))
                .isEqualTo(RowState.Disabled(DisabledReason.TRANSPARENCY_OFF))
        }
    }

    @Test
    fun `the glass rows are disabled while High contrast is on`() {
        for (row in glassRows) {
            assertThat(state(row, defaults.on(Settings.HIGH_CONTRAST)))
                .isEqualTo(RowState.Disabled(DisabledReason.HIGH_CONTRAST_ON))
        }
    }

    @Test
    fun `High contrast is the reason that is given when Transparency is also lowered`() {
        val both = defaults.on(Settings.HIGH_CONTRAST).transparency("off")

        for (row in glassRows) {
            assertThat(state(row, both))
                .isEqualTo(RowState.Disabled(DisabledReason.HIGH_CONTRAST_ON))
        }
    }

    @Test
    fun `an unknown transparency leaves the glass rows enabled, as Standard`() {
        for (row in glassRows) {
            assertThat(state(row, defaults.transparency("bogus"))).isEqualTo(RowState.Enabled)
        }
    }

    @Test
    fun `High contrast disables Transparency and Thicker focus outline`() {
        val contrast = defaults.on(Settings.HIGH_CONTRAST)

        for (row in listOf(Settings.TRANSPARENCY, Settings.THICK_FOCUS)) {
            assertThat(state(row, contrast))
                .isEqualTo(RowState.Disabled(DisabledReason.HIGH_CONTRAST_ON))
            assertThat(state(row, defaults)).isEqualTo(RowState.Enabled)
        }
    }

    @Test
    fun `the other Accessibility rows are always enabled`() {
        val lowered = defaults.on(Settings.HIGH_CONTRAST).transparency("off")
        val rows =
            listOf(
                Settings.REDUCE_MOTION,
                Settings.TEXT_SIZE,
                Settings.HIGH_CONTRAST,
                Settings.COLOUR_VISION,
                Settings.BACKGROUND,
                Settings.FONT,
                Settings.ACCENT,
                Settings.BACKGROUND_INTENSITY,
                Settings.STREAM_BACKGROUND,
                Settings.STREAM_MENU_GLASS,
                Settings.CARD_DENSITY,
                Settings.TEXT_SPACING,
            )

        for (row in rows) {
            assertThat(state(row, lowered)).isEqualTo(RowState.Enabled)
        }
    }

    @Test
    fun `the answers follow the facts that are passed in, with no memory of the last ones`() {
        val withoutUsb = deviceFacts { hasUsbHost = false }

        assertThat(options.rowState(Settings.USB_DRIVER, withoutUsb, defaults))
            .isEqualTo(RowState.Hidden)
        assertThat(options.rowState(Settings.USB_DRIVER, deviceFacts(), defaults))
            .isEqualTo(RowState.Enabled)
    }
}
