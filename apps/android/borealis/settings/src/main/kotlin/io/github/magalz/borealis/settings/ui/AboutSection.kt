package io.github.magalz.borealis.settings.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.settings.SettingsInfoRow
import io.github.magalz.borealis.designsystem.components.settings.SettingsValueRow
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.settings.R
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val SOURCE_CODE_URL = "https://github.com/magalz/Borealis"
private const val PRIVACY_POLICY_URL = "https://github.com/magalz/Borealis/blob/main/PRIVACY.md"
private val LICENSE_SCROLL_STEP = 96.dp
private const val LICENSE_HEIGHT_SHARE = 0.6f
private val LICENSE_OUTLINE_ROOM = 8.dp
private val LICENSE_TEXT_PADDING = 12.dp

/**
 * The rows of the About group: the version and commit, the source code, the open-source licences
 * and the privacy policy. A link that no app can open shows its address in a dialog.
 */
@Composable
internal fun AboutRows(about: AboutSource) {
    var linkAddress by remember { mutableStateOf<String?>(null) }
    var showLicenses by remember { mutableStateOf(false) }
    val openLink: (String) -> Unit = { url -> if (!about.openLink(url)) linkAddress = url }

    SettingsInfoRow(
        title = stringResource(R.string.settings_about_version_title),
        value =
            stringResource(
                R.string.settings_about_version_summary,
                about.versionName ?: stringResource(R.string.settings_about_version_unknown),
                about.commit,
            ),
    )
    SettingsValueRow(
        title = stringResource(R.string.settings_about_source_code_title),
        value = null,
        summary = SOURCE_CODE_URL,
        onClick = { openLink(SOURCE_CODE_URL) },
    )
    SettingsValueRow(
        title = stringResource(R.string.settings_about_licenses_title),
        value = null,
        summary = stringResource(R.string.settings_about_licenses_summary),
        onClick = { showLicenses = true },
    )
    SettingsValueRow(
        title = stringResource(R.string.settings_about_privacy_policy_title),
        value = null,
        onClick = { openLink(PRIVACY_POLICY_URL) },
    )

    linkAddress?.let { address ->
        SettingsMessageDialog(
            title = stringResource(R.string.settings_about_link_dialog_title),
            message = address,
            confirm = DialogButton(stringResource(R.string.settings_close), { linkAddress = null }),
            onDismiss = { linkAddress = null },
        )
    }
    if (showLicenses) {
        LicensesDialog(about, onDismiss = { showLicenses = false })
    }
}

/**
 * The bundled `NOTICE` and `LICENSE` texts in a scrollable dialog. The text is a focus stop above
 * Close: Up from Close moves focus to it, the D-pad Up and Down then scroll it, and Down at its end
 * moves on to Close.
 */
@Composable
private fun LicensesDialog(about: AboutSource, onDismiss: () -> Unit) {
    val text =
        remember(about) { about.loadLicenses() }
            ?: stringResource(R.string.settings_about_licenses_unavailable)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val stepPx = with(density) { LICENSE_SCROLL_STEP.toPx() }
    val windowHeight = LocalWindowInfo.current.containerSize.height
    // The dialog scrolls its content, so this text needs a height of its own to scroll in. A share
    // of the window is right for a tall one; in a short one the dialog's title, Close and padding
    // leave less, and the text learns what is left from how much of it the dialog shows.
    var room by remember(windowHeight) { mutableIntStateOf(Int.MAX_VALUE) }
    val maxHeight =
        with(density) { minOf(windowHeight * LICENSE_HEIGHT_SHARE, room.toFloat()).toDp() }
    SettingsDialog(
        title = stringResource(R.string.settings_about_licenses_title),
        confirm = DialogButton(stringResource(R.string.settings_close), onDismiss),
        onDismiss = onDismiss,
    ) {
        Text(
            text,
            Modifier.fillMaxWidth()
                // A text taller than the room the dialog has runs on under Close: its focus search
                // from Close does not find it above, so Up does not reach it. It is cut at what
                // the dialog shows, so this keeps the height at that.
                .onGloballyPositioned { placed ->
                    val shown = placed.boundsInWindow().height.roundToInt()
                    if (shown < placed.size.height) room = shown
                }
                .onKeyEvent { event ->
                    val delta =
                        when (event.key) {
                            Key.DirectionDown -> stepPx
                            Key.DirectionUp -> -stepPx
                            else -> 0f
                        }
                    val canScroll =
                        if (delta > 0) scroll.canScrollForward else scroll.canScrollBackward
                    val scrolls = delta != 0f && canScroll
                    if (scrolls && event.type == KeyEventType.KeyDown) {
                        scope.launch { scroll.animateScrollBy(delta) }
                    }
                    scrolls
                }
                .heightIn(max = maxHeight)
                // The dialog clips what is outside its content, and the outline reaches about 5dp.
                .padding(LICENSE_OUTLINE_ROOM)
                .borealisFocusIndication(MaterialTheme.shapes.medium)
                // Before verticalScroll: a focusable after it takes the size of the whole text, not
                // of the viewport, and the focus search from Close then does not find it above
                // Close.
                .focusable()
                .verticalScroll(scroll)
                .padding(LICENSE_TEXT_PADDING),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
