package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.StreamNotice.BluetoothOutput
import io.github.magalz.borealis.stream.StreamNotice.StereoFallback
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import org.junit.Test

/**
 * The notices that the stream gives the viewer (the audio notices of Sprint 17a): one is on show at
 * a time, the others wait their turn in the order they came, and the card says when its time is up.
 */
class StreamSessionNoticeTest {
    private val session = aSession()
    private val stereo71 = StereoFallback(SurroundLayout.SURROUND_7_1)
    private val stereo51 = StereoFallback(SurroundLayout.SURROUND_5_1)

    private fun streaming(): StreamSession =
        aSession().also {
            it.onConnectionStarted(aSessionInfo)
            it.onFirstFrame()
        }

    @Test
    fun `no notice is on show until one is given`() {
        assertThat(session.notice.value).isNull()
    }

    @Test
    fun `a notice is on show at once`() {
        session.notify(BluetoothOutput)

        assertThat(session.notice.value).isEqualTo(BluetoothOutput)
    }

    @Test
    fun `a notice that is shown goes, and nothing is on show after it`() {
        session.notify(BluetoothOutput)

        session.noticeShown(BluetoothOutput)

        assertThat(session.notice.value).isNull()
    }

    @Test
    fun `a second notice waits until the first has been shown`() {
        session.notify(BluetoothOutput)
        session.notify(stereo71)

        assertThat(session.notice.value).isEqualTo(BluetoothOutput)

        session.noticeShown(BluetoothOutput)

        assertThat(session.notice.value).isEqualTo(stereo71)
    }

    @Test
    fun `notices come out in the order they were given`() {
        session.notify(stereo71)
        session.notify(BluetoothOutput)
        session.notify(stereo51)

        val order = mutableListOf<StreamNotice?>()
        repeat(3) {
            order += session.notice.value
            session.noticeShown(session.notice.value!!)
        }

        assertThat(order).containsExactly(stereo71, BluetoothOutput, stereo51).inOrder()
        assertThat(session.notice.value).isNull()
    }

    @Test
    fun `a notice that is on show, or the last one waiting, is not queued again`() {
        session.notify(BluetoothOutput)
        session.notify(BluetoothOutput)
        session.notify(stereo71)
        session.notify(stereo71)

        session.noticeShown(BluetoothOutput)
        assertThat(session.notice.value).isEqualTo(stereo71)

        session.noticeShown(stereo71)
        assertThat(session.notice.value).isNull()
    }

    @Test
    fun `a notice that was shown can be given again`() {
        session.notify(BluetoothOutput)
        session.noticeShown(BluetoothOutput)

        session.notify(BluetoothOutput)

        assertThat(session.notice.value).isEqualTo(BluetoothOutput)
    }

    @Test
    fun `a notice that is not the one on show cannot end the one that is`() {
        session.notify(BluetoothOutput)
        session.notify(stereo71)

        session.noticeShown(stereo71)

        assertThat(session.notice.value).isEqualTo(BluetoothOutput)

        session.noticeShown(BluetoothOutput)
        session.noticeShown(BluetoothOutput)

        assertThat(session.notice.value).isEqualTo(stereo71)
    }

    @Test
    fun `notices given from several threads are all kept, each once`() {
        val given = listOf(BluetoothOutput, stereo71, stereo51)

        given.map { notice -> Thread { session.notify(notice) }.apply { start() } }.forEach {
            it.join()
        }

        val shown = mutableListOf<StreamNotice>()
        while (session.notice.value != null) {
            shown += session.notice.value!!
            session.noticeShown(session.notice.value!!)
        }
        assertThat(shown).containsExactlyElementsIn(given)
    }

    @Test
    fun `a notice given while the stream starts is on show, for the card to draw later`() {
        session.notify(BluetoothOutput)

        assertThat(session.state.value.phase).isEqualTo(StreamPhase.Starting(StartStage.CONNECTING))
        assertThat(session.notice.value).isEqualTo(BluetoothOutput)
    }

    @Test
    fun `a notice changes nothing else of the session, and none of the input`() {
        val started = streaming()
        val before = started.state.value
        val mode = started.chromeMode

        started.notify(BluetoothOutput)

        assertThat(started.state.value).isEqualTo(before)
        assertThat(started.chromeMode).isEqualTo(mode)
        assertThat(mode).isEqualTo(ChromeMode.IDLE)
    }
}
