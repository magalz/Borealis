package io.github.magalz.borealis.settings.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.BorealisFonts
import io.github.magalz.borealis.designsystem.theme.DialogWidth
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.diagnostics.transfer.QrMatrix
import io.github.magalz.borealis.settings.R
import kotlin.math.roundToInt

private const val SOURCE_CODE_URL = "https://github.com/magalz/Borealis"
private const val PRIVACY_POLICY_URL = "https://github.com/magalz/Borealis/blob/main/PRIVACY.md"
private const val LICENSE_HEIGHT_SHARE = 0.6f
private val LICENSE_TEXT_PADDING = 12.dp

/** The side of the QR code of a link: a TV is read across a room, so the code is large. */
private val LINK_QR_SIDE = 240.dp
private val LINK_COLUMN_GAP = 24.dp
private val LINK_TEXT_GAP = 12.dp

/** A link of the About group: the [title] of its row and the [url] that it opens. */
private data class AboutLink(val title: String, val url: String)

/**
 * The rows of the About group: the version and commit, the source code, the open-source licences
 * and the privacy policy. A link opens in an app, and one that no app can open shows its address in
 * a dialog. A TV does not hand a link to an app: the one app there that takes it keeps Back, so the
 * dialog shows the address and a QR code, and a phone reads it from there.
 */
@Composable
internal fun AboutRows(about: AboutSource) {
    val television = LocalFormFactor.current == FormFactor.Tv
    var linkAddress by remember { mutableStateOf<String?>(null) }
    var shownLink by remember { mutableStateOf<AboutLink?>(null) }
    var showLicenses by remember { mutableStateOf(false) }
    val openLink: (AboutLink) -> Unit = { link ->
        if (television) shownLink = link else if (!about.openLink(link.url)) linkAddress = link.url
    }
    val sourceCode =
        AboutLink(stringResource(R.string.settings_about_source_code_title), SOURCE_CODE_URL)
    val privacyPolicy =
        AboutLink(stringResource(R.string.settings_about_privacy_policy_title), PRIVACY_POLICY_URL)

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
        title = sourceCode.title,
        value = null,
        summary = sourceCode.url,
        onClick = { openLink(sourceCode) },
    )
    SettingsValueRow(
        title = stringResource(R.string.settings_about_licenses_title),
        value = null,
        summary = stringResource(R.string.settings_about_licenses_summary),
        onClick = { showLicenses = true },
    )
    SettingsValueRow(
        title = privacyPolicy.title,
        value = null,
        onClick = { openLink(privacyPolicy) },
    )

    shownLink?.let { link -> LinkQrDialog(link, onDismiss = { shownLink = null }) }
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
 * The address of [link] and a QR code that holds it, for a phone to scan, in a dialog titled with
 * the link. The address is a code, so it is set in the code font, and the screen reader says it
 * once: the image is described as a QR code for the link and does not repeat it. Close has the
 * focus.
 */
@Composable
private fun LinkQrDialog(link: AboutLink, onDismiss: () -> Unit) {
    val colors = LocalBorealisColors.current
    val qr = remember(link.url) { QrMatrix.encode(link.url) }
    SettingsDialog(
        title = link.title,
        confirm = DialogButton(stringResource(R.string.settings_close), onDismiss),
        onDismiss = onDismiss,
        width = DialogWidth.Wide,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(LINK_COLUMN_GAP)) {
            QrCode(
                qr,
                LINK_QR_SIDE,
                stringResource(R.string.settings_about_link_qr_description, link.title),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LINK_TEXT_GAP)) {
                Text(
                    stringResource(R.string.settings_about_link_scan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink2,
                )
                Text(
                    link.url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = BorealisFonts.Mono,
                    color = colors.ink2,
                )
            }
        }
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
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    // The dialog scrolls its content, so this text needs a height of its own to scroll in. A share
    // of the window does for a tall one; in a short one the dialog's title, padding and Close leave
    // less, and the text learns what is left from how much of it the dialog shows.
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
                // The dialog clips the text where its room ends. A text that runs on under Close is
                // not above Close for the focus search, so Up from Close would not find it: keep
                // its height to the part that is shown.
                .onGloballyPositioned { placed ->
                    val shown = placed.boundsInWindow().height.roundToInt()
                    if (shown < placed.size.height) room = shown
                }
                .dpadScroll(scroll)
                .heightIn(max = maxHeight)
                // The dialog clips what is outside its content, and the outline reaches
                // BorealisDimens.outlineReach, thickOutlineReach when it is the thicker one: the
                // padding of a list holds both.
                .padding(BorealisDimens.listPadding)
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
