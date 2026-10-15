package io.github.magalz.borealis.buildlogic.tokens

import groovy.json.JsonSlurper

/** The two files generated from `design/tokens.json`. */
data class GeneratedTokens(val kotlin: String, val colorsXml: String)

/**
 * Turns the DTCG token file into Compose constants and Android colour resources (ADR 0044).
 *
 * Every failure is an [IllegalArgumentException] that names the offending token path, so a bad edit
 * to `design/tokens.json` points at the token. Font families are not generated: the theme owns
 * them, because the font files are resources.
 */
object TokenGenerator {
    private const val PACKAGE = "io.github.magalz.borealis.designsystem.tokens"
    private const val HEADER =
        "Generated from design/tokens.json by BorealisTokensTask (ADR 0044). Do not edit."
    private const val COLOR_GROUP = "color"

    /** `font.weight` is generated as `FontWeight`; the `font` group adds no object of its own. */
    private const val FONT_GROUP = "font"
    private const val FONT_FAMILY = "fontFamily"

    private val IDENTIFIER = Regex("[A-Za-z][A-Za-z0-9]*")
    private val SEPARATORS = Regex("[^A-Za-z0-9]+")

    /** A leaf token after validation. [expression] is the Kotlin initialiser. */
    private class Token(
        val path: List<String>,
        val type: String,
        val expression: String,
        val description: String?,
        val argb: String?,
    ) {
        val dotted
            get() = path.joinToString(".")
    }

    /** A generated Kotlin object; [source] is the token group it came from. */
    private class Scope(val source: List<String>) {
        val members = LinkedHashMap<String, Any>()
    }

    fun generate(json: String): GeneratedTokens {
        val root = JsonSlurper().parseText(json) as? Map<*, *>
        require(root != null) { "design/tokens.json must be a JSON object" }
        val tokens = ArrayList<Token>()
        collect(root, emptyList(), null, tokens)
        return GeneratedTokens(kotlin(tokens), colorsXml(tokens))
    }

    private fun collect(
        group: Map<*, *>,
        path: List<String>,
        inheritedType: String?,
        into: MutableList<Token>,
    ) {
        val type = group["\$type"] as? String ?: inheritedType
        for ((key, value) in group) {
            if (key !is String || key.startsWith("$") || value !is Map<*, *>) continue
            val child = path + key
            if (value.containsKey("\$value")) {
                token(child, value, type)?.let(into::add)
            } else {
                collect(value, child, type, into)
            }
        }
    }

    private fun token(path: List<String>, node: Map<*, *>, inheritedType: String?): Token? {
        val dotted = path.joinToString(".")
        val type = node["\$type"] as? String ?: inheritedType
        requireNotNull(type) { "$dotted: no \$type on the token or on any group above it" }
        if (type == FONT_FAMILY) return null
        val value = node["\$value"]
        val description = (node["\$description"] as? String)?.let(TokenValues::singleLine)
        return when (type) {
            "color" -> {
                val argb = TokenValues.argb(dotted, value)
                Token(path, type, "Color(0x$argb)", description, argb)
            }
            "dimension" ->
                Token(path, type, TokenValues.dimension(dotted, value), description, null)
            "number" -> Token(path, type, TokenValues.number(dotted, value), description, null)
            "fontWeight" ->
                Token(path, type, TokenValues.fontWeight(dotted, value), description, null)
            else -> throw IllegalArgumentException("$dotted: unsupported \$type \"$type\"")
        }
    }

    private fun kotlinName(path: List<String>, segment: String): String {
        val name =
            segment.split(SEPARATORS).joinToString("") { it.replaceFirstChar(Char::uppercase) }
        require(IDENTIFIER.matches(name)) {
            "${path.joinToString(".")}: \"$segment\" does not convert to a Kotlin name"
        }
        return name
    }

    private fun kotlin(tokens: List<Token>): String {
        val root = Scope(emptyList())
        for (token in tokens) {
            val groups = token.path.dropLast(1)
            var scope = root
            for ((index, segment) in groups.withIndex()) {
                if (index == 0 && segment == FONT_GROUP) continue
                val source = groups.take(index + 1)
                val prefix = if (groups.first() == FONT_GROUP) "Font" else ""
                val name = prefix + kotlinName(source, segment)
                val existing = scope.members.getOrPut(name) { Scope(source) }
                require(existing is Scope && existing.source == source) {
                    "${source.joinToString(".")}: converts to \"$name\", which another token " +
                        "or group already uses"
                }
                scope = existing
            }
            val name = kotlinName(token.path, token.path.last())
            val clash = scope.members.putIfAbsent(name, token)
            require(clash == null) {
                val other = (clash as? Token)?.dotted ?: "a group"
                "${token.dotted}: converts to \"$name\", which $other already uses"
            }
        }
        val out = StringBuilder()
        out.append("// $HEADER\n")
        out.append("package $PACKAGE\n\n")
        for (import in imports(tokens)) out.append("import $import\n")
        out.append("\nobject BorealisTokens {\n")
        emit(root, 1, out)
        out.append("}\n")
        return out.toString()
    }

    private fun imports(tokens: List<Token>): List<String> {
        val types = tokens.map { it.type }.toSet()
        val units = tokens.filter { it.type == "dimension" }.map { it.expression.takeLast(2) }
        return buildList {
            if ("color" in types) add("androidx.compose.ui.graphics.Color")
            if ("fontWeight" in types) add("androidx.compose.ui.text.font.FontWeight")
            if ("dp" in units) add("androidx.compose.ui.unit.dp")
            if ("sp" in units) add("androidx.compose.ui.unit.sp")
        }
    }

    private fun emit(scope: Scope, depth: Int, out: StringBuilder) {
        val indent = "    ".repeat(depth)
        for ((name, member) in scope.members) {
            when (member) {
                is Scope -> {
                    out.append("$indent/** Tokens of `${member.source.joinToString(".")}`. */\n")
                    out.append("${indent}object $name {\n")
                    emit(member, depth + 1, out)
                    out.append("$indent}\n")
                }
                is Token -> {
                    member.description?.let { out.append("$indent/** $it */\n") }
                    out.append("${indent}val $name = ${member.expression}\n")
                }
            }
        }
    }

    private fun colorsXml(tokens: List<Token>): String {
        val names = HashMap<String, Token>()
        val out = StringBuilder()
        out.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        out.append("<!-- $HEADER -->\n")
        out.append("<resources>\n")
        for (token in tokens.filter { it.path.first() == COLOR_GROUP && it.argb != null }) {
            val name =
                "borealis_" +
                    token.path.drop(1).joinToString("_") {
                        it.replace(SEPARATORS, "_").lowercase()
                    }
            val clash = names.putIfAbsent(name, token)
            require(clash == null) {
                "${token.dotted}: becomes the colour resource \"$name\", which " +
                    "${clash?.dotted} already uses"
            }
            out.append("    <color name=\"$name\">#${token.argb}</color>\n")
        }
        out.append("</resources>\n")
        return out.toString()
    }
}
