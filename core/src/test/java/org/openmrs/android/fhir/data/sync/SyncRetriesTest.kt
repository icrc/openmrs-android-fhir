/*
* BSD 3-Clause License
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice, this
*    list of conditions and the following disclaimer.
*
* 2. Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*
* 3. Neither the name of the copyright holder nor the names of its
*    contributors may be used to endorse or promote products derived from
*    this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
* FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
* DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
* SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
* CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.openmrs.android.fhir.data.sync

import com.google.android.fhir.sync.download.DownloadRequest
import com.google.android.fhir.sync.upload.request.UploadRequest
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Resource
import org.junit.Test
import org.mockito.Mockito.mock
import retrofit2.HttpException
import retrofit2.Response

class SyncRetriesTest {
  @Test
  fun aBrokenPipeIsLostOnTheWayAndNeverArrived() {
    val error = RuntimeException("The upload failed", SocketException("Broken pipe"))

    assertTrue(SyncRetries.isTransient(error))
    assertTrue(SyncRetries.neverArrived(error))
  }

  @Test
  fun aResetOrATimeoutIsLostOnTheWayButMayHaveArrived() {
    listOf(
        SocketException("Connection reset"),
        IOException("unexpected end of stream on http://server"),
        SocketTimeoutException("timeout"),
      )
      .forEach { error ->
        assertTrue(SyncRetries.isTransient(error), error.toString())
        assertFalse(SyncRetries.neverArrived(error), error.toString())
      }
  }

  @Test
  fun aRefusalOfTheServerIsNotAnAccident() {
    assertFalse(SyncRetries.isTransient(IllegalStateException("Unknown response for Patient")))
    assertFalse(SyncRetries.isTransient(IOException("HTTP 401 Unauthorized")))
    assertFalse(SyncRetries.neverArrived(null))
  }

  @Test
  fun theStatusesThatSayTryAgain() {
    listOf(408, 429, 500, 502, 503, 504).forEach { assertTrue(SyncRetries.isRetryableStatus(it)) }
    listOf(200, 400, 401, 403, 404, 409, 422).forEach {
      assertFalse(SyncRetries.isRetryableStatus(it))
    }
  }

  @Test
  fun thePausesGrowAndThenStay() {
    assertEquals(
      listOf(2_000L, 5_000L, 10_000L, 10_000L),
      (1..4).map { SyncRetries.pauseBeforeRetry(it) },
    )
  }
}

class RetryingRequestsTest {
  private val pauses = mutableListOf<Long>()
  private val answer: Resource = Patient()
  private val download = DownloadRequest.of("Patient?_count=100")
  private val upload = mock(UploadRequest::class.java)

  /** A server that fails with [failures] in turn, then answers. */
  private class Flaky(private val failures: List<Exception>, private val answer: Resource) :
    Requests {
    var calls = 0

    override suspend fun download(request: DownloadRequest): Resource = next()

    override suspend fun upload(request: UploadRequest): Resource = next()

    private fun next(): Resource {
      val failure = failures.getOrNull(calls++)
      if (failure != null) throw failure
      return answer
    }
  }

  private fun source(vararg failures: Exception) = Flaky(failures.toList(), answer)

  private fun retrying(source: Requests) = RetryingRequests(source) { pauses.add(it) }

  @Test
  fun aDownloadLostOnTheWayIsSentAgainUntilItIsAnswered() = runBlocking {
    val server =
      source(SocketException("Connection reset"), IOException("unexpected end of stream"))

    val result = retrying(server).download(download)

    assertSame(answer, result)
    assertEquals(3, server.calls)
    assertEquals(listOf(2_000L, 5_000L), pauses)
  }

  @Test
  fun aDownloadAnsweredWithAGatewayErrorIsSentAgain() = runBlocking {
    val busy = HttpException(Response.error<Any>(503, "".toResponseBody(null)))
    val server = source(busy)

    assertSame(answer, retrying(server).download(download))
    assertEquals(2, server.calls)
  }

  @Test
  fun aDownloadTheServerRefusesIsNotSentAgain() = runBlocking {
    val refused = HttpException(Response.error<Any>(404, "".toResponseBody(null)))
    val server = source(refused)

    val error = assertFailsWith<HttpException> { retrying(server).download(download) }

    assertSame(refused, error)
    assertEquals(1, server.calls)
    assertTrue(pauses.isEmpty())
  }

  @Test
  fun aDownloadThatNeverWorksFailsAfterThreeRetries() = runBlocking {
    val reset = SocketException("Connection reset")
    val server = source(reset, reset, reset, reset, reset)

    assertFailsWith<SocketException> { retrying(server).download(download) }

    assertEquals(1 + SyncRetries.MAX_RETRIES, server.calls)
    assertEquals(listOf(2_000L, 5_000L, 10_000L), pauses)
  }

  @Test
  fun anUploadThatNeverReachedTheServerIsSentAgain() = runBlocking {
    val server = source(SocketException("Broken pipe"))

    assertSame(answer, retrying(server).upload(upload))
    assertEquals(2, server.calls)
    assertEquals(listOf(2_000L), pauses)
  }

  @Test
  fun anUploadThatMayHaveReachedTheServerIsNeverSentTwice() = runBlocking {
    val reset = SocketException("Connection reset")
    val server = source(reset)

    assertFailsWith<SocketException> { retrying(server).upload(upload) }

    assertEquals(1, server.calls)
    assertTrue(pauses.isEmpty())
  }

  @Test
  fun aStoppedSyncIsNotRetried() = runBlocking {
    val server = source(CancellationException("The sync was cancelled"))

    assertFailsWith<CancellationException> { retrying(server).download(download) }

    assertEquals(1, server.calls)
  }

  @Test
  fun oneSyncDoesNotRetryWithoutEnd() = runBlocking {
    val reset = SocketException("Connection reset")
    val retrying = retrying(source(*Array(1_000) { reset }))

    // The server is down: every request fails, and only so many retries are made in all.
    repeat(30) { runCatching { retrying.download(download) } }

    assertEquals(SyncRetries.MAX_RETRIES_PER_SYNC, pauses.size)
  }
}
