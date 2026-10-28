// The stream activity looks the class of Samsung up by its name, so the fake takes that package.
@file:Suppress("InvalidPackageDeclaration")

package com.samsung.android.view

import android.content.ComponentName
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The window manager that some Samsung devices have, as far as the stream activity uses it: it asks
 * for the meta keys while the input is grabbed. The class is looked up and called by reflection, so
 * the fake has the two members that the activity names. It is not there for the devices of other
 * makers, and here it answers no manager (the activity warns and goes on) until a test makes it
 * [available]. A test calls [reset] when it is done.
 */
class SemWindowManager private constructor() {
    @Suppress("unused") // Called by reflection.
    fun requestMetaKeyEvent(component: ComponentName, enabled: Boolean) {
        requests += component to enabled
        failure?.let { throw it }
    }

    companion object {
        /** What the activity asked for, in order: the component and whether it wants the keys. */
        val requests = CopyOnWriteArrayList<Pair<ComponentName, Boolean>>()

        /** Whether the device has a manager to give. */
        @Volatile var available = false

        /** What a request throws, when the manager fails. */
        @Volatile var failure: RuntimeException? = null

        @Suppress("unused") // Called by reflection.
        @JvmStatic
        fun getInstance(): SemWindowManager? = if (available) SemWindowManager() else null

        fun reset() {
            requests.clear()
            available = false
            failure = null
        }
    }
}
