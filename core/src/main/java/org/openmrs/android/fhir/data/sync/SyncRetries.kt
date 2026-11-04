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

import java.util.Locale

/**
 * What the sync does when a request to the server is lost on the way: a connection the server
 * closed while the app was writing to it ("Broken pipe"), one that was reset, a response that was
 * cut short, a timeout, or a gateway that gave up (502, 503, 504). These are not answers of the
 * server about the data but accidents of the network, and sending the same request again usually
 * works; without that, the part of the sync that was lost waits for the next sync (a page of a
 * download is skipped, an upload stops).
 */
object SyncRetries {
  /** The pause before each time a request is sent again (so also how many times it is). */
  private val PAUSES_MILLIS = longArrayOf(2_000, 5_000, 10_000)

  /** How many times one request is sent again after it failed. */
  val MAX_RETRIES = PAUSES_MILLIS.size

  /**
   * The most requests one sync sends again in all: a server that is really down must not keep the
   * sync waiting for every request it has.
   */
  const val MAX_RETRIES_PER_SYNC = 20

  fun pauseBeforeRetry(retry: Int): Long =
    PAUSES_MILLIS[(retry - 1).coerceIn(0, PAUSES_MILLIS.lastIndex)]

  private val LOST_ON_THE_WAY =
    listOf(
      "broken pipe",
      "connection reset",
      "connection abort",
      "connection closed",
      "unexpected end of stream",
      "stream was reset",
      "stream reset",
      "timeout",
      "timed out",
      "failed to connect",
      "connection refused",
    )

  /** The failures that prove the request never reached the server, so sending it again is safe. */
  private val NEVER_ARRIVED =
    listOf(
      "broken pipe",
      "failed to connect",
      "connection refused",
      "unable to resolve host",
      "no route to host",
    )

  /** The statuses of an answer that say "try again", not "this is wrong". */
  fun isRetryableStatus(code: Int): Boolean =
    code == 408 || code == 429 || code == 500 || code == 502 || code == 503 || code == 504

  /** True when [text] (an exception as text, a log line) describes a request lost on the way. */
  fun isTransient(text: String): Boolean {
    val lower = text.lowercase(Locale.ROOT)
    return LOST_ON_THE_WAY.any { it in lower }
  }

  /** True when [error], or what caused it, is a request lost on the way. */
  fun isTransient(error: Throwable?): Boolean = causes(error).any { isTransient(describe(it)) }

  /**
   * True when [error] is one that can only happen before the server got the request, as a
   * connection that was closed when the app started to write to it. The other failures may leave
   * the server having done the work (a reset after the request was sent), so a request that creates
   * something is not sent again for them: it could be created twice.
   */
  fun neverArrived(error: Throwable?): Boolean =
    causes(error).any { cause ->
      val text = describe(cause).lowercase(Locale.ROOT)
      NEVER_ARRIVED.any { it in text }
    }

  private fun causes(error: Throwable?): Sequence<Throwable> =
    generateSequence(error) { it.cause?.takeIf { cause -> cause !== it } }.take(8)

  private fun describe(error: Throwable) = "${error.javaClass.name}: ${error.message.orEmpty()}"
}
