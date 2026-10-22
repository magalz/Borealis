package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.components.settings.SettingsGroup
import io.github.magalz.borealis.designsystem.components.settings.SettingsTwoPane
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the marks of a selection on a TV for each accent (ADR 0058, decision 5), named
 * `accent_tv_marks-<accent>.png`: the selected segment, chips, rail item and Settings group. With
 * white each of the segment, the rail item and the group has an indicator bar in the accent, and
 * the other accents mark the selection by colour alone.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class AccentScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val rail =
        listOf("Hosts", "Add host", "Settings").map { RailItem(it, BorealisIcons.Gear) }
    private val groups = listOf(SettingsGroup("stream", "Stream"), SettingsGroup("video", "Video"))

    @Test fun marksTeal() = marks(BorealisLook(accent = AccentChoice.Teal), "teal")

    @Test fun marksWhite() = marks(BorealisLook(accent = AccentChoice.White), "white")

    @Test
    fun marksWhiteHighContrast() =
        marks(BorealisLook(accent = AccentChoice.White, highContrast = true), "white-high-contrast")

    private fun marks(look: BorealisLook, name: String) {
        composeRule.setContent { Marks(look) }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/accent_tv_marks-$name.png")
    }

    @Composable
    private fun Marks(look: BorealisLook) {
        BorealisTheme(FormFactor.Tv, look) {
            Row(Modifier.fillMaxSize().background(BorealisTokens.Color.Page)) {
                NavigationRail(rail, selectedIndex = 0, onSelect = {})
                Column(Modifier.weight(1f).padding(24.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SegmentedControl(listOf("30", "60", "120"), "60", { "$it fps" }, {})
                        Chip("Alt+Tab", selected = true)
                        Chip("Win+D")
                    }
                    SettingsTwoPane(
                        groups,
                        selected = { "stream" },
                        onSelect = {},
                        modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(200.dp),
                    ) {
                        Box(Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}
