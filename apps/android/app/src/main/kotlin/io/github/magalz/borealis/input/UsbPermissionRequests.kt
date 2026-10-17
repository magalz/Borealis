package io.github.magalz.borealis.input

/**
 * Holds the USB permission requests of the driver service until the stream shows a picture, so the
 * system's permission dialog does not get in the way of the connection. After the picture, requests
 * go out at once. Devices are told apart by [request]'s name, and a device is asked once until its
 * request is [resolved].
 *
 * Not thread-safe: the driver service calls it from the main thread only.
 */
class UsbPermissionRequests<D : Any>(private val asker: Asker<D>) {
    /** Puts the permission dialog of [device] on screen. */
    fun interface Asker<D> {
        fun ask(device: D)
    }

    private var ready = false
    private val held = LinkedHashMap<String, D>()
    private val pending = HashSet<String>()

    /** Asks for [device] now when the picture is up, else holds it. */
    fun request(name: String, device: D) {
        if (name in pending) return
        if (!ready) {
            held[name] = device
            return
        }
        pending += name
        asker.ask(device)
    }

    /** The picture is up: asks for the held devices, and from now on asks at once. */
    fun onReady() {
        ready = true
        val devices = held.entries.toList()
        held.clear()
        for ((name, device) in devices) request(name, device)
    }

    /**
     * The request of [name] was answered, granted or not, or was refused: it can be asked again.
     */
    fun resolved(name: String) {
        pending -= name
        held -= name
    }

    /** A new stream starts: nothing is held or pending, and the picture is not up yet. */
    fun reset() {
        ready = false
        held.clear()
        pending.clear()
    }
}
