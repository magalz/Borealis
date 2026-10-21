package io.github.magalz.borealis.stream.host

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.stream.testing.aSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The look that the host is built with is the look that the screens of the chrome draw with: the
 * colours and the text size of what the Starting screen puts on the screen, which the host composes
 * (the host name under the title is in the muted ink, which high contrast brightens).
 */
@RunWith(AndroidJUnit4::class)
class StreamChromeHostLookTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The Starting screen of the session [aSession] makes, in a host built with [look]. */
    private fun showChrome(look: BorealisLook?) {
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            val stream = View(activity)
            activity.addContentView(stream, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            val host =
                if (look == null) {
                    StreamChromeHost(activity, aSession(), stream, {}, {})
                } else {
                    StreamChromeHost(activity, aSession(), stream, {}, {}, look)
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

        assertThat(composeRule.textColor(HOST_NAME)).isEqualTo(borealisColors(chosen).ink3)
        assertThat(composeRule.textColor(HOST_NAME)).isNotEqualTo(BorealisColors().ink3)
    }

    @Test
    fun `the screens of the chrome have the text size of the look that the host was built with`() {
        showChrome(BorealisLook(textSize = TextSize.Larger))

        assertThat(fontScale()).isWithin(EPSILON).of(TextSize.Larger.scale)
    }

    @Test
    fun `a host built with no look draws its screens with the default look`() {
        showChrome(null)

        assertThat(composeRule.textColor(HOST_NAME)).isEqualTo(BorealisColors().ink3)
        assertThat(fontScale()).isWithin(EPSILON).of(1f)
    }

    private companion object {
        const val HOST_NAME = "Gaming PC"
        const val EPSILON = 0.001f
    }
}
