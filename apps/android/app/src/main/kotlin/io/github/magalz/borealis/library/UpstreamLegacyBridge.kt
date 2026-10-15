package io.github.magalz.borealis.library

import android.app.Activity
import io.github.magalz.borealis.library.domain.LegacyBridge
import javax.inject.Inject
import javax.inject.Singleton

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

/** [LegacyBridge] over the upstream helpers of `PcView`. */
@Singleton
internal class UpstreamLegacyBridge
@Inject
constructor(private val startup: UpstreamStartup, private val glProbe: GlRendererProbe) :
    LegacyBridge {
    override fun onLauncherCreated(activity: Activity): Unit = TODO()

    override fun onLauncherResumed(activity: Activity): Unit = TODO()

    override fun openStreamSettings(activity: Activity): Unit = TODO()
}
