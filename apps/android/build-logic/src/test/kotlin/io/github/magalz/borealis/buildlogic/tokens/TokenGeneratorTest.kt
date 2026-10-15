package io.github.magalz.borealis.buildlogic.tokens

import com.google.common.truth.Truth.assertThat
import groovy.json.JsonSlurper
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Test

class TokenGeneratorTest {
    @Test
    fun `every colour of the real token file is generated`() {
        val json = realTokens()
        val colors = (JsonSlurper().parseText(json) as Map<*, *>)["color"] as Map<*, *>
        val names = colors.keys.map { it as String }.filterNot { it.startsWith("$") }

        val generated = TokenGenerator.generate(json)

        assertThat(names).hasSize(25)
        for (name in names) {
            assertThat(generated.kotlin).contains("val ${pascal(name)} = Color(0x")
            assertThat(generated.colorsXml)
                .contains("<color name=\"borealis_${name.replace('-', '_')}\">")
        }
    }

    @Test
    fun `an eight digit colour moves its alpha first`() {
        val generated =
            TokenGenerator.generate(
                tokens("""{"color": {"~type": "color", "line": {"~value": "#FFFFFF12"}}}""")
            )

        assertThat(generated.kotlin).contains("val Line = Color(0x12FFFFFF)")
        assertThat(generated.colorsXml).contains("""<color name="borealis_line">#12FFFFFF</color>""")
    }

    @Test
    fun `a six digit colour is opaque`() {
        val generated =
            TokenGenerator.generate(
                tokens("""{"color": {"~type": "color", "page": {"~value": "#0e1013"}}}""")
            )

        assertThat(generated.kotlin).contains("val Page = Color(0xFF0E1013)")
        assertThat(generated.colorsXml).contains("""<color name="borealis_page">#FF0E1013</color>""")
    }

    @Test
    fun `a hyphenated name becomes pascal case`() {
        val generated =
            TokenGenerator.generate(
                tokens(
                    """{"color": {"~type": "color", "ink-2": {"~value": "#C3C7CC"},
                       "surface-raised": {"~value": "#1A1E24"}}}"""
                )
            )

        assertThat(generated.kotlin).contains("val Ink2 = Color(")
        assertThat(generated.kotlin).contains("val SurfaceRaised = Color(")
        assertThat(generated.colorsXml).contains("borealis_ink_2")
    }

    @Test
    fun `dimensions use the dp and sp extensions`() {
        val generated =
            TokenGenerator.generate(
                tokens(
                    """{"size": {"~type": "dimension", "phone": {"secondary": {"~value": "12.5sp"}}},
                       "space": {"~type": "dimension", "gutter": {"~value": "16dp"}}}"""
                )
            )

        assertThat(generated.kotlin).contains("val Secondary = 12.5.sp")
        assertThat(generated.kotlin).contains("val Gutter = 16.dp")
        assertThat(generated.kotlin).contains("object Size {")
        assertThat(generated.kotlin).contains("object Phone {")
    }

    @Test
    fun `the type is inherited from the group and a token can override it`() {
        val generated =
            TokenGenerator.generate(
                tokens(
                    """{"focus": {"~type": "dimension", "outline": {"~value": "3dp"},
                       "tile-scale": {"~value": 1.04, "~type": "number"}}}"""
                )
            )

        assertThat(generated.kotlin).contains("val Outline = 3.dp")
        assertThat(generated.kotlin).contains("val TileScale = 1.04f")
    }

    @Test
    fun `font weights are generated and font families are not`() {
        val generated =
            TokenGenerator.generate(
                tokens(
                    """{"font": {"family": {"~type": "fontFamily",
                       "sans": {"~value": ["IBM Plex Sans", "sans-serif"]}},
                       "weight": {"~type": "fontWeight", "semibold": {"~value": 600}}}}"""
                )
            )

        assertThat(generated.kotlin).contains("object FontWeight {")
        assertThat(generated.kotlin).contains("val Semibold = FontWeight(600)")
        assertThat(generated.kotlin).doesNotContain("Sans")
    }

    @Test
    fun `descriptions become kdoc and the file says it is generated`() {
        val generated =
            TokenGenerator.generate(
                tokens(
                    """{"color": {"~type": "color",
                       "page": {"~value": "#0E1013", "~description": "Screen background"}}}"""
                )
            )

        assertThat(generated.kotlin).contains("/** Screen background */")
        assertThat(generated.kotlin)
            .contains("Generated from design/tokens.json by BorealisTokensTask (ADR 0044). Do not edit.")
        assertThat(generated.kotlin).contains("package io.github.magalz.borealis.designsystem.tokens")
        assertThat(generated.kotlin).contains("object BorealisTokens {")
    }

    @Test
    fun `an unknown type names the token path`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                TokenGenerator.generate(
                    tokens(
                        """{"size": {"phone": {"title": {"~value": "18sp", "~type": "shadow"}}}}"""
                    )
                )
            }

        assertThat(error).hasMessageThat().contains("size.phone.title")
        assertThat(error).hasMessageThat().contains("shadow")
    }

    @Test
    fun `a token without any type names the token path`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                TokenGenerator.generate(
                    tokens("""{"size": {"phone": {"title": {"~value": "18sp"}}}}""")
                )
            }

        assertThat(error).hasMessageThat().contains("size.phone.title")
    }

    @Test
    fun `an unknown unit names the token path`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                TokenGenerator.generate(
                    tokens(
                        """{"size": {"~type": "dimension", "phone": {"title": {"~value": "18px"}}}}"""
                    )
                )
            }

        assertThat(error).hasMessageThat().contains("size.phone.title")
        assertThat(error).hasMessageThat().contains("18px")
    }

    @Test
    fun `a malformed colour names the token path`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                TokenGenerator.generate(
                    tokens("""{"color": {"~type": "color", "page": {"~value": "#12345"}}}""")
                )
            }

        assertThat(error).hasMessageThat().contains("color.page")
    }

    @Test
    fun `a name collision after conversion names both token paths`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                TokenGenerator.generate(
                    tokens(
                        """{"color": {"~type": "color", "ink-2": {"~value": "#C3C7CC"},
                           "ink2": {"~value": "#C3C7CD"}}}"""
                    )
                )
            }

        assertThat(error).hasMessageThat().contains("color.ink-2")
        assertThat(error).hasMessageThat().contains("color.ink2")
    }

    @Test
    fun `the output is identical for the same input`() {
        val json = realTokens()

        assertThat(TokenGenerator.generate(json)).isEqualTo(TokenGenerator.generate(json))
    }

    /** `~` stands for `$`, which a Kotlin raw string would read as a template. */
    private fun tokens(body: String) = body.replace('~', '$')

    private fun pascal(name: String) =
        name.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }

    // Gradle runs the tests from the build-logic project directory.
    private fun realTokens(): String = File("../../../design/tokens.json").readText()
}
