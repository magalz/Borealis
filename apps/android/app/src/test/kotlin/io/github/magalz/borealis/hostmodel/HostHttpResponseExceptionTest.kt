package io.github.magalz.borealis.hostmodel

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.HostHttpResponseException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import org.junit.Test

/** Characterizes the host error of the host model as it was before the Kotlin conversion. */
class HostHttpResponseExceptionTest {
    @Test
    fun `the exception keeps the code and the message of the host`() {
        val error = HostHttpResponseException(404, "Not found")

        assertThat(error.errorCode).isEqualTo(404)
        assertThat(error.errorMessage).isEqualTo("Not found")
    }

    @Test
    fun `the message names the host error and its code`() {
        assertThat(HostHttpResponseException(401, "Server certificate mismatch").message)
            .isEqualTo("Host PC returned error: Server certificate mismatch (Error code: 401)")
        assertThat(HostHttpResponseException(599, "").message)
            .isEqualTo("Host PC returned error:  (Error code: 599)")
    }

    @Test
    fun `a missing host message shows as null in the message`() {
        val error = HostHttpResponseException(500, null)

        assertThat(error.errorMessage).isNull()
        assertThat(error.message).isEqualTo("Host PC returned error: null (Error code: 500)")
    }

    @Test
    fun `the localized message and the text of the exception are those of the message`() {
        val error = HostHttpResponseException(503, "busy")

        assertThat(error.localizedMessage).isEqualTo(error.message)
        assertThat(error.toString())
            .isEqualTo(
                "com.limelight.nvstream.http.HostHttpResponseException: " +
                    "Host PC returned error: busy (Error code: 503)"
            )
    }

    @Test
    fun `the exception is an IOException without a cause`() {
        val error: Any = HostHttpResponseException(400, "Out of order call")

        assertThat(error).isInstanceOf(IOException::class.java)
        assertThat((error as IOException).cause).isNull()
    }

    @Test
    fun `the serial version is the one of the inherited class`() {
        assertThat(ObjectStreamClass.lookup(HostHttpResponseException::class.java).serialVersionUID)
            .isEqualTo(1543508830807804222L)
    }

    @Test
    fun `the exception survives serialization`() {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(HostHttpResponseException(410, "reset")) }

        val read =
            ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use {
                it.readObject() as HostHttpResponseException
            }

        assertThat(read.errorCode).isEqualTo(410)
        assertThat(read.errorMessage).isEqualTo("reset")
        assertThat(read.message).isEqualTo("Host PC returned error: reset (Error code: 410)")
    }
}
