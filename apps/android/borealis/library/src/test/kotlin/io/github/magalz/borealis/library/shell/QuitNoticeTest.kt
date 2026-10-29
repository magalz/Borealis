package io.github.magalz.borealis.library.shell

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QuitNoticeTest {
    private val notice = QuitNotice()

    @Test
    fun `a notice left while a Library is there is delivered once`() = runTest {
        notice.attach()

        notice.post(LeaveNotice.Quit)

        notice.notices.test {
            awaitItem()
            expectNoEvents()
        }
        notice.notices.test { expectNoEvents() }
    }

    @Test
    fun `a disconnect notice is delivered as it was posted, with the app name`() = runTest {
        notice.attach()

        notice.post(LeaveNotice.Disconnected("Desktop"))

        notice.notices.test {
            assertThat(awaitItem()).isEqualTo(LeaveNotice.Disconnected("Desktop"))
        }
    }

    @Test
    fun `a notice left while the Library does not show waits for it`() = runTest {
        notice.attach()

        notice.notices.test { expectNoEvents() }
        notice.post(LeaveNotice.Quit)

        notice.notices.test { awaitItem() }
    }

    @Test
    fun `a notice left with no Library is dropped`() = runTest {
        notice.post(LeaveNotice.Quit)

        notice.attach()

        notice.notices.test { expectNoEvents() }
    }

    @Test
    fun `a notice that waits goes with the Library that leaves`() = runTest {
        notice.attach()
        notice.post(LeaveNotice.Quit)

        notice.detach()
        notice.attach()

        notice.notices.test { expectNoEvents() }
    }

    @Test
    fun `a notice stays while another Library is still there`() = runTest {
        notice.attach()
        notice.attach()
        notice.post(LeaveNotice.Quit)

        notice.detach()

        notice.notices.test { awaitItem() }
    }
}
