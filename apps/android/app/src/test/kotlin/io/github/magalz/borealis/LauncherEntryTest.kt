package io.github.magalz.borealis

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The merged manifest: the Compose Library is the only launcher entry (ADR 0052, Sprint 4). */
@RunWith(RobolectricTestRunner::class)
class LauncherEntryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun launchers(category: String): List<String> =
        context.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(context.packageName),
                0,
            )
            .map { it.activityInfo.name }

    @Test
    fun `only LibraryActivity is a phone launcher`() {
        assertThat(launchers(Intent.CATEGORY_LAUNCHER)).containsExactly(LIBRARY_ACTIVITY)
    }

    @Test
    fun `only LibraryActivity is a TV launcher`() {
        assertThat(launchers(Intent.CATEGORY_LEANBACK_LAUNCHER)).containsExactly(LIBRARY_ACTIVITY)
    }

    private companion object {
        const val LIBRARY_ACTIVITY = "io.github.magalz.borealis.library.shell.LibraryActivity"
    }
}
