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
package org.openmrs.android.fhir.patientlists

import org.hl7.fhir.r4.model.Group

class ServerGroups(val groups: List<Group>, val complete: Boolean)

sealed interface ServerGroupRead {
  class Found(val group: Group) : ServerGroupRead

  data object Gone : ServerGroupRead
}

sealed interface ServerGroupWrite {
  class Done(val group: Group?) : ServerGroupWrite

  /**
   * The server understood and said no (400, 401, 403, 404, 409, 410, 422): sending again will not
   * help.
   */
  class Refused(val code: Int, val reason: String) : ServerGroupWrite {
    val gone: Boolean
      get() = code == 404 || code == 410
  }

  /**
   * Not answered, or answered with "try later" (5xx, 408, 429): the change stays and goes later.
   */
  class Unreachable(val reason: String) : ServerGroupWrite
}

/**
 * What the app asks of the server for patient lists. Reads throw `IOException` when the server
 * cannot be reached or answers with an error; writes answer with a [ServerGroupWrite]. `ApiManager`
 * is the implementation; the tests use a fake.
 */
interface GroupServer {
  suspend fun groups(): ServerGroups

  suspend fun group(id: String): ServerGroupRead

  suspend fun patients(ids: List<String>): List<PatientSummary>

  suspend fun create(group: Group): ServerGroupWrite

  suspend fun update(group: Group): ServerGroupWrite

  suspend fun delete(id: String): ServerGroupWrite

  suspend fun addMembers(id: String, patientIds: List<String>): ServerGroupWrite

  suspend fun removeMembers(id: String, patientIds: List<String>): ServerGroupWrite
}
