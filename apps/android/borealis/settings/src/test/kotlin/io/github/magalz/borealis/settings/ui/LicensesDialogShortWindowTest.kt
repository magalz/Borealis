package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val TV_300 = "w960dp-h300dp-land-television-xhdpi"
private const val TV_260 = "w960dp-h260dp-land-television-xhdpi"
private const val PHONE_300 = "w360dp-h300dp-xhdpi"
private const val PHONE_260 = "w360dp-h260dp-xhdpi"
private const val LINES = 40

/**
 * The licences dialog in a short window, such as a phone in landscape with its system bars. The
 * dialog scrolls its content when the window leaves too little room after its title, its Close
 * button and its padding, so the text may not be taller than what is left: a focus target that runs
 * on under Close is not above Close for the focus search, and Up from Close does not find it.
 */
abstract class LicensesDialogShortWindowBase : PhoneSettingsTestBase() {
    /** Opens the licences dialog, with a long text, on the form factor of the subclass. */
    protected abstract fun openLicences()

    protected val text: SemanticsNodeInteraction
        get() =
            composeRule.onNode(
                hasText("Licence line 1", substring = true) and hasAnyAncestor(isDialog())
            )

    protected val close: SemanticsNodeInteraction
        get() = composeRule.onNode(hasText("Close") and hasAnyAncestor(isDialog()))

    protected fun openedWithLongLicences() {
        licenses = (1..LINES).joinToString("\n") { "Licence line $it" }
        openLicences()
    }

    protected fun openOnTelevision() {
        asTelevision()
        show(keyboard = true, initialGroup = SettingsGroupId.ABOUT)
        row("Open-source licences").performClick()
        composeRule.waitForIdle()
    }

    protected fun openOnPhone() {
        show(keyboard = true)
        header("ABOUT").performClick()
        composeRule.waitForIdle()
        row("Open-source licences").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the text is in view and wholly above Close`() {
        openedWithLongLicences()

        text.assertIsDisplayed()
        val textBounds = text.getUnclippedBoundsInRoot()
        val closeBounds = close.getUnclippedBoundsInRoot()
        assertThat(textBounds.bottom.value).isAtMost(closeBounds.top.value)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV_300)
class LicensesDialogTv300Test : LicensesDialogShortWindowBase() {
    override fun openLicences() = openOnTelevision()
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_300)
class LicensesDialogPhone300Test : LicensesDialogShortWindowBase() {
    override fun openLicences() = openOnPhone()
}

/** A phone has no initial focus in its touch mode, so only its layout is checked. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_260)
class LicensesDialogPhone260Test : LicensesDialogShortWindowBase() {
    override fun openLicences() = openOnPhone()
}

/** On a TV the D-pad is the only input: the text is the stop above Close. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV_260)
class LicensesDialogTv260Test : LicensesDialogShortWindowBase() {
    override fun openLicences() = openOnTelevision()

    private fun press(target: SemanticsNodeInteraction, key: Key) {
        target.performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun scrolled(): Float =
        text.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun scrollEnd(): Float =
        text.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    @Test
    fun `Up from Close reaches the text`() {
        openedWithLongLicences()
        close.assertIsFocused()

        press(close, Key.DirectionUp)

        text.assertIsFocused()
    }

    @Test
    fun `Down scrolls the text to its end and one more Down goes to Close`() {
        openedWithLongLicences()
        press(close, Key.DirectionUp)

        repeat(LINES) { if (scrolled() < scrollEnd()) press(text, Key.DirectionDown) }
        assertThat(scrolled()).isEqualTo(scrollEnd())
        text.assertIsFocused()
        press(text, Key.DirectionDown)

        close.assertIsFocused()
    }
}
