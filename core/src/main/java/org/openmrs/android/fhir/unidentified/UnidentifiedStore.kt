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
package org.openmrs.android.fhir.unidentified

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.database.model.UnidentifiedPatient

/**
 * Which patients are "to identify after", read once and kept in memory: the engine asks on every
 * save, and a device that never uses the feature pays one empty-set check.
 */
@Singleton
class UnidentifiedStore @Inject constructor(private val database: Lazy<AppDatabase>) {
  private val dao
    get() = database.get().unidentifiedDao()

  @Volatile private var cache: Map<String, UnidentifiedPatient>? = null

  private suspend fun entries(): Map<String, UnidentifiedPatient> =
    cache ?: dao.all().associateBy { it.patientId }.also { cache = it }

  suspend fun ids(): Set<String> = entries().keys

  suspend fun all(): List<UnidentifiedPatient> =
    entries().values.sortedByDescending { it.createdAt }

  suspend fun get(patientId: String): UnidentifiedPatient? = entries()[patientId]

  suspend fun put(entry: UnidentifiedPatient) {
    dao.upsert(entry)
    cache = null
  }

  suspend fun remove(patientId: String) {
    dao.remove(patientId)
    cache = null
  }

  /** The database was emptied (log out and delete the data): read it again. */
  fun invalidate() {
    cache = null
  }
}
