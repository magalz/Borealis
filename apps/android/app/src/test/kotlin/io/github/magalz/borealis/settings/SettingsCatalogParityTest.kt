package io.github.magalz.borealis.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import io.github.magalz.borealis.settings.catalog.BooleanSetting
import io.github.magalz.borealis.settings.catalog.IntSetting
import io.github.magalz.borealis.settings.catalog.OptionalIntSetting
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.catalog.StringSetting
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/**
 * The Kotlin settings catalog (ADR 0055) matches the inherited `preferences.xml`: the same keys,
 * the same stored types and the same defaults. The XML is the source of truth until the Phase 2
 * screen replaces it.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsCatalogParityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** One keyed preference of an XML file: its element, key and `android:defaultValue`. */
    private data class XmlPreference(val element: String, val key: String, val default: String?)

    private fun preferencesOf(xmlRes: Int): List<XmlPreference> {
        val found = mutableListOf<XmlPreference>()
        context.resources.getXml(xmlRes).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != XmlPullParser.START_TAG) continue
                val key = xml.getAttributeValue(ANDROID_NS, "key") ?: continue
                found += XmlPreference(xml.name, key, xml.getAttributeValue(ANDROID_NS, "defaultValue"))
            }
        }
        return found
    }

    /** The stream preferences that carry a setting: no category and no dropped key. */
    private fun streamPreferences(): List<XmlPreference> =
        preferencesOf(R.xml.preferences).filter {
            it.element != "PreferenceCategory" && it.key !in DROPPED_KEYS
        }

    private fun settingByKey(key: String): Setting<*>? = Settings.all.firstOrNull { it.key == key }

    private fun expectedType(element: String) =
        when (element) {
            "CheckBoxPreference" -> BooleanSetting::class
            "ListPreference" -> StringSetting::class
            "com.limelight.preferences.SeekBarPreference" -> IntSetting::class
            else -> error("The parity test does not know how $element is stored")
        }

    @Test
    fun `the XML holds the 38 stream settings that the screen ports`() {
        assertThat(streamPreferences()).hasSize(STREAM_SETTINGS)
    }

    @Test
    fun `every stream preference of the XML is in the catalog with the same key`() {
        val missing = streamPreferences().map { it.key }.filter { settingByKey(it) == null }

        assertThat(missing).isEmpty()
    }

    @Test
    fun `every catalog entry has the stored type of its XML preference`() {
        for (preference in streamPreferences()) {
            val setting = settingByKey(preference.key) ?: throw AssertionError("${preference.key} is not in the catalog")
            val expected =
                if (preference.key == Settings.BITRATE_KBPS.key) {
                    OptionalIntSetting::class
                } else {
                    expectedType(preference.element)
                }

            assertThat(setting::class).isEqualTo(expected)
        }
    }

    @Test
    fun `every catalog entry has the default of its XML preference`() {
        for (preference in streamPreferences()) {
            val setting = settingByKey(preference.key) ?: throw AssertionError("${preference.key} is not in the catalog")

            // The bitrate has no android:defaultValue: absent means automatic.
            val expected = preference.default
            assertThat(setting.default?.toString()).isEqualTo(expected)
        }
    }

    @Test
    fun `the catalog has no stream key that is missing from the XML`() {
        val xmlKeys = preferencesOf(R.xml.preferences).map { it.key }.toSet()

        val extra =
            Settings.all.map { it.key }.filter { !it.startsWith(BOREALIS_PREFIX) && it !in xmlKeys }

        assertThat(extra).isEmpty()
    }

    @Test
    fun `the dropped keys are not in the catalog`() {
        for (key in DROPPED_KEYS) {
            assertThat(settingByKey(key)).isNull()
        }
    }

    @Test
    fun `the catalog has no duplicate key`() {
        val keys = Settings.all.map { it.key }

        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun `the two logging switches match the Borealis XML and live in DataStore`() {
        val xmlRes = context.resources.getIdentifier("borealis_preferences", "xml", context.packageName)
        val switches =
            preferencesOf(xmlRes).filter { it.key == Settings.VERBOSE_LOGGING.key || it.key == Settings.MASK_ADDRESSES.key }

        assertThat(switches.map { it.key })
            .containsExactly(Settings.VERBOSE_LOGGING.key, Settings.MASK_ADDRESSES.key)
        for (switch in switches) {
            val setting = settingByKey(switch.key)
            assertThat(setting).isInstanceOf(BooleanSetting::class.java)
            assertThat(switch.element).isEqualTo("CheckBoxPreference")
            assertThat(setting?.default?.toString()).isEqualTo(switch.default)
            assertThat(setting?.store).isEqualTo(SettingStore.DATASTORE)
        }
    }

    @Test
    fun `the new performance overlay choice is a String that is off by default`() {
        assertThat(Settings.PERF_OVERLAY.key).isEqualTo("borealis_perf_overlay")
        assertThat(Settings.PERF_OVERLAY).isInstanceOf(StringSetting::class.java)
        assertThat(Settings.PERF_OVERLAY.default).isEqualTo("off")
        assertThat(Settings.PERF_OVERLAY.store).isEqualTo(SettingStore.PREFS)
        assertThat(
                listOf(
                    Settings.PERF_OVERLAY_OFF,
                    Settings.PERF_OVERLAY_COMPACT,
                    Settings.PERF_OVERLAY_DETAILED,
                )
            )
            .containsExactly("off", "compact", "detailed")
            .inOrder()
    }

    @Test
    fun `only the two logging switches are stored in DataStore`() {
        val datastore = Settings.all.filter { it.store == SettingStore.DATASTORE }.map { it.key }

        assertThat(datastore)
            .containsExactly(Settings.VERBOSE_LOGGING.key, Settings.MASK_ADDRESSES.key)
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val BOREALIS_PREFIX = "borealis_"
        const val STREAM_SETTINGS = 38

        /** The language list and the small icon mode are not ported (ADR 0055, decision 6). */
        val DROPPED_KEYS = setOf("list_languages", "checkbox_small_icon_mode")
    }
}
