package io.github.magalz.borealis.testing

import org.junit.rules.ExternalResource

/**
 * Closes what a test opens in its own body, after the test: the resources it hands to [own], last
 * one first. Every one is closed even when an earlier one throws; the first failure is rethrown
 * with the others attached as suppressed. A rig that a test field holds is a rule itself; a rig
 * that a test makes for one case goes through here.
 */
class OwnedResources : ExternalResource() {
    private val owned = mutableListOf<AutoCloseable>()

    fun <T : AutoCloseable> own(resource: T): T = resource.also { owned += it }

    @Suppress("TooGenericExceptionCaught") // Any failure of a close must not skip the next ones.
    override fun after() {
        var failure: Throwable? = null
        for (resource in owned.asReversed()) {
            try {
                resource.close()
            } catch (error: Throwable) {
                val first = failure
                if (first == null) failure = error else first.addSuppressed(error)
            }
        }
        owned.clear()
        failure?.let { throw it }
    }
}
