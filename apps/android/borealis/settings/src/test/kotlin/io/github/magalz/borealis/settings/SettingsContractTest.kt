package io.github.magalz.borealis.settings

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.options.DisabledReason
import io.github.magalz.borealis.settings.options.ResolutionChoice
import io.github.magalz.borealis.settings.options.ResolutionKind
import io.github.magalz.borealis.settings.options.RowState
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.testing.FakeDeviceFactsSource
import io.github.magalz.borealis.settings.testing.FakeSettingsOptions
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository
import io.github.magalz.borealis.settings.testing.deviceFacts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

/** The frozen settings contract: the catalog, the snapshot, and the fakes that tests build on. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsContractTest {
    @Test
    fun `the catalog holds the ported, logging, overlay, background, look and mouse speed settings`() {
        assertThat(Settings.all).hasSize(CATALOG_SIZE)
        assertThat(Settings.all.map { it.key }).containsNoDuplicates()
        assertThat(Settings.all).contains(Settings.RESOLUTION)
        assertThat(Settings.all).contains(Settings.PERF_OVERLAY)
        assertThat(Settings.all.filter { it.store == SettingStore.DATASTORE })
            .containsExactly(
                Settings.VERBOSE_LOGGING,
                Settings.MASK_ADDRESSES,
                Settings.BACKGROUND,
                Settings.GLASS_TINT,
                Settings.GLASS_BLUR,
                Settings.REDUCE_MOTION,
                Settings.TRANSPARENCY,
                Settings.THICK_FOCUS,
                Settings.TEXT_SIZE,
                Settings.HIGH_CONTRAST,
                Settings.COLOUR_VISION,
                Settings.FONT,
                Settings.ACCENT,
                Settings.BACKGROUND_INTENSITY,
                Settings.STREAM_BACKGROUND,
                Settings.STREAM_MENU_GLASS,
                Settings.CARD_DENSITY,
                Settings.TEXT_SPACING,
            )
    }

    @Test
    fun `a snapshot of an empty store reads the default of every setting`() {
        val snapshot = SettingsSnapshot.DEFAULTS

        assertThat(snapshot[Settings.RESOLUTION]).isEqualTo("1280x720")
        assertThat(snapshot[Settings.FRAME_RATE]).isEqualTo("60")
        assertThat(snapshot[Settings.DEADZONE]).isEqualTo(7)
        assertThat(snapshot[Settings.USB_DRIVER]).isTrue()
        assertThat(snapshot[Settings.ENABLE_HDR]).isFalse()
        assertThat(snapshot[Settings.PERF_OVERLAY]).isEqualTo("off")
        assertThat(snapshot[Settings.BACKGROUND]).isEqualTo("aurora_curtains")
        for (setting in Settings.all) {
            assertThat(snapshot[setting]).isEqualTo(setting.default)
        }
    }

    @Test
    fun `a snapshot reads the value that was stored`() {
        val snapshot =
            SettingsSnapshot.DEFAULTS.with(Settings.RESOLUTION, "1920x1080")
                .with(Settings.DEADZONE, 12)
                .with(Settings.USB_DRIVER, false)

        assertThat(snapshot[Settings.RESOLUTION]).isEqualTo("1920x1080")
        assertThat(snapshot[Settings.DEADZONE]).isEqualTo(12)
        assertThat(snapshot[Settings.USB_DRIVER]).isFalse()
        assertThat(snapshot[Settings.FRAME_RATE]).isEqualTo("60")
    }

    @Test
    fun `a snapshot does not change when a copy is made`() {
        val original = SettingsSnapshot.DEFAULTS

        original.with(Settings.DEADZONE, 12)

        assertThat(original[Settings.DEADZONE]).isEqualTo(7)
    }

    @Test
    fun `a stored default is the same as an absent key`() {
        val stored = SettingsSnapshot.DEFAULTS.with(Settings.DEADZONE, 7)

        assertThat(stored).isEqualTo(SettingsSnapshot.DEFAULTS)
    }

    @Test
    fun `without makes keys read as their defaults again`() {
        val snapshot =
            SettingsSnapshot.DEFAULTS.with(Settings.DEADZONE, 12)
                .with(Settings.FLIP_FACE_BUTTONS, true)

        val cleared = snapshot.without(Settings.DEADZONE)

        assertThat(cleared[Settings.DEADZONE]).isEqualTo(7)
        assertThat(cleared[Settings.FLIP_FACE_BUTTONS]).isTrue()
    }

    @Test
    fun `an absent bitrate reads as automatic`() {
        val snapshot = SettingsSnapshot.DEFAULTS

        assertThat(snapshot[Settings.BITRATE_KBPS]).isNull()
        assertThat(snapshot.isBitrateAutomatic).isTrue()
    }

    @Test
    fun `a stored bitrate is not automatic and a null returns it to automatic`() {
        val manual = SettingsSnapshot.DEFAULTS.with(Settings.BITRATE_KBPS, 25_000)

        assertThat(manual[Settings.BITRATE_KBPS]).isEqualTo(25_000)
        assertThat(manual.isBitrateAutomatic).isFalse()
        assertThat(manual.effectiveBitrateKbps()).isEqualTo(25_000)
        assertThat(manual.with(Settings.BITRATE_KBPS, null).isBitrateAutomatic).isTrue()
    }

    @Test
    fun `a stored bitrate of 0 is automatic, as the inherited code reads it`() {
        val zero = SettingsSnapshot.DEFAULTS.with(Settings.BITRATE_KBPS, 0)

        assertThat(zero.isBitrateAutomatic).isTrue()
        assertThat(zero.effectiveBitrateKbps()).isEqualTo(10_000)
    }

    @Test
    fun `the effective bitrate of an automatic bitrate follows the resolution and the frame rate`() {
        val defaults = SettingsSnapshot.DEFAULTS
        val fullHd120 =
            defaults.with(Settings.RESOLUTION, "1920x1080").with(Settings.FRAME_RATE, "120")

        assertThat(defaults.effectiveBitrateKbps()).isEqualTo(10_000)
        assertThat(fullHd120.effectiveBitrateKbps())
            .isEqualTo(DefaultBitrate.kbps("1920x1080", "120"))
        assertThat(fullHd120.effectiveBitrateKbps()).isEqualTo(28_000)
    }

    @Test
    fun `fromStored builds a snapshot and refuses a value of the wrong type`() {
        val snapshot =
            SettingsSnapshot.fromStored(
                mapOf(
                    Settings.DEADZONE to 3,
                    Settings.BITRATE_KBPS to null,
                    Settings.FULL_RANGE to true,
                )
            )

        assertThat(snapshot[Settings.DEADZONE]).isEqualTo(3)
        assertThat(snapshot[Settings.FULL_RANGE]).isTrue()
        assertThat(snapshot.isBitrateAutomatic).isTrue()
        assertThrows(IllegalArgumentException::class.java) {
            SettingsSnapshot.fromStored(mapOf(Settings.DEADZONE to "3"))
        }
    }

    @Test
    fun `fromStored skips a null value, also for a setting that has a default`() {
        val snapshot =
            SettingsSnapshot.fromStored(
                mapOf(Settings.RESOLUTION to null, Settings.STRETCH_VIDEO to null)
            )

        assertThat(snapshot).isEqualTo(SettingsSnapshot.DEFAULTS)
        assertThat(snapshot[Settings.RESOLUTION]).isEqualTo(Settings.RESOLUTION.default)
    }

    @Test
    fun `the fake repository applies set, setNow and clear to the snapshot`() = runTest {
        val repository = FakeSettingsRepository()

        repository.set(Settings.RESOLUTION, "1920x1080")
        repository.setNow(Settings.DEADZONE, 9)
        repository.set(Settings.BITRATE_KBPS, 30_000)

        assertThat(repository.snapshot.value[Settings.RESOLUTION]).isEqualTo("1920x1080")
        assertThat(repository.snapshot.value[Settings.DEADZONE]).isEqualTo(9)
        assertThat(repository.snapshot.value.isBitrateAutomatic).isFalse()

        repository.clear(Settings.BITRATE_KBPS, Settings.DEADZONE)

        assertThat(repository.snapshot.value.isBitrateAutomatic).isTrue()
        assertThat(repository.snapshot.value[Settings.DEADZONE]).isEqualTo(7)
        assertThat(repository.snapshot.value[Settings.RESOLUTION]).isEqualTo("1920x1080")
    }

    @Test
    fun `the fake repository does not clear the bitrate when the resolution changes`() = runTest {
        val repository = FakeSettingsRepository()
        repository.set(Settings.BITRATE_KBPS, 30_000)

        repository.set(Settings.RESOLUTION, "1920x1080")

        assertThat(repository.snapshot.value[Settings.BITRATE_KBPS]).isEqualTo(30_000)
    }

    @Test
    fun `the fake repository records every write in order`() = runTest {
        val repository = FakeSettingsRepository()

        repository.set(Settings.DEADZONE, 9)
        repository.setNow(Settings.FULL_RANGE, true)
        repository.clear(Settings.DEADZONE)

        assertThat(repository.writes)
            .containsExactly(
                FakeSettingsRepository.Write(Settings.DEADZONE, 9),
                FakeSettingsRepository.Write(Settings.FULL_RANGE, true),
                FakeSettingsRepository.Write(Settings.DEADZONE, null),
            )
            .inOrder()
    }

    @Test
    fun `the fake repository starts from the snapshot it is given`() {
        val repository =
            FakeSettingsRepository(SettingsSnapshot.DEFAULTS.with(Settings.DEADZONE, 15))

        assertThat(repository.snapshot.value[Settings.DEADZONE]).isEqualTo(15)
    }

    @Test
    fun `observe emits the current value, then each change, and skips repeats`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakeSettingsRepository()
            val seen = mutableListOf<Int>()
            val job = backgroundScope.launch { repository.observe(Settings.DEADZONE).toList(seen) }

            repository.set(Settings.DEADZONE, 9)
            repository.set(Settings.FULL_RANGE, true)
            repository.set(Settings.DEADZONE, 9)
            repository.set(Settings.DEADZONE, 11)

            assertThat(seen).containsExactly(7, 9, 11).inOrder()
            job.cancel()
        }

    @Test
    fun `the fake device facts source returns the facts it holds and counts the calls`() {
        val source = FakeDeviceFactsSource()
        assertThat(source.facts()).isEqualTo(deviceFacts())

        val tv = deviceFacts { isTelevision = true }
        source.facts = tv

        assertThat(source.facts()).isEqualTo(tv)
        assertThat(source.calls).isEqualTo(2)
    }

    @Test
    fun `the facts builder starts as a phone that has everything and changes only what is set`() {
        val facts = deviceFacts { hasGyroscope = false }

        assertThat(facts.hasGyroscope).isFalse()
        assertThat(facts.hasTouchscreen).isTrue()
        assertThat(facts.supportsHdr10).isTrue()
        assertThat(facts.sdkInt).isEqualTo(35)
    }

    @Test
    fun `the fake options enable every row until a test sets a state`() {
        val options = FakeSettingsOptions()
        val facts = deviceFacts()
        val snapshot = SettingsSnapshot.DEFAULTS
        val disabled =
            RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, Settings.MOUSE_EMULATION)

        assertThat(options.rowState(Settings.ENABLE_HDR, facts, snapshot))
            .isEqualTo(RowState.Enabled)
        options.rowStates[Settings.ANALOG_SCROLLING] = disabled
        options.rowStates[Settings.ENABLE_HDR] = RowState.Hidden

        assertThat(options.rowState(Settings.ANALOG_SCROLLING, facts, snapshot)).isEqualTo(disabled)
        assertThat(options.rowState(Settings.ENABLE_HDR, facts, snapshot))
            .isEqualTo(RowState.Hidden)
        assertThat(options.rowState(Settings.DEADZONE, facts, snapshot)).isEqualTo(RowState.Enabled)
    }

    @Test
    fun `the fake options answer the lists and the group states that the test set`() {
        val options = FakeSettingsOptions()
        val facts = deviceFacts()

        assertThat(options.resolutionChoices(facts).map { it.key }).containsExactly("1280x720")
        assertThat(options.frameRateChoices(facts, unlockAllFrameRates = false).map { it.fps })
            .containsExactly(30, 60)
            .inOrder()
        assertThat(options.frameRateChoices(facts, unlockAllFrameRates = true).map { it.fps })
            .containsExactly(30, 60, 90, 120)
            .inOrder()
        assertThat(options.onScreenControlsGroupState(facts)).isEqualTo(RowState.Enabled)

        options.onScreenControlsGroup = RowState.Hidden
        options.resetLayout = RowState.Hidden

        assertThat(options.onScreenControlsGroupState(facts)).isEqualTo(RowState.Hidden)
        assertThat(options.resetLayoutState(facts)).isEqualTo(RowState.Hidden)
    }

    @Test
    fun `only a standard resolution choice is not native`() {
        val standard = ResolutionChoice("1920x1080", 1920, 1080, ResolutionKind.STANDARD)
        val native = ResolutionChoice("2400x1080", 2400, 1080, ResolutionKind.NATIVE)
        val fullscreen = ResolutionChoice("2520x1080", 2520, 1080, ResolutionKind.NATIVE_FULLSCREEN)

        assertThat(standard.isNative).isFalse()
        assertThat(native.isNative).isTrue()
        assertThat(fullscreen.isNative).isTrue()
        assertThat(standard.orientation).isNull()
    }

    private companion object {
        /**
         * 38 ported stream settings, the gamepad mouse speed, 2 logging switches, the overlay
         * choice, the background, the 8 settings of the glass and the accessibility group, and the
         * 5 of Sprint 11: font, accent colour, background intensity, card density and text spacing,
         * and the 2 of Sprint 15: the background and the menu glass in the stream.
         */
        const val CATALOG_SIZE = 58
    }
}
