package io.github.magalz.borealis.support

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.GameManager
import android.app.GameState
import android.content.Context
import android.os.Build
import android.os.Looper
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.utils.UiHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers

/**
 * What the stream tells the system about itself (the game state of Android 13), and the dialog that
 * says that the video decoder crashed. The game manager is a recording shadow: the platform one
 * needs a system service that a unit test does not have.
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [UiHelperCharacterizationTest.RecordingGameManager::class])
class UiHelperCharacterizationTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    // The platform has no game manager under Robolectric: the stream is told about this one.
    private val gameManager = Shadow.newInstanceOf(GameManager::class.java)
    private val recorder: RecordingGameManager = Shadow.extract(gameManager)
    private val context = ServicesContext(app, Context.GAME_SERVICE to gameManager)

    /** The game state of the stream as the system has it: loading or not, and the mode. */
    private fun modes(): List<Pair<Boolean, Int>> = recorder.states.map { it.isLoading to it.mode }

    @Test
    fun `connecting is gameplay that the system may interrupt`() {
        UiHelper.notifyStreamConnecting(context)

        assertThat(modes()).containsExactly(false to GameState.MODE_GAMEPLAY_INTERRUPTIBLE)
    }

    @Test
    fun `connected is gameplay that the system must not interrupt`() {
        UiHelper.notifyStreamConnected(context)

        assertThat(modes()).containsExactly(false to GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE)
    }

    @Test
    fun `entering picture in picture is gameplay that the system may interrupt`() {
        UiHelper.notifyStreamEnteringPiP(context)

        assertThat(modes()).containsExactly(false to GameState.MODE_GAMEPLAY_INTERRUPTIBLE)
    }

    @Test
    fun `leaving picture in picture is gameplay that the system must not interrupt`() {
        UiHelper.notifyStreamExitingPiP(context)

        assertThat(modes()).containsExactly(false to GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE)
    }

    @Test
    fun `an ended stream is no game mode`() {
        UiHelper.notifyStreamEnded(context)

        assertThat(modes()).containsExactly(false to GameState.MODE_NONE)
    }

    @Test
    fun `the states follow each other in the order that the stream went through`() {
        UiHelper.notifyStreamConnecting(context)
        UiHelper.notifyStreamConnected(context)
        UiHelper.notifyStreamEnteringPiP(context)
        UiHelper.notifyStreamExitingPiP(context)
        UiHelper.notifyStreamEnded(context)

        assertThat(modes().map { it.second })
            .containsExactly(
                GameState.MODE_GAMEPLAY_INTERRUPTIBLE,
                GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE,
                GameState.MODE_GAMEPLAY_INTERRUPTIBLE,
                GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE,
                GameState.MODE_NONE,
            )
            .inOrder()
    }

    @Test
    fun `a game manager that fails, in whatever way, does not fail the stream`() {
        for (failure in listOf(IllegalStateException("oem"), NoSuchMethodError("oem"))) {
            recorder.failure = failure

            UiHelper.notifyStreamConnecting(context)
            UiHelper.notifyStreamEnded(context)
        }

        assertThat(recorder.states).hasSize(4)
    }

    @Test
    fun `a device without a game manager has no game state to set`() {
        val without = ServicesContext(app, Context.GAME_SERVICE to null)

        UiHelper.notifyStreamConnecting(without)
        UiHelper.notifyStreamEnded(without)

        assertThat(recorder.states).isEmpty()
    }

    @Test
    fun `an Android version before 13 has no game state to set`() {
        val sdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(
            Build.VERSION::class.java,
            "SDK_INT",
            Build.VERSION_CODES.S_V2,
        )
        try {
            UiHelper.notifyStreamConnecting(context)
            UiHelper.notifyStreamEnded(context)
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
        }

        assertThat(recorder.states).isEmpty()
    }

    private val decoderPrefs
        get() = app.getSharedPreferences("DecoderTombstone", Context.MODE_PRIVATE)

    private val settings
        get() = PreferenceManager.getDefaultSharedPreferences(app)

    private fun crashed(count: Int, notified: Int = 0) {
        decoderPrefs
            .edit()
            .putInt("CrashCount", count)
            .putInt("LastNotifiedCrashCount", notified)
            .commit()
    }

    private fun activity(): Activity =
        Robolectric.buildActivity(Activity::class.java).create().get()

    private fun latest(): AlertDialog? = ShadowAlertDialog.getLatestAlertDialog()

    // A dialog button hands its click to the handler of the dialog.
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `an app that never crashed shows no dialog`() {
        UiHelper.showDecoderCrashDialog(activity())

        assertThat(latest()).isNull()
    }

    @Test
    fun `a crash that was already notified shows no dialog`() {
        crashed(count = 2, notified = 2)

        UiHelper.showDecoderCrashDialog(activity())

        assertThat(latest()).isNull()
    }

    @Test
    fun `a crash is notified with the error dialog, and the settings are not touched`() {
        settings.edit().putString("seekbar_bitrate_kbps", "30000").commit()
        crashed(count = 1)

        UiHelper.showDecoderCrashDialog(activity())

        val dialog = checkNotNull(latest())
        assertThat(dialog.isShowing).isTrue()
        assertThat(shadowOf(dialog).title.toString()).isEqualTo("Video Decoder Crashed")
        assertThat(shadowOf(dialog).message.toString())
            .startsWith("Borealis has crashed due to an incompatibility")
        assertThat(settings.getString("seekbar_bitrate_kbps", null)).isEqualTo("30000")
    }

    @Test
    fun `the crash is acknowledged when the dialog is dismissed, and not before`() {
        crashed(count = 1)

        UiHelper.showDecoderCrashDialog(activity())

        assertThat(decoderPrefs.getInt("LastNotifiedCrashCount", -1)).isEqualTo(0)
        checkNotNull(latest()).getButton(AlertDialog.BUTTON_POSITIVE).performClick().also { idle() }
        assertThat(decoderPrefs.getInt("LastNotifiedCrashCount", -1)).isEqualTo(1)
        assertThat(decoderPrefs.getInt("CrashCount", -1)).isEqualTo(1)
    }

    @Test
    fun `the crash count of a dialog is the one that it showed, even if the count grows meanwhile`() {
        crashed(count = 2)

        UiHelper.showDecoderCrashDialog(activity())
        decoderPrefs.edit().putInt("CrashCount", 5).commit()
        checkNotNull(latest()).getButton(AlertDialog.BUTTON_POSITIVE).performClick().also { idle() }

        assertThat(decoderPrefs.getInt("LastNotifiedCrashCount", -1)).isEqualTo(2)
    }

    @Test
    fun `a crash that was notified before is notified again when it is a new one`() {
        crashed(count = 2, notified = 1)

        UiHelper.showDecoderCrashDialog(activity())

        assertThat(shadowOf(checkNotNull(latest())).title.toString())
            .isEqualTo("Video Decoder Crashed")
    }

    @Test
    fun `the third crash in a row resets the streaming settings and says so`() {
        settings
            .edit()
            .putString("seekbar_bitrate_kbps", "30000")
            .putString("list_resolution", "3840x2160")
            .putString("list_fps", "120")
            .putString("video_format", "forceh265")
            .putBoolean("checkbox_enable_hdr", true)
            .putBoolean("checkbox_full_range", true)
            .putBoolean("checkbox_stretch_video", true)
            .commit()
        crashed(count = 3)

        UiHelper.showDecoderCrashDialog(activity())

        val dialog = checkNotNull(latest())
        assertThat(shadowOf(dialog).title.toString()).isEqualTo("Video Settings Reset")
        assertThat(shadowOf(dialog).message.toString())
            .startsWith("Your device's video decoder continues to crash")
        for (key in
            listOf(
                "seekbar_bitrate_kbps",
                "list_resolution",
                "list_fps",
                "video_format",
                "checkbox_enable_hdr",
                "checkbox_full_range",
            )) {
            assertThat(settings.contains(key)).isFalse()
        }
        // What is not a streaming setting stays.
        assertThat(settings.getBoolean("checkbox_stretch_video", false)).isTrue()
    }

    @Test
    fun `every third crash resets, the ones between do not`() {
        for (count in listOf(2, 3, 4, 6, 7, 9)) {
            settings.edit().putString("list_fps", "120").commit()
            crashed(count = count)

            UiHelper.showDecoderCrashDialog(activity())

            val reset = !settings.contains("list_fps")
            assertThat(reset).isEqualTo(count % 3 == 0)
            assertThat(shadowOf(checkNotNull(latest())).title.toString())
                .isEqualTo(if (reset) "Video Settings Reset" else "Video Decoder Crashed")
        }
    }

    @Test
    fun `a reset is acknowledged when its dialog is dismissed`() {
        crashed(count = 3)

        UiHelper.showDecoderCrashDialog(activity())
        checkNotNull(latest()).getButton(AlertDialog.BUTTON_POSITIVE).performClick().also { idle() }

        assertThat(decoderPrefs.getInt("LastNotifiedCrashCount", -1)).isEqualTo(3)
    }

    @Test
    fun `an activity that is finishing resets the settings and shows no dialog`() {
        settings.edit().putString("list_fps", "120").commit()
        crashed(count = 3)
        val finishing = activity().also { it.finish() }

        UiHelper.showDecoderCrashDialog(finishing)

        assertThat(latest()).isNull()
        assertThat(settings.contains("list_fps")).isFalse()
    }

    /** The game manager of the system, which only keeps the states that it was given. */
    @Implements(GameManager::class)
    class RecordingGameManager {
        val states = mutableListOf<GameState>()
        var failure: Throwable? = null

        @Implementation
        fun setGameState(state: GameState) {
            states += state
            failure?.let { throw it }
        }
    }
}
