package io.github.magalz.borealis.library.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.library.apps.AppItemUiState
import io.github.magalz.borealis.library.apps.AppsUiState
import io.github.magalz.borealis.library.domain.HostCapability
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.hosts.HostAction
import io.github.magalz.borealis.library.hosts.HostItemUiState
import io.github.magalz.borealis.library.hosts.checkingPc
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.testing.AppsOf
import io.github.magalz.borealis.library.testing.HostsOf
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of Hosts and Apps in the denser layouts (ADR 0058, decision 7), in IBM Plex, named
 * `density_<device>_<screen>-<density>.png`: Compact and Dense on a TV and on a phone, and Apps on
 * a phone with 4 columns at the largest text, where the Running badge of the first cover is the dot
 * alone. The first host card of a TV is the fullest, and the cards of its row are as high as it, on
 * a phone in two columns too; Dense has the least padding. Hosts at Wider, with `-wider` in the
 * name, keep the columns of Comfortable: 3 on a TV and 1 on a phone. The background is the plain
 * page.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class DensityScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val bedroomPc =
        HostItemUiState(
            "bed",
            "Bedroom PC",
            Reachability.ONLINE,
            true,
            null,
            HostAction.OPEN,
            address = "192.168.1.52",
            tags = listOf(HostCapability.HEVC, HostCapability.HDR),
        )
    private val hosts = withHosts.copy(items = withHosts.items + checkingPc + bedroomPc)

    private val apps =
        AppsUiState(
            loading = false,
            hostName = "Desk PC",
            runningAppId = 1,
            runningAppName = "Desktop",
            items =
                listOf(
                        "Desktop",
                        "Steam Big Picture",
                        "Playnite",
                        "Hades II",
                        "Elden Ring",
                        "Doom Eternal",
                        "Cyberpunk 2077",
                        "Baldur's Gate 3",
                        "Celeste",
                        "Hollow Knight",
                        "Stardew Valley",
                        "Disco Elysium",
                    )
                    .mapIndexed { index, name ->
                        AppItemUiState(index + 1, name, index == 0, false, index % 2 == 0)
                    },
        )

    @Test
    @Config(qualifiers = TV)
    fun hostsTvCompact() = hostsTv(CardDensity.Compact, "density_tv_hosts-compact")

    @Test
    @Config(qualifiers = TV)
    fun hostsTvDense() = hostsTv(CardDensity.Dense, "density_tv_hosts-dense")

    @Test
    @Config(qualifiers = PHONE)
    fun hostsPhoneCompact() = hostsPhone(CardDensity.Compact, "density_phone_hosts-compact")

    @Test
    @Config(qualifiers = PHONE)
    fun hostsPhoneDense() = hostsPhone(CardDensity.Dense, "density_phone_hosts-dense")

    /** At Wider the denser layouts keep the columns of Comfortable: 3 hosts, with Dense padding. */
    @Test
    @Config(qualifiers = TV)
    fun hostsTvDenseWider() =
        hostsTv(CardDensity.Dense, "density_tv_hosts-dense-wider", TextSpacing.Wider)

    /** One column, as in Comfortable, with the padding of Compact. */
    @Test
    @Config(qualifiers = PHONE)
    fun hostsPhoneCompactWider() =
        hostsPhone(CardDensity.Compact, "density_phone_hosts-compact-wider", TextSpacing.Wider)

    @Test
    @Config(qualifiers = TV)
    fun appsTvCompact() = appsTv(CardDensity.Compact, "density_tv_apps-compact")

    @Test
    @Config(qualifiers = TV)
    fun appsTvDense() = appsTv(CardDensity.Dense, "density_tv_apps-dense")

    @Test
    @Config(qualifiers = PHONE)
    fun appsPhoneCompact() = appsPhone(BorealisLook(cardDensity = CardDensity.Compact), "compact")

    @Test
    @Config(qualifiers = PHONE)
    fun appsPhoneDense() = appsPhone(BorealisLook(cardDensity = CardDensity.Dense), "dense")

    @Test
    @Config(qualifiers = PHONE, fontScale = SYSTEM_SCALE)
    fun appsPhoneCompactLargest() =
        appsPhone(
            BorealisLook(cardDensity = CardDensity.Compact, textSize = TextSize.Largest),
            "compact-largest",
        )

    private fun hostsTv(
        density: CardDensity,
        name: String,
        spacing: TextSpacing = TextSpacing.Normal,
    ) =
        shoot(FormFactor.Tv, BorealisLook(cardDensity = density, textSpacing = spacing), name) {
            HostsOf(hosts, FormFactor.Tv)
        }

    private fun hostsPhone(
        density: CardDensity,
        name: String,
        spacing: TextSpacing = TextSpacing.Normal,
    ) =
        shoot(FormFactor.Phone, BorealisLook(cardDensity = density, textSpacing = spacing), name) {
            HostsOf(hosts, FormFactor.Phone)
        }

    private fun appsTv(density: CardDensity, name: String) =
        shoot(FormFactor.Tv, BorealisLook(cardDensity = density), name) {
            AppsOf(apps, FormFactor.Tv)
        }

    private fun appsPhone(look: BorealisLook, state: String) =
        shoot(FormFactor.Phone, look, "density_phone_apps-$state") {
            AppsOf(apps, FormFactor.Phone)
        }

    private fun shoot(
        formFactor: FormFactor,
        look: BorealisLook,
        name: String,
        screen: @Composable () -> Unit,
    ) {
        val content: @Composable () -> Unit = {
            LibraryScaffold(
                formFactor,
                selected = RailSelection.Hosts,
                actions = RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
                look = look,
            ) {
                screen()
            }
        }
        // A phone has no focus outline until a key is pressed; a TV shows it on the first card.
        if (formFactor == FormFactor.Phone) composeRule.setContent(content)
        else composeRule.setContentInKeyboardMode(content)

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val SYSTEM_SCALE = 1.3f
    }
}
