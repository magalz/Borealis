package io.github.magalz.borealis.designsystem.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.GlassHostOver
import io.github.magalz.borealis.designsystem.glass.GlassSurfaceCases
import io.github.magalz.borealis.designsystem.glass.backdropLuminance
import io.github.magalz.borealis.designsystem.glass.contrast
import io.github.magalz.borealis.designsystem.glass.glassSpec
import io.github.magalz.borealis.designsystem.glass.luminance as wcagLuminance
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val PHONE = "w360dp-h640dp-xhdpi"

/**
 * The container of the text field is a glass surface (issue #33): over a background it shows the
 * background blurred under the tint, and it is the `surface` colour where the glass is Off, under
 * High contrast and on the plain page.
 */
@Config(qualifiers = PHONE)
class BorealisTextFieldGlassTest : GlassSurfaceCases() {
    // On the right of the field, where it has no text and the label is not.
    override val probes = listOf(DpOffset(300.dp, 28.dp))

    @Composable
    override fun Specimen() {
        BorealisTextField(
            value = "",
            onValueChange = {},
            label = "Address",
            modifier = Modifier.width(340.dp),
        )
    }
}

/**
 * The text and the outline of the field on the glass keep their contrast with the backdrop behind
 * it (WCAG 1.4.3 and 1.4.11): the label and the typed text 4.5:1, the outline 3:1. The backdrop is
 * the brightest pixel behind a glass of that style, in the table that the tint floors come from;
 * the colours are what the field draws.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class BorealisTextFieldOnGlassTest {
    @get:Rule val composeRule = createComposeRule()

    private val colors = BorealisColors()
    private val ink3OverFlowingLight = Color(0xFFA8ACB1)
    private val styles = BackgroundStyle.entries.filter { it != BackgroundStyle.Off }

    // The text

    @Test
    fun `the label is the glass ink-3 over Flowing light at 40 percent and 8 dp`() {
        show(BackgroundStyle.FlowingLight) { Field() }

        assertThat(composeRule.textColor("Address")).isEqualTo(ink3OverFlowingLight)
    }

    @Test
    fun `the label is the token ink-3 where the glass is Off`() {
        show(BackgroundStyle.FlowingLight, BorealisLook(transparency = Transparency.Off)) {
            Field()
        }

        assertThat(composeRule.textColor("Address")).isEqualTo(colors.ink3)
    }

    @Test
    fun `the typed text is ink`() {
        show(BackgroundStyle.FlowingLight) { Field(value = "192.168.1.20") }

        assertThat(composeRule.textColor("192.168.1.20")).isEqualTo(colors.ink)
    }

    @Test
    fun `a focused label is accent-soft, which the glass keeps at 4 and a half to 1, and accent does not`() {
        show(BackgroundStyle.FlowingLight) { Field() }

        composeRule.onNode(hasSetTextAction()).requestFocus()
        composeRule.waitForIdle()

        assertThat(composeRule.textColor("Address")).isEqualTo(colors.accentSoft)
    }

    @Test
    fun `the label and the typed text keep 4 and a half to 1 over every glass`() {
        for (look in listOf(BorealisLook(), BorealisLook(transparency = Transparency.Reduced))) {
            for (style in styles) {
                show(style, look) { Field(value = "192.168.1.20") }
                val backdrop = backdropOf(style, look)

                assertAtLeast(AA, composeRule.textColor("Address"), backdrop, "label over $style")
                assertAtLeast(
                    AA,
                    composeRule.textColor("192.168.1.20"),
                    backdrop,
                    "text over $style",
                )
            }
        }
    }

    @Test
    fun `the focused label keeps 4 and a half to 1 over every glass`() {
        show(styles.first()) { Field() }
        composeRule.onNode(hasSetTextAction()).requestFocus()
        for (style in styles) {
            change(style)

            assertAtLeast(
                AA,
                composeRule.textColor("Address"),
                backdropOf(style),
                "focused label over $style",
            )
        }
    }

    @Test
    fun `the message of an error keeps 4 and a half to 1 over every glass at the defaults`() {
        for (look in listOf(BorealisLook(), BorealisLook(transparency = Transparency.Reduced))) {
            for (style in styles) {
                show(style, look) { Field(error = "Not valid") }

                assertAtLeast(
                    AA,
                    composeRule.textColor("Not valid"),
                    backdropOf(style, look),
                    "message over $style",
                )
            }
        }
    }

    // The outline

    @Test
    fun `the outline is the glass ink-3 over Flowing light, and the token ink-3 where the glass is Off`() {
        show(BackgroundStyle.FlowingLight) { Field() }
        assertThat(outline()).isEqualTo(ink3OverFlowingLight)

        change(look = BorealisLook(transparency = Transparency.Off))
        assertThat(outline()).isEqualTo(colors.ink3)
    }

    @Test
    fun `the outline keeps 3 to 1 over every glass`() {
        for (look in listOf(BorealisLook(), BorealisLook(transparency = Transparency.Reduced))) {
            for (style in styles) {
                show(style, look) { Field() }

                assertAtLeast(NON_TEXT, outline(), backdropOf(style, look), "outline over $style")
            }
        }
    }

    @Test
    fun `the outline of a focused field is accent and keeps 3 to 1 over every glass`() {
        show(styles.first()) { Field() }
        composeRule.onNode(hasSetTextAction()).requestFocus()
        for (style in styles) {
            change(style)

            assertThat(outline()).isEqualTo(colors.accent)
            assertAtLeast(NON_TEXT, outline(), backdropOf(style), "focus outline over $style")
        }
    }

    @Test
    fun `the outline keeps 3 to 1 over the solid surface of Off, High contrast and the plain page`() {
        val surface = wcagLuminance(colors.surface)
        val looks =
            listOf(
                BackgroundStyle.AuroraCurtains to BorealisLook(transparency = Transparency.Off),
                BackgroundStyle.AuroraCurtains to BorealisLook(highContrast = true),
                BackgroundStyle.Off to BorealisLook(),
            )
        for ((style, look) in looks) {
            show(style, look) { Field() }

            assertAtLeast(NON_TEXT, outline(), surface, "outline, $style under $look")
        }
    }

    @Test
    fun `the outline of a field in error is danger ink`() {
        show(BackgroundStyle.FlowingLight) { Field(error = "Not valid") }

        assertThat(outline()).isEqualTo(colors.dangerInk)
    }

    // The shape

    @Test
    fun `the glass of the field is clipped to the shape of the field`() {
        show(BackgroundStyle.AuroraCurtains) { Field() }

        // The corner of the rounded field is outside it, so it shows the backdrop and not the
        // glass.
        val corner = composeRule.frame(HOST)[CORNER_PX, CORNER_PX]
        assertThat(corner.luminance()).isGreaterThan(0.9f)
        assertThat(composeRule.frame(HOST)[CENTRE_PX, MIDDLE_PX].luminance()).isLessThan(0.9f)
    }

    // Helpers

    private var style by mutableStateOf(BackgroundStyle.Off)
    private var look by mutableStateOf(BorealisLook())
    private var shown = false

    /**
     * [content] in a host of [style] over white, under [look]. The content is set once; a second
     * call, as a loop makes, changes the host and keeps the content.
     */
    private fun show(
        style: BackgroundStyle,
        look: BorealisLook = BorealisLook(),
        content: @Composable () -> Unit,
    ) {
        if (shown) {
            change(style, look)
            return
        }
        shown = true
        this.style = style
        this.look = look
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone, this.look) {
                GlassHostOver(
                    this.style,
                    background = { Box(Modifier.fillMaxSize().background(Color.White)) },
                    modifier = Modifier.size(HOST_WIDTH.dp, HOST_HEIGHT.dp).testTag(HOST),
                    content = content,
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** Changes the host that is shown, as the viewer does in Settings. */
    private fun change(style: BackgroundStyle = this.style, look: BorealisLook = this.look) {
        composeRule.runOnIdle {
            this.style = style
            this.look = look
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Field(value: String = "", error: String? = null) {
        BorealisTextField(
            value = value,
            onValueChange = {},
            label = "Address",
            modifier = Modifier.width(FIELD_WIDTH.dp),
            error = error,
        )
    }

    /** The colour of the outline at the middle of its left side, which is its own pixel. */
    private fun outline(): Color = composeRule.frame(HOST)[OUTLINE_PX, MIDDLE_PX]

    /** The luminance of the brightest pixel behind the glass of [style] under [look]. */
    private fun backdropOf(style: BackgroundStyle, look: BorealisLook = BorealisLook()): Double {
        val spec = glassSpec(look, style, Build.VERSION.SDK_INT)
        return backdropLuminance(style, spec.blurDp, spec.tintPercent)
    }

    private fun assertAtLeast(limit: Double, colour: Color, backdrop: Double, what: String) {
        assertWithMessage("$what is ${contrast(colour, backdrop)}:1, and needs $limit:1")
            .that(contrast(colour, backdrop))
            .isAtLeast(limit)
    }

    private companion object {
        const val HOST = "field-host"
        const val HOST_WIDTH = 360
        const val HOST_HEIGHT = 200
        const val FIELD_WIDTH = 340

        /** The tests run at xhdpi, where one dp is two pixels. */
        const val OUTLINE_PX = 1
        const val CORNER_PX = 1
        const val MIDDLE_PX = 56
        const val CENTRE_PX = 300

        const val AA = 4.5
        const val NON_TEXT = 3.0
    }
}
