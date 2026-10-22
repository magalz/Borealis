package io.github.magalz.borealis.settings.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A row is skipped when its inputs did not change, and recomposed when its value did. A row reads
 * its title from the resources each time it is composed, so the reads show the recompositions.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class SettingsRowSkippingTest {
    @get:Rule val composeRule = createComposeRule()
    private var unrelated by mutableIntStateOf(0)
    private var value by mutableStateOf(RowValue(checked = false))
    private var stringReads = 0

    private val actions = SettingsActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, {})
    private val requests = RowRequests({}, {})

    @Test
    fun `a row is skipped when something else on the screen changes`() {
        composeRule.setContent { Screen() }
        composeRule.waitForIdle()
        val before = stringReads

        unrelated++
        composeRule.waitForIdle()

        assertThat(stringReads).isEqualTo(before)
    }

    @Test
    fun `a row is recomposed when its own value changes`() {
        composeRule.setContent { Screen() }
        composeRule.waitForIdle()
        val before = stringReads

        value = RowValue(checked = true)
        composeRule.waitForIdle()

        assertThat(stringReads).isGreaterThan(before)
    }

    @Composable
    private fun Screen() {
        val resources = LocalResources.current
        val counting =
            androidx.compose.runtime.remember { CountingResources(resources) { stringReads++ } }
        BorealisTheme(FormFactor.Tv) {
            CompositionLocalProvider(LocalResources provides counting) {
                Column {
                    // Read in the screen, as the screen reads its state, and handed down as a
                    // value.
                    Text("Unrelated $unrelated")
                    SettingsItemRow(SettingsItemState(SettingsItem.HDR), value, actions, requests)
                }
            }
        }
    }

    /** Resources that tell when a string is read. */
    @Suppress("DEPRECATION")
    private class CountingResources(base: Resources, private val onRead: () -> Unit) :
        Resources(base.assets, base.displayMetrics, base.configuration) {
        override fun getString(id: Int): String {
            onRead()
            return super.getString(id)
        }
    }
}
