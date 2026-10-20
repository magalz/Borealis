package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import kotlinx.coroutines.Dispatchers
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
        "High contrast",
        "Colour-blind palette",
    )

private val APPEARANCE_ROWS = listOf("Background", "Glass tint", "Glass blur")

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
    override fun viewModel(): SettingsViewModel =
        SettingsViewModel(
            repository,
            DefaultSettingsOptions(),
            facts,
            { resets++ },
            Dispatchers.Unconfined,
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
        sheetOption(label).performClick()
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
    fun `Appearance has the Background row, then the glass tint and the glass blur`() {
        show(keyboard = keyboard)
        open("Appearance")

        assertInOrder(APPEARANCE_ROWS)
        row("40 %").assertExists()
        row("8 dp").assertExists()
    }

    @Test
    fun `the Accessibility group has its six rows`() {
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
    fun `High contrast leaves the other Accessibility rows to change`() {
        stored(Settings.HIGH_CONTRAST to true)
        show(keyboard = keyboard)
        open("Accessibility")

        listOf("Reduce motion", "Text size", "High contrast", "Colour-blind palette").forEach {
            row(it).assertIsEnabled()
        }
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
