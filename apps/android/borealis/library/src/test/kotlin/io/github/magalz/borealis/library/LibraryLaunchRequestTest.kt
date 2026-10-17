package io.github.magalz.borealis.library

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryLaunchRequestTest {
    private fun roundTrip(request: LibraryLaunchRequest) =
        LibraryLaunchRequest.fromExtras(request.toExtras())

    @Test
    fun `every request survives the extras`() {
        val requests =
            listOf(
                LibraryLaunchRequest.Hosts,
                LibraryLaunchRequest.Apps("host-1"),
                LibraryLaunchRequest.Launch("host-1", appId = 1_234),
                LibraryLaunchRequest.Pair("host-1"),
                LibraryLaunchRequest.Privacy,
            )

        for (request in requests) {
            assertThat(roundTrip(request)).isEqualTo(request)
        }
    }

    @Test
    fun `no extras is no request`() {
        assertThat(LibraryLaunchRequest.fromExtras(null)).isNull()
        assertThat(LibraryLaunchRequest.fromExtras(Bundle())).isNull()
    }

    @Test
    fun `an unknown request is no request`() {
        val extras = Bundle().apply { putString(LibraryLaunchRequest.KEY_REQUEST, "reboot") }

        assertThat(LibraryLaunchRequest.fromExtras(extras)).isNull()
    }

    @Test
    fun `a request of the wrong type is no request`() {
        val extras = Bundle().apply { putInt(LibraryLaunchRequest.KEY_REQUEST, 1) }

        assertThat(LibraryLaunchRequest.fromExtras(extras)).isNull()
    }

    @Test
    fun `a host request without a host is no request`() {
        for (request in
            listOf(
                LibraryLaunchRequest.Apps("host-1"),
                LibraryLaunchRequest.Launch("host-1", 7),
                LibraryLaunchRequest.Pair("host-1"),
            )) {
            val extras = request.toExtras().apply { remove(LibraryLaunchRequest.KEY_HOST_ID) }

            assertThat(LibraryLaunchRequest.fromExtras(extras)).isNull()
        }
    }

    @Test
    fun `a blank host is no host`() {
        assertThat(roundTrip(LibraryLaunchRequest.Apps(" "))).isNull()
    }

    @Test
    fun `a launch without an app is no request`() {
        val extras =
            LibraryLaunchRequest.Launch("host-1", 7).toExtras().apply {
                remove(LibraryLaunchRequest.KEY_APP_ID)
            }

        assertThat(LibraryLaunchRequest.fromExtras(extras)).isNull()
    }

    @Test
    fun `a launch whose app is not a number is no request`() {
        val extras =
            LibraryLaunchRequest.Launch("host-1", 7).toExtras().apply {
                putString(LibraryLaunchRequest.KEY_APP_ID, "seven")
            }

        assertThat(LibraryLaunchRequest.fromExtras(extras)).isNull()
    }
}
