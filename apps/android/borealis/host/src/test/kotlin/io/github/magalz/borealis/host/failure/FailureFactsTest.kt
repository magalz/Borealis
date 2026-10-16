package io.github.magalz.borealis.host.failure

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FailureFactsTest {
    @Test
    fun `the facts of a plain failure name no cause and no retry`() {
        val facts =
            FailureFacts(
                stage = "RTSP handshake",
                errorCode = -1,
                portFlags = 0,
                terminated = false,
            )

        assertThat(facts.cause).isEqualTo(CauseKind.NONE)
        assertThat(facts.httpCode).isNull()
        assertThat(facts.resumeCollision).isFalse()
        assertThat(facts.retriesExhausted).isFalse()
    }
}
