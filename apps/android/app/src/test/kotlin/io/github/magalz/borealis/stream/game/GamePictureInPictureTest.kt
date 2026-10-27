package io.github.magalz.borealis.stream.game

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.util.Rational
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.ui.StreamView
import org.junit.Test
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * The picture-in-picture parameters of the stream activity, on the SDK level of the rig (Android
 * 15). The branches for Android 11 and earlier (`autoEnterPip`, `onUserLeaveHint`) and the one for
 * Android 12 only (no title) need another SDK level and are not reached.
 */
internal class GamePictureInPictureTest : GameTestBase() {
    private fun paramsFor(autoEnter: Boolean): PictureInPictureParams =
        ReflectionHelpers.callInstanceMethod(
            rig.game,
            "getPictureInPictureParams",
            ClassParameter.from(Boolean::class.javaPrimitiveType, autoEnter),
        )

    @Test
    fun `the window keeps the aspect ratio of the stream`() {
        rig.preference("list_resolution", "1920x1080")
        rig.create()

        assertThat(paramsFor(false).aspectRatio).isEqualTo(Rational(16, 9))
    }

    @Test
    fun `the window shrinks to the stream view`() {
        rig.create()
        val view = rig.game.findViewById<StreamView>(R.id.surfaceView)

        val hint = paramsFor(false).sourceRectHint

        assertThat(hint).isEqualTo(Rect(view.left, view.top, view.right, view.bottom))
        assertThat(hint!!.isEmpty).isFalse()
    }

    @Test
    fun `the window enters and resizes seamlessly as the caller says`() {
        rig.create()

        assertThat(paramsFor(true).isAutoEnterEnabled).isTrue()
        assertThat(paramsFor(false).isAutoEnterEnabled).isFalse()
        assertThat(paramsFor(false).isSeamlessResizeEnabled).isTrue()
    }

    @Test
    fun `the window is titled with the app and the PC`() {
        rig.create()

        val params = paramsFor(false)

        assertThat(params.title.toString()).isEqualTo("Desktop")
        assertThat(params.subtitle.toString()).isEqualTo("Den PC")
    }

    @Test
    fun `without an app name the window is titled with the PC and has no subtitle`() {
        rig.create(GameIntent(appName = null))

        val params = paramsFor(false)

        assertThat(params.title.toString()).isEqualTo("Den PC")
        assertThat(params.subtitle).isNull()
    }

    @Test
    fun `an app without a PC is titled with the app only`() {
        rig.create(GameIntent(pcName = null))

        val params = paramsFor(false)

        assertThat(params.title.toString()).isEqualTo("Desktop")
        assertThat(params.subtitle).isNull()
    }

    @Test
    fun `without a name of any kind the window has no title`() {
        rig.create(GameIntent(appName = null, pcName = null))

        val params = paramsFor(false)

        assertThat(params.title).isNull()
        assertThat(params.subtitle).isNull()
    }

    @Test
    fun `the setting for picture in picture is off, so nothing is set`() {
        val parameters = rig.recordPictureInPicture()
        rig.streaming()

        rig.game.onUsbPermissionPromptStarting()
        rig.game.onUsbPermissionPromptCompleted()

        assertThat(parameters).isEmpty()
    }

    @Test
    fun `the window may enter on its own once the stream is connected`() {
        rig.preference("checkbox_enable_pip", true)
        val parameters = rig.recordPictureInPicture()
        rig.create()
        assertThat(parameters).isEmpty()

        rig.game.connectionStarted()

        assertThat(parameters.map { it.isAutoEnterEnabled }).containsExactly(true)
    }

    @Test
    fun `the window does not enter on its own while a USB permission prompt is up`() {
        rig.preference("checkbox_enable_pip", true)
        val parameters = rig.recordPictureInPicture()
        rig.streaming()

        rig.game.onUsbPermissionPromptStarting()
        rig.game.onUsbPermissionPromptStarting()
        rig.game.onUsbPermissionPromptCompleted()
        rig.game.onUsbPermissionPromptCompleted()

        // Connected: enter. Two prompts: no, no. One closed: still no. Both closed: enter.
        assertThat(parameters.map { it.isAutoEnterEnabled })
            .containsExactly(true, false, false, false, true)
            .inOrder()
    }

    @Test
    fun `the window may not enter on its own once the stream stopped`() {
        rig.preference("checkbox_enable_pip", true)
        val parameters = rig.recordPictureInPicture()
        rig.streaming()

        rig.stop()

        assertThat(parameters.map { it.isAutoEnterEnabled }).containsExactly(true, false).inOrder()
    }

    @Test
    fun `a request to enter on Android 12 and later is answered yes and left to the system`() {
        rig.preference("checkbox_enable_pip", true)
        rig.streaming()

        assertThat(rig.game.onPictureInPictureRequested()).isTrue()
        assertThat(rig.field<Boolean>("autoEnterPip")).isFalse()
    }
}
