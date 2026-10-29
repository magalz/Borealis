package io.github.magalz.borealis.stream.host

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.host.launch.LaunchProgressText
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.stream.LaunchRetry
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.testing.aSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The look that the host is built with is the look that the screens of the chrome draw with: the
 * colours and the text size of what the Starting screen puts on the screen, which the host composes
 * (the host name under the title stands on the background, so it is in the second ink with the
 * halo, and high contrast brightens it).
 */
@RunWith(AndroidJUnit4::class)
class StreamChromeHostLookTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The Starting screen of [session], in a host built with [look]. */
    private fun showChrome(look: BorealisLook?, session: StreamSession = aSession()) {
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            val stream = View(activity)
            activity.addContentView(stream, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            val host =
                if (look == null) {
                    StreamChromeHost(activity, session, stream, {}, {})
                } else {
                    StreamChromeHost(activity, session, stream, {}, {}, look)
                }
            activity.addContentView(host, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        composeRule.waitForIdle()
    }

    private fun fontScale(): Float =
        composeRule
            .onNodeWithText(HOST_NAME, useUnmergedTree = true)
            .textLayout()
            .layoutInput
            .density
            .fontScale

    @Test
    fun `the screens of the chrome have the colours of the look that the host was built with`() {
        val chosen = BorealisLook(highContrast = true)

        showChrome(chosen)

        assertThat(composeRule.textColor(HOST_NAME)).isEqualTo(borealisColors(chosen).ink2)
        assertThat(composeRule.textColor(HOST_NAME)).isNotEqualTo(BorealisColors().ink2)
    }

    @Test
    fun `the screens of the chrome have the text size of the look that the host was built with`() {
        showChrome(BorealisLook(textSize = TextSize.Larger))

        assertThat(fontScale()).isWithin(EPSILON).of(TextSize.Larger.scale)
    }

    @Test
    fun `the screens of the chrome have the status colours of the palette that the host was built with`() {
        val retry =
            LaunchRetry(LaunchCategory.PENDING, attempt = 1, maxAttempts = 3, delayMs = 2000)
        val chosen = BorealisLook(colourVision = ColourVision.RedGreen)

        showChrome(chosen, aSession().also { it.onLaunchRetry(retry) })

        // The note of a host that asks for a retry is drawn in the ink of the warning status.
        val note =
            LaunchProgressText.format(
                composeRule.activity,
                retry.category?.ordinal ?: -1,
                retry.attempt,
                retry.maxAttempts,
                retry.delayMs,
            )
        assertThat(composeRule.textColor(note)).isEqualTo(borealisColors(chosen).warningInk)
        assertThat(composeRule.textColor(note)).isNotEqualTo(BorealisColors().warningInk)
    }

    @Test
    fun `the screens of the chrome have the accent that the host was built with`() {
        val chosen = BorealisLook(accent = AccentChoice.Teal)

        showChrome(chosen)

        // The number of the step that is under way is drawn in the soft accent, on the accent tint.
        assertThat(composeRule.textColor(CURRENT_STEP)).isEqualTo(Color(0xFFA2FCE8))
        assertThat(composeRule.textColor(CURRENT_STEP)).isEqualTo(borealisColors(chosen).accentSoft)
        assertThat(composeRule.textColor(CURRENT_STEP)).isNotEqualTo(BorealisColors().accentSoft)
    }

    @Test
    fun `a host built with no look draws its screens with the default look`() {
        showChrome(null)

        assertThat(composeRule.textColor(HOST_NAME)).isEqualTo(BorealisColors().ink2)
        assertThat(fontScale()).isWithin(EPSILON).of(1f)
    }

    private companion object {
        const val HOST_NAME = "Gaming PC"
        const val CURRENT_STEP = "1"
        const val EPSILON = 0.001f
    }
}
