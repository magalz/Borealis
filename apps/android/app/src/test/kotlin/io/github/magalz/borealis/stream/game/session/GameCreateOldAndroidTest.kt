package io.github.magalz.borealis.stream.game.session

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.testing.ANDROID_9
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What `Game.onCreate` does on an Android older than 10, where the sides of its `SDK_INT` checks
 * are the old ones: the cutout mode of a window under a notch and the low latency Wi-Fi lock. The
 * unbuffered input dispatch has its old side here too: `View.requestUnbufferedDispatch(int)` does
 * not exist before Android 11, so an activity that asked for it would not start. Every other `Game`
 * test runs at the level of `robolectric.properties`.
 */
@Config(sdk = [ANDROID_9])
internal class GameCreateOldAndroidTest : GameTestBase() {
    @Before
    fun grantTheReceiverPermission() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application)
            .grantPermissions("${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }

    @Test
    fun `a window under a notch only lays out under the short edges before Android 11`() {
        rig.preference("checkbox_stretch_video", true)

        rig.create()

        assertThat(rig.game.window.attributes.layoutInDisplayCutoutMode)
            .isEqualTo(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)
    }

    @Test
    fun `only the high performance Wi-Fi lock is taken before Android 10`() {
        rig.create()

        val manager = rig.application.getSystemService(Context.WIFI_SERVICE) as WifiManager
        assertThat(shadowOf(manager).activeLockCount).isEqualTo(1)
        assertThat(rig.field<WifiManager.WifiLock>("highPerfWifiLock").isHeld).isTrue()
        assertThat(rig.field<WifiManager.WifiLock?>("lowLatencyWifiLock")).isNull()
    }
}
