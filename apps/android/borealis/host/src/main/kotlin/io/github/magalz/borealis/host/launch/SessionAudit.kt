package io.github.magalz.borealis.host.launch

/** Audit events of a stream session (stub). */
object SessionAudit {
    internal var clock: () -> Long = System::currentTimeMillis

    internal fun reset() {}

    @JvmStatic
    fun started(
        name: String?,
        address: String?,
        videoFormat: Int,
        width: Int,
        height: Int,
        fps: Int,
    ) {}

    @JvmStatic fun stopped() {}

    @JvmStatic fun terminated(errorCode: Int) {}
}
