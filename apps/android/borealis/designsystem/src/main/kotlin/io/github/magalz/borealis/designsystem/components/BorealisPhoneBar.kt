package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.BorealisDimens

/**
 * The bar at the top of a phone screen: a [BorealisBackButton] that calls [onBack], the [title],
 * and the [actions] at the end. [backDescription] is what a screen reader says for the arrow. The
 * title takes the width that the arrow and the actions leave, so a long title is shortened by its
 * own text and the actions stay in view. The bar is as wide as its parent, with
 * [BorealisDimens.phoneBarPadding] at each side, so the arrow lines up with the gutter. [gap] is
 * the space between the arrow, the title and the actions, none by default; the standard spacing is
 * [BorealisDimens.phoneBarGap].
 */
@Composable
fun BorealisPhoneBar(
    onBack: () -> Unit,
    backDescription: String,
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = BorealisDimens.phoneBarPadding),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BorealisBackButton(onBack, backDescription)
        Box(Modifier.weight(1f)) { title() }
        actions()
    }
}
