package com.limelight.binding.video

// Package-private in the Java: only the renderer and, by field name, its tests reach it.
internal class VideoStats {
    var decoderTimeMs = 0L
    // BOREALIS: the decode time in microseconds, for the overlay (stream.perf)
    var decoderTimeUs = 0L
    var totalTimeMs = 0L
    var totalFrames = 0
    var totalFramesReceived = 0
    var totalFramesRendered = 0
    var frameLossEvents = 0
    var framesLost = 0
    // A window that saw no host processing latency holds the char 0.
    var minHostProcessingLatency = Char.MIN_VALUE
    var maxHostProcessingLatency = Char.MIN_VALUE
    var totalHostProcessingLatency = 0
    var framesWithHostProcessingLatency = 0
    var measurementStartTimestamp = 0L
    // BOREALIS: encoded bytes received, for the bitrate (stream.perf)
    var totalBytes = 0L

    fun add(other: VideoStats) {
        decoderTimeMs += other.decoderTimeMs
        decoderTimeUs += other.decoderTimeUs // BOREALIS
        totalTimeMs += other.totalTimeMs
        totalFrames += other.totalFrames
        totalFramesReceived += other.totalFramesReceived
        totalFramesRendered += other.totalFramesRendered
        frameLossEvents += other.frameLossEvents
        framesLost += other.framesLost

        minHostProcessingLatency =
            if (minHostProcessingLatency == Char.MIN_VALUE) {
                other.minHostProcessingLatency
            } else {
                minOf(minHostProcessingLatency.code, other.minHostProcessingLatency.code).toChar()
            }
        maxHostProcessingLatency =
            maxOf(maxHostProcessingLatency.code, other.maxHostProcessingLatency.code).toChar()
        totalHostProcessingLatency += other.totalHostProcessingLatency
        framesWithHostProcessingLatency += other.framesWithHostProcessingLatency
        totalBytes += other.totalBytes // BOREALIS

        if (measurementStartTimestamp == 0L) {
            measurementStartTimestamp = other.measurementStartTimestamp
        }

        assert(other.measurementStartTimestamp >= measurementStartTimestamp)
    }

    fun copy(other: VideoStats) {
        decoderTimeMs = other.decoderTimeMs
        decoderTimeUs = other.decoderTimeUs // BOREALIS
        totalTimeMs = other.totalTimeMs
        totalFrames = other.totalFrames
        totalFramesReceived = other.totalFramesReceived
        totalFramesRendered = other.totalFramesRendered
        frameLossEvents = other.frameLossEvents
        framesLost = other.framesLost
        minHostProcessingLatency = other.minHostProcessingLatency
        maxHostProcessingLatency = other.maxHostProcessingLatency
        totalHostProcessingLatency = other.totalHostProcessingLatency
        framesWithHostProcessingLatency = other.framesWithHostProcessingLatency
        totalBytes = other.totalBytes // BOREALIS
        measurementStartTimestamp = other.measurementStartTimestamp
    }

    fun clear() {
        decoderTimeMs = 0
        decoderTimeUs = 0 // BOREALIS
        totalTimeMs = 0
        totalFrames = 0
        totalFramesReceived = 0
        totalFramesRendered = 0
        frameLossEvents = 0
        framesLost = 0
        minHostProcessingLatency = Char.MIN_VALUE
        maxHostProcessingLatency = Char.MIN_VALUE
        totalHostProcessingLatency = 0
        framesWithHostProcessingLatency = 0
        totalBytes = 0 // BOREALIS
        measurementStartTimestamp = 0
    }
}
