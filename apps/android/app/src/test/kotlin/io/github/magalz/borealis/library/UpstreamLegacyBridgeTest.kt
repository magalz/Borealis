package io.github.magalz.borealis.library

import android.app.Activity
import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.GlPreferences
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpstreamLegacyBridgeTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val calls = mutableListOf<String>()
    private var probes = 0
    private var renderer: String? = "Adreno (TM) 740"

    private val startup =
        object : UpstreamStartup {
            override fun setDefaultPreferences(activity: Activity) {
                calls += "defaults"
            }

            override fun applyLocale(activity: Activity) {
                calls += "locale"
            }

            override fun showDecoderCrashDialog(activity: Activity) {
                calls += "crash dialog"
            }

            override fun openStreamSettings(activity: Activity) {
                calls += "settings"
            }
        }

    private val probe = GlRendererProbe { _, onRenderer ->
        probes++
        renderer?.let(onRenderer)
    }

    private val bridge = UpstreamLegacyBridge(startup, probe)

    @Test
    fun `creating the launcher sets the default preferences and the locale`() {
        bridge.onLauncherCreated(activity)

        assertThat(calls).containsExactly("defaults", "locale").inOrder()
    }

    @Test
    fun `the GL renderer is probed on the first run and saved with the build fingerprint`() {
        bridge.onLauncherCreated(activity)

        assertThat(probes).isEqualTo(1)
        val saved = GlPreferences.readPreferences(activity)
        assertThat(saved.glRenderer).isEqualTo("Adreno (TM) 740")
        assertThat(saved.savedFingerprint).isEqualTo(Build.FINGERPRINT)
    }

    @Test
    fun `the GL renderer is not probed again while the fingerprint is the same`() {
        bridge.onLauncherCreated(activity)
        bridge.onLauncherCreated(activity)

        assertThat(probes).isEqualTo(1)
    }

    @Test
    fun `the GL renderer is probed again when the build fingerprint changed`() {
        bridge.onLauncherCreated(activity)
        GlPreferences.readPreferences(activity).apply {
            savedFingerprint = "an older build"
            writePreferences()
        }
        renderer = "Mali-G715"

        bridge.onLauncherCreated(activity)

        assertThat(probes).isEqualTo(2)
        assertThat(GlPreferences.readPreferences(activity).glRenderer).isEqualTo("Mali-G715")
        assertThat(GlPreferences.readPreferences(activity).savedFingerprint).isEqualTo(Build.FINGERPRINT)
    }

    @Test
    fun `the GL renderer is probed again when none was saved`() {
        GlPreferences.readPreferences(activity).apply {
            savedFingerprint = Build.FINGERPRINT
            glRenderer = ""
            writePreferences()
        }

        bridge.onLauncherCreated(activity)

        assertThat(probes).isEqualTo(1)
    }

    @Test
    fun `a probe that never answers saves nothing`() {
        renderer = null

        bridge.onLauncherCreated(activity)

        assertThat(GlPreferences.readPreferences(activity).glRenderer).isEmpty()
    }

    @Test
    fun `resuming shows the decoder crash notice`() {
        bridge.onLauncherResumed(activity)

        assertThat(calls).containsExactly("crash dialog")
    }

    @Test
    fun `the stream settings open through the upstream screen`() {
        bridge.openStreamSettings(activity)

        assertThat(calls).containsExactly("settings")
    }
}
