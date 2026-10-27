package io.github.magalz.borealis.testing

import org.junit.rules.ExternalResource

/**
 * Closes what a test opens in its own body, after the test: the resources it hands to [own], last
 * one first. A rig that a test field holds is a rule itself; a rig that a test makes for one case
 * goes through here.
 */
class OwnedResources : ExternalResource() {
    private val owned = mutableListOf<AutoCloseable>()

    fun <T : AutoCloseable> own(resource: T): T = resource.also { owned += it }

    override fun after() {
        owned.asReversed().forEach { it.close() }
        owned.clear()
    }
}
