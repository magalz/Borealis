package io.github.magalz.borealis.host

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.net.InetAddress
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element

/** Guards the scrubbing and provenance rules of `core/tests/fixtures/README.md`. */
class FixturePolicyTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `fixtures directory exists`() {
        assertThat(Fixtures.root().isDirectory).isTrue()
    }

    @Test
    fun `committed LuminalShine fixtures follow the policy`() {
        assertThat(violations(Fixtures.root())).isEmpty()
    }

    @Test
    fun `unscrubbed address fields are reported with file and field`() {
        val version = temp.newFolder("luminalshine", "1.0")
        version
            .resolve("serverinfo-http.xml")
            .writeText(
                "<root><hostname>my-pc</hostname><uniqueid>ABC</uniqueid>" +
                    "<mac>AA:BB:CC:DD:EE:FF</mac><LocalIP>192.168.1.5</LocalIP>" +
                    "<ExternalIP>203.0.113.7</ExternalIP><LocalIP6>fe80::1</LocalIP6>" +
                    "<GfeVersion>3.23.0.74</GfeVersion></root>"
            )
        version
            .resolve("PROVENANCE.md")
            .writeText(provenanceTable("serverinfo-http.xml" to "device"))

        val found = violations(temp.root).joinToString("\n")

        for (field in listOf("hostname", "uniqueid", "mac", "LocalIP", "ExternalIP", "LocalIP6")) {
            assertThat(found).contains("luminalshine/1.0/serverinfo-http.xml: <$field>")
        }
        assertThat(found).doesNotContain("GfeVersion")
    }

    @Test
    fun `scrubbed addresses pass`() {
        val version = temp.newFolder("luminalshine", "1.0")
        version
            .resolve("serverinfo-http.xml")
            .writeText(
                "<root><hostname>LUMINALSHINE-HOST</hostname>" +
                    "<uniqueid>00000000-0000-0000-0000-000000000001</uniqueid>" +
                    "<mac>00:00:00:00:00:00</mac><LocalIP>192.0.2.10</LocalIP>" +
                    "<LocalIP6>2001:db8::10</LocalIP6><GfeVersion>3.23.0.74</GfeVersion></root>"
            )
        version
            .resolve("PROVENANCE.md")
            .writeText(provenanceTable("serverinfo-http.xml" to "source"))

        assertThat(violations(temp.root)).isEmpty()
    }

    @Test
    fun `files without a device or source provenance row are reported`() {
        val version = temp.newFolder("luminalshine", "1.0")
        for (name in listOf("a.xml", "b.xml", "c.xml")) version.resolve(name).writeText("<root/>")
        version
            .resolve("PROVENANCE.md")
            .writeText(provenanceTable("a.xml" to "guess", "b.xml" to "device"))

        assertThat(violations(temp.root))
            .containsExactly(
                "luminalshine/1.0/a.xml: provenance is 'guess', expected device or source",
                "luminalshine/1.0/c.xml: no row in PROVENANCE.md",
            )
    }

    @Test
    fun `a version folder without PROVENANCE is reported`() {
        temp.newFolder("luminalshine", "1.0").resolve("a.xml").writeText("<root/>")

        assertThat(violations(temp.root))
            .containsExactly("luminalshine/1.0: PROVENANCE.md is missing")
    }

    @Test
    fun `an empty fixtures tree is reported instead of passing`() {
        temp.newFolder("luminalshine", "1.0")

        assertThat(violations(temp.root)).contains("luminalshine: no fixtures found")
    }

    @Test
    fun `a DOCTYPE is rejected instead of resolving entities`() {
        val version = temp.newFolder("luminalshine", "1.0")
        version
            .resolve("serverinfo-http.xml")
            .writeText(
                "<!DOCTYPE root [<!ENTITY h \"LUMINALSHINE-HOST\">]>" +
                    "<root><hostname>&h;</hostname></root>"
            )
        version
            .resolve("PROVENANCE.md")
            .writeText(provenanceTable("serverinfo-http.xml" to "device"))

        assertThat(violations(temp.root))
            .containsExactly("luminalshine/1.0/serverinfo-http.xml: not readable as plain XML")
    }

    @Test
    fun `malformed XML is reported with the file`() {
        val version = temp.newFolder("luminalshine", "1.0")
        version.resolve("serverinfo-http.xml").writeText("<root><hostname>")
        version
            .resolve("PROVENANCE.md")
            .writeText(provenanceTable("serverinfo-http.xml" to "device"))

        assertThat(violations(temp.root))
            .containsExactly("luminalshine/1.0/serverinfo-http.xml: not readable as plain XML")
    }

    @Test
    fun `malformed IPv6 values are reported with file and field`() {
        val version = temp.newFolder("luminalshine", "1.0")
        version
            .resolve("serverinfo-http.xml")
            .writeText(
                "<root><LocalIP6>2001:db8</LocalIP6><ExternalIP6>2001:db8::g</ExternalIP6></root>"
            )
        version
            .resolve("PROVENANCE.md")
            .writeText(provenanceTable("serverinfo-http.xml" to "device"))

        val found = violations(temp.root).joinToString("\n")

        assertThat(found).contains("luminalshine/1.0/serverinfo-http.xml: <LocalIP6>")
        assertThat(found).contains("luminalshine/1.0/serverinfo-http.xml: <ExternalIP6>")
    }

    @Test
    fun `IPv4 values must be a dotted quad in 192_0_2_0 24`() {
        val version = temp.newFolder("luminalshine", "1.0")
        version
            .resolve("serverinfo-http.xml")
            .writeText(
                "<root><LocalIP>192.0.2.999</LocalIP><ExternalIP>192.0.2..</ExternalIP></root>"
            )
        version
            .resolve("PROVENANCE.md")
            .writeText(provenanceTable("serverinfo-http.xml" to "device"))

        val found = violations(temp.root).joinToString("\n")

        assertThat(found)
            .contains("luminalshine/1.0/serverinfo-http.xml: <LocalIP> is '192.0.2.999'")
        assertThat(found)
            .contains("luminalshine/1.0/serverinfo-http.xml: <ExternalIP> is '192.0.2..'")
    }

    @Test
    fun `duplicate provenance rows are reported`() {
        val version = temp.newFolder("luminalshine", "1.0")
        version.resolve("a.xml").writeText("<root/>")
        version
            .resolve("PROVENANCE.md")
            .writeText(provenanceTable("a.xml" to "device", "a.xml" to "source"))

        assertThat(violations(temp.root))
            .containsExactly("luminalshine/1.0/a.xml: duplicate row in PROVENANCE.md")
    }

    private fun provenanceTable(vararg rows: Pair<String, String>): String =
        "| File | Provenance | Capture | Scrubbed fields |\n| --- | --- | --- | --- |\n" +
            rows.joinToString("\n") { (file, provenance) ->
                "| `$file` | `$provenance` | now | none |"
            }

    private fun violations(root: File): List<String> {
        val luminalshine = root.resolve("luminalshine")
        val versions = luminalshine.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }
        return versions.flatMap { version ->
            val label = "luminalshine/${version.name}"
            val xml = version.walkTopDown().filter { it.extension == "xml" }.toList()
            xml.flatMap { scrubViolations(it, "$label/${relativePath(it, version)}") } +
                provenanceViolations(version, label)
        }
    }

    private fun relativePath(file: File, base: File): String =
        file.relativeTo(base).path.replace(File.separatorChar, '/')

    private fun scrubViolations(file: File, label: String): List<String> {
        val doc =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val elements = doc.getElementsByTagName("*")
        return (0 until elements.length)
            .map { elements.item(it) as Element }
            .mapNotNull { element ->
                val value = element.textContent.trim()
                val name = element.tagName
                val rule = scrubRule(name)
                if (rule == null || rule.accepts(value)) {
                    null
                } else {
                    "$label: <$name> is '$value', expected ${rule.description}"
                }
            }
    }

    private class ScrubRule(val description: String, val accepts: (String) -> Boolean)

    /** The scrubbing rule for an element, or null for fields the policy does not constrain. */
    private fun scrubRule(element: String): ScrubRule? =
        when {
            element == "hostname" -> exactly("LUMINALSHINE-HOST")
            element == "uniqueid" -> exactly("00000000-0000-0000-0000-000000000001")
            element == "mac" -> exactly("00:00:00:00:00:00")
            element == "LocalIP" || element == "ExternalIP" ->
                ScrubRule("an address in 192.0.2.0/24") {
                    it.startsWith("192.0.2.") && isLiteral(it)
                }
            element.contains("ip6", ignoreCase = true) ||
                element.contains("ipv6", ignoreCase = true) ->
                ScrubRule("an address in 2001:db8::/32") { isDocumentationIpv6(it) }
            else -> null
        }

    private fun exactly(value: String) = ScrubRule("'$value'") { it == value }

    private fun isLiteral(value: String) = Regex("[0-9.]+").matches(value)

    private fun isDocumentationIpv6(value: String): Boolean {
        if (!Regex("[0-9a-fA-F:.]+").matches(value) || ':' !in value) return false
        val bytes = InetAddress.getByName(value).address
        return bytes.size == 16 &&
            bytes[0] == 0x20.toByte() &&
            bytes[1] == 0x01.toByte() &&
            bytes[2] == 0x0d.toByte() &&
            bytes[3] == 0xb8.toByte()
    }

    private fun provenanceViolations(version: File, label: String): List<String> {
        val provenance = version.resolve("PROVENANCE.md")
        if (!provenance.isFile) return listOf("$label: PROVENANCE.md is missing")
        val rows =
            provenance
                .readLines()
                .filter { it.trimStart().startsWith("|") }
                .map { line -> line.trim().trim('|').split('|').map { it.trim().trim('`') } }
                .filter { it.size >= 2 }
                .associate { it[0] to it[1] }
        return version
            .walkTopDown()
            .filter { it.isFile && it.name != "PROVENANCE.md" }
            .map { relativePath(it, version) }
            .sorted()
            .mapNotNull { name ->
                when (val value = rows[name]) {
                    null -> "$label/$name: no row in PROVENANCE.md"
                    "device",
                    "source" -> null
                    else -> "$label/$name: provenance is '$value', expected device or source"
                }
            }
            .toList()
    }
}
