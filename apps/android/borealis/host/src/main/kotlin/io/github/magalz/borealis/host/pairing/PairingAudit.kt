package io.github.magalz.borealis.host.pairing

/** Audit events of the pairing flow (stub). */
object PairingAudit {
    @JvmStatic fun started(host: String?, restarted: Boolean) {}

    @JvmStatic fun finished(host: String?, pairState: String) {}
}
