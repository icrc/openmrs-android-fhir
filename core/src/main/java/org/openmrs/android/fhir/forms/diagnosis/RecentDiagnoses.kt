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
package org.openmrs.android.fhir.forms.diagnosis

import android.content.Context
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The diagnoses used last on this device, offered at the top of the search so that the few a clinic
 * uses all the time are one tap away. It is a short list of concept uuids, one for each line of a
 * small file in the files directory of the app: it goes with the rest of the data when the data is
 * deleted at logout (and stays when the data stays), and holds nothing about any patient.
 */
object RecentDiagnoses {
  /** How many diagnoses are remembered. */
  const val MAX = 12

  private const val FILE_NAME = "diagnosis-recent.txt"

  /**
   * The list after [newest] was used: [newest] first (in that order), then what was there, each
   * uuid once (whatever its case), at most [max].
   */
  fun merged(existing: List<String>, newest: List<String>, max: Int = MAX): List<String> {
    val seen = HashSet<String>()
    return (newest + existing)
      .map { it.trim() }
      .filter { it.isNotEmpty() && seen.add(it.lowercase(Locale.ROOT)) }
      .take(max)
  }

  /** The remembered uuids, the most recent first. A file that cannot be read is an empty list. */
  fun get(context: Context): List<String> =
    try {
      file(context).takeIf { it.isFile }?.readLines().orEmpty().filter { it.isNotBlank() }.take(MAX)
    } catch (e: Exception) {
      Timber.w(e, "The recent diagnoses cannot be read")
      emptyList()
    }

  /** Remembers that [uuids] (first the one that matters most) were just used. */
  suspend fun record(context: Context, uuids: List<String>) {
    if (uuids.isEmpty()) return
    withContext(Dispatchers.IO) {
      try {
        val updated = merged(get(context), uuids)
        file(context).writeText(updated.joinToString("\n"))
      } catch (e: Exception) {
        // A convenience: whatever goes wrong here must not fail the save it follows.
        Timber.w(e, "The recent diagnoses cannot be saved")
      }
    }
  }

  private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)
}
