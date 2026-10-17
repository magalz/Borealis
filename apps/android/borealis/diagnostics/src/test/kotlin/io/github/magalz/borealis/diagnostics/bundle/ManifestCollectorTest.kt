package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class ManifestCollectorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-10-14T12:34:56.789Z")
    private val switches = FakeLogSwitches()

    @Before
    fun setUp() {
        ShadowBuild.setManufacturer("Acme")
        ShadowBuild.setModel("Phone 1")
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "RELEASE", "15")
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    private fun install(versionName: String?, versionCode: Long, debuggable: Boolean) {
        val info =
            PackageInfo().apply {
                packageName = context.packageName
                this.versionName = versionName
                longVersionCode = versionCode
                applicationInfo =
                    ApplicationInfo().apply {
                        packageName = context.packageName
                        flags = if (debuggable) ApplicationInfo.FLAG_DEBUGGABLE else 0
                    }
            }
        shadowOf(context.packageManager).installPackage(info)
    }

    @Test
    fun `collects the app, device and creation time`() {
        install("1.2.3", 42L, debuggable = false)

        val manifest = ManifestCollector.collect(context, switches, now)

        assertThat(manifest.app)
            .isEqualTo(AppInfo("1.2.3", 42L, gitSha = "unknown", buildType = "release"))
        assertThat(manifest.device.androidRelease).isEqualTo("15")
        assertThat(manifest.device.sdk).isEqualTo(Build.VERSION.SDK_INT)
        assertThat(manifest.device.manufacturer).isEqualTo("Acme")
        assertThat(manifest.device.model).isEqualTo("Phone 1")
        assertThat(manifest.device.abis).containsExactlyElementsIn(Build.SUPPORTED_ABIS.toList())
        assertThat(manifest.createdUtc).isEqualTo("2026-10-14T12:34:56Z")
        assertThat(manifest.truncated).isEmpty()
    }

    @Test
    fun `a debuggable app is a debug build`() {
        install("1.0", 1L, debuggable = true)

        assertThat(ManifestCollector.collect(context, switches, now).app.buildType)
            .isEqualTo("debug")
    }

    @Test
    fun `a missing version name is unknown`() {
        install(null, 1L, debuggable = false)

        assertThat(ManifestCollector.collect(context, switches, now).app.versionName)
            .isEqualTo("unknown")
    }

    @Test
    fun `the log settings default to off`() {
        install("1.0", 1L, debuggable = false)

        assertThat(ManifestCollector.collect(context, switches, now).logSettings)
            .isEqualTo(LogSettingsInfo(verbose = false, maskAddresses = false))
    }

    @Test
    fun `the log settings reflect the switches it is given`() {
        install("1.0", 1L, debuggable = false)
        switches.verboseLogging = true
        switches.maskAddresses = true

        assertThat(ManifestCollector.collect(context, switches, now).logSettings)
            .isEqualTo(LogSettingsInfo(verbose = true, maskAddresses = true))

        switches.maskAddresses = false

        assertThat(ManifestCollector.collect(context, switches, now).logSettings)
            .isEqualTo(LogSettingsInfo(verbose = true, maskAddresses = false))
    }

    @Test
    fun `the manifest does not read the Borealis preferences itself`() {
        install("1.0", 1L, debuggable = false)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("borealis_verbose_logging", true)
            .putBoolean("borealis_mask_addresses", true)
            .commit()

        assertThat(ManifestCollector.collect(context, switches, now).logSettings)
            .isEqualTo(LogSettingsInfo(verbose = false, maskAddresses = false))
    }
}
