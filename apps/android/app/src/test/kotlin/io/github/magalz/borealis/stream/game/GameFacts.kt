package io.github.magalz.borealis.stream.game

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.ConnectionContext
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.StreamConfiguration
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamSession
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers

/** The stream session that the activity reports to. */
internal val GameRig.session: StreamSession
    get() = field("streamSession")

/** What the stream chrome shows now. */
internal val GameRig.phase: StreamPhase
    get() = session.state.value.phase

/** The connection that the activity built. */
internal val GameRig.connection: NvConnection
    get() = field("conn")

/**
 * What the connection knows about the start it will make: the host, the certificate, the stream.
 */
internal val GameRig.connectionContext: ConnectionContext
    get() = ReflectionHelpers.getField(connection, "context")

/** The stream that the activity asked the connection for. */
internal val GameRig.streamConfiguration: StreamConfiguration
    get() = checkNotNull(connectionContext.streamConfig)

/** The toasts that were shown, as a whole: no other, and each one with this text. */
internal fun assertToasts(vararg texts: String) {
    assertThat(ShadowToast.shownToastCount()).isEqualTo(texts.size)
    texts.forEach { assertThat(ShadowToast.showedToast(it)).isTrue() }
}
