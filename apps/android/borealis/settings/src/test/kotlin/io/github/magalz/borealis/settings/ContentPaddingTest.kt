package io.github.magalz.borealis.settings

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ContentPaddingTest {
    private val inset = ContentPadding.Padding(1, 2, 3, 4)

    @Test
    fun `handhelds take the system insets as they are`() {
        assertThat(ContentPadding.compute(false, 2f, inset)).isEqualTo(inset)
    }

    @Test
    fun `a TV gets 15 dp of overscan padding on every side and ignores the insets`() {
        assertThat(ContentPadding.compute(true, 2f, inset))
            .isEqualTo(ContentPadding.Padding(30, 30, 30, 30))
        assertThat(ContentPadding.compute(true, 1.5f, inset))
            .isEqualTo(ContentPadding.Padding(23, 23, 23, 23))
    }

    private fun startSettings(television: Boolean): Activity {
        val app =
            androidx.test.core.app.ApplicationProvider.getApplicationContext<
                android.app.Application
            >()
        val modeManager = app.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        shadowOf(modeManager).currentModeType =
            if (television) Configuration.UI_MODE_TYPE_TELEVISION
            else Configuration.UI_MODE_TYPE_NORMAL
        return Robolectric.buildActivity(BorealisSettingsActivity::class.java).setup().get()
    }

    private fun Activity.content(): View = findViewById(android.R.id.content)

    @Test
    fun `on a TV the content view gets the overscan padding`() {
        val activity = startSettings(television = true)
        val expected = (15 * activity.resources.displayMetrics.density + 0.5f).toInt()

        val content = activity.content()

        assertThat(
                listOf(
                    content.paddingLeft,
                    content.paddingTop,
                    content.paddingRight,
                    content.paddingBottom,
                )
            )
            .containsExactly(expected, expected, expected, expected)
    }

    @Test
    fun `on a handheld the system bar insets become padding`() {
        val activity = startSettings(television = false)
        val insets =
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(), Insets.of(10, 20, 30, 40))
                .build()

        activity.content().dispatchApplyWindowInsets(insets)

        val content = activity.content()
        assertThat(
                listOf(
                    content.paddingLeft,
                    content.paddingTop,
                    content.paddingRight,
                    content.paddingBottom,
                )
            )
            .containsExactly(10, 20, 30, 40)
            .inOrder()
    }

    @Test
    fun `a display cutout larger than the bars wins on its side`() {
        val activity = startSettings(television = false)
        val insets =
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 20, 0, 40))
                .setInsets(WindowInsets.Type.displayCutout(), Insets.of(50, 60, 0, 0))
                .build()

        activity.content().dispatchApplyWindowInsets(insets)

        val content = activity.content()
        assertThat(
                listOf(
                    content.paddingLeft,
                    content.paddingTop,
                    content.paddingRight,
                    content.paddingBottom,
                )
            )
            .containsExactly(50, 60, 0, 40)
            .inOrder()
    }
}
