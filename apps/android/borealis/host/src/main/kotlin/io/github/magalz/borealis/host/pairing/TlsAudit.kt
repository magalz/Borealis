package io.github.magalz.borealis.host.pairing

import java.security.cert.Certificate

/** Audit events of TLS and pinning failures (stub). */
object TlsAudit {
    @JvmStatic fun certificateChanged(host: String?, presented: Certificate, pinned: Certificate) {}

    @JvmStatic fun failure(host: String?, error: Throwable) {}
}
