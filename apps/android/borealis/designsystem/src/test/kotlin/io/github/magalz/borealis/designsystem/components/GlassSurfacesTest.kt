package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.settings.CollapsibleSection
import io.github.magalz.borealis.designsystem.components.settings.SettingsGroup
import io.github.magalz.borealis.designsystem.components.settings.SettingsTwoPane
import io.github.magalz.borealis.designsystem.components.settings.SettingsValueRow
import io.github.magalz.borealis.designsystem.glass.GlassSurfaceCases
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.robolectric.annotation.Config

private const val PHONE = "w360dp-h640dp-xhdpi"
private const val TV = "w960dp-h540dp-land-television-xhdpi"

/**
 * The Host card is a glass surface: 1dp of `line` round it, and the `surface` colour where solid.
 */
@Config(qualifiers = PHONE)
class HostCardGlassTest : GlassSurfaceCases() {
    override val probes = listOf(DpOffset(220.dp, 40.dp))

    @Composable
    override fun Specimen() {
        HostCard(
            name = "Desk PC",
            status = DotStatus.Online,
            statusText = "Online",
            onClick = {},
            modifier = Modifier.width(240.dp),
            detail = "192.168.1.20",
        )
    }
}

/** The cover of an app is a glass surface, and the `surfaceRaised` colour where solid. */
@Config(qualifiers = PHONE)
class CoverTileGlassTest : GlassSurfaceCases() {
    override val probes = listOf(DpOffset(100.dp, 80.dp))
    override val solid = BorealisTokens.Color.SurfaceRaised

    @Composable
    override fun Specimen() {
        CoverTile(name = "Desktop", onClick = {}, modifier = Modifier.width(120.dp))
    }
}

/** The Continue card is a glass surface with the accent tint over it, and `surface` where solid. */
@Config(qualifiers = PHONE)
class ContinueCardGlassTest : GlassSurfaceCases() {
    // In the padding at the top, which nothing but the card draws on.
    override val probes = listOf(DpOffset(160.dp, 6.dp))

    override fun over(base: Color): Color = BorealisTokens.Color.AccentTint.compositeOver(base)

    @Composable
    override fun Specimen() {
        ContinueCard(
            appName = "Desktop",
            detail = "Running on Desk PC since 14:02",
            resumeLabel = "Resume",
            onClick = {},
            modifier = Modifier.width(320.dp),
        )
    }
}

/** The rail is a glass surface, and the `chrome` colour where solid. */
@Config(qualifiers = TV)
class NavigationRailGlassTest : GlassSurfaceCases() {
    override val formFactor = FormFactor.Tv
    override val hostSize = DpSize(480.dp, 240.dp)

    // Below the three items.
    override val probes = listOf(DpOffset(100.dp, 220.dp))
    override val solid = BorealisTokens.Color.Chrome

    @Composable
    override fun Specimen() {
        NavigationRail(
            items =
                listOf(
                    RailItem("Hosts", BorealisIcons.Hosts),
                    RailItem("Add host", BorealisIcons.Add),
                    RailItem("Settings", BorealisIcons.Gear),
                ),
            selectedIndex = 0,
            onSelect = {},
        )
    }
}

/** A section of the phone settings is one glass card, header and rows. */
@Config(qualifiers = PHONE)
class CollapsibleSectionGlassTest : GlassSurfaceCases() {
    // In the padding above the header.
    override val probes = listOf(DpOffset(150.dp, 3.dp))

    @Composable
    override fun Specimen() {
        CollapsibleSection(
            title = "Stream",
            expanded = true,
            onExpandedChange = {},
            modifier = Modifier.width(300.dp),
        ) {
            SettingsValueRow("Resolution", "Native", onClick = {})
        }
    }
}

/** The group list and the pane of the TV settings are a glass panel each. */
@Config(qualifiers = TV)
class SettingsTwoPaneGlassTest : GlassSurfaceCases() {
    override val formFactor = FormFactor.Tv
    override val hostSize = DpSize(480.dp, 240.dp)

    // Below the groups on the left, and below the row on the right.
    override val probes = listOf(DpOffset(110.dp, 220.dp), DpOffset(400.dp, 220.dp))

    @Composable
    override fun Specimen() {
        SettingsTwoPane(
            groups = listOf(SettingsGroup("stream", "Stream"), SettingsGroup("video", "Video")),
            selected = { "stream" },
            onSelect = {},
            modifier = Modifier.fillMaxSize(),
        ) {
            SettingsValueRow("Resolution", "Native", onClick = {})
        }
    }
}
