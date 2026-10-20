package io.github.magalz.borealis.stream.host

import android.os.Looper
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.stream.testing.aSession
import java.time.Duration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The look that the host is built with is the look that the screens of the chrome draw with: read
 * as `LocalBorealisLook` inside the chrome that the host composes, not beside it.
 */
@RunWith(RobolectricTestRunner::class)
class StreamChromeHostLookTest {
    private lateinit var activity: ComponentActivity
    private var seen: BorealisLook? = null

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    }

    @Composable
    private fun Probe() {
        seen = LocalBorealisLook.current
    }

    private fun showChrome(look: BorealisLook?) {
        val stream = View(activity)
        activity.addContentView(stream, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val host =
            if (look == null) {
                StreamChromeHost(activity, aSession(), stream, {}, {})
            } else {
                StreamChromeHost(activity, aSession(), stream, {}, {}, look)
            }
        host.chromeExtra = { Probe() }
        activity.addContentView(host, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
    }

    @Test
    fun `the screens of the chrome get the look that the host was built with`() {
        val chosen =
            BorealisLook(
                reduceMotion = ReduceMotion.On,
                textSize = TextSize.Larger,
                highContrast = true,
                colourVision = ColourVision.RedGreen,
            )

        showChrome(chosen)

        assertThat(seen).isEqualTo(chosen)
    }

    @Test
    fun `a host built with no look draws its screens with the default look`() {
        showChrome(null)

        assertThat(seen).isEqualTo(BorealisLook())
    }
}
