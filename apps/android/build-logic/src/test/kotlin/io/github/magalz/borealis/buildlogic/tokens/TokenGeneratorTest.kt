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

        assertThat(names).hasSize(26)
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
        assertThat(generated.colorsXml)
            .contains("""<color name="borealis_line">#12FFFFFF</color>""")
    }

    @Test
    fun `a six digit colour is opaque`() {
        val generated =
            TokenGenerator.generate(
                tokens("""{"color": {"~type": "color", "page": {"~value": "#0e1013"}}}""")
            )

        assertThat(generated.kotlin).contains("val Page = Color(0xFF0E1013)")
        assertThat(generated.colorsXml)
            .contains("""<color name="borealis_page">#FF0E1013</color>""")
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
            .contains(
                "Generated from design/tokens.json by BorealisTokensTask (ADR 0044). Do not edit."
            )
        assertThat(generated.kotlin)
            .contains("package io.github.magalz.borealis.designsystem.tokens")
        assertThat(generated.kotlin).contains("object BorealisTokens {")
    }

    @Test
    fun `a description that does not fit on one line is wrapped into a multi line kdoc`() {
        val description =
            "Height of a host card on a TV or handheld, whatever its content: the fullest card " +
                "(one row of tags, a running app) plus a few dp of margin for density rounding; " +
                "a card whose tags wrap or whose font is large grows past it"

        val kotlin =
            TokenGenerator.generate(
                    tokens(
                        """{"size": {"~type": "dimension",
                       "card": {"~value": "96dp", "~description": "$description"}}}"""
                    )
                )
                .kotlin

        val lines = kotlin.lines()
        val declaration = lines.indexOf("        val Card = 96.dp")
        assertThat(declaration).isGreaterThan(0)
        assertThat(lines[declaration - 1]).isEqualTo("         */")
        val open = lines.subList(0, declaration).lastIndexOf("        /**")
        val body = lines.subList(open + 1, declaration - 1)
        assertThat(body.size).isAtLeast(2)
        assertThat(body.all { it.startsWith("         * ") }).isTrue()
        assertThat(body.joinToString(" ") { it.removePrefix("         * ") }).isEqualTo(description)
    }

    @Test
    fun `no line of the generated kotlin is longer than 100 characters`() {
        val kotlin = TokenGenerator.generate(realTokens()).kotlin

        val longest = kotlin.lines().maxOf { it.length }

        assertThat(longest).isAtMost(100)
    }

    @Test
    fun `a description that fits on one line stays a one line kdoc`() {
        val generated =
            TokenGenerator.generate(
                tokens(
                    """{"size": {"~type": "dimension",
                       "gutter": {"~value": "16dp", "~description": "Screen edge padding"}}}"""
                )
            )

        val lines = generated.kotlin.lines()
        val declaration = lines.indexOf("        val Gutter = 16.dp")
        assertThat(declaration).isGreaterThan(0)
        assertThat(lines[declaration - 1]).isEqualTo("        /** Screen edge padding */")
    }

    @Test
    fun `a number token is a compile time constant`() {
        val generated =
            TokenGenerator.generate(
                tokens("""{"focus": {"~type": "number", "tile-scale": {"~value": 1.04}}}""")
            )

        assertThat(generated.kotlin).contains("    const val TileScale = 1.04f")
    }

    @Test
    fun `a whole number token keeps its float suffix as a constant`() {
        val generated =
            TokenGenerator.generate(
                tokens("""{"focus": {"~type": "number", "steps": {"~value": 3}}}""")
            )

        assertThat(generated.kotlin).contains("    const val Steps = 3.0f")
    }

    @Test
    fun `colours dimensions and font weights stay plain values`() {
        val generated =
            TokenGenerator.generate(
                tokens(
                    """{"color": {"~type": "color", "page": {"~value": "#0E1013"}},
                       "space": {"~type": "dimension", "gutter": {"~value": "16dp"}},
                       "font": {"weight": {"~type": "fontWeight", "bold": {"~value": 700}}}}"""
                )
            )

        assertThat(generated.kotlin).doesNotContain("const val Page")
        assertThat(generated.kotlin).doesNotContain("const val Gutter")
        assertThat(generated.kotlin).doesNotContain("const val Bold")
        assertThat(generated.kotlin).contains("val Page = Color(0xFF0E1013)")
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
    fun `a group name collision after conversion names both group paths`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                TokenGenerator.generate(
                    tokens(
                        """{"font": {"weight": {"~type": "fontWeight", "bold": {"~value": 700}}},
                           "font-weight": {"~type": "fontWeight", "bold": {"~value": 700}}}"""
                    )
                )
            }

        assertThat(error).hasMessageThat().contains("font-weight")
        assertThat(error).hasMessageThat().contains("FontWeight")
    }

    @Test
    fun `a token without a value is an error that names its path`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                TokenGenerator.generate(
                    tokens(
                        """{"color": {"~type": "color", "page": {"~description": "no value"}}}"""
                    )
                )
            }

        assertThat(error).hasMessageThat().contains("color.page")
    }

    @Test
    fun `an empty group is an error that names its path`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                TokenGenerator.generate(tokens("""{"size": {"~type": "dimension", "tv": {}}}"""))
            }

        assertThat(error).hasMessageThat().contains("size.tv")
    }

    @Test
    fun `the sizes radii spacing focus scale and weights of the real file are generated`() {
        val kotlin = TokenGenerator.generate(realTokens()).kotlin

        assertThat(kotlin).contains("val Title = 18.sp")
        assertThat(kotlin).contains("val Secondary = 12.5.sp")
        assertThat(kotlin).contains("val Rail = 14.5.sp")
        assertThat(kotlin).contains("val Pin = 34.sp")
        assertThat(kotlin).contains("val Pill = 999.dp")
        assertThat(kotlin).contains("val Card = 12.dp")
        assertThat(kotlin).contains("val GutterTv = 34.dp")
        assertThat(kotlin).contains("val TileScale = 1.04f")
        assertThat(kotlin).contains("val Semibold = FontWeight(600)")
    }

    @Test
    fun `the high contrast and the colour vision groups of the real file are generated`() {
        val kotlin = TokenGenerator.generate(realTokens()).kotlin

        assertThat(kotlin).contains("object HighContrast {")
        assertThat(kotlin).contains("object ColourVision {")
        assertThat(kotlin).contains("object RedGreen {")
        assertThat(kotlin).contains("object BlueYellow {")
        assertThat(kotlin).contains("val OutlineThick = 5.dp")
        assertThat(kotlin).contains("const val Dim = ")
    }

    @Test
    fun `the teal and white accent sets of the real file are generated with their high contrast`() {
        val generated = TokenGenerator.generate(realTokens())

        val teal =
            generated.kotlin
                .substringAfter("object AccentTeal {")
                .substringBefore("object AccentWhite")
        val white = generated.kotlin.substringAfter("object AccentWhite {")
        assertThat(teal).contains("val Accent = Color(0xFF4BECD0)")
        assertThat(teal).contains("val AccentSoft = Color(0xFFA2FCE8)")
        assertThat(teal).contains("val AccentInverse = Color(0xFF027464)")
        assertThat(teal).contains("val AccentTint = Color(0x214BECD0)")
        assertThat(teal).contains("object HighContrast {")
        assertThat(teal).contains("val AccentInverse = Color(0xFF026053)")
        assertThat(white).contains("val Accent = Color(0xFFEFF3FF)")
        assertThat(white).contains("val AccentSoft = Color(0xFFFFFFFF)")
        assertThat(white).contains("val AccentInverse = Color(0xFF636671)")
        assertThat(white).contains("val AccentInverse = Color(0xFF51555F)")
        assertThat(white).contains("object HighContrast {")
    }

    @Test
    fun `the high contrast group of the real file has the snackbar action of violet`() {
        val kotlin = TokenGenerator.generate(realTokens()).kotlin

        val high =
            kotlin.substringAfter("object HighContrast {").substringBefore("object AccentTeal")
        assertThat(high).contains("val AccentInverse = Color(0xFF5744A9)")
    }

    @Test
    fun `only the colour group becomes colour resources, so the accent sets do not`() {
        val xml = TokenGenerator.generate(realTokens()).colorsXml

        assertThat(xml).contains("""<color name="borealis_accent_inverse">#FF7C3AED</color>""")
        assertThat(xml).doesNotContain("teal")
        assertThat(xml).doesNotContain("white")
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
