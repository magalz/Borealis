package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.components.settings.CollapsibleSection
import io.github.magalz.borealis.designsystem.components.settings.SettingsGroup
import io.github.magalz.borealis.designsystem.components.settings.SettingsTwoPane
import io.github.magalz.borealis.designsystem.components.settings.SettingsValueRow
import io.github.magalz.borealis.designsystem.glass.GlassHostOver
import io.github.magalz.borealis.designsystem.glass.GlassSpec
import io.github.magalz.borealis.designsystem.glass.onGlass
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The text on a glass surface has the colours that keep 4.5:1 over the background behind it: the
 * secondary text of a Host card, the version in the rail, the title of a section and the summary of
 * a row of Settings. Over Flowing light at 40 percent and 8dp, ink-3 is `#A8ACB1`. Where the glass
 * is solid the text has the tokens as they are.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class GlassTextOnSurfacesTest {
    @get:Rule val composeRule = createComposeRule()

    private val ink3 = Color(0xFFA8ACB1)
    private val standardOverFlowingLight = GlassSpec(solid = false, tintPercent = 40, blurDp = 8)

    // A card

    @Test
    fun `the address of a Host card is the glass ink-3`() {
        showOverFlowingLight(FormFactor.Phone) { Card() }

        assertThat(textColor("192.168.1.20")).isEqualTo(ink3)
    }

    @Test
    fun `the address of a Host card is the token ink-3 where the glass is off`() {
        showOverFlowingLight(FormFactor.Phone, BorealisLook(transparency = Transparency.Off)) {
            Card()
        }

        assertThat(textColor("192.168.1.20")).isEqualTo(BorealisColors().ink3)
    }

    // The rail

    @Test
    fun `the version in the footer of the rail is the glass ink-3`() {
        showOverFlowingLight(FormFactor.Tv) { Rail() }

        assertThat(textColor("0.1.0")).isEqualTo(ink3)
    }

    @Test
    fun `the version in the footer of the rail is the ink-3 of high contrast where the glass is off`() {
        showOverFlowingLight(FormFactor.Tv, BorealisLook(highContrast = true)) { Rail() }

        assertThat(textColor("0.1.0")).isEqualTo(BorealisColors().withHighContrast().ink3)
    }

    // The settings

    @Test
    fun `the title of a section of the phone settings is the glass ink-4`() {
        showOverFlowingLight(FormFactor.Phone) { Section() }

        val expected =
            BorealisColors().onGlass(BackgroundStyle.FlowingLight, standardOverFlowingLight)
        assertThat(textColor("STREAM")).isEqualTo(expected.ink4)
        assertThat(textColor("STREAM")).isNotEqualTo(BorealisColors().ink4)
    }

    @Test
    fun `the summary of a row in a section of the phone settings is the glass ink-3`() {
        showOverFlowingLight(FormFactor.Phone) { Section() }

        assertThat(textColor("Pixels per frame")).isEqualTo(ink3)
    }

    @Test
    fun `the title of a section is the token ink-4 where the glass is off`() {
        showOverFlowingLight(FormFactor.Phone, BorealisLook(transparency = Transparency.Off)) {
            Section()
        }

        assertThat(textColor("STREAM")).isEqualTo(BorealisColors().ink4)
    }

    @Test
    fun `the summary of a row in the pane of the TV settings is the glass ink-3`() {
        showOverFlowingLight(FormFactor.Tv) { Panes() }

        assertThat(textColor("Pixels per frame")).isEqualTo(ink3)
    }

    @Test
    fun `the summary of a row in the pane of the TV settings is the token ink-3 where the glass is off`() {
        showOverFlowingLight(FormFactor.Tv, BorealisLook(transparency = Transparency.Off)) {
            Panes()
        }

        assertThat(textColor("Pixels per frame")).isEqualTo(BorealisColors().ink3)
    }

    // Helpers

    private fun showOverFlowingLight(
        formFactor: FormFactor,
        look: BorealisLook = BorealisLook(),
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            BorealisTheme(formFactor, look) {
                GlassHostOver(
                    BackgroundStyle.FlowingLight,
                    background = { Box(Modifier.fillMaxSize()) },
                    content = content,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun textColor(text: String): Color {
        val results = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.single().layoutInput.style.color
    }

    @Composable
    private fun Card() {
        HostCard(
            name = "Desk PC",
            status = DotStatus.Online,
            statusText = "Online",
            onClick = {},
            modifier = Modifier.width(240.dp),
            detail = "192.168.1.20",
        )
    }

    @Composable
    private fun Rail() {
        NavigationRail(
            items = listOf(RailItem("Hosts", BorealisIcons.Hosts)),
            selectedIndex = 0,
            onSelect = {},
            footer = { RailFooter("Borealis", "0.1.0", "Borealis, version 0.1.0") },
        )
    }

    @Composable
    private fun Section() {
        CollapsibleSection("Stream", expanded = true, onExpandedChange = {}) {
            SettingsValueRow("Resolution", "Native", onClick = {}, summary = "Pixels per frame")
        }
    }

    @Composable
    private fun Panes() {
        SettingsTwoPane(
            groups = listOf(SettingsGroup("stream", "Stream")),
            selected = { "stream" },
            onSelect = {},
            modifier = Modifier.fillMaxSize(),
        ) {
            SettingsValueRow("Resolution", "Native", onClick = {}, summary = "Pixels per frame")
        }
    }
}
