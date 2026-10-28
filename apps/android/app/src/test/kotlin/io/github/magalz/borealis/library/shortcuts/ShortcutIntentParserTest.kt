package io.github.magalz.borealis.library.shortcuts

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.LibraryLaunchRequest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The shortcuts pinned by 12.2 name `ShortcutTrampoline` with these extras and must keep working.
 */
@RunWith(RobolectricTestRunner::class)
class ShortcutIntentParserTest {
    private fun extras(vararg entries: Pair<String, Any>) =
        Bundle().apply {
            entries.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                }
            }
        }

    @Test
    fun `a PC shortcut opens the apps of that PC`() {
        val request = ShortcutIntentParser.parse(extras("UUID" to "uuid-1", "Name" to "Desk PC"))

        assertThat(request).isEqualTo(LibraryLaunchRequest.Apps("uuid-1"))
    }

    @Test
    fun `a game shortcut launches the app on that PC`() {
        val request =
            ShortcutIntentParser.parse(
                extras(
                    "UUID" to "uuid-1",
                    "Name" to "Desk PC",
                    "AppId" to "1234",
                    "AppName" to "Hades II",
                    "HDR" to true,
                )
            )

        assertThat(request).isEqualTo(LibraryLaunchRequest.Launch("uuid-1", 1234))
    }

    @Test
    fun `a malformed app id falls back to the apps of the PC`() {
        val request = ShortcutIntentParser.parse(extras("UUID" to "uuid-1", "AppId" to "hades"))

        assertThat(request).isEqualTo(LibraryLaunchRequest.Apps("uuid-1"))
    }

    @Test
    fun `an app id that the Library cannot carry falls back to the apps of the PC`() {
        val request =
            ShortcutIntentParser.parse(
                extras("UUID" to "uuid-1", "AppId" to Int.MIN_VALUE.toString())
            )

        assertThat(request).isEqualTo(LibraryLaunchRequest.Apps("uuid-1"))
    }

    @Test
    fun `an empty app id is a PC shortcut`() {
        val request = ShortcutIntentParser.parse(extras("UUID" to "uuid-1", "AppId" to ""))

        assertThat(request).isEqualTo(LibraryLaunchRequest.Apps("uuid-1"))
    }

    @Test
    fun `a missing or blank UUID shows the host list`() {
        assertThat(ShortcutIntentParser.parse(extras("Name" to "Desk PC")))
            .isEqualTo(LibraryLaunchRequest.Hosts)
        assertThat(ShortcutIntentParser.parse(extras("UUID" to "  ", "AppId" to "5")))
            .isEqualTo(LibraryLaunchRequest.Hosts)
        assertThat(ShortcutIntentParser.parse(null)).isEqualTo(LibraryLaunchRequest.Hosts)
    }
}
