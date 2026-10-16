package io.github.magalz.borealis.stream.host

/**
 * The keys (Windows virtual-key codes) that were sent down to the PC and have not gone up, so the
 * chrome can release them when it takes the input. Safe to call from any thread.
 */
class HeldKeys {
    private val keys = LinkedHashSet<Int>()

    /** [vk] went down; a key pressed again counts at its latest press. */
    @Synchronized
    fun down(vk: Int) {
        keys.remove(vk)
        keys.add(vk)
    }

    /** [vk] went up. */
    @Synchronized
    fun up(vk: Int) {
        keys.remove(vk)
    }

    /** Calls [release] for every held key, the last pressed first, and forgets them. */
    fun releaseAll(release: (Int) -> Unit) {
        val held = synchronized(this) { keys.toList().asReversed().also { keys.clear() } }
        held.forEach(release)
    }
}
