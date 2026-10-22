package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.theme.effectiveTextSpacing
import io.github.magalz.borealis.settings.R
import io.github.magalz.borealis.settings.appearance.LookLists

/**
 * A Text spacing that is drawn as a smaller level at the font scale of the screen: the key of the
 * level that the user chose, [chosenKey], and the key of the one that is drawn, [drawnKey]. Both
 * are keys of [FixedChoices.TEXT_SPACING].
 */
internal class SpacingLimit(val chosenKey: String, val drawnKey: String)

/**
 * What the font scale does to the Text spacing with the key [chosenKey], or `null` when it is drawn
 * as chosen. The scale is the one that the screen draws its text at
 * (`LocalDensity.current.fontScale` under the theme), which is the one that [effectiveTextSpacing]
 * reads.
 */
internal fun spacingLimit(chosenKey: String, fontScale: Float): SpacingLimit? {
    val chosen = LookLists.TEXT_SPACING.choices.firstOrNull { it.id == chosenKey } ?: return null
    val drawn = effectiveTextSpacing(chosen.value, fontScale)
    if (drawn == chosen.value) return null
    return SpacingLimit(chosenKey, LookLists.TEXT_SPACING.choices.first { it.value == drawn }.id)
}

/** The name of the Text spacing with the key [key], as the list shows it. */
@Composable
private fun spacingName(key: String): String =
    stringResource(FixedChoices.TEXT_SPACING.first { it.key == key }.labelRes)

/** The summary of the Text spacing row, which says what is drawn instead of the level chosen. */
@Composable
internal fun SpacingLimit.summary(): String =
    stringResource(R.string.settings_text_spacing_limited_summary, spacingName(drawnKey))

/**
 * The dialog that says which Text spacing [limit] draws instead of the one that the user chose, and
 * that the choice stays. OK closes it through [onDismiss].
 */
@Composable
internal fun SpacingLimitDialog(limit: SpacingLimit, onDismiss: () -> Unit) {
    val chosen = spacingName(limit.chosenKey)
    SettingsMessageDialog(
        title = stringResource(R.string.settings_spacing_limit_title, chosen),
        message =
            stringResource(
                R.string.settings_spacing_limit_message,
                chosen,
                spacingName(limit.drawnKey),
            ),
        confirm = DialogButton(stringResource(R.string.settings_ok), onDismiss),
        onDismiss = onDismiss,
    )
}
