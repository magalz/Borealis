package io.github.magalz.borealis.library

import android.app.Activity
import android.content.Intent
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.ViewGroup
import com.limelight.R
import com.limelight.preferences.GlPreferences
import com.limelight.preferences.StreamSettings
import com.limelight.utils.UiHelper
import io.github.magalz.borealis.library.domain.LegacyBridge
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import javax.inject.Inject
import javax.inject.Singleton
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** The upstream screens' work that the new launcher has to keep doing. */
internal interface UpstreamStartup {
    fun setDefaultPreferences(activity: Activity)

    fun applyLocale(activity: Activity)

    fun showDecoderCrashDialog(activity: Activity)

    fun openStreamSettings(activity: Activity)
}

/** Reads the GL renderer name by drawing one frame. Decoder choices depend on it. */
internal fun interface GlRendererProbe {
    /** Calls [onRenderer] once, on any thread, with the renderer name. */
    fun probe(activity: Activity, onRenderer: (String) -> Unit)
}

/**
 * [LegacyBridge] over the upstream helpers of the 12.2 host list.
 *
 * [onLauncherCreated] may be called before or after the activity sets its content: the GL renderer
 * probe is not started inside the call but posted to the main looper, so it runs after the callback
 * that is running now (an `onCreate` that sets its content later in the same callback included) and
 * its surface is not replaced by that content. It does not run for an activity that is finishing.
 */
@Singleton
internal class UpstreamLegacyBridge
@Inject
constructor(private val startup: UpstreamStartup, private val glProbe: GlRendererProbe) :
    LegacyBridge {
    override fun onLauncherCreated(activity: Activity) {
        startup.setDefaultPreferences(activity)
        startup.applyLocale(activity)
        Handler(Looper.getMainLooper()).post {
            if (!activity.isFinishing && !activity.isDestroyed) probeRendererIfNeeded(activity)
        }
    }

    override fun onLauncherResumed(activity: Activity) = startup.showDecoderCrashDialog(activity)

    override fun openStreamSettings(activity: Activity) = startup.openStreamSettings(activity)

    /** Decoder choices for Adreno and PowerVR depend on the renderer name: keep it up to date. */
    private fun probeRendererIfNeeded(activity: Activity) {
        val saved = GlPreferences.readPreferences(activity)
        if (saved.savedFingerprint != Build.FINGERPRINT || saved.glRenderer.isEmpty()) {
            glProbe.probe(activity) { renderer ->
                saved.glRenderer = renderer
                saved.savedFingerprint = Build.FINGERPRINT
                saved.writePreferences()
                BorealisLog.i(LogTag.APP, "GL renderer read", "renderer" to renderer)
            }
        } else {
            BorealisLog.i(LogTag.APP, "GL renderer cached", "renderer" to saved.glRenderer)
        }
    }
}

/** [UpstreamStartup] over the helpers that the 12.2 host list called at start-up. */
@Singleton
internal class UpstreamLauncherStartup @Inject constructor() : UpstreamStartup {
    @Suppress("DEPRECATION")
    override fun setDefaultPreferences(activity: Activity) =
        PreferenceManager.setDefaultValues(activity, R.xml.preferences, false)

    override fun applyLocale(activity: Activity) = UiHelper.setLocale(activity)

    override fun showDecoderCrashDialog(activity: Activity) =
        UiHelper.showDecoderCrashDialog(activity)

    override fun openStreamSettings(activity: Activity) =
        activity.startActivity(Intent(activity, StreamSettings::class.java))
}

/**
 * [GlRendererProbe] with the one-pixel `GLSurfaceView` that the 12.2 host list rendered a frame in.
 * It lives on top of the activity until the renderer name is known.
 */
@Singleton
internal class GlSurfaceRendererProbe @Inject constructor() : GlRendererProbe {
    override fun probe(activity: Activity, onRenderer: (String) -> Unit) {
        val surface = GLSurfaceView(activity)
        surface.setRenderer(
            object : GLSurfaceView.Renderer {
                override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
                    val renderer: String? = gl.glGetString(GL10.GL_RENDERER)
                    activity.runOnUiThread { (surface.parent as? ViewGroup)?.removeView(surface) }
                    if (renderer != null) onRenderer(renderer)
                }

                override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) = Unit

                override fun onDrawFrame(gl: GL10) = Unit
            }
        )
        activity.addContentView(surface, ViewGroup.LayoutParams(1, 1))
    }
}
