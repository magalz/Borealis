package io.github.magalz.borealis.library

import android.app.Activity
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.GlPreferences
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class UpstreamLegacyBridgeTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val calls = mutableListOf<String>()
    private var probes = 0
    private var renderer: String? = "Adreno (TM) 740"

    private val startup =
        object : UpstreamStartup {
            override fun showDecoderCrashDialog(activity: Activity) {
                calls += "crash dialog"
            }
        }

    private var onProbe: (Activity) -> Unit = {}

    private val probe = GlRendererProbe { probed, onRenderer ->
        probes++
        onProbe(probed)
        renderer?.let(onRenderer)
    }

    private val bridge = UpstreamLegacyBridge(startup, probe)

    /** Creates the launcher and lets the main looper run what it posted. */
    private fun create() {
        bridge.onLauncherCreated(activity)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `the GL renderer is probed on the first run and saved with the build fingerprint`() {
        create()

        assertThat(probes).isEqualTo(1)
        val saved = GlPreferences.readPreferences(activity)
        assertThat(saved.glRenderer).isEqualTo("Adreno (TM) 740")
        assertThat(saved.savedFingerprint).isEqualTo(Build.FINGERPRINT)
    }

    @Test
    fun `the GL renderer is not probed again while the fingerprint is the same`() {
        create()
        create()

        assertThat(probes).isEqualTo(1)
    }

    @Test
    fun `the GL renderer is probed again when the build fingerprint changed`() {
        create()
        GlPreferences.readPreferences(activity).apply {
            savedFingerprint = "an older build"
            writePreferences()
        }
        renderer = "Mali-G715"

        create()

        assertThat(probes).isEqualTo(2)
        assertThat(GlPreferences.readPreferences(activity).glRenderer).isEqualTo("Mali-G715")
        assertThat(GlPreferences.readPreferences(activity).savedFingerprint)
            .isEqualTo(Build.FINGERPRINT)
    }

    @Test
    fun `the GL renderer is probed again when none was saved`() {
        GlPreferences.readPreferences(activity).apply {
            savedFingerprint = Build.FINGERPRINT
            glRenderer = ""
            writePreferences()
        }

        create()

        assertThat(probes).isEqualTo(1)
    }

    @Test
    fun `a probe that never answers saves nothing`() {
        renderer = null

        create()

        assertThat(GlPreferences.readPreferences(activity).glRenderer).isEmpty()
    }

    @Test
    fun `the probe runs after the content that the launcher sets later in the same callback`() {
        var childrenWhenProbed = -1
        onProbe = {
            childrenWhenProbed = it.findViewById<ViewGroup>(android.R.id.content).childCount
        }

        bridge.onLauncherCreated(activity)
        assertThat(probes).isEqualTo(0)
        activity.setContentView(View(activity))
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(probes).isEqualTo(1)
        assertThat(childrenWhenProbed).isEqualTo(1)
    }

    @Test
    fun `an activity that is finishing is not probed`() {
        bridge.onLauncherCreated(activity)
        activity.finish()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(probes).isEqualTo(0)
    }

    @Test
    fun `resuming shows the decoder crash notice`() {
        bridge.onLauncherResumed(activity)

        assertThat(calls).containsExactly("crash dialog")
    }
}
