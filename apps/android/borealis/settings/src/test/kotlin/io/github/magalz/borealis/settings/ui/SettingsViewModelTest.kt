package io.github.magalz.borealis.settings.ui

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.device.OscLayoutReset
import io.github.magalz.borealis.settings.options.DisabledReason
import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.ResolutionChoice
import io.github.magalz.borealis.settings.options.ResolutionKind
import io.github.magalz.borealis.settings.options.RowState
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.testing.FakeDeviceFactsSource
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository.Write
import io.github.magalz.borealis.settings.testing.MainDispatcherRule
import io.github.magalz.borealis.settings.testing.deviceFacts
import io.github.magalz.borealis.settings.testing.subscribe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private var repository = FakeSettingsRepository()
    private val options = fakeOptions()
    private val facts = FakeDeviceFactsSource()
    private var resets = 0

    private fun TestScope.viewModel(): SettingsViewModel =
        SettingsViewModel(
                repository,
                options,
                facts,
                OscLayoutReset { resets++ },
                UnconfinedTestDispatcher(testScheduler),
            )
            .also {
                subscribe(it.uiState)
                advanceUntilIdle()
            }

    /** Starts from a store that holds [values]; writing them is not a write of the test. */
    private fun stored(vararg values: Pair<Setting<*>, Any?>) {
        repository = FakeSettingsRepository(SettingsSnapshot.fromStored(values.toMap()))
    }

    private fun SettingsUiState.rows(group: SettingsGroupId): List<SettingsItem> =
        groups.single { it.group == group }.items.map { it.item }

    @Test
    fun `the groups are those of UC-009 in its order`() = runTest {
        val state = viewModel().uiState.value

        assertThat(state.groups.map { it.group })
            .containsExactlyElementsIn(UC_009_ROWS.keys)
            .inOrder()
    }

    @Test
    fun `the rows of every group are those of UC-009 in its order`() = runTest {
        val state = viewModel().uiState.value

        for ((group, rows) in UC_009_ROWS) {
            assertThat(state.rows(group)).containsExactlyElementsIn(rows).inOrder()
        }
    }

    @Test
    fun `a row that the device cannot use is not in the state`() = runTest {
        options.rowStates[Settings.ENABLE_HDR] = RowState.Hidden
        options.rowStates[Settings.ABSOLUTE_MOUSE_MODE] = RowState.Hidden

        val state = viewModel().uiState.value

        assertThat(state.rows(SettingsGroupId.VIDEO)).doesNotContain(SettingsItem.HDR)
        assertThat(state.rows(SettingsGroupId.INPUT)).doesNotContain(SettingsItem.ABSOLUTE_MOUSE)
        assertThat(state.rows(SettingsGroupId.INPUT)).contains(SettingsItem.TOUCH)
    }

    @Test
    fun `a hidden On-screen controls group takes all its rows with it`() = runTest {
        options.onScreenControlsGroup = RowState.Hidden

        val state = viewModel().uiState.value

        assertThat(state.groups.map { it.group }).doesNotContain(SettingsGroupId.ON_SCREEN_CONTROLS)
        assertThat(state.groups.map { it.group }).contains(SettingsGroupId.AUDIO)
        assertThat(state.groups.flatMap { it.items }.map { it.item })
            .containsNoneOf(
                SettingsItem.SHOW_ON_SCREEN_CONTROLS,
                SettingsItem.OSC_OPACITY,
                SettingsItem.RESET_LAYOUT,
            )
    }

    @Test
    fun `a hidden Reset layout leaves the other rows of its group`() = runTest {
        options.resetLayout = RowState.Hidden

        val state = viewModel().uiState.value

        assertThat(state.rows(SettingsGroupId.ON_SCREEN_CONTROLS))
            .doesNotContain(SettingsItem.RESET_LAYOUT)
        assertThat(state.rows(SettingsGroupId.ON_SCREEN_CONTROLS))
            .contains(SettingsItem.OSC_OPACITY)
    }

    @Test
    fun `a disabled row is in the state with its reason`() = runTest {
        val disabled =
            RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, Settings.MOUSE_EMULATION)
        options.rowStates[Settings.ANALOG_SCROLLING] = disabled
        options.rowStates[Settings.ENABLE_HDR] =
            RowState.Disabled(DisabledReason.HDR_BROKEN_ON_FIRMWARE)

        val state = viewModel().uiState.value
        val rows = state.groups.flatMap { it.items }.associateBy { it.item }

        assertThat(rows.getValue(SettingsItem.ANALOG_SCROLLING).disabled).isEqualTo(disabled)
        assertThat(rows.getValue(SettingsItem.HDR).disabled)
            .isEqualTo(RowState.Disabled(DisabledReason.HDR_BROKEN_ON_FIRMWARE))
        assertThat(rows.getValue(SettingsItem.TOUCH).disabled).isNull()
    }

    @Test
    fun `a switch is stored through the repository`() = runTest {
        val viewModel = viewModel()

        viewModel.onSwitchChanged(SettingsItem.STRETCH_VIDEO, true)
        advanceUntilIdle()

        assertThat(repository.writes).containsExactly(Write(Settings.STRETCH_VIDEO, true))
        assertThat(viewModel.uiState.value.snapshot[Settings.STRETCH_VIDEO]).isTrue()
    }

    @Test
    fun `every switch row stores its own setting`() = runTest {
        val viewModel = viewModel()
        val switches = SettingsItem.entries.filter { it.kind == SettingsItemKind.SWITCH }

        switches.forEach { viewModel.onSwitchChanged(it, true) }
        advanceUntilIdle()

        assertThat(repository.writes.map { it.setting })
            .containsExactlyElementsIn(switches.map { it.setting })
            .inOrder()
        assertThat(repository.writes.map { it.value }.toSet()).containsExactly(true)
    }

    @Test
    fun `the lists store the key of the chosen entry`() = runTest {
        val viewModel = viewModel()

        viewModel.onChoiceSelected(SettingsItem.FRAME_PACING, "smoothness")
        viewModel.onChoiceSelected(SettingsItem.CODEC, "forceav1")
        viewModel.onChoiceSelected(SettingsItem.ANALOG_SCROLLING, "left")
        viewModel.onChoiceSelected(SettingsItem.AUDIO_CHANNELS, "51")
        viewModel.onChoiceSelected(SettingsItem.PERF_OVERLAY, Settings.PERF_OVERLAY_COMPACT)
        advanceUntilIdle()

        assertThat(repository.writes)
            .containsExactly(
                Write(Settings.FRAME_PACING, "smoothness"),
                Write(Settings.VIDEO_FORMAT, "forceav1"),
                Write(Settings.ANALOG_SCROLLING, "left"),
                Write(Settings.AUDIO_CONFIG, "51"),
                Write(Settings.PERF_OVERLAY, Settings.PERF_OVERLAY_COMPACT),
            )
            .inOrder()
    }

    @Test
    fun `Touch stores the trackpad switch`() = runTest {
        val viewModel = viewModel()

        viewModel.onChoiceSelected(SettingsItem.TOUCH, FixedChoices.TOUCH_DIRECT)
        advanceUntilIdle()

        assertThat(repository.writes).containsExactly(Write(Settings.TOUCHSCREEN_TRACKPAD, false))
        assertThat(SettingsItem.TOUCH.selectedChoiceKey(viewModel.uiState.value.snapshot))
            .isEqualTo(FixedChoices.TOUCH_DIRECT)
    }

    @Test
    fun `a list entry that the list does not offer is not stored`() = runTest {
        val viewModel = viewModel()

        viewModel.onChoiceSelected(SettingsItem.FRAME_PACING, "bogus")
        viewModel.onChoiceSelected(SettingsItem.RESOLUTION, "1x1")
        viewModel.onChoiceSelected(SettingsItem.FRAME_RATE, "240")
        viewModel.onChoiceSelected(SettingsItem.FRAME_PACING, "balanced")
        advanceUntilIdle()

        assertThat(repository.writes).containsExactly(Write(Settings.FRAME_PACING, "balanced"))
    }

    @Test
    fun `the Background list stores the stable id of the chosen style`() = runTest {
        val viewModel = viewModel()

        viewModel.onChoiceSelected(SettingsItem.BACKGROUND, "starfield")
        advanceUntilIdle()

        assertThat(repository.writes).containsExactly(Write(Settings.BACKGROUND, "starfield"))
        assertThat(viewModel.uiState.value.snapshot[Settings.BACKGROUND]).isEqualTo("starfield")
        assertThat(SettingsItem.BACKGROUND.selectedChoiceKey(viewModel.uiState.value.snapshot))
            .isEqualTo("starfield")
    }

    @Test
    fun `each of the seven styles of the Background list can be chosen`() = runTest {
        val viewModel = viewModel()
        val ids =
            listOf(
                "aurora_curtains",
                "soft_glow",
                "northern_lights",
                "light_mist",
                "flowing_light",
                "starfield",
                "off",
            )

        for (id in ids) viewModel.onChoiceSelected(SettingsItem.BACKGROUND, id)
        advanceUntilIdle()

        assertThat(repository.writes.map { it.value }).containsExactlyElementsIn(ids).inOrder()
    }

    @Test
    fun `a style that the Background list does not offer is not stored`() = runTest {
        val viewModel = viewModel()

        viewModel.onChoiceSelected(SettingsItem.BACKGROUND, "bogus")
        viewModel.onChoiceSelected(SettingsItem.BACKGROUND, "Starfield")
        viewModel.onChoiceSelected(SettingsItem.BACKGROUND, "5")
        advanceUntilIdle()

        assertThat(repository.writes).isEmpty()
    }

    @Test
    fun `an unknown stored background shows the default style as the chosen one`() = runTest {
        stored(Settings.BACKGROUND to "bogus")
        val viewModel = viewModel()

        assertThat(SettingsItem.BACKGROUND.selectedChoiceKey(viewModel.uiState.value.snapshot))
            .isEqualTo("aurora_curtains")
    }

    private fun rowOf(setting: Setting<*>) = SettingsItem.entries.single { it.setting == setting }

    @Test
    fun `the stream menu style stores the stable id of the chosen entry`() = runTest {
        val viewModel = viewModel()

        viewModel.onChoiceSelected(rowOf(Settings.STREAM_MENU_GLASS), "solid")
        viewModel.onChoiceSelected(rowOf(Settings.STREAM_MENU_GLASS), "blur")
        advanceUntilIdle()

        assertThat(repository.writes)
            .containsExactly(
                Write(Settings.STREAM_MENU_GLASS, "solid"),
                Write(Settings.STREAM_MENU_GLASS, "blur"),
            )
            .inOrder()
    }

    @Test
    fun `an entry that the stream menu style does not offer is not stored`() = runTest {
        val viewModel = viewModel()

        viewModel.onChoiceSelected(rowOf(Settings.STREAM_MENU_GLASS), "tint")
        viewModel.onChoiceSelected(rowOf(Settings.STREAM_MENU_GLASS), "Blur")
        viewModel.onChoiceSelected(rowOf(Settings.STREAM_MENU_GLASS), "Blurred")
        viewModel.onChoiceSelected(rowOf(Settings.STREAM_MENU_GLASS), "still")
        advanceUntilIdle()

        assertThat(repository.writes).isEmpty()
    }

    @Test
    fun `each list of Sprint 11 stores the stable id of the chosen entry`() = runTest {
        val viewModel = viewModel()
        val ids =
            mapOf(
                SettingsItem.FONT to listOf("atkinson_next", "luciole", "lexend", "system"),
                SettingsItem.ACCENT to listOf("teal", "white"),
                SettingsItem.BACKGROUND_INTENSITY to listOf("75", "50", "25"),
                SettingsItem.CARD_DENSITY to listOf("compact", "dense"),
                SettingsItem.TEXT_SPACING to listOf("wide", "wider"),
            )

        for ((item, values) in ids) {
            for (id in values) {
                viewModel.onChoiceSelected(item, id)
                advanceUntilIdle()

                assertThat(repository.writes).contains(Write(item.setting!!, id))
                assertThat(item.selectedChoiceKey(viewModel.uiState.value.snapshot)).isEqualTo(id)
            }
        }
    }

    @Test
    fun `an entry that a list of Sprint 11 does not offer is not stored`() = runTest {
        val viewModel = viewModel()
        val items =
            listOf(
                SettingsItem.FONT,
                SettingsItem.ACCENT,
                SettingsItem.BACKGROUND_INTENSITY,
                SettingsItem.CARD_DENSITY,
                SettingsItem.TEXT_SPACING,
            )

        for (item in items) {
            viewModel.onChoiceSelected(item, "bogus")
            viewModel.onChoiceSelected(item, "")
        }
        viewModel.onChoiceSelected(SettingsItem.FONT, "Lexend")
        viewModel.onChoiceSelected(SettingsItem.BACKGROUND_INTENSITY, "60")
        advanceUntilIdle()

        assertThat(repository.writes).isEmpty()
    }

    @Test
    fun `an unknown stored id of a list of Sprint 11 shows the default as the chosen one`() =
        runTest {
            stored(
                Settings.FONT to "bogus",
                Settings.ACCENT to "bogus",
                Settings.BACKGROUND_INTENSITY to "60",
                Settings.CARD_DENSITY to "bogus",
                Settings.TEXT_SPACING to "bogus",
            )
            val snapshot = viewModel().uiState.value.snapshot

            assertThat(SettingsItem.FONT.selectedChoiceKey(snapshot)).isEqualTo("plex")
            assertThat(SettingsItem.ACCENT.selectedChoiceKey(snapshot)).isEqualTo("violet")
            assertThat(SettingsItem.BACKGROUND_INTENSITY.selectedChoiceKey(snapshot))
                .isEqualTo("100")
            assertThat(SettingsItem.CARD_DENSITY.selectedChoiceKey(snapshot))
                .isEqualTo("comfortable")
            assertThat(SettingsItem.TEXT_SPACING.selectedChoiceKey(snapshot)).isEqualTo("normal")
        }

    @Test
    fun `changing the resolution stores it and then returns the bitrate to automatic`() = runTest {
        stored(Settings.BITRATE_KBPS to 30_000)
        val viewModel = viewModel()

        viewModel.onChoiceSelected(SettingsItem.RESOLUTION, "1920x1080")
        advanceUntilIdle()

        assertThat(repository.writes)
            .containsExactly(
                Write(Settings.RESOLUTION, "1920x1080"),
                Write(Settings.BITRATE_KBPS, null),
            )
            .inOrder()
        assertThat(viewModel.uiState.value.bitrateAutomatic).isTrue()
    }

    @Test
    fun `changing the frame rate stores it and then returns the bitrate to automatic`() = runTest {
        stored(Settings.BITRATE_KBPS to 30_000)
        val viewModel = viewModel()

        viewModel.onChoiceSelected(SettingsItem.FRAME_RATE, "30")
        advanceUntilIdle()

        assertThat(repository.writes)
            .containsExactly(Write(Settings.FRAME_RATE, "30"), Write(Settings.BITRATE_KBPS, null))
            .inOrder()
    }

    @Test
    fun `the bitrate is automatic until one is stored, then it is the stored one`() = runTest {
        val viewModel = viewModel()

        // 1280x720 at 60 fps: 10 Mbps.
        assertThat(viewModel.uiState.value.bitrateAutomatic).isTrue()
        assertThat(viewModel.uiState.value.bitrateKbps).isEqualTo(10_000)

        viewModel.onSliderChanged(SettingsItem.BITRATE, 25_500)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.bitrateAutomatic).isFalse()
        assertThat(viewModel.uiState.value.bitrateKbps).isEqualTo(25_500)
    }

    @Test
    fun `a stored bitrate of zero is automatic`() = runTest {
        stored(Settings.BITRATE_KBPS to 0)

        val state = viewModel().uiState.value

        assertThat(state.bitrateAutomatic).isTrue()
        assertThat(state.bitrateKbps).isEqualTo(10_000)
    }

    @Test
    fun `the sliders store whole numbers in their stored unit`() = runTest {
        val viewModel = viewModel()

        viewModel.onSliderChanged(SettingsItem.BITRATE, 12_500)
        viewModel.onSliderChanged(SettingsItem.DEADZONE, 12)
        viewModel.onSliderChanged(SettingsItem.VIBRATION_STRENGTH, 150)
        viewModel.onSliderChanged(SettingsItem.OSC_OPACITY, 40)
        viewModel.onSliderChanged(SettingsItem.GAMEPAD_MOUSE_SPEED, 150)
        advanceUntilIdle()

        assertThat(repository.writes)
            .containsExactly(
                Write(Settings.BITRATE_KBPS, 12_500),
                Write(Settings.DEADZONE, 12),
                Write(Settings.VIBRATE_FALLBACK_STRENGTH, 150),
                Write(Settings.OSC_OPACITY, 40),
                Write(Settings.GAMEPAD_MOUSE_SPEED, 150),
            )
            .inOrder()
    }

    @Test
    fun `a slider value outside its range is kept in the range`() = runTest {
        val viewModel = viewModel()

        viewModel.onSliderChanged(SettingsItem.DEADZONE, 99)
        viewModel.onSliderChanged(SettingsItem.DEADZONE, -3)
        viewModel.onSliderChanged(SettingsItem.BITRATE, 200_000)
        viewModel.onSliderChanged(SettingsItem.GAMEPAD_MOUSE_SPEED, 999)
        viewModel.onSliderChanged(SettingsItem.GAMEPAD_MOUSE_SPEED, 3)
        advanceUntilIdle()

        assertThat(repository.writes)
            .containsExactly(
                Write(Settings.DEADZONE, 20),
                Write(Settings.DEADZONE, 0),
                Write(Settings.BITRATE_KBPS, 150_000),
                Write(Settings.GAMEPAD_MOUSE_SPEED, 200),
                Write(Settings.GAMEPAD_MOUSE_SPEED, 10),
            )
            .inOrder()
    }

    @Test
    fun `a disabled or hidden row cannot be changed`() = runTest {
        options.rowStates[Settings.ANALOG_SCROLLING] =
            RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, Settings.MOUSE_EMULATION)
        options.rowStates[Settings.VIBRATE_FALLBACK_STRENGTH] =
            RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, Settings.VIBRATE_FALLBACK)
        options.rowStates[Settings.ENABLE_HDR] = RowState.Hidden
        val viewModel = viewModel()

        viewModel.onChoiceSelected(SettingsItem.ANALOG_SCROLLING, "left")
        viewModel.onSliderChanged(SettingsItem.VIBRATION_STRENGTH, 50)
        viewModel.onSwitchChanged(SettingsItem.HDR, true)
        viewModel.onSwitchChanged(SettingsItem.FULL_RANGE, true)
        advanceUntilIdle()

        assertThat(repository.writes).containsExactly(Write(Settings.FULL_RANGE, true))
    }

    @Test
    fun `turning on Unlock all frame rates changes the frame rate list at once`() = runTest {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.frameRateChoices.map { it.fps })
            .containsExactly(30, 60)
            .inOrder()

        viewModel.onSwitchChanged(SettingsItem.UNLOCK_FRAME_RATES, true)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.frameRateChoices.map { it.fps })
            .containsExactly(30, 60, 90, 120)
            .inOrder()
    }

    @Test
    fun `the lists come from the options for the facts of the device`() = runTest {
        val native = ResolutionChoice("2400x1080", 2400, 1080, ResolutionKind.NATIVE)
        options.resolutions = options.resolutions + native
        options.frameRates = options.frameRates + FrameRateChoice(144, isNative = true)

        val state = viewModel().uiState.value

        assertThat(state.resolutionChoices).contains(native)
        assertThat(state.frameRateChoices).contains(FrameRateChoice(144, isNative = true))
    }

    @Test
    fun `a stored resolution that is not offered becomes the nearest lower one`() = runTest {
        stored(Settings.RESOLUTION to "3840x2160")
        options.resolutions = STANDARD_RESOLUTIONS.take(3)

        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.snapshot[Settings.RESOLUTION]).isEqualTo("2560x1440")
    }

    @Test
    fun `a stored frame rate that is not offered becomes the nearest lower one`() = runTest {
        stored(Settings.FRAME_RATE to "120")

        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.snapshot[Settings.FRAME_RATE]).isEqualTo("60")
    }

    @Test
    fun `replacing a stored choice also returns the bitrate to automatic`() = runTest {
        stored(Settings.FRAME_RATE to "120", Settings.BITRATE_KBPS to 50_000)

        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.snapshot[Settings.BITRATE_KBPS]).isNull()
        assertThat(viewModel.uiState.value.bitrateAutomatic).isTrue()
    }

    @Test
    fun `with nothing lower on offer the lowest offered choice is used`() = runTest {
        stored(Settings.FRAME_RATE to "24")
        options.frameRates = listOf(FrameRateChoice(30, false), FrameRateChoice(60, false))

        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.snapshot[Settings.FRAME_RATE]).isEqualTo("30")
    }

    @Test
    fun `stored choices that are offered are left alone`() = runTest {
        stored(
            Settings.RESOLUTION to "1920x1080",
            Settings.FRAME_RATE to "30",
            Settings.BITRATE_KBPS to 12_000,
        )

        val viewModel = viewModel()

        assertThat(repository.writes).isEmpty()
        assertThat(viewModel.uiState.value.snapshot[Settings.BITRATE_KBPS]).isEqualTo(12_000)
    }

    @Test
    fun `a stored value that is not a resolution is replaced by an offered one`() = runTest {
        stored(Settings.RESOLUTION to "wide")

        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.snapshot[Settings.RESOLUTION]).isEqualTo("1280x720")
    }

    @Test
    fun `asking again for the device facts applies the new display`() = runTest {
        stored(Settings.RESOLUTION to "2560x1440", Settings.BITRATE_KBPS to 40_000)
        val viewModel = viewModel()
        assertThat(repository.writes).isEmpty()
        val askedBefore = facts.calls

        facts.facts = deviceFacts { isTelevision = true }
        options.resolutions = STANDARD_RESOLUTIONS.take(2)
        viewModel.refreshDeviceFacts()
        advanceUntilIdle()

        assertThat(facts.calls).isEqualTo(askedBefore + 1)
        assertThat(viewModel.uiState.value.resolutionChoices)
            .isEqualTo(STANDARD_RESOLUTIONS.take(2))
        assertThat(viewModel.uiState.value.snapshot[Settings.RESOLUTION]).isEqualTo("1920x1080")
        assertThat(viewModel.uiState.value.snapshot[Settings.BITRATE_KBPS]).isNull()
    }

    @Test
    fun `Reset layout resets the layout once`() = runTest {
        val viewModel = viewModel()

        viewModel.onResetLayout()
        advanceUntilIdle()

        assertThat(resets).isEqualTo(1)
    }

    @Test
    fun `Reset layout does nothing when the device does not offer it`() = runTest {
        options.resetLayout = RowState.Hidden
        val viewModel = viewModel()

        viewModel.onResetLayout()
        advanceUntilIdle()

        assertThat(resets).isEqualTo(0)
    }

    @Test
    fun `Reset layout runs off the calling thread`() = runTest {
        val resetThread = arrayOfNulls<Thread>(1)
        val done = CountDownLatch(1)
        val viewModel =
            SettingsViewModel(
                repository,
                options,
                facts,
                OscLayoutReset {
                    resetThread[0] = Thread.currentThread()
                    done.countDown()
                },
            )

        viewModel.onResetLayout()
        runCurrent()

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(resetThread[0]).isNotSameInstanceAs(Thread.currentThread())
        assertThat(resetThread[0]?.name).startsWith("DefaultDispatcher")
    }

    @Test
    fun `turning off Unlock all frame rates steps 120 fps down and returns the bitrate to automatic`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onSwitchChanged(SettingsItem.UNLOCK_FRAME_RATES, true)
            advanceUntilIdle()
            viewModel.onChoiceSelected(SettingsItem.FRAME_RATE, "120")
            viewModel.onSliderChanged(SettingsItem.BITRATE, 30_000)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.snapshot[Settings.FRAME_RATE]).isEqualTo("120")
            assertThat(viewModel.uiState.value.snapshot[Settings.BITRATE_KBPS]).isEqualTo(30_000)

            viewModel.onSwitchChanged(SettingsItem.UNLOCK_FRAME_RATES, false)
            advanceUntilIdle()

            val snapshot = viewModel.uiState.value.snapshot
            assertThat(snapshot[Settings.UNLOCK_FPS]).isFalse()
            assertThat(snapshot[Settings.FRAME_RATE]).isEqualTo("60")
            assertThat(snapshot[Settings.BITRATE_KBPS]).isNull()
            assertThat(viewModel.uiState.value.bitrateAutomatic).isTrue()
        }

    @Test
    fun `turning off Unlock all frame rates leaves an offered frame rate and the bitrate alone`() =
        runTest {
            stored(Settings.UNLOCK_FPS to true, Settings.BITRATE_KBPS to 30_000)
            val viewModel = viewModel()

            viewModel.onSwitchChanged(SettingsItem.UNLOCK_FRAME_RATES, false)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.snapshot[Settings.FRAME_RATE]).isEqualTo("60")
            assertThat(viewModel.uiState.value.snapshot[Settings.BITRATE_KBPS]).isEqualTo(30_000)
        }
}

/**
 * The groups and rows of the table in `docs/ux/phase-2-proposal.md#settings-groups`, the Appearance
 * group that Sprint 9 added after Advanced, the glass rows that Sprint 10 added to it, the
 * Accessibility group after it, and the font, accent colour, background intensity, card density and
 * text spacing rows of Sprint 11.
 */
private val UC_009_ROWS =
    linkedMapOf(
        SettingsGroupId.STREAM to
            listOf(
                SettingsItem.RESOLUTION,
                SettingsItem.FRAME_RATE,
                SettingsItem.BITRATE,
                SettingsItem.FRAME_PACING,
            ),
        SettingsGroupId.VIDEO to
            listOf(
                SettingsItem.CODEC,
                SettingsItem.HDR,
                SettingsItem.FULL_RANGE,
                SettingsItem.PERF_OVERLAY,
                SettingsItem.STRETCH_VIDEO,
            ),
        SettingsGroupId.INPUT to
            listOf(
                SettingsItem.TOUCH,
                SettingsItem.MOUSE_NAV_BUTTONS,
                SettingsItem.ABSOLUTE_MOUSE,
                SettingsItem.GAMEPAD_MOUSE,
                SettingsItem.ANALOG_SCROLLING,
                SettingsItem.GAMEPAD_MOUSE_SPEED,
                SettingsItem.DEADZONE,
            ),
        SettingsGroupId.GAMEPAD to
            listOf(
                SettingsItem.MULTI_CONTROLLER,
                SettingsItem.USB_DRIVER,
                SettingsItem.USB_BIND_ALL,
                SettingsItem.FLIP_FACE_BUTTONS,
                SettingsItem.TOUCHPAD_AS_MOUSE,
                SettingsItem.MOTION_SENSORS,
                SettingsItem.MOTION_FALLBACK,
                SettingsItem.VIBRATE_FALLBACK,
                SettingsItem.VIBRATION_STRENGTH,
            ),
        SettingsGroupId.ON_SCREEN_CONTROLS to
            listOf(
                SettingsItem.SHOW_ON_SCREEN_CONTROLS,
                SettingsItem.OSC_VIBRATE,
                SettingsItem.ONLY_L3_R3,
                SettingsItem.GUIDE_BUTTON,
                SettingsItem.OSC_OPACITY,
                SettingsItem.RESET_LAYOUT,
            ),
        SettingsGroupId.AUDIO to
            listOf(
                SettingsItem.AUDIO_CHANNELS,
                SettingsItem.HOST_AUDIO,
                SettingsItem.AUDIO_EFFECTS,
            ),
        SettingsGroupId.ADVANCED to
            listOf(
                SettingsItem.OPTIMISE_GAME_SETTINGS,
                SettingsItem.UNLOCK_FRAME_RATES,
                SettingsItem.REDUCE_REFRESH_RATE,
                SettingsItem.DISABLE_WARNINGS,
                SettingsItem.LATENCY_SUMMARY,
                SettingsItem.PICTURE_IN_PICTURE,
            ),
        SettingsGroupId.APPEARANCE to
            listOf(
                SettingsItem.FONT,
                SettingsItem.ACCENT,
                SettingsItem.BACKGROUND,
                SettingsItem.BACKGROUND_INTENSITY,
                SettingsItem.STREAM_MENU_GLASS,
                SettingsItem.CARD_DENSITY,
                SettingsItem.GLASS_TINT,
                SettingsItem.GLASS_BLUR,
            ),
        SettingsGroupId.ACCESSIBILITY to
            listOf(
                SettingsItem.REDUCE_MOTION,
                SettingsItem.TRANSPARENCY,
                SettingsItem.THICK_FOCUS,
                SettingsItem.TEXT_SIZE,
                SettingsItem.TEXT_SPACING,
                SettingsItem.HIGH_CONTRAST,
                SettingsItem.COLOUR_VISION,
            ),
    )
