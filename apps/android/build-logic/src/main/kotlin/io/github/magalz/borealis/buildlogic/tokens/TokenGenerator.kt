package io.github.magalz.borealis.buildlogic.tokens

/** The two files generated from `design/tokens.json`. */
data class GeneratedTokens(val kotlin: String, val colorsXml: String)

/** Turns the DTCG token file into Compose constants and Android colour resources (ADR 0044). */
object TokenGenerator {
    fun generate(json: String): GeneratedTokens = TODO("not implemented")
}
