package io.github.magalz.borealis.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A guard on the two lists of the licences (ADR 0058, decision 2): the build names the assets that
 * it bundles in `LegalAssetsTask`, and the licences dialog reads them by name in [LegalTexts]. A
 * licence that one list has and the other does not is missing from the dialog and fails here.
 */
@RunWith(RobolectricTestRunner::class)
class LegalAssetsReachTest {
    private val text: String =
        ApplicationProvider.getApplicationContext<Context>().let { context ->
            checkNotNull(LegalTexts.load { context.assets.open(it) }) { "no legal text bundled" }
        }

    @Test
    fun `the dialog shows NOTICE, LICENSE and the licence of every font that the build bundles`() {
        for (heading in
            listOf(
                "NOTICE",
                "LICENSE",
                "IBM Plex Sans and Mono: OFL.txt",
                "Atkinson Hyperlegible Next: OFL.txt",
                "Atkinson Hyperlegible Mono: OFL.txt",
                "Luciole: LICENSE.txt",
                "Lexend: OFL.txt",
                "Andika: OFL.txt",
                "B612 and B612 Mono: OFL.txt",
            )) {
            assertThat(text).contains(heading)
        }
    }

    @Test
    fun `the dialog shows the credit of Luciole, as its licence asks`() {
        assertThat(text)
            .contains(
                "Luciole © Laurent Bourcellier & Jonathan Perez, CC BY 4.0, " +
                    "https://www.luciole-vision.com"
            )
    }

    @Test
    fun `the dialog shows the reserved font names and the B612 trademark`() {
        assertThat(text).contains("\"RevReading Lexend\"")
        assertThat(text).contains("Reserved Font Names \"Andika\" and \"SIL\"")
        assertThat(text).contains("\"B612\" is a trademark of the Eclipse Foundation")
    }
}
