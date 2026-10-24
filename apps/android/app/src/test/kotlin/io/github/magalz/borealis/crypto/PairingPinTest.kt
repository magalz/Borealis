package io.github.magalz.borealis.crypto

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.http.PairingManager.PairState
import java.util.Locale
import org.junit.Test

private const val DRAWS = 2_000

/** The PIN that the user types on the host, and the states that a pairing can end in. */
class PairingPinTest {
    @Test
    fun `a PIN is four ASCII digits`() {
        repeat(DRAWS) {
            assertThat(PairingManager.generatePinString()).matches("[0-9]{4}")
        }
    }

    @Test
    fun `a PIN is made of four separate digits, so it can start with a zero`() {
        val pins = List(DRAWS) { PairingManager.generatePinString() }

        assertThat(pins.any { it.startsWith("0") }).isTrue()
        assertThat(pins.toSet().size).isGreaterThan(DRAWS / 2)
    }

    @Test
    fun `every digit shows up in every position of a PIN`() {
        val pins = List(DRAWS) { PairingManager.generatePinString() }

        for (position in 0 until 4) {
            assertThat(pins.map { it[position] }.toSet()).containsExactlyElementsIn('0'..'9')
        }
    }

    @Test
    fun `a PIN is the same under a locale that writes other digits`() {
        val saved = Locale.getDefault()
        try {
            val locale =
                listOf("ar-u-nu-arab", "fa", "hi-IN-u-nu-deva", "th-TH-u-nu-thai", "ar-EG")
                    .map { Locale.forLanguageTag(it) }
                    .firstOrNull { String.format(it, "%d", 7) != "7" }
            // The check is meaningful only when this JDK writes another digit for 7 in that locale.
            assertWithMessage("a locale that writes another digit for 7").that(locale).isNotNull()
            Locale.setDefault(checkNotNull(locale))
            assertThat(String.format(Locale.getDefault(), "%d", 7)).isNotEqualTo("7")

            repeat(DRAWS) {
                assertThat(PairingManager.generatePinString()).matches("[0-9]{4}")
            }
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `the pairing states keep their names and their order`() {
        assertThat(PairState.values().map { it.name })
            .containsExactly("NOT_PAIRED", "PAIRED", "PIN_WRONG", "FAILED", "ALREADY_IN_PROGRESS")
            .inOrder()
    }
}
