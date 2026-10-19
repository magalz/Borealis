package io.github.magalz.borealis.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisPhoneBar
import io.github.magalz.borealis.designsystem.components.settings.SettingsGroup
import io.github.magalz.borealis.designsystem.components.settings.SettingsGroupHeader
import io.github.magalz.borealis.designsystem.components.settings.SettingsTwoPane
import io.github.magalz.borealis.designsystem.components.settings.SettingsValueRow
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.settings.R

/** An outline sits 5dp outside its row, so a list of rows needs 8dp around it. */
private val LIST_PADDING = 8.dp

/** An outline sits 5dp outside its row, so neighbouring rows need 10dp between them. */
private val LIST_GAP = 10.dp
private val TITLE_GAP = 12.dp

/**
 * The settings screen (UC-009), stateless. A phone shows one list: the groups with their rows, a
 * Privacy and diagnostics link row and About. A TV or a handheld shows two panes: the groups on the
 * left, Privacy and diagnostics and About among them, and the rows of the selected group on the
 * right. Back calls [onBack].
 *
 * A change is reported through [actions] and stored at once, so there is no Save. Lists open a
 * choice sheet; Reset layout and a native resolution or frame rate ask or warn first.
 *
 * @param onOpenPrivacy opens Privacy and diagnostics from the link row of a phone.
 * @param privacyPane the rows of Privacy and diagnostics, drawn in the right pane of a TV or a
 *   handheld.
 * @param initialGroup the group a TV or a handheld selects when the panes open; a phone shows every
 *   group in one list.
 * @param about the version, the links and the licence texts of the About rows.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    formFactor: FormFactor,
    actions: SettingsActions,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    privacyPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialGroup: SettingsGroupId = SettingsGroupId.STREAM,
    about: AboutSource = rememberAboutSource(),
) {
    BorealisTheme(formFactor) {
        BackHandler(onBack = onBack)
        val overlays = remember { SettingsOverlays() }
        Box(
            modifier
                .fillMaxSize()
                .background(LocalBorealisColors.current.page)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (formFactor == FormFactor.Phone) {
                PhoneSettings(state, actions, overlays, onBack, onOpenPrivacy, about)
            } else {
                TwoPaneSettings(state, actions, overlays, privacyPane, initialGroup, about)
            }
        }
        SettingsOverlayHost(overlays, state, actions)
    }
}

@Composable
private fun ScreenTitle(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.settings_title),
        modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun PhoneSettings(
    state: SettingsUiState,
    actions: SettingsActions,
    overlays: SettingsOverlays,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    about: AboutSource,
) {
    val gutter = LocalBorealisSpacing.current.gutter
    Column(Modifier.fillMaxSize()) {
        BorealisPhoneBar(
            onBack,
            stringResource(R.string.settings_back),
            gap = BorealisDimens.phoneBarGap,
        ) {
            ScreenTitle()
        }
        Column(
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter - LIST_PADDING, vertical = LIST_PADDING),
            verticalArrangement = Arrangement.spacedBy(LIST_GAP),
        ) {
            for (group in state.groups) {
                SettingsGroupHeader(stringResource(group.group.titleRes))
                for (item in group.items) {
                    SettingsItemRow(item, state.rowValue(item.item), actions, overlays.requests)
                }
            }
            SettingsValueRow(
                title = stringResource(R.string.settings_group_privacy),
                value = null,
                summary = stringResource(R.string.settings_privacy_link_summary),
                onClick = onOpenPrivacy,
            )
            SettingsGroupHeader(stringResource(R.string.settings_group_about))
            AboutRows(about)
        }
    }
}

@Composable
private fun TwoPaneSettings(
    state: SettingsUiState,
    actions: SettingsActions,
    overlays: SettingsOverlays,
    privacyPane: @Composable () -> Unit,
    initialGroup: SettingsGroupId,
    about: AboutSource,
) {
    val gutter = LocalBorealisSpacing.current.gutter
    val groups =
        (state.groups.map { it.group } + SettingsGroupId.PRIVACY + SettingsGroupId.ABOUT).map {
            SettingsGroup(it, stringResource(it.titleRes))
        }
    var selected by rememberSaveable { mutableStateOf(initialGroup) }
    Column(Modifier.fillMaxSize().padding(gutter)) {
        ScreenTitle()
        SettingsTwoPane(
            groups = groups,
            selected = { selected },
            onSelect = { selected = it },
            // The Privacy pane scrolls itself and has its own snackbar.
            selfScrolling = { it == SettingsGroupId.PRIVACY },
            // A remote or a gamepad works at once and the outline shows, also when the rail opened
            // the screen.
            focusOnOpen = true,
            modifier = Modifier.padding(top = TITLE_GAP),
        ) { group ->
            when (group) {
                SettingsGroupId.PRIVACY -> privacyPane()
                SettingsGroupId.ABOUT -> AboutRows(about)
                else ->
                    state.groups
                        .firstOrNull { it.group == group }
                        ?.items
                        ?.forEach {
                            SettingsItemRow(it, state.rowValue(it.item), actions, overlays.requests)
                        }
            }
        }
    }
}
