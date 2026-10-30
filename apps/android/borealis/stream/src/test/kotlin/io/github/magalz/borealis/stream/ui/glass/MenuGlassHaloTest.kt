package io.github.magalz.borealis.stream.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.components.BackdropIconHalo
import io.github.magalz.borealis.designsystem.components.BackdropTextHalo
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The text on the video glass stands on a halo, and the segmented controls on the solid surface
 * (owner, 2026-10-29): every text of the menu and of the Quit popup draws the halo of a backdrop
 * text, except the labels of the segmented controls, whose container is the `surface` colour and so
 * does not show the video. The halo itself, and what it keeps over the lightest glass, is in
 * `TextHaloTest` of the design system and `contrast.py halo`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class MenuGlassHaloTest {
    @get:Rule val composeRule = createComposeRule()

    private val tokens = borealisColors(BorealisLook())
    private val surface = BorealisColors().surface

    private fun fixture(look: BorealisLook = BorealisLook()) =
        GlassFixture(composeRule, FakeVideoSource(completing = true)).also {
            it.look = look
            it.show()
            it.run(100)
        }

    /** The texts that the screen draws: a node with a text and a layout of it, with the text. */
    private fun texts(under: SemanticsMatcher? = null): List<Pair<String, SemanticsNode>> =
        composeRule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
                    .and(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))
                    .and(under ?: SemanticsMatcher("any") { true }),
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
            .map { node ->
                node.config[SemanticsProperties.Text].joinToString("") { it.text } to node
            }

    private fun SemanticsNode.hasHalo() = BackdropTextHalo in config && config[BackdropTextHalo]

    private val segmentLabels = listOf("Trackpad", "Direct", "Off", "Off", "Compact", "Detailed")

    // The halo

    @Test
    fun `every text of the menu has a halo, but the labels of the segmented controls`() {
        fixture()

        val found = texts()

        assertThat(found).isNotEmpty()
        assertThat(found.filter { !it.second.hasHalo() }.map { it.first })
            .containsExactlyElementsIn(segmentLabels)
    }

    @Test
    fun `the texts of the Quit popup have a halo, its title, its body and its two buttons`() {
        val glass = fixture()
        glass.openQuitPopup()

        val found = texts(hasAnyAncestor(glass.popupMatcher()))

        assertThat(found.map { it.first }).containsAtLeast("Quit Desktop?", "Cancel", "Quit")
        assertThat(found.filter { !it.second.hasHalo() }).isEmpty()
    }

    @Test
    fun `the colours of the text that has the halo are the four that it is measured for`() {
        fixture()
        val measured = setOf(tokens.ink, tokens.ink2, tokens.accentSoft, tokens.dangerInk)

        for ((text, node) in texts().filter { it.second.hasHalo() }) {
            val color =
                mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    .also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }
                    .single()
                    .layoutInput
                    .style
                    .color

            assertWithMessage("the colour of \"$text\"").that(color).isIn(measured)
        }
    }

    @Test
    fun `the chevrons of the rows that expand have the halo of an icon`() {
        fixture()

        val icons =
            composeRule
                .onAllNodes(SemanticsMatcher.keyIsDefined(BackdropIconHalo), useUnmergedTree = true)
                .fetchSemanticsNodes()

        assertThat(icons).hasSize(2)
        assertThat(icons.map { it.config[BackdropIconHalo] }).containsExactly(true, true)
    }

    @Test
    fun `where the surface is solid no text has a halo, and ink-3 is ink-3`() {
        fixture(BorealisLook(transparency = Transparency.Off))

        assertThat(texts().filter { it.second.hasHalo() }).isEmpty()
        assertThat(composeRule.textColor(REFRESH_NOTE)).isEqualTo(tokens.ink3)
    }

    // The segmented controls

    @Test
    fun `a segmented control is on the solid surface, whatever the video is`() {
        fixture()

        // Left of the first segment: inside the border and the padding of the control, which is
        // its container, and outside every segment.
        val segment = composeRule.onNodeWithText("Trackpad").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().captureToImage().toPixelMap()
        val container = root[(segment.left - CONTAINER_PX).toInt(), segment.center.y.toInt()]

        assertNear(container, surface)
    }

    @Test
    fun `the labels of a segmented control keep 4_5 to 1 over their solid container`() {
        fixture()

        val unselected = composeRule.textColor("Direct")
        val selected = composeRule.textColor("Trackpad")
        val chosen =
            composeRule.onNodeWithText("Trackpad").captureToImage().toPixelMap().let {
                it[SEGMENT_PX, it.height / 2]
            }

        // The labels are the colours of the look, not the glass's: ink-3 is ink-3 on the surface.
        assertThat(unselected).isEqualTo(tokens.ink3)
        assertThat(selected).isEqualTo(tokens.accentSoft)
        assertThat(contrastOver(unselected, wcagLuminance(surface))).isAtLeast(AA_RATIO)
        // The selected segment is the tint of the accent on the surface.
        assertNear(chosen, tokens.accentTint.compositeOver(surface))
        assertThat(contrastOver(selected, wcagLuminance(chosen))).isAtLeast(AA_RATIO)
    }

    private fun assertNear(pixel: Color, expected: Color) {
        assertThat(pixel.red).isWithin(TOLERANCE).of(expected.red)
        assertThat(pixel.green).isWithin(TOLERANCE).of(expected.green)
        assertThat(pixel.blue).isWithin(TOLERANCE).of(expected.blue)
    }

    private companion object {
        /** Left of a segment: past the 1 dp border (2 px at xhdpi), in the 3 dp of padding. */
        const val CONTAINER_PX = 3
        const val TOLERANCE = 0.012f

        /** Inside a segment, outside its label. */
        const val SEGMENT_PX = 4
    }
}
