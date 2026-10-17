package io.github.magalz.borealis.settings.store

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository
import org.junit.Test

/** The diagnostic bundle reads the switches through these, from the repository. */
class SnapshotLogSwitchesTest {
    private val repository = FakeSettingsRepository()
    private val switches = SnapshotLogSwitches(repository.snapshot)

    @Test
    fun `both switches are off for an empty store`() {
        assertThat(switches.verboseLogging).isFalse()
        assertThat(switches.maskAddresses).isFalse()
    }

    @Test
    fun `the switches follow the snapshot at the moment of the read`() {
        repository.setNow(Settings.VERBOSE_LOGGING, true)
        assertThat(switches.verboseLogging).isTrue()
        assertThat(switches.maskAddresses).isFalse()

        repository.setNow(Settings.MASK_ADDRESSES, true)
        repository.setNow(Settings.VERBOSE_LOGGING, false)

        assertThat(switches.verboseLogging).isFalse()
        assertThat(switches.maskAddresses).isTrue()
    }

    @Test
    fun `a primed snapshot is read as it is`() {
        val primed = SettingsSnapshot.DEFAULTS.with(Settings.MASK_ADDRESSES, true)

        assertThat(SnapshotLogSwitches(FakeSettingsRepository(primed).snapshot).maskAddresses)
            .isTrue()
    }
}
