package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class StepListTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `every step shows its text`() {
        composeRule.setContent { Specimen(current = 1) }

        for (step in STEPS) composeRule.onNodeWithText(step).assertIsDisplayed()
    }

    @Test
    fun `the steps before the current one are done`() {
        composeRule.setContent { Specimen(current = 2) }

        composeRule.onNodeWithText("Find the host").assertState("Done")
        composeRule.onNodeWithText("Pair").assertState("Done")
    }

    @Test
    fun `the current step is in progress and selected`() {
        composeRule.setContent { Specimen(current = 2) }

        composeRule.onNodeWithText("Start the app").assertState("In progress").assertIsSelected()
        composeRule.onNodeWithText("Pair").assertIsNotSelected()
    }

    @Test
    fun `the steps after the current one have no state`() {
        composeRule.setContent { Specimen(current = 1) }

        composeRule
            .onNodeWithText("Start the app")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            .assertIsNotSelected()
    }

    @Test
    fun `a failed step is announced as failed and is not in progress`() {
        composeRule.setContent { Specimen(current = 1, failedAt = 1) }

        composeRule.onNodeWithText("Pair").assertState("Failed").assertIsNotSelected()
        composeRule.onNodeWithText("Find the host").assertState("Done")
        composeRule
            .onNodeWithText("Start the app")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `the steps are one list of their count`() {
        composeRule.setContent { Specimen(current = 0) }

        val list =
            composeRule
                .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
                .fetchSemanticsNode()
        val info = list.config[SemanticsProperties.CollectionInfo]
        assertThat(info.rowCount).isEqualTo(STEPS.size)
    }

    @Test
    fun `the number of a step is not read out apart from its text`() {
        composeRule.setContent { Specimen(current = 0) }

        // The circle is decoration: the step is one node whose text is the label alone.
        composeRule
            .onNode(hasText("Find the host"))
            .assert(
                SemanticsMatcher("has only the label as text") {
                    it.config[SemanticsProperties.Text].map { text -> text.text } ==
                        listOf("Find the host")
                }
            )
    }

    @Test
    @Config(qualifiers = TV)
    fun `the number of a step is 13 sp on a TV`() {
        composeRule.setContent { Specimen(current = 1, formFactor = FormFactor.Tv) }

        assertThat(numberSize("2")).isEqualTo(13.sp)
        assertThat(numberSize("3")).isEqualTo(13.sp)
    }

    @Test
    fun `the number of a step stays 12 sp on a phone`() {
        composeRule.setContent { Specimen(current = 1) }

        assertThat(numberSize("2")).isEqualTo(12.sp)
        assertThat(numberSize("3")).isEqualTo(12.sp)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `the number of a step stays 12 sp on a handheld`() {
        composeRule.setContent { Specimen(current = 1, formFactor = FormFactor.Handheld) }

        assertThat(numberSize("2")).isEqualTo(12.sp)
        assertThat(numberSize("3")).isEqualTo(12.sp)
    }

    // The circle is decoration and has no semantics of its own, so it is read from the unmerged
    // tree.
    private fun numberSize(number: String) =
        composeRule
            .onNodeWithText(number, useUnmergedTree = true)
            .textLayout()
            .layoutInput
            .style
            .fontSize

    private fun SemanticsNodeInteraction.assertState(state: String) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))

    @Composable
    private fun Specimen(
        current: Int,
        failedAt: Int? = null,
        formFactor: FormFactor = FormFactor.Phone,
    ) {
        BorealisTheme(formFactor) { StepList(STEPS, current, failedAt = failedAt) }
    }

    private companion object {
        val STEPS = listOf("Find the host", "Pair", "Start the app")
        const val TV = "w960dp-h540dp-land-television-xhdpi"
        const val HANDHELD = "w800dp-h360dp-land-xhdpi"
    }
}
