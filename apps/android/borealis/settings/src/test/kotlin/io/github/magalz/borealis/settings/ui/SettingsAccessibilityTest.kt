package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.options.DefaultSettingsOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private val ACCESSIBILITY_ROWS =
    listOf(
        "Reduce motion",
        "Transparency",
        "Thicker focus outline",
        "Text size",
        "Text spacing",
        "High contrast",
        "Colour-blind palette",
    )

private val APPEARANCE_ROWS =
    listOf(
        "Font",
        "Accent colour",
        "Background",
        "Background intensity",
        "Card density",
        "Glass tint",
        "Glass blur",
    )

/** The fonts of the Font row, each with the purpose line that is its summary. */
private val FONTS =
    listOf(
        "IBM Plex Sans" to "Default. Clear at small sizes, covers many languages.",
        "Atkinson Hyperlegible Next" to
            "Made for low vision: letters that look alike are drawn to differ.",
        "Luciole" to "Made with readers with low vision and tested in a published study.",
        "Lexend" to "Wider spacing built into the letters, for text that feels crowded.",
        "Andika" to "Made for new readers: mirror letters such as b and d look different.",
        "B612" to "Made for cockpit screens: quick to read at a glance and from a distance.",
        "System font" to "Uses your device's font.",
    )

private val CARD_DENSITIES =
    listOf(
        "Comfortable" to null,
        "Compact" to "More cards in each row.",
        "Dense" to "More cards in each row, with less padding.",
    )

private val TEXT_SPACINGS =
    listOf(
        "Normal" to null,
        "Wide" to "More space between letters, words and lines.",
        "Wider" to "Even more space, for text that feels crowded.",
    )

/** The texts that a screen reader reads for the node: its [labels] and no other. */
private fun hasTexts(vararg labels: String) =
    SemanticsMatcher("has the texts ${labels.toList()}") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } == labels.toList()
    }

private val ACCESSIBILITY_SUMMARIES =
    listOf(
        "On removes animations; Off keeps them even when the device removes them. " +
            "Follow system uses the device's animation setting.",
        "Reduced and Off override Glass tint and Glass blur.",
        "Draws a thicker, lighter outline around the focused item.",
        "Scales the text of Borealis on top of the system font size.",
        "Uses solid panels, brighter text, a dimmed background and the thicker outline.",
        "Uses status colours that are easy to tell apart with red-green or blue-yellow " +
            "colour blindness.",
    )

private val APPEARANCE_SUMMARIES =
    listOf(
        "How much the panels tint the background. Higher is more opaque.",
        "How much the panels blur the background.",
    )

private const val NOTE_OFF = "Transparency is set to Off in Accessibility"
private const val NOTE_REDUCED = "Transparency is set to Reduced in Accessibility"
private const val NOTE_CONTRAST = "High contrast is on in Accessibility"

/**
 * What the screens of the three form factors share about the Accessibility group and the glass rows
 * of Appearance (ADR 0057, decision 11). The rules of which row is blocked are the real ones.
 */
abstract class SettingsLookTestBase : SettingsScreenTestBase() {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun viewModel(): SettingsViewModel =
        SettingsViewModel(
            repository,
            DefaultSettingsOptions(),
            facts,
            { resets++ },
            UnconfinedTestDispatcher(),
        )

    /**
     * Whether the window is in the keyboard input mode: a TV and a handheld are, a phone is not.
     */
    protected open val keyboard: Boolean = true

    /** Opens the group (a TV or a handheld) or the section (a phone) with [title]. */
    protected abstract fun open(title: String)

    protected fun top(title: String): Float =
        composeRule.onNodeWithText(title).getUnclippedBoundsInRoot().top.value

    protected fun assertInOrder(titles: List<String>) {
        titles.forEach { row(it) }
        assertThat(titles.map { top(it) }).isInStrictOrder()
    }

    private fun choose(title: String, label: String) {
        row(title).performClick()
        composeRule.waitForIdle()
        // A long list, such as the fonts with their lines, scrolls in the window of a TV.
        sheetOption(label).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    /** The list of [title] offers exactly [labels]; the sheet is closed by choosing [current]. */
    private fun assertOffers(
        title: String,
        labels: List<String>,
        current: String = labels.first(),
    ) {
        row(title).performClick()
        composeRule.waitForIdle()
        labels.forEach { sheetOption(it).assertExists() }
        composeRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertCountEquals(labels.size)
        sheetOption(current).performClick()
        composeRule.waitForIdle()
    }

    protected fun assertDisabledWithNote(note: String) {
        open("Appearance")
        listOf("Glass tint", "Glass blur").forEach { row(it).assertIsNotEnabled() }
        composeRule.onAllNodesWithText(note).assertCountEquals(2)
    }

    protected fun assertEnabledWithNoNote() {
        open("Appearance")
        listOf("Glass tint", "Glass blur").forEach { row(it).assertIsEnabled() }
        listOf(NOTE_OFF, NOTE_REDUCED, NOTE_CONTRAST).forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun `Appearance has the Font and Accent colour rows first, the glass rows last`() {
        show(keyboard = keyboard)
        open("Appearance")

        assertInOrder(APPEARANCE_ROWS)
        row("IBM Plex Sans").assertExists()
        row("Violet").assertExists()
        row("Aurora curtains").assertExists()
        row("100 %").assertExists()
        row("Comfortable").assertExists()
        row("40 %").assertExists()
        row("8 dp").assertExists()
    }

    @Test
    fun `the Accessibility group has its seven rows`() {
        show(keyboard = keyboard)
        open("Accessibility")

        assertInOrder(ACCESSIBILITY_ROWS)
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
        composeRule.onNodeWithText("Background").assertDoesNotExist()
    }

    @Test
    fun `the rows start at their defaults`() {
        show(keyboard = keyboard)
        open("Accessibility")

        row("Follow system").assertExists()
        row("Standard").assertExists()
        row("100 %").assertExists()
        row("Normal").assertExists()
        row("Thicker focus outline").assertIsOff()
        row("High contrast").assertIsOff()
        row("Off").assertExists()
    }

    @Test
    fun `each row of the Accessibility group has a short summary`() {
        show(keyboard = keyboard)
        open("Accessibility")

        ACCESSIBILITY_SUMMARIES.forEach { row(it).assertExists() }
    }

    @Test
    fun `the glass rows have a short summary`() {
        show(keyboard = keyboard)
        open("Appearance")

        APPEARANCE_SUMMARIES.forEach { row(it).assertExists() }
    }

    @Test
    fun `Font offers the seven fonts, each with its purpose line as the summary`() {
        show(keyboard = keyboard)
        open("Appearance")

        row("Font").performClick()
        composeRule.waitForIdle()

        FONTS.forEach { (name, line) -> sheetOption(name).assert(hasTexts(name, line)) }
        composeRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertCountEquals(FONTS.size)
        sheetOption("IBM Plex Sans")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun `Font stores the stable id of the font that is chosen`() {
        show(keyboard = keyboard)
        open("Appearance")

        choose("Font", "Lexend")
        assertThat(snapshot[Settings.FONT]).isEqualTo("lexend")
        row("Lexend").assertExists()
        choose("Font", "Atkinson Hyperlegible Next")
        assertThat(snapshot[Settings.FONT]).isEqualTo("atkinson_next")
        choose("Font", "System font")
        assertThat(snapshot[Settings.FONT]).isEqualTo("system")
        choose("Font", "IBM Plex Sans")
        assertThat(snapshot[Settings.FONT]).isEqualTo("plex")
    }

    @Test
    fun `Accent colour offers Violet, Teal and White, and stores the id`() {
        show(keyboard = keyboard)
        open("Appearance")
        assertOffers("Accent colour", listOf("Violet", "Teal", "White"))

        choose("Accent colour", "Teal")
        assertThat(snapshot[Settings.ACCENT]).isEqualTo("teal")
        row("Teal").assertExists()
        choose("Accent colour", "White")
        assertThat(snapshot[Settings.ACCENT]).isEqualTo("white")
    }

    @Test
    fun `Background intensity offers 100, 75, 50 and 25 percent, and stores the id`() {
        show(keyboard = keyboard)
        open("Appearance")
        assertOffers("Background intensity", listOf("100 %", "75 %", "50 %", "25 %"))

        choose("Background intensity", "50 %")

        assertThat(snapshot[Settings.BACKGROUND_INTENSITY]).isEqualTo("50")
        row("50 %").assertExists()
    }

    @Test
    fun `Card density offers Comfortable, Compact and Dense, with a line under the last two`() {
        show(keyboard = keyboard)
        open("Appearance")
        assertOffers("Card density", CARD_DENSITIES.map { it.first })

        row("Card density").performClick()
        composeRule.waitForIdle()
        CARD_DENSITIES.forEach { (name, line) ->
            sheetOption(name).assert(hasTexts(*listOfNotNull(name, line).toTypedArray()))
        }
        sheetOption("Comfortable").performClick()
        composeRule.waitForIdle()
        choose("Card density", "Dense")
        assertThat(snapshot[Settings.CARD_DENSITY]).isEqualTo("dense")
        choose("Card density", "Compact")
        assertThat(snapshot[Settings.CARD_DENSITY]).isEqualTo("compact")
    }

    @Test
    fun `Text spacing offers Normal, Wide and Wider, with a line under the last two`() {
        show(keyboard = keyboard)
        open("Accessibility")
        assertOffers("Text spacing", TEXT_SPACINGS.map { it.first })

        row("Text spacing").performClick()
        composeRule.waitForIdle()
        TEXT_SPACINGS.forEach { (name, line) ->
            sheetOption(name).assert(hasTexts(*listOfNotNull(name, line).toTypedArray()))
        }
        sheetOption("Normal").performClick()
        composeRule.waitForIdle()
        choose("Text spacing", "Wide")
        assertThat(snapshot[Settings.TEXT_SPACING]).isEqualTo("wide")
        row("Wide").assertExists()
        choose("Text spacing", "Wider")
        assertThat(snapshot[Settings.TEXT_SPACING]).isEqualTo("wider")
    }

    @Test
    fun `a stored id that nothing has shows the default of Sprint 11 as the chosen one`() {
        stored(
            Settings.FONT to "bogus",
            Settings.ACCENT to "bogus",
            Settings.BACKGROUND_INTENSITY to "60",
            Settings.CARD_DENSITY to "bogus",
            Settings.TEXT_SPACING to "bogus",
        )
        show(keyboard = keyboard)
        open("Appearance")

        row("IBM Plex Sans").assertExists()
        row("Violet").assertExists()
        row("100 %").assertExists()
        row("Comfortable").assertExists()
        listOf("bogus", "60 %").forEach { composeRule.onNodeWithText(it).assertDoesNotExist() }
        row("Font").performClick()
        composeRule.waitForIdle()
        sheetOption("IBM Plex Sans")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        sheetOption("IBM Plex Sans").performClick()
        composeRule.waitForIdle()

        open("Accessibility")
        row("Normal").assertExists()
        composeRule.onNodeWithText("bogus").assertDoesNotExist()
    }

    @Test
    fun `High contrast and Transparency Off leave the new Appearance rows to change`() {
        stored(Settings.HIGH_CONTRAST to true, Settings.TRANSPARENCY to "off")
        show(keyboard = keyboard)
        open("Appearance")

        listOf("Font", "Accent colour", "Background intensity", "Card density").forEach {
            row(it).assertIsEnabled()
        }
        composeRule.onAllNodesWithText(NOTE_CONTRAST).assertCountEquals(2)
    }

    @Test
    fun `Reduce motion stores the id of its choice`() {
        show(keyboard = keyboard)
        open("Accessibility")

        choose("Reduce motion", "On")

        assertThat(snapshot[Settings.REDUCE_MOTION]).isEqualTo("on")
        row("On").assertExists()
        choose("Reduce motion", "Off")
        assertThat(snapshot[Settings.REDUCE_MOTION]).isEqualTo("off")
        choose("Reduce motion", "Follow system")
        assertThat(snapshot[Settings.REDUCE_MOTION]).isEqualTo("follow_system")
    }

    @Test
    fun `Reduce motion offers Follow system, On and Off`() {
        show(keyboard = keyboard)
        open("Accessibility")

        assertOffers("Reduce motion", listOf("Follow system", "On", "Off"))
    }

    @Test
    fun `Transparency offers Standard, Reduced and Off, and stores the id`() {
        show(keyboard = keyboard)
        open("Accessibility")
        assertOffers("Transparency", listOf("Standard", "Reduced", "Off"))

        choose("Transparency", "Reduced")
        assertThat(snapshot[Settings.TRANSPARENCY]).isEqualTo("reduced")
        choose("Transparency", "Off")
        assertThat(snapshot[Settings.TRANSPARENCY]).isEqualTo("off")
    }

    @Test
    fun `Text size offers 100, 115 and 130 percent, and stores the id`() {
        show(keyboard = keyboard)
        open("Accessibility")
        assertOffers("Text size", listOf("100 %", "115 %", "130 %"))

        choose("Text size", "115 %")
        assertThat(snapshot[Settings.TEXT_SIZE]).isEqualTo("larger")
        choose("Text size", "130 %")
        assertThat(snapshot[Settings.TEXT_SIZE]).isEqualTo("largest")
        row("130 %").assertExists()
    }

    @Test
    fun `The colour-blind palette offers Off, Red-green and Blue-yellow, and stores the id`() {
        show(keyboard = keyboard)
        open("Accessibility")
        assertOffers("Colour-blind palette", listOf("Off", "Red-green", "Blue-yellow"))

        choose("Colour-blind palette", "Red-green")
        assertThat(snapshot[Settings.COLOUR_VISION]).isEqualTo("red_green")
        choose("Colour-blind palette", "Blue-yellow")
        assertThat(snapshot[Settings.COLOUR_VISION]).isEqualTo("blue_yellow")
    }

    @Test
    fun `the two switches store their value`() {
        show(keyboard = keyboard)
        open("Accessibility")

        row("Thicker focus outline").performClick()
        composeRule.waitForIdle()
        row("High contrast").performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.THICK_FOCUS]).isTrue()
        assertThat(snapshot[Settings.HIGH_CONTRAST]).isTrue()
        row("Thicker focus outline").assertIsOn()
        row("High contrast").assertIsOn()
    }

    @Test
    fun `a stored id that nothing has shows the default as the chosen one`() {
        stored(
            Settings.TEXT_SIZE to "huge",
            Settings.REDUCE_MOTION to "maybe",
            Settings.TRANSPARENCY to "glassy",
            Settings.COLOUR_VISION to "all",
        )
        show(keyboard = keyboard)
        open("Accessibility")

        row("100 %").assertExists()
        row("Follow system").assertExists()
        row("Standard").assertExists()
        row("Off").assertExists()
        listOf("huge", "maybe", "glassy", "all").forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
        row("Text size").performClick()
        composeRule.waitForIdle()
        sheetOption("100 %")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun `Glass tint offers 30 to 85 percent, and stores the id`() {
        show(keyboard = keyboard)
        open("Appearance")
        assertOffers(
            "Glass tint",
            listOf("30 %", "40 %", "50 %", "60 %", "70 %", "85 %"),
            current = "40 %",
        )

        choose("Glass tint", "70 %")

        assertThat(snapshot[Settings.GLASS_TINT]).isEqualTo("70")
        row("70 %").assertExists()
    }

    @Test
    fun `Glass blur offers 8 to 48 dp, and stores the id`() {
        show(keyboard = keyboard)
        open("Appearance")
        assertOffers("Glass blur", listOf("8 dp", "16 dp", "24 dp", "32 dp", "48 dp"))

        choose("Glass blur", "32 dp")

        assertThat(snapshot[Settings.GLASS_BLUR]).isEqualTo("32")
        row("32 dp").assertExists()
    }

    @Test
    fun `a stored tint or blur that is not offered shows the default as the chosen one`() {
        stored(Settings.GLASS_TINT to "35", Settings.GLASS_BLUR to "bogus")
        show(keyboard = keyboard)
        open("Appearance")

        row("40 %").assertExists()
        row("8 dp").assertExists()
        composeRule.onNodeWithText("35 %").assertDoesNotExist()
    }

    @Test
    fun `the glass rows can be changed with Standard transparency`() {
        show(keyboard = keyboard)

        assertEnabledWithNoNote()
    }

    @Test
    fun `the glass rows are disabled while Transparency is Off, and say so`() {
        stored(Settings.TRANSPARENCY to "off")
        show(keyboard = keyboard)

        assertDisabledWithNote(NOTE_OFF)
    }

    @Test
    fun `the glass rows are disabled while Transparency is Reduced, and say so`() {
        stored(Settings.TRANSPARENCY to "reduced")
        show(keyboard = keyboard)

        assertDisabledWithNote(NOTE_REDUCED)
    }

    @Test
    fun `the glass rows are disabled while High contrast is on, and say so`() {
        stored(Settings.HIGH_CONTRAST to true)
        show(keyboard = keyboard)

        assertDisabledWithNote(NOTE_CONTRAST)
    }

    @Test
    fun `High contrast is the reason when Transparency is also lowered`() {
        stored(Settings.HIGH_CONTRAST to true, Settings.TRANSPARENCY to "off")
        show(keyboard = keyboard)

        assertDisabledWithNote(NOTE_CONTRAST)
        composeRule.onNodeWithText(NOTE_OFF).assertDoesNotExist()
    }

    @Test
    fun `High contrast disables Transparency and Thicker focus outline, and says so`() {
        stored(Settings.HIGH_CONTRAST to true)
        show(keyboard = keyboard)
        open("Accessibility")

        listOf("Transparency", "Thicker focus outline").forEach { row(it).assertIsNotEnabled() }
        composeRule.onAllNodesWithText(NOTE_CONTRAST).assertCountEquals(2)
    }

    @Test
    fun `Thicker focus outline shows on while High contrast is on, whatever is stored`() {
        stored(Settings.HIGH_CONTRAST to true, Settings.THICK_FOCUS to false)
        show(keyboard = keyboard)
        open("Accessibility")

        row("Thicker focus outline")
            .assertIsNotEnabled()
            .assertIsOn()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.On,
                )
            )
        assertThat(snapshot[Settings.THICK_FOCUS]).isFalse()
    }

    @Test
    fun `Thicker focus outline shows what is stored again when High contrast is turned off`() {
        show(keyboard = keyboard)
        open("Accessibility")
        row("Thicker focus outline").assertIsOff()

        row("High contrast").performClick()
        composeRule.waitForIdle()
        row("Thicker focus outline").assertIsOn()

        row("High contrast").performClick()
        composeRule.waitForIdle()
        row("Thicker focus outline").assertIsOff()
        assertThat(snapshot[Settings.THICK_FOCUS]).isFalse()
    }

    @Test
    fun `High contrast leaves the other Accessibility rows to change`() {
        stored(Settings.HIGH_CONTRAST to true)
        show(keyboard = keyboard)
        open("Accessibility")

        listOf(
                "Reduce motion",
                "Text size",
                "Text spacing",
                "High contrast",
                "Colour-blind palette",
            )
            .forEach { row(it).assertIsEnabled() }
    }

    @Test
    fun `Transparency and Thicker focus outline can be changed without High contrast`() {
        stored(Settings.TRANSPARENCY to "reduced")
        show(keyboard = keyboard)
        open("Accessibility")

        listOf("Transparency", "Thicker focus outline").forEach { row(it).assertIsEnabled() }
        composeRule.onNodeWithText(NOTE_CONTRAST).assertDoesNotExist()
    }

    @Test
    fun `turning High contrast on disables the two rows at once, and off frees them`() {
        show(keyboard = keyboard)
        open("Accessibility")

        row("High contrast").performClick()
        composeRule.waitForIdle()
        listOf("Transparency", "Thicker focus outline").forEach { row(it).assertIsNotEnabled() }

        row("High contrast").performClick()
        composeRule.waitForIdle()
        listOf("Transparency", "Thicker focus outline").forEach { row(it).assertIsEnabled() }
    }

    @Test
    fun `a disabled glass tint opens no list`() {
        stored(Settings.TRANSPARENCY to "off")
        show(keyboard = keyboard)
        open("Appearance")

        row("Glass tint").performClick()
        composeRule.waitForIdle()

        sheetOption("30 %").assertDoesNotExist()
        assertThat(snapshot[Settings.GLASS_TINT]).isEqualTo(Settings.GLASS_TINT.default)
    }

    @Test
    fun `choosing Off for Transparency disables the glass rows at once`() {
        show(keyboard = keyboard)
        open("Accessibility")
        choose("Transparency", "Off")

        assertDisabledWithNote(NOTE_OFF)

        open("Accessibility")
        choose("Transparency", "Standard")
        assertEnabledWithNoNote()
    }

    @Test
    fun `a disabled glass row still shows the value that is stored`() {
        stored(Settings.TRANSPARENCY to "off", Settings.GLASS_TINT to "70")
        show(keyboard = keyboard)
        open("Appearance")

        row("70 %").assertIsDisplayed()
    }
}

/** The group list of a TV, which opens a group by a click on its entry. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class SettingsLookTvTest : SettingsLookTestBase() {
    @Before fun theDeviceIsATelevision() = asTelevision()

    override fun open(title: String) {
        openGroup(title)
    }

    @Test
    fun `Accessibility is the group after Appearance`() {
        show(keyboard = keyboard)

        val titles = listOf("Appearance", "Accessibility", "Privacy and diagnostics")
        assertThat(titles.map { groupTop(it) }).isInStrictOrder()
    }
}

/** The group list of a handheld, which may scroll. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = HANDHELD)
class SettingsLookHandheldTest : SettingsLookTestBase() {
    override fun open(title: String) {
        openGroup(title)
    }

    @Test
    fun `Accessibility is the group after Appearance`() {
        show(keyboard = keyboard)

        val titles = listOf("Appearance", "Accessibility", "Privacy and diagnostics")
        assertThat(titles.map { groupTop(it) }).isInStrictOrder()
    }
}

/** The sections of a phone: Accessibility is one more, after Appearance. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class SettingsLookPhoneTest : SettingsLookTestBase() {
    override val keyboard = false

    override fun open(title: String) {
        val header = composeRule.onNodeWithText(title.uppercase())
        header.performScrollTo()
        // Opened by a first tap and left open: a second call for the same section must not close
        // it.
        val opened =
            header.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) ==
                "Expanded"
        if (!opened) header.performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `Accessibility is a section after Appearance, collapsed at first`() {
        show(keyboard = keyboard)

        val titles = listOf("APPEARANCE", "ACCESSIBILITY", "ABOUT")
        titles.forEach { composeRule.onNodeWithText(it).performScrollTo() }
        assertThat(titles.map { top(it) }).isInStrictOrder()
        composeRule
            .onNodeWithText("ACCESSIBILITY")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        composeRule.onNodeWithText("Reduce motion").assertDoesNotExist()
    }
}

/** Opens the group [title] of the two panes. */
private fun SettingsScreenTestBase.openGroup(title: String) {
    val entry = groupEntry(title)
    entry.performScrollTo()
    entry.performClick()
    composeRule.waitForIdle()
}

private fun SettingsScreenTestBase.groupEntry(title: String): SemanticsNodeInteraction =
    composeRule.onNode(
        hasText(title) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    )

private fun SettingsScreenTestBase.groupTop(title: String): Float =
    groupEntry(title).getUnclippedBoundsInRoot().top.value
