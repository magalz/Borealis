package io.github.magalz.borealis.host.pairing

import java.io.IOException

/**
 * The host presented another certificate than the one pinned when the PC was paired. The message
 * never quotes certificate data; [TlsAudit.certificateChanged] records the fingerprints.
 */
class CertificateMismatchException(cause: Throwable? = null) :
    IOException("Server certificate mismatch", cause)
