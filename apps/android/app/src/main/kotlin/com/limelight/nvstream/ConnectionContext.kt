package com.limelight.nvstream

import com.limelight.nvstream.http.ComputerDetails
import java.security.cert.X509Certificate
import javax.crypto.SecretKey

class ConnectionContext {
    @JvmField var serverAddress: ComputerDetails.AddressTuple? = null
    @JvmField var httpsPort = 0
    @JvmField var isNvidiaServerSoftware = false
    @JvmField var serverCert: X509Certificate? = null
    @JvmField var streamConfig: StreamConfiguration? = null
    @JvmField var connListener: NvConnectionListener? = null
    @JvmField var riKey: SecretKey? = null
    @JvmField var riKeyId = 0

    // This is the version quad from the appversion tag of /serverinfo
    @JvmField var serverAppVersion: String? = null
    @JvmField var serverGfeVersion: String? = null
    @JvmField var serverCodecModeSupport = 0

    // This is the sessionUrl0 tag from /resume and /launch
    @JvmField var rtspSessionUrl: String? = null

    @JvmField var negotiatedWidth = 0
    @JvmField var negotiatedHeight = 0
    @JvmField var negotiatedHdr = false

    @JvmField var negotiatedRemoteStreaming = 0
    @JvmField var negotiatedPacketSize = 0

    @JvmField var videoCapabilities = 0
}
