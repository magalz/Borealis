package io.github.magalz.borealis.support

import android.content.Context
import android.content.ContextWrapper

/**
 * A context that hands out the system services that a test provides, by their name, and the
 * platform's for the rest. A service that is provided as null is a service that this device does
 * not have.
 */
class ServicesContext(base: Context, vararg services: Pair<String, Any?>) : ContextWrapper(base) {
    private val provided = services.toMap()

    override fun getSystemService(name: String): Any? =
        if (provided.containsKey(name)) provided[name] else super.getSystemService(name)
}
