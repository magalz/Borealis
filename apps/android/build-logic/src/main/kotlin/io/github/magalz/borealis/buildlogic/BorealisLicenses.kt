package io.github.magalz.borealis.buildlogic

import app.cash.licensee.LicenseeExtension
import app.cash.licensee.UnusedAction
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Licence allowlist enforced by licensee (ADR 0041, docs/policies/dependencies.md): Borealis is
 * GPL-3.0, so a dependency outside these licences, or one without a recognisable licence, fails the
 * build until the owner decides otherwise in an ADR.
 */
internal object BorealisLicenses {
    const val PLUGIN_ID = "app.cash.licensee"

    /** SPDX identifiers that are GPL-3.0 compatible and on the policy allowlist. */
    val ALLOWED_SPDX = listOf("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause", "ISC", "Zlib")

    fun Project.configureLicensee() {
        pluginManager.apply(PLUGIN_ID)
        extensions.configure<LicenseeExtension> {
            ALLOWED_SPDX.forEach { allow(it) }
            // The allowlist is policy, not a mirror of today's dependencies.
            unusedAction(UnusedAction.IGNORE)
            // JCodec's POM links its licence page instead of naming an SPDX identifier; the text is
            // the two-clause "FreeBSD" licence (BSD-2-Clause, see NOTICE).
            allowUrl("http://jcodec.org/lic.html") {
                because("JCodec is BSD-2-Clause (FreeBSD licence), declared by URL only")
            }
            // Bouncy Castle publishes its MIT-style licence only as a URL, with no SPDX identifier.
            allowUrl("https://www.bouncycastle.org/licence.html") {
                because("Bouncy Castle Licence, the MIT licence under another name (see NOTICE)")
            }
        }
    }
}
