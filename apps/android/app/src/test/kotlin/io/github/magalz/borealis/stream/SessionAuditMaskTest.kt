package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.host.launch.SessionAudit
import org.junit.Test

/**
 * `SessionAudit` cannot see `MoonBridge`, so it keeps its own copy of the video format masks. The
 * copy that tells an HDR session from an SDR one is tied to the mask of the bridge here, where both
 * are visible, so the two cannot drift apart.
 */
class SessionAuditMaskTest {
    @Test
    fun `the 10-bit mask of the audit is the mask of the bridge`() {
        assertThat(SessionAudit.MASK_10BIT).isEqualTo(MoonBridge.VIDEO_FORMAT_MASK_10BIT)
    }
}
