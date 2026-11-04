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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.hl7.fhir.r4.model.Resource
import retrofit2.HttpException
import timber.log.Timber

/**
 * The requests of a sync to the server. It is the form library's data source in a form Kotlin can
 * use (the library's own interface is `internal`): [RetryingFhirSyncWorker] connects the two.
 */
interface Requests {
  suspend fun download(request: DownloadRequest): Resource

  suspend fun upload(request: UploadRequest): Resource
}

/**
 * The requests of a sync, sent again when they are lost on the way (see [SyncRetries]). It sits
 * between the form library and the server, so a request that is lost is sent again before the
 * library hears of it: the library counts every failure it hears of against the sync, and a sync
 * with one failure is repeated from the start, however the request ended. Here the sync only fails
 * for a request that is lost [SyncRetries.MAX_RETRIES] times in a row.
 *
 * A download only reads, so any request lost on the way (or answered with a status that says "try
 * again") is sent again. An upload creates and changes records: it is only sent again for failures
 * that prove that the server never got it ([SyncRetries.neverArrived]), as a connection closed when
 * the app started to write to it ("Broken pipe"). After a reset in the middle of an exchange the
 * server may have done the work already, and sending it again could create it twice.
 */
class RetryingRequests
@JvmOverloads
constructor(
  private val delegate: Requests,
  private val pause: suspend (Long) -> Unit = { delay(it) },
) : Requests {
  private val retriesInThisSync = AtomicInteger()

  override suspend fun download(request: DownloadRequest): Resource =
    retrying("download", request, ::isWorthSendingAgain) { delegate.download(request) }

  override suspend fun upload(request: UploadRequest): Resource =
    retrying("upload", request, SyncRetries::neverArrived) { delegate.upload(request) }

  private fun isWorthSendingAgain(error: Throwable): Boolean =
    SyncRetries.isTransient(error) ||
      (error as? HttpException)?.code()?.let { SyncRetries.isRetryableStatus(it) } == true

  private suspend fun <T> retrying(
    what: String,
    request: Any,
    canSendAgain: (Throwable) -> Boolean,
    send: suspend () -> T,
  ): T {
    var retries = 0
    while (true) {
      try {
        return send()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        if (
          retries >= SyncRetries.MAX_RETRIES ||
            retriesInThisSync.get() >= SyncRetries.MAX_RETRIES_PER_SYNC ||
            !canSendAgain(error)
        ) {
          throw error
        }
        retries++
        retriesInThisSync.incrementAndGet()
        val pauseMillis = SyncRetries.pauseBeforeRetry(retries)
        Timber.w(
          "A request of the %s was lost on the way (%s): sending it again in %d s (%d of %d): %s",
          what,
          error.message,
          pauseMillis / 1000,
          retries,
          SyncRetries.MAX_RETRIES,
          request,
        )
        pause(pauseMillis)
      }
    }
  }
}
