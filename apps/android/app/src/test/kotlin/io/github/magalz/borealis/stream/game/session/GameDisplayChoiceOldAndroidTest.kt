package io.github.magalz.borealis.stream.game.session

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.game.DisplayMode
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.testing.ANDROID_9
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The display mode choice of `Game.prepareDisplayForRendering` on an Android that has no
 * `Surface.setFrameRate` to leave a refresh rate change to. Every other `Game` test runs at the
 * level of `robolectric.properties`.
 */
@Config(sdk = [ANDROID_9])
internal class GameDisplayChoiceOldAndroidTest : GameTestBase() {
    @Before
    fun grantTheReceiverPermission() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application)
            .grantPermissions("${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }

    @Test
    fun `a mode with the same resolution is asked for through the window before Android 12`() {
        rig.preference("list_resolution", "1920x1080")
        rig.preference("list_fps", "120")
        rig.displayModes(DisplayMode(0, 1920, 1080, 60f), DisplayMode(2, 1920, 1080, 120f))

        rig.create()

        assertThat(rig.game.window.attributes.preferredDisplayModeId).isEqualTo(2)
    }
}
