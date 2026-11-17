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

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.openmrs.android.fhir.data.database.PatientListsDao
import org.openmrs.android.fhir.data.database.model.PatientListChange
import org.openmrs.android.fhir.data.database.model.PatientListMemberChange
import org.openmrs.android.fhir.multiuser.UserRegistry

fun interface ActiveUser {
  fun name(): String

  companion object {
    fun of(context: Context): ActiveUser {
      val appContext = context.applicationContext
      return ActiveUser { UserRegistry.active(appContext).orEmpty() }
    }
  }
}

/**
 * The patient list tables as one user sees them. Several people can use one device and the changes
 * to patient lists wait in tables of the device, so each change carries its [ActiveUser] and this
 * view reads, writes and removes only the changes of the signed-in user: another user's changes are
 * neither shown nor sent under this user's session, and the same patient can wait in two users'
 * changes without one replacing the other. What the server said, the history and the notes of
 * uploads are the device's, shared.
 *
 * The repository's steps that follow a patient (an upload gave it its id, a patient to identify was
 * identified) must find the changes of every user: they use [allMemberChanges] and name the owner.
 */
class UserScopedPatientLists(private val raw: PatientListsDao, private val user: ActiveUser) :
  PatientListsDao by raw {

  private fun owner() = user.name()

  override suspend fun changes(): List<PatientListChange> =
    raw.allChanges().filter { it.owner == owner() }

  override fun observeChanges(): Flow<List<PatientListChange>> =
    raw.observeChanges().map { list -> list.filter { it.owner == owner() } }

  override suspend fun change(listId: String): PatientListChange? =
    raw.allChanges().firstOrNull { it.listId == listId && it.owner == owner() }

  override suspend fun putChange(change: PatientListChange) {
    raw.putChange(if (change.owner.isEmpty()) change.copy(owner = owner()) else change)
  }

  override suspend fun removeChange(listId: String) = raw.removeChangeFor(listId, owner())

  override suspend fun clearChanges() = raw.clearChangesFor(owner())

  override suspend fun memberChanges(): List<PatientListMemberChange> =
    raw.allMemberChanges().filter { it.owner == owner() }

  override fun observeMemberChanges(): Flow<List<PatientListMemberChange>> =
    raw.observeMemberChanges().map { list -> list.filter { it.owner == owner() } }

  override suspend fun memberChange(listId: String, patientId: String): PatientListMemberChange? =
    raw.allMemberChanges().firstOrNull {
      it.listId == listId && it.patientId == patientId && it.owner == owner()
    }

  override suspend fun putMemberChanges(changes: List<PatientListMemberChange>) {
    raw.putMemberChanges(
      changes.map { if (it.owner.isEmpty()) it.copy(owner = owner()) else it },
    )
  }

  override suspend fun removeMemberChanges(listId: String, patientIds: List<String>) =
    raw.removeMemberChangesFor(listId, patientIds, owner())

  override suspend fun removeMemberChangesOf(listId: String) =
    raw.removeMemberChangesOfListFor(listId, owner())

  override suspend fun clearMemberChanges() = raw.clearMemberChangesFor(owner())

  override suspend fun pendingCount(): Int = changes().size + memberChanges().size

  override fun observePendingCount(): Flow<Int> =
    combine(observeChanges(), observeMemberChanges()) { lists, members ->
      lists.size + members.size
    }
}
