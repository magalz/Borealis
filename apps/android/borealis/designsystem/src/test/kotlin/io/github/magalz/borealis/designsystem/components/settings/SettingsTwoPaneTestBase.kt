package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the tests of [SettingsTwoPane] share: the TV window they lay the panes out in, the compose
 * rule, the key that is [selected], the keys that move focus and the pane in its theme. Each test
 * brings its own groups and its own rows.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
// Abstract so that the runner does not take it for a test class of its own.
@Suppress("AbstractClassCanBeConcreteClass")
abstract class SettingsTwoPaneTestBase(initialSelection: String) {
    @get:Rule val composeRule = createComposeRule()
    protected var selected by mutableStateOf(initialSelection)

    /** Presses [key] on the window and lets the UI settle. */
    protected fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    /**
     * The gamepad A button as a device delivers it. A click listens for OK, Enter and the numpad
     * Enter, not for A: when the app leaves A alone, Android hands the focused item the key OK in
     * its place (the fallback of the key map). Keys injected into a test skip that step, so the
     * test does it.
     */
    protected fun pressGamepadA() {
        press(Key.ButtonA)
        press(Key.DirectionCenter)
    }

    /**
     * The pane of [groups] in the theme of [formFactor], with [selected] as its selection. A
     * selection that the pane asks for is applied by [onSelect], which a test replaces to note it.
     */
    @Composable
    protected fun TwoPane(
        groups: List<SettingsGroup<String>>,
        modifier: Modifier = Modifier,
        formFactor: FormFactor = FormFactor.Tv,
        focusOnOpen: Boolean = false,
        selfScrolling: (String) -> Boolean = { false },
        onSelect: (String) -> Unit = { selected = it },
        content: @Composable ColumnScope.(String) -> Unit,
    ) {
        BorealisTheme(formFactor) {
            SettingsTwoPane(
                groups = groups,
                selected = { selected },
                onSelect = onSelect,
                modifier = modifier,
                selfScrolling = selfScrolling,
                focusOnOpen = focusOnOpen,
                content = content,
            )
        }
    }
}

/** Enough frames for one pane in each of them. */
internal const val FRAMES_TO_BUILD_ALL = 12

/** The groups of the tests that count what is built, laid out and drawn; "own" scrolls itself. */
internal val NUMBERED_GROUPS =
    listOf("g1", "g2", "g3", "g4", "own").map { SettingsGroup(it, "Group $it") }

/** A modifier that is never equal to itself, so that each recomposition that applies it shows. */
internal class CountingModifier(private val applied: IntArray) :
    ModifierNodeElement<CountingNode>() {
    override fun create() = CountingNode()

    override fun update(node: CountingNode) {
        applied[0]++
    }

    // On purpose: see the class.
    @Suppress("EqualsAlwaysReturnsTrueOrFalse") override fun equals(other: Any?) = false

    override fun hashCode() = System.identityHashCode(this)
}

internal class CountingNode : Modifier.Node()
