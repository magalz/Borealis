package io.github.magalz.borealis.input

/** Holds the USB permission requests of the driver service until the stream shows a picture. */
class UsbPermissionRequests<D : Any>(private val asker: Asker<D>) {
    /** Puts the permission dialog of [device] on screen. */
    fun interface Asker<D> {
        fun ask(device: D)
    }

    fun request(name: String, device: D) = Unit

    fun onReady() = Unit

    fun resolved(name: String) = Unit

    fun reset() = Unit
}
