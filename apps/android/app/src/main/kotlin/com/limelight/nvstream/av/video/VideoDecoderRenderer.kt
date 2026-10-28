package com.limelight.nvstream.av.video

// MediaCodecDecoderRenderer extends this class and calls its constructor, so it cannot become an
// interface without changing that class (Sprint 14 backlog).
@Suppress("AbstractClassCanBeInterface")
abstract class VideoDecoderRenderer {
    abstract fun setup(format: Int, width: Int, height: Int, redrawRate: Int): Int

    abstract fun start()

    abstract fun stop()

    // This is called once for each frame-start NALU. This means it will be called several times
    // for an IDR frame which contains several parameter sets and the I-frame data.
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("LongParameterList")
    abstract fun submitDecodeUnit(
        decodeUnitData: ByteArray?,
        decodeUnitLength: Int,
        decodeUnitType: Int,
        frameNumber: Int,
        frameType: Int,
        frameHostProcessingLatency: Char,
        receiveTimeUs: Long,
        enqueueTimeUs: Long,
    ): Int

    abstract fun cleanup()

    abstract fun getCapabilities(): Int

    abstract fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?)
}
