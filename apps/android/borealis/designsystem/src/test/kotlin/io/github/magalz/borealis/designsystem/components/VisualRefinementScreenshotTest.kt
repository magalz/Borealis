package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.DialogWidth
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the pieces of the visual refinement: the dialog frame in its three widths, the
 * icon set, the loader and the check. The references are in `src/test/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class VisualRefinementScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = TV)
    fun dialogTvCompact() = dialog(FormFactor.Tv, DialogWidth.Compact, "dialog_tv_compact")

    @Test
    @Config(qualifiers = TV)
    fun dialogTvStandard() = dialog(FormFactor.Tv, DialogWidth.Standard, "dialog_tv_standard")

    @Test
    @Config(qualifiers = TV)
    fun dialogTvWide() = dialog(FormFactor.Tv, DialogWidth.Wide, "dialog_tv_wide")

    // On a phone every width is the window less 16dp on each side, so one reference covers all.
    @Test
    @Config(qualifiers = PHONE)
    fun dialogPhone() = dialog(FormFactor.Phone, DialogWidth.Wide, "dialog_phone")

    @Test
    @Config(qualifiers = PHONE)
    fun iconSet() {
        composeRule.setContent {
            Page {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ICONS.forEach { (name, icon) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, contentDescription = null, Modifier.size(32.dp))
                            Icon(icon, contentDescription = null, Modifier.size(24.dp))
                            Icon(icon, contentDescription = null, Modifier.size(20.dp))
                            Text(name)
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/icons_phone.png")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun auroraLoaderFixedFrame() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Page { AuroraLoader(size = 160.dp) }
        }
        // Part way through the fill: the left of the bands is drawn and the right is not yet.
        composeRule.mainClock.advanceTimeBy(FRAME_MILLIS)

        composeRule.onRoot().captureRoboImage("src/test/screenshots/loader_phone_frame.png")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun animatedCheckFinalFrame() {
        composeRule.setContent {
            Page {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    AnimatedCheck(Modifier.size(64.dp))
                    AnimatedCheck(Modifier.size(64.dp), circled = true)
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/check_phone_final.png")
    }

    private fun dialog(formFactor: FormFactor, width: DialogWidth, name: String) {
        composeRule.setContent {
            Page(formFactor) {
                Text("Hosts")
                BorealisDialog(
                    title = "Forget this host?",
                    onDismissRequest = {},
                    confirm = DialogAction("Forget", BorealisButtonStyle.Danger) {},
                    width = width,
                    dismiss = DialogAction("Cancel", BorealisButtonStyle.Secondary) {},
                    focusDismiss = true,
                ) {
                    Text(
                        "Desk PC is removed from this device and its pairing is deleted. You can " +
                            "pair it again later.",
                        color = LocalBorealisColors.current.ink2,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun Page(
        formFactor: FormFactor = FormFactor.Phone,
        content: @Composable () -> Unit,
    ) {
        BorealisTheme(formFactor) {
            Box(
                Modifier.fillMaxSize()
                    .background(LocalBorealisColors.current.page)
                    .padding(if (formFactor == FormFactor.Phone) 16.dp else 34.dp)
            ) {
                content()
            }
        }
    }

    private companion object {
        const val FRAME_MILLIS = 500L
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
        val ICONS: List<Pair<String, ImageVector>> =
            listOf(
                "Back" to BorealisIcons.Back,
                "ChevronRight" to BorealisIcons.ChevronRight,
                "ChevronDown" to BorealisIcons.ChevronDown,
                "Check" to BorealisIcons.Check,
                "Gear" to BorealisIcons.Gear,
                "Hosts" to BorealisIcons.Hosts,
                "Add" to BorealisIcons.Add,
                "Visibility" to BorealisIcons.Visibility,
                "VisibilityOff" to BorealisIcons.VisibilityOff,
            )
    }
}
