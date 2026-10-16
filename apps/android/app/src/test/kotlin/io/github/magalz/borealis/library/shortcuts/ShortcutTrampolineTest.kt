package io.github.magalz.borealis.library.shortcuts

import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.ShortcutTrampoline
import io.github.magalz.borealis.library.LibraryLaunchRequest
import io.github.magalz.borealis.library.shell.LibraryActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/** The component that 12.2 shortcuts name keeps working: it hands the Library a request. */
@RunWith(RobolectricTestRunner::class)
class ShortcutTrampolineTest {
    private fun open(vararg extras: Pair<String, String>): ActivityController<ShortcutTrampoline> =
        Robolectric.buildActivity(
                ShortcutTrampoline::class.java,
                Intent(ApplicationProvider.getApplicationContext(), ShortcutTrampoline::class.java)
                    .putExtras(Bundle().apply { extras.forEach { (k, v) -> putString(k, v) } }),
            )
            .setup()

    private fun ActivityController<ShortcutTrampoline>.started(): Intent =
        shadowOf(get()).nextStartedActivity

    @Test
    fun `a PC shortcut starts the Library on the apps of that PC and finishes`() {
        val controller = open("UUID" to "uuid-1", "Name" to "Desk PC")

        val intent = controller.started()
        assertThat(intent.component?.className).isEqualTo(LibraryActivity::class.java.name)
        assertThat(LibraryLaunchRequest.fromExtras(intent.extras))
            .isEqualTo(LibraryLaunchRequest.Apps("uuid-1"))
        assertThat(controller.get().isFinishing).isTrue()
    }

    @Test
    fun `a game shortcut asks the Library to launch the app`() {
        val intent = open("UUID" to "uuid-1", "AppId" to "42", "AppName" to "Hades II").started()

        assertThat(LibraryLaunchRequest.fromExtras(intent.extras))
            .isEqualTo(LibraryLaunchRequest.Launch("uuid-1", 42))
    }

    @Test
    fun `a running Library gets the request in onNewIntent instead of a second copy`() {
        val intent = open("UUID" to "uuid-1").started()

        assertThat(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP).isNotEqualTo(0)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP).isNotEqualTo(0)
    }
}
