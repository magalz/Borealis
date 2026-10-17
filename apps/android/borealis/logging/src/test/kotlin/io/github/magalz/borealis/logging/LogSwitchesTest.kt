package io.github.magalz.borealis.logging

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LogSwitchesTest {
    @Test
    fun `both switches are off when nothing is stored`() {
        val switches = PreferencesLogSwitches(FakePreferences())

        assertThat(switches.verboseLogging).isFalse()
        assertThat(switches.maskAddresses).isFalse()
    }

    @Test
    fun `the switches follow the Borealis preference keys`() {
        val preferences = FakePreferences("borealis_verbose_logging" to true)
        val switches = PreferencesLogSwitches(preferences)

        assertThat(switches.verboseLogging).isTrue()
        assertThat(switches.maskAddresses).isFalse()

        preferences.values["borealis_mask_addresses"] = true
        preferences.values["borealis_verbose_logging"] = false

        assertThat(switches.verboseLogging).isFalse()
        assertThat(switches.maskAddresses).isTrue()
    }

    @Test
    fun `the keys are the ones the settings screen writes`() {
        assertThat(LogStorage.VERBOSE_LOGGING_KEY).isEqualTo("borealis_verbose_logging")
        assertThat(LogStorage.MASK_ADDRESSES_KEY).isEqualTo("borealis_mask_addresses")
    }
}

/** Only `getBoolean` is read; everything else is outside what the switches need. */
private class FakePreferences(vararg initial: Pair<String, Boolean>) : SharedPreferences {
    val values = mutableMapOf(*initial)

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] ?: defValue

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(key: String?, defValue: String?): String? = unused()

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        unused()

    override fun getInt(key: String?, defValue: Int): Int = unused()

    override fun getLong(key: String?, defValue: Long): Long = unused()

    override fun getFloat(key: String?, defValue: Float): Float = unused()

    override fun contains(key: String?): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = unused()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = unused()

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("not used by the switches")
}
