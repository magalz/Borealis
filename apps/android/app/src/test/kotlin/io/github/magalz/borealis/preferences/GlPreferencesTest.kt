package io.github.magalz.borealis.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.GlPreferences
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes the inherited `GlPreferences`, which keeps the GL renderer and the build
 * fingerprint it was read on: the file, the keys, the defaults and the result of `commit()`. It
 * records the behaviour before the Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
class GlPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = context.getSharedPreferences("GlPreferences", Context.MODE_PRIVATE)

    @Before
    fun emptyFile() {
        file.edit().clear().commit()
    }

    @Test
    fun `an empty file reads an empty renderer and an empty fingerprint`() {
        val saved = GlPreferences.readPreferences(context)

        assertThat(saved.glRenderer).isEmpty()
        assertThat(saved.savedFingerprint).isEmpty()
    }

    @Test
    fun `the renderer and the fingerprint are read from the keys Renderer and Fingerprint`() {
        file
            .edit()
            .putString("Renderer", "Adreno (TM) 740")
            .putString("Fingerprint", "a/b/c")
            .commit()

        val saved = GlPreferences.readPreferences(context)

        assertThat(saved.glRenderer).isEqualTo("Adreno (TM) 740")
        assertThat(saved.savedFingerprint).isEqualTo("a/b/c")
    }

    @Test
    fun `reading asks only for the file GlPreferences in private mode and writes nothing`() {
        val recording = RecordingContext(context)

        GlPreferences.readPreferences(recording)

        assertThat(recording.files).containsExactly("GlPreferences" to Context.MODE_PRIVATE)
        assertThat(recording.log).isEmpty()
    }

    @Test
    fun `the fields can be set and are written under the same two keys`() {
        val saved = GlPreferences.readPreferences(context)
        saved.glRenderer = "Mali-G715"
        saved.savedFingerprint = "google/pixel/pixel:15"

        val result = saved.writePreferences()

        assertThat(result).isTrue()
        assertThat(file.all)
            .containsExactly("Renderer", "Mali-G715", "Fingerprint", "google/pixel/pixel:15")
        assertThat(GlPreferences.readPreferences(context).glRenderer).isEqualTo("Mali-G715")
        assertThat(GlPreferences.readPreferences(context).savedFingerprint)
            .isEqualTo("google/pixel/pixel:15")
    }

    @Test
    fun `writing puts the renderer then the fingerprint in one edit that is committed`() {
        val recording = RecordingContext(context)
        val saved = GlPreferences.readPreferences(recording)
        recording.log.clear()
        saved.glRenderer = "PowerVR"
        saved.savedFingerprint = "fp"

        saved.writePreferences()

        assertThat(recording.log)
            .containsExactly(
                "edit",
                "putString(Renderer=PowerVR)",
                "putString(Fingerprint=fp)",
                "commit()",
            )
            .inOrder()
    }

    @Test
    fun `writing returns what the commit answered`() {
        val failing = RecordingContext(context, commitResult = false)
        val saved = GlPreferences.readPreferences(failing)

        assertThat(saved.writePreferences()).isFalse()
    }

    @Test
    fun `a write that changes nothing writes the empty strings it read`() {
        val saved = GlPreferences.readPreferences(context)

        assertThat(saved.writePreferences()).isTrue()

        assertThat(file.all).containsExactly("Renderer", "", "Fingerprint", "")
    }
}
