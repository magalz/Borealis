package io.github.magalz.borealis

import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.host.launch.ResumeCollisions
import io.github.magalz.borealis.host.launch.StageFailure

/**
 * The listener moonlight-common-c reports to. A failed RTSP handshake right after an accepted
 * `/resume` is held back here so that [com.limelight.nvstream.NvConnection] can restart the start
 * once; every decision is made by [collisions].
 */
class HeldStageFailures
@JvmOverloads
constructor(
    private val inner: NvConnectionListener,
    private val collisions: ResumeCollisions,
    private val rtspStageName: () -> String = {
        MoonBridge.getStageName(ResumeCollisions.STAGE_RTSP_HANDSHAKE)
    },
) : NvConnectionListener by inner {
    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
        val isRtspStage = stage == MoonBridge.getStageName(ResumeCollisions.STAGE_RTSP_HANDSHAKE)
        when (collisions.onStageFailed(isRtspStage, errorCode)) {
            StageFailure.HOLD -> Unit
            StageFailure.REPORT_COLLISION -> inner.stageFailed(stage, 0, errorCode)
            StageFailure.REPORT -> inner.stageFailed(stage, portFlags, errorCode)
        }
    }
}
