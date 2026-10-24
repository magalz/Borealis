package io.github.magalz.borealis.support

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.ShortcutTrampoline
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.utils.ShortcutHelper
import io.github.magalz.borealis.library.LibraryLaunchRequest
import io.github.magalz.borealis.library.shortcuts.ShortcutIntentParser
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers

/**
 * The launcher shortcuts of a PC and of its apps. The Library goes through `UpstreamHostShortcuts`;
 * `Game` uses the helper directly. The shortcut manager is the shadow of Robolectric, whose pinned
 * shortcuts a launcher would own: the tests pin the calls that the helper makes and what they
 * leave.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutHelperCharacterizationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(ShortcutManager::class.java)
    private val shadow = shadowOf(manager)
    private val helper = ShortcutHelper(context)

    // ShortcutInfo.getIcon and the Icon accessors are hidden API: the tests read them by
    // reflection.
    private fun iconOf(shortcut: ShortcutInfo): Icon =
        ShortcutInfo::class.java.getMethod("getIcon").invoke(shortcut) as Icon

    private fun computer(uuid: String? = "uuid-1", name: String? = "Den PC") =
        ComputerDetails().also {
            it.uuid = uuid
            it.name = name
        }

    // The pinned shortcuts that were disabled: the shadow lists them with the others.
    private fun disabledIds(): Set<String> =
        ReflectionHelpers.getStaticField<Map<String, ShortcutInfo>>(
                ShadowShortcutManager::class.java,
                "disabledPinnedShortcuts",
            )
            .keys

    private fun pin(id: String) {
        manager.requestPinShortcut(
            ShortcutInfo.Builder(context, id)
                .setIntent(Intent(Intent.ACTION_VIEW))
                .setShortLabel(id)
                .build(),
            null,
        )
    }

    @Before
    fun pinningIsSupported() {
        shadow.setIsRequestPinShortcutSupported(true)
        shadow.setMaxShortcutCountPerActivity(4)
    }

    @Test
    fun `the shortcut of a new host is a dynamic shortcut named after it`() {
        helper.createHostShortcut(computer(), false, false)

        val shortcut = manager.dynamicShortcuts.single()
        assertThat(shortcut.id).isEqualTo("uuid-1")
        assertThat(shortcut.shortLabel.toString()).isEqualTo("Den PC")
        assertThat(shortcut.longLabel.toString()).isEqualTo("Den PC")
        assertThat(shortcut.intent?.component)
            .isEqualTo(ComponentName(context, ShortcutTrampoline::class.java))
        assertThat(shortcut.intent?.extras?.keySet()).containsExactly("Name", "UUID")
        assertThat(ShortcutIntentParser.parse(shortcut.intent?.extras))
            .isEqualTo(LibraryLaunchRequest.Apps("uuid-1"))
        assertThat(iconOf(shortcut).resId).isEqualTo(R.mipmap.ic_pc_scut)
        assertThat(iconOf(shortcut).type).isEqualTo(TYPE_RESOURCE)
    }

    @Test
    fun `the shortcut of a host that already has one is updated in place`() {
        helper.createHostShortcut(computer(name = "Den PC"), false, false)

        helper.createHostShortcut(computer(name = "Study PC"), false, false)

        assertThat(manager.dynamicShortcuts.map { it.id to it.shortLabel.toString() })
            .containsExactly("uuid-1" to "Study PC")
    }

    @Test
    fun `a host is not added when the dynamic shortcuts are full, unless the user opened it`() {
        for (i in 1..4) helper.createHostShortcut(computer(uuid = "full-$i"), false, false)

        helper.createHostShortcut(computer(uuid = "uuid-1"), false, false)

        assertThat(manager.dynamicShortcuts.map { it.id })
            .containsExactly("full-1", "full-2", "full-3", "full-4")
    }

    @Test
    fun `a host that was forced in is added when there is room`() {
        helper.createHostShortcut(computer(uuid = "other"), false, false)

        helper.createHostShortcut(computer(), true, false)

        assertThat(manager.dynamicShortcuts.map { it.id }).containsExactly("other", "uuid-1")
    }

    @Test
    fun `no shortcut is added when the launcher allows none, forced or not`() {
        shadow.setMaxShortcutCountPerActivity(0)

        helper.createHostShortcut(computer(), false, false)
        helper.createHostShortcut(computer(), true, false)

        assertThat(manager.dynamicShortcuts).isEmpty()
    }

    @Test
    fun `a host that has no name or id cannot get a shortcut`() {
        assertThrows(RuntimeException::class.java) {
            helper.createHostShortcut(computer(uuid = null), false, false)
        }
        assertThrows(RuntimeException::class.java) {
            helper.createHostShortcut(computer(name = null), false, false)
        }
    }

    @Test
    fun `using the shortcut of a host reports it to the launcher`() {
        helper.createHostShortcut(computer(), false, false)

        helper.reportComputerShortcutUsed(computer())

        assertThat(shadow.reportedShortcutsUsed).containsExactly("uuid-1")
    }

    @Test
    fun `using a host that has no shortcut reports nothing`() {
        helper.reportComputerShortcutUsed(computer())

        assertThat(shadow.reportedShortcutsUsed).isEmpty()
    }

    @Test
    fun `using a host whose shortcut is pinned reports it`() {
        pin("uuid-1")

        helper.reportComputerShortcutUsed(computer())

        assertThat(shadow.reportedShortcutsUsed).containsExactly("uuid-1")
    }

    @Test
    fun `a pinned app is a shortcut with the id of the host and the app, its label and its intent`() {
        val pinned = helper.createPinnedGameShortcut(computer(), NvApp("Hades II", 42, true), null)

        assertThat(pinned).isTrue()
        val shortcut = manager.pinnedShortcuts.single()
        assertThat(shortcut.id).isEqualTo("uuid-142")
        assertThat(shortcut.shortLabel.toString()).isEqualTo("Hades II (Den PC)")
        assertThat(shortcut.longLabel).isNull()
        assertThat(shortcut.intent?.component)
            .isEqualTo(ComponentName(context, ShortcutTrampoline::class.java))
        assertThat(shortcut.intent?.extras?.keySet())
            .containsExactly("Name", "UUID", "AppName", "AppId", "HDR")
        assertThat(ShortcutIntentParser.parse(shortcut.intent?.extras))
            .isEqualTo(LibraryLaunchRequest.Launch("uuid-1", 42))
    }

    @Test
    fun `a pinned app without art has the shortcut icon of the app`() {
        helper.createPinnedGameShortcut(computer(), NvApp("Hades II", 42, true), null)

        val icon = iconOf(manager.pinnedShortcuts.single())
        assertThat(icon.type).isEqualTo(TYPE_RESOURCE)
        assertThat(icon.resId).isEqualTo(R.mipmap.ic_pc_scut)
    }

    @Test
    fun `a pinned app with art has it as an adaptive bitmap`() {
        helper.createPinnedGameShortcut(
            computer(),
            NvApp("Hades II", 42, true),
            Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888),
        )

        assertThat(iconOf(manager.pinnedShortcuts.single()).type).isEqualTo(TYPE_ADAPTIVE_BITMAP)
    }

    @Test
    fun `an app is not pinned when the launcher cannot pin`() {
        shadow.setIsRequestPinShortcutSupported(false)

        assertThat(helper.createPinnedGameShortcut(computer(), NvApp("Hades II", 42, true), null))
            .isFalse()
        assertThat(manager.pinnedShortcuts).isEmpty()
    }

    @Test
    fun `disabling a host disables its pinned shortcut and those of its apps and no other`() {
        pin("uuid-1")
        pin("uuid-142")
        pin("uuid-17")
        pin("uuid-2")
        pin("uuid-242")

        helper.disableComputerShortcut(computer(), "PC deleted")

        assertThat(disabledIds()).containsExactly("uuid-1", "uuid-142", "uuid-17")
    }

    @Test
    fun `disabling a host disables the pinned shortcuts of every id that starts with its own`() {
        pin("uuid-1")
        pin("uuid-10")
        pin("uuid-10-and-more")

        helper.disableComputerShortcut(computer(), "PC deleted")

        assertThat(disabledIds()).containsExactly("uuid-1", "uuid-10", "uuid-10-and-more")
    }

    @Test
    fun `disabling a host that has no shortcut changes nothing`() {
        pin("uuid-2")

        helper.disableComputerShortcut(computer(), "PC deleted")

        assertThat(disabledIds()).isEmpty()
    }

    @Test
    fun `a host that has no id cannot be disabled while there are shortcuts to compare it with`() {
        pin("uuid-2")

        assertThrows(RuntimeException::class.java) {
            helper.disableComputerShortcut(computer(uuid = null), "PC deleted")
        }
    }

    @Test
    fun `a host that has no id has nothing to disable when there are no shortcuts`() {
        helper.disableComputerShortcut(computer(uuid = null), "PC deleted")

        assertThat(disabledIds()).isEmpty()
    }

    @Test
    fun `disabling an app disables its pinned shortcut and enabling it brings it back`() {
        helper.createPinnedGameShortcut(computer(), NvApp("Hades II", 42, true), null)
        helper.createPinnedGameShortcut(computer(), NvApp("Steam", 7, false), null)

        helper.disableAppShortcut(computer(), NvApp("", 42, false), "App hidden")

        assertThat(disabledIds()).containsExactly("uuid-142")
        // The platform (and its shadow) rejects a pin request for a disabled shortcut.
        assertThrows(IllegalArgumentException::class.java) {
            helper.createPinnedGameShortcut(computer(), NvApp("Hades II", 42, true), null)
        }

        helper.enableAppShortcut(computer(), NvApp("", 42, false))

        assertThat(disabledIds()).isEmpty()
        assertThat(manager.pinnedShortcuts.map { it.id }).containsExactly("uuid-142", "uuid-17")
    }

    @Test
    fun `disabling or enabling an app that has no shortcut changes nothing`() {
        pin("uuid-17")

        helper.disableAppShortcut(computer(), NvApp("", 42, false), "App hidden")
        helper.enableAppShortcut(computer(), NvApp("", 42, false))

        assertThat(disabledIds()).isEmpty()
        assertThat(manager.pinnedShortcuts.map { it.id }).containsExactly("uuid-17")
    }

    private companion object {
        const val TYPE_RESOURCE = 2
        const val TYPE_ADAPTIVE_BITMAP = 5
    }
}
