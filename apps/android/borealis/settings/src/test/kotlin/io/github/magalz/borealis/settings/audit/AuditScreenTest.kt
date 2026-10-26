package io.github.magalz.borealis.settings.audit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Audit trail screen (UC-007-T1, T2 and the empty trail): what shows and how it responds. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AuditScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private val selected = mutableListOf<String>()
    private var dismissed = 0
    private var backs = 0

    private val sessionEnd = AuditRow("end", AuditType.SESSION_END, "20:42", "Desk PC · Desktop")
    private val sessionStart = AuditRow("start", AuditType.SESSION_START, "20:34", "Desk PC · HEVC")
    private val certificate =
        AuditRow("cert", AuditType.CERTIFICATE_CHANGED, "Mon 17:00", "Living Room PC")
    private val pairingFailure =
        AuditDetail(
            AuditType.PAIRING_FAILURE,
            "12 Oct 2026 18:02:09",
            AuditOutcome.FAILURE,
            "Studio (192.168.1.40)",
            "Host answered 401 after the PIN",
        )

    private fun state(
        vararg rows: AuditRow,
        detail: AuditDetail? = null,
        loading: Boolean = false,
    ) = AuditUiState(loading = loading, entries = rows.toList(), detail = detail)

    private fun show(state: AuditUiState, formFactor: FormFactor = FormFactor.Phone) {
        composeRule.setContent { Screen(state, formFactor) }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Screen(state: AuditUiState, formFactor: FormFactor) =
        AuditScreen(
            state = state,
            formFactor = formFactor,
            onEntrySelected = { selected += it },
            onDetailDismissed = { dismissed++ },
            onBack = { backs++ },
        )

    private fun entry(index: Int): SemanticsNodeInteraction =
        composeRule.onNodeWithTag("audit:entry:$index")

    @Test
    fun `each entry shows its time, its title, its event code and its detail`() {
        show(state(sessionEnd, sessionStart))

        composeRule.onNodeWithText("20:42").assertIsDisplayed()
        composeRule.onNodeWithText("Stream ended").assertIsDisplayed()
        composeRule.onNodeWithText("SESSION_END").assertIsDisplayed()
        composeRule.onNodeWithText("Desk PC · Desktop").assertIsDisplayed()
        composeRule.onNodeWithText("Stream started").assertIsDisplayed()
        composeRule.onNodeWithText("SESSION_START").assertIsDisplayed()
    }

    @Test
    fun `the entries are listed in the order of the state, newest first`() {
        show(state(sessionEnd, sessionStart))

        val first = entry(0).fetchSemanticsNode().boundsInRoot.top
        val second = entry(1).fetchSemanticsNode().boundsInRoot.top

        assertThat(first).isLessThan(second)
        entry(0).assertTextContains("Stream ended")
        entry(1).assertTextContains("Stream started")
    }

    @Test
    fun `every event type has its own title and shows its code`() {
        val types = AuditType.entries
        show(state(*types.map { AuditRow(it.name, it, "20:00", null) }.toTypedArray()))

        for ((index, type) in types.withIndex()) {
            composeRule.onNode(hasScrollAction()).performScrollToIndex(index)
            val title = checkNotNull(TITLES[type]) { "no expected title for $type" }
            entry(index).assertTextContains(title)
            entry(index).assertTextContains(type.name)
        }
        assertThat(TITLES.keys).containsExactlyElementsIn(types)
        assertThat(TITLES.values.toSet()).hasSize(types.size)
    }

    @Test
    fun `a certificate change has its title in the warning colour`() {
        show(state(certificate, sessionEnd))

        assertThat(composeRule.textColor("Host certificate changed"))
            .isEqualTo(BorealisColors().warningInk)
    }

    @Test
    fun `the other titles are in the primary ink`() {
        show(state(certificate, sessionEnd))

        assertThat(composeRule.textColor("Stream ended")).isEqualTo(BorealisColors().ink)
    }

    @Test
    fun `an entry without host and detail shows no detail line`() {
        show(state(AuditRow("pairing", AuditType.PAIRING_START, "18:00", null)))

        entry(0).assertTextContains("Pairing started")
        entry(0).assertTextContains("PAIRING_START")
    }

    @Test
    fun `an empty trail says that no events were recorded`() {
        show(state())

        composeRule.onNodeWithText("No events recorded yet.").assertIsDisplayed()
    }

    @Test
    fun `a trail that is still loading does not say that it is empty`() {
        show(state(loading = true))

        composeRule.onNodeWithText("No events recorded yet.").assertDoesNotExist()
    }

    @Test
    fun `a trail with entries does not say that it is empty`() {
        show(state(sessionEnd))

        composeRule.onNodeWithText("No events recorded yet.").assertDoesNotExist()
    }

    @Test
    fun `the screen says how long the entries are kept`() {
        show(state(sessionEnd))

        composeRule.onNodeWithText("Audit trail").assertIsDisplayed()
        composeRule.onNodeWithText("Kept for 90 days or 2,000 entries").assertIsDisplayed()
    }

    @Test
    fun `a long list scrolls to its last entry`() {
        val rows =
            List(40) {
                AuditRow(
                    "e$it",
                    AuditType.LAUNCH_ATTEMPT,
                    "10:${it.toString().padStart(2, '0')}",
                    "Desk PC · $it",
                )
            }
        show(state(*rows.toTypedArray()))
        composeRule.onNodeWithText("Desk PC · 39").assertDoesNotExist()

        composeRule.onNode(hasScrollAction()).performScrollToIndex(39)

        composeRule.onNodeWithText("Desk PC · 39").assertIsDisplayed()
    }

    @Test
    fun `a tap on an entry asks to open it`() {
        show(state(sessionEnd, sessionStart))

        entry(1).performClick()

        assertThat(selected).containsExactly("start")
    }

    @Test
    fun `the opened entry shows its time, title, code, result, host and detail in full`() {
        show(state(sessionEnd, detail = pairingFailure))

        composeRule.onNodeWithText("Pairing failed").assertIsDisplayed()
        composeRule.onNodeWithText("PAIRING_FAILURE").assertIsDisplayed()
        composeRule.onNodeWithText("12 Oct 2026 18:02:09").assertIsDisplayed()
        composeRule.onNodeWithText("Failed").assertIsDisplayed()
        composeRule.onNodeWithText("Studio (192.168.1.40)").assertIsDisplayed()
        composeRule.onNodeWithText("Host answered 401 after the PIN").assertIsDisplayed()
    }

    @Test
    fun `the opened entry replaces the list`() {
        show(state(sessionEnd, detail = pairingFailure))

        composeRule.onNodeWithText("Stream ended").assertDoesNotExist()
    }

    @Test
    fun `the opened entry leaves out the host and the detail it does not have`() {
        show(
            state(
                detail =
                    AuditDetail(
                        AuditType.PAIRING_START,
                        "12 Oct 2026 18:00:00",
                        AuditOutcome.SUCCESS,
                        null,
                        null,
                    )
            )
        )

        composeRule.onNodeWithText("Pairing started").assertIsDisplayed()
        composeRule.onNodeWithText("Succeeded").assertIsDisplayed()
        composeRule.onNodeWithText("Host").assertDoesNotExist()
        composeRule.onNodeWithText("Detail").assertDoesNotExist()
    }

    @Test
    fun `the opened certificate change has its title in the warning colour`() {
        show(
            state(
                detail =
                    AuditDetail(
                        AuditType.CERTIFICATE_CHANGED,
                        "12 Oct 2026 18:00:00",
                        AuditOutcome.FAILURE,
                        "Living Room PC",
                        null,
                    )
            )
        )

        assertThat(composeRule.textColor("Host certificate changed"))
            .isEqualTo(BorealisColors().warningInk)
    }

    @Test
    fun `Back closes the opened entry before it leaves the screen`() {
        show(state(sessionEnd, detail = pairingFailure))

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(dismissed).isEqualTo(1)
        assertThat(backs).isEqualTo(0)
    }

    @Test
    fun `Back with the list in view leaves the screen`() {
        show(state(sessionEnd))

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(backs).isEqualTo(1)
        assertThat(dismissed).isEqualTo(0)
    }

    @Test
    fun `the arrow of the phone bar leaves the screen from the list`() {
        show(state(sessionEnd))

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
        assertThat(dismissed).isEqualTo(0)
    }

    @Test
    fun `the arrow of the phone bar closes the opened entry first`() {
        show(state(sessionEnd, detail = pairingFailure))

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(dismissed).isEqualTo(1)
        assertThat(backs).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the list is no wider than 560dp`() {
        show(state(sessionEnd, sessionStart), FormFactor.Tv)

        val bounds = composeRule.onNode(hasScrollAction()).getUnclippedBoundsInRoot()

        assertThat((bounds.right - bounds.left).value).isAtMost(560f)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV an opened entry is no wider than 560dp`() {
        show(state(sessionEnd, detail = pairingFailure), FormFactor.Tv)

        val bounds = composeRule.onNode(hasScrollAction()).getUnclippedBoundsInRoot()

        assertThat((bounds.right - bounds.left).value).isAtMost(560f)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV has no arrow and Back leaves the screen`() {
        show(state(sessionEnd), FormFactor.Tv)

        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the first entry has focus`() {
        show(state(sessionEnd, sessionStart), FormFactor.Tv)

        entry(0).assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Down moves from entry to entry and Up moves back`() {
        composeRule.setContentInKeyboardMode {
            Screen(state(sessionEnd, sessionStart, certificate), FormFactor.Tv)
        }
        entry(0).assertIsFocused()

        entry(0).performKeyInput { pressKey(Key.DirectionDown) }
        entry(1).assertIsFocused()
        entry(1).performKeyInput { pressKey(Key.DirectionDown) }
        entry(2).assertIsFocused()
        entry(2).performKeyInput { pressKey(Key.DirectionUp) }
        entry(1).assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV OK on the focused entry asks to open it`() {
        composeRule.setContentInKeyboardMode {
            Screen(state(sessionEnd, sessionStart), FormFactor.Tv)
        }

        entry(0).performKeyInput { pressKey(Key.DirectionDown) }
        entry(1).performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(selected).containsExactly("start")
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the focus returns to the entry that was open`() {
        val state = mutableStateOf(state(sessionEnd, sessionStart, certificate))
        composeRule.setContentInKeyboardMode { Screen(state.value, FormFactor.Tv) }
        entry(0).performKeyInput { pressKey(Key.DirectionDown) }
        entry(1).performKeyInput { pressKey(Key.DirectionCenter) }

        state.value = state(sessionEnd, sessionStart, certificate, detail = pairingFailure)
        composeRule.waitForIdle()
        state.value = state(sessionEnd, sessionStart, certificate)
        composeRule.waitForIdle()

        entry(1).assertIsFocused()
    }

    private fun manyRows(count: Int) =
        List(count) {
            AuditRow("e$it", AuditType.LAUNCH_ATTEMPT, "10:00", "Desk PC · $it")
        }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV a deep entry is shown and focused again when the list comes back`() {
        val rows = manyRows(45).toTypedArray()
        val state = mutableStateOf(state(*rows))
        composeRule.setContentInKeyboardMode { Screen(state.value, FormFactor.Tv) }
        composeRule.onNode(hasScrollAction()).performScrollToIndex(32)
        entry(32).requestFocus()
        entry(32).performKeyInput { pressKey(Key.DirectionCenter) }
        assertThat(selected).containsExactly("e32")

        state.value = state(*rows, detail = pairingFailure)
        composeRule.waitForIdle()
        state.value = state(*rows)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Desk PC · 32").assertIsDisplayed()
        composeRule.onNodeWithText("Desk PC · 32").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV a deep entry is shown again even when newer entries arrived meanwhile`() {
        val rows = manyRows(45)
        val state = mutableStateOf(state(*rows.toTypedArray()))
        composeRule.setContentInKeyboardMode { Screen(state.value, FormFactor.Tv) }
        composeRule.onNode(hasScrollAction()).performScrollToIndex(32)
        entry(32).requestFocus()
        entry(32).performKeyInput { pressKey(Key.DirectionCenter) }
        val newer = List(5) { AuditRow("n$it", AuditType.SESSION_END, "10:01", "Newer $it") }

        state.value = state(*rows.toTypedArray(), detail = pairingFailure)
        composeRule.waitForIdle()
        state.value = state(*(newer + rows).toTypedArray())
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Desk PC · 32").assertIsDisplayed()
        composeRule.onNodeWithText("Desk PC · 32").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV an entry that arrives at the head does not move the focus to another entry`() {
        val first = AuditRow("a", AuditType.SESSION_END, "20:42", "summary A")
        val second = AuditRow("b", AuditType.SESSION_START, "20:34", "summary B")
        val third = AuditRow("c", AuditType.LAUNCH_ATTEMPT, "20:33", "summary C")
        val state = mutableStateOf(state(first, second, third))
        composeRule.setContentInKeyboardMode { Screen(state.value, FormFactor.Tv) }
        entry(1).requestFocus()
        composeRule.onNodeWithText("summary B").assertIsFocused()

        state.value =
            state(
                AuditRow("n", AuditType.PAIRING_START, "20:50", "summary N"),
                first,
                second,
                third,
            )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("summary B").assertIsFocused()
    }

    @Test
    fun `a cancelled event shows its result as cancelled in the opened entry`() {
        show(
            state(
                detail =
                    AuditDetail(
                        AuditType.PAIRING_START,
                        "12 Oct 2026 18:00:00",
                        AuditOutcome.CANCELLED,
                        "Studio",
                        null,
                    )
            )
        )

        composeRule.onNodeWithText("Result").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelled").assertIsDisplayed()
    }

    private val longDetail =
        AuditDetail(
            AuditType.PAIRING_FAILURE,
            "12 Oct 2026 18:02:09",
            AuditOutcome.FAILURE,
            "Studio (192.168.1.40)",
            (1..60).joinToString("\n") { "The host answered with line $it of its reason" },
        )

    /** The opened entry: the one node of the screen that scrolls. */
    private val opened: SemanticsNodeInteraction
        get() = composeRule.onNode(hasScrollAction())

    private fun scrolled(): Float =
        opened.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the opened entry has focus`() {
        composeRule.setContentInKeyboardMode { Screen(state(detail = longDetail), FormFactor.Tv) }

        opened.assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV a long opened entry scrolls with Down and back with Up`() {
        composeRule.setContentInKeyboardMode { Screen(state(detail = longDetail), FormFactor.Tv) }
        assertThat(scrolled()).isEqualTo(0f)

        opened.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        val afterDown = scrolled()
        opened.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        assertThat(afterDown).isGreaterThan(0f)
        assertThat(scrolled()).isLessThan(afterDown)
        opened.assertIsFocused()
    }
}
