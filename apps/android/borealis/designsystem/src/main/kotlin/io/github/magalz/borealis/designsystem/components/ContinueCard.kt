package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.optionsClickable
import io.github.magalz.borealis.designsystem.glass.glassSurface
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing

private val COVER_SIZE = 56.dp

/** The cover in Dense (ADR 0058, decision 7). */
private val COVER_SIZE_DENSE = 44.dp
private val CONTENT_GAP = 14.dp

/**
 * The session to continue (decision D2): the app, a [detail] line that says where it runs and since
 * when, and a resume affordance. The whole card is the target: [onClick] resumes, and every way of
 * asking for options runs [onOptions]. The resume button only looks like one, so the card is a
 * single focus stop. The card is a glass surface with the accent tint over it. Dense gives it less
 * padding and a smaller cover (ADR 0058, decision 7).
 */
@Composable
fun ContinueCard(
    appName: String,
    detail: String,
    resumeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOptions: (() -> Unit)? = null,
    optionsLabel: String? = null,
) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.large
    Row(
        modifier
            .borealisFocusIndication(shape)
            .glassSurface(shape)
            .background(colors.accentTint, shape)
            .border(1.dp, colors.accentLine, shape)
            .clip(shape)
            .optionsClickable(onClick, onOptions, Role.Button, optionsLabel)
            .padding(LocalBorealisSpacing.current.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverPlaceholder(
            appName,
            Modifier.size(
                if (LocalBorealisLook.current.cardDensity == CardDensity.Dense) COVER_SIZE_DENSE
                else COVER_SIZE
            ),
            initialsAlignment = Alignment.Center,
        )
        Column(Modifier.weight(1f)) {
            // No limit on the lines: at a large text size the Resume button leaves the name and the
            // detail a narrow column, and the card grows to hold them rather than cut them off.
            Text(appName, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
        }
        ButtonFace(
            resumeLabel,
            BorealisButtonStyle.Primary,
            enabled = true,
            modifier = Modifier,
            onClick = null,
        )
    }
}
