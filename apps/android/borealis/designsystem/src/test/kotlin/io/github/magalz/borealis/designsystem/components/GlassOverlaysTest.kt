package io.github.magalz.borealis.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.magalz.borealis.designsystem.components.settings.ChoiceOption
import io.github.magalz.borealis.designsystem.components.settings.ChoiceSheet
import io.github.magalz.borealis.designsystem.glass.OverlayGlassCases
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import org.robolectric.annotation.Config

private const val PHONE = "w360dp-h640dp-xhdpi"
private const val TV = "w960dp-h540dp-land-television-xhdpi"

/** The dialog frame over a glass host. */
@Config(qualifiers = TV)
class BorealisDialogGlassTest : OverlayGlassCases() {
    override val title = "Forget this host?"
    override val secondaryText = "Its pairing is removed."

    @Composable
    override fun Overlay() {
        BorealisDialog(
            title = title,
            onDismissRequest = {},
            confirm = DialogAction("Forget") {},
        ) {
            Text(secondaryText, color = LocalBorealisColors.current.ink3)
        }
    }
}

/** The action sheet of a TV or a handheld, a dialog, over a glass host. */
@Config(qualifiers = TV)
class ActionSheetTvGlassTest : OverlayGlassCases() {
    override val title = "Desktop"
    override val secondaryText = "Back to the running desktop"

    // The dialog window of the sheet dims what is behind it, as the platform does.
    override fun scrim(): Float = windowDim()

    @Composable override fun Overlay() = Sheet(title, secondaryText)
}

/** The action sheet of a phone, a bottom sheet, over a glass host. */
@Config(qualifiers = PHONE)
class ActionSheetPhoneGlassTest : OverlayGlassCases() {
    override val formFactor = FormFactor.Phone
    override val title = "Desktop"
    override val secondaryText = "Back to the running desktop"

    @Composable override fun Overlay() = Sheet(title, secondaryText)
}

/** The choice sheet of a TV or a handheld, a dialog, over a glass host. */
@Config(qualifiers = TV)
class ChoiceSheetTvGlassTest : OverlayGlassCases() {
    override val title = "Resolution"
    override val secondaryText = "Matches most displays"

    override fun scrim(): Float = windowDim()

    @Composable override fun Overlay() = Choices(title, secondaryText)
}

/** The choice sheet of a phone, a bottom sheet, over a glass host. */
@Config(qualifiers = PHONE)
class ChoiceSheetPhoneGlassTest : OverlayGlassCases() {
    override val formFactor = FormFactor.Phone
    override val title = "Resolution"
    override val secondaryText = "Matches most displays"

    @Composable override fun Overlay() = Choices(title, secondaryText)
}

@Composable
private fun Sheet(title: String, consequence: String) {
    ActionSheet(
        title = title,
        actions = listOf(ActionSheetAction("resume", "Resume", consequence)),
        onAction = {},
        onDismiss = {},
    )
}

@Composable
private fun Choices(title: String, summary: String) {
    ChoiceSheet(
        title = title,
        options = listOf(ChoiceOption("1080p", "1920 x 1080", summary = summary)),
        selected = "1080p",
        onSelect = {},
        onDismiss = {},
    )
}
