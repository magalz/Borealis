package io.github.magalz.borealis.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The permission dialog of a USB pad stays off the screen until the stream shows a picture, so it
 * does not get in the way of the connection; a pad that wakes later is asked at once.
 */
class UsbPermissionRequestsTest {
    private val asked = mutableListOf<String>()
    private val requests = UsbPermissionRequests<String> { asked += it }

    @Test
    fun `before the picture nothing is requested`() {
        requests.request("pad-1", "pad-1")

        assertThat(asked).isEmpty()
    }

    @Test
    fun `the picture asks for the held devices once each`() {
        requests.request("pad-1", "pad-1")
        requests.request("pad-2", "pad-2")
        requests.request("pad-1", "pad-1")

        requests.onReady()

        assertThat(asked).containsExactly("pad-1", "pad-2").inOrder()
    }

    @Test
    fun `a second signal does not ask again`() {
        requests.request("pad-1", "pad-1")
        requests.onReady()

        requests.onReady()

        assertThat(asked).containsExactly("pad-1")
    }

    @Test
    fun `after the picture a request goes out at once`() {
        requests.onReady()

        requests.request("pad-1", "pad-1")

        assertThat(asked).containsExactly("pad-1")
    }

    @Test
    fun `the same device is not asked twice while a request is pending`() {
        requests.onReady()
        requests.request("pad-1", "pad-1")

        requests.request("pad-1", "pad-1")

        assertThat(asked).containsExactly("pad-1")
    }

    @Test
    fun `a device is asked again once its request was answered`() {
        requests.onReady()
        requests.request("pad-1", "pad-1")
        requests.resolved("pad-1")

        requests.request("pad-1", "pad-1")

        assertThat(asked).containsExactly("pad-1", "pad-1")
    }

    @Test
    fun `a device held and answered before the picture is not asked`() {
        requests.request("pad-1", "pad-1")
        requests.resolved("pad-1")

        requests.onReady()

        assertThat(asked).isEmpty()
    }

    @Test
    fun `reset starts over for a new stream`() {
        requests.request("pad-1", "pad-1")
        requests.onReady()
        asked.clear()

        requests.reset()
        requests.request("pad-1", "pad-1")
        assertThat(asked).isEmpty()

        requests.onReady()
        assertThat(asked).containsExactly("pad-1")
    }

    @Test
    fun `reset drops the devices that were held`() {
        requests.request("pad-1", "pad-1")

        requests.reset()
        requests.onReady()

        assertThat(asked).isEmpty()
    }
}
