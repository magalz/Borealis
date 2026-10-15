package io.github.magalz.borealis.library.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.testing.HANDHELD
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The rail beside Hosts and Apps: Hosts is selected, and the other items call back. */
@RunWith(AndroidJUnit4::class)
class LibraryScaffoldTest {
    @get:Rule val composeRule = createComposeRule()

    private var hosts = 0
    private var addHost = 0
    private var settings = 0

    private val isRailItem = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun show(formFactor: FormFactor, showRail: Boolean = true) {
        composeRule.setContent {
            LibraryScaffold(
                formFactor,
                showRail,
                RailActions({ hosts++ }, { addHost++ }, { settings++ }),
            ) {
                Box(Modifier.fillMaxSize().testTag("content"))
            }
        }
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV has a rail with Hosts selected, Add host and Settings`() {
        show(FormFactor.Tv)

        composeRule.onNode(isRailItem and hasText("Hosts")).assertIsSelected()
        composeRule.onNode(isRailItem and hasText("Add host")).assertIsDisplayed()
        composeRule.onNode(isRailItem and hasText("Settings")).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `a handheld has the rail too`() {
        show(FormFactor.Handheld)

        composeRule.onNode(isRailItem and hasText("Hosts")).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the items call back`() {
        show(FormFactor.Tv)

        composeRule.onNode(isRailItem and hasText("Hosts")).performClick()
        composeRule.onNode(isRailItem and hasText("Add host")).performClick()
        composeRule.onNode(isRailItem and hasText("Settings")).performClick()

        assertThat(hosts).isEqualTo(1)
        assertThat(addHost).isEqualTo(1)
        assertThat(settings).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the content takes the width the rail leaves`() {
        show(FormFactor.Tv)

        val content = composeRule.onNodeWithTag("content").getUnclippedBoundsInRoot()

        assertThat(content.left.value).isEqualTo(RAIL_WIDTH_DP)
        assertThat((content.right - content.left).value).isEqualTo(TV_WIDTH_DP - RAIL_WIDTH_DP)
    }

    @Test
    @Config(qualifiers = TV)
    fun `without showRail the content has the whole width`() {
        show(FormFactor.Tv, showRail = false)

        composeRule.onNode(isRailItem).assertDoesNotExist()
        assertThat(composeRule.onNodeWithTag("content").getUnclippedBoundsInRoot().left.value)
            .isEqualTo(0f)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone has no rail`() {
        show(FormFactor.Phone)

        composeRule.onNode(isRailItem).assertDoesNotExist()
    }

    private companion object {
        const val RAIL_WIDTH_DP = 200f
        const val TV_WIDTH_DP = 960f
    }
}
