package com.limelight.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

class GlPreferences private constructor(private val prefs: SharedPreferences) {
    @JvmField var glRenderer: String = ""

    @JvmField var savedFingerprint: String = ""

    // The KTX edit(commit = true) drops the result of commit(), and this returns it.
    @SuppressLint("UseKtx")
    fun writePreferences(): Boolean =
        prefs
            .edit()
            .putString(GL_RENDERER_PREF_STRING, glRenderer)
            .putString(FINGERPRINT_PREF_STRING, savedFingerprint)
            .commit()

    companion object {
        private const val PREF_NAME = "GlPreferences"

        private const val FINGERPRINT_PREF_STRING = "Fingerprint"
        private const val GL_RENDERER_PREF_STRING = "Renderer"

        @JvmStatic
        fun readPreferences(context: Context): GlPreferences {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val glPrefs = GlPreferences(prefs)

            glPrefs.glRenderer = checkNotNull(prefs.getString(GL_RENDERER_PREF_STRING, ""))
            glPrefs.savedFingerprint = checkNotNull(prefs.getString(FINGERPRINT_PREF_STRING, ""))

            return glPrefs
        }
    }
}
