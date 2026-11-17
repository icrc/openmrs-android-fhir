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

import org.openmrs.android.fhir.data.database.model.PatientListChange
import org.openmrs.android.fhir.data.database.model.PatientListChangeKind
import org.openmrs.android.fhir.data.database.model.PatientListMemberAction
import org.openmrs.android.fhir.data.database.model.PatientListMemberChange
import org.openmrs.android.fhir.data.database.model.PatientListMemberRow
import org.openmrs.android.fhir.data.database.model.PatientListRow

enum class PatientListState {
  SYNCED,
  NEW,
  EDITED,
  DELETING,
}

data class PatientListEntry(
  val id: String,
  val name: String,
  val description: String?,
  val typeName: String?,
  val locationId: String?,
  val locationName: String?,
  val creatorName: String?,
  val dateCreated: Long?,
  val changedByName: String?,
  val dateChanged: Long?,
  val quantity: Int,
  val attributes: List<Pair<String, String>>,
  val state: PatientListState,
  val pendingChanges: Int,
  val error: String?,
  val membersLoaded: Boolean,
)

enum class MemberPending {
  ADD,
  REMOVE,
}

data class PatientListMember(
  val patientId: String,
  val name: String,
  val gender: String?,
  val birthDate: String?,
  val identifier: String?,
  val pending: MemberPending?,
  val error: String?,
  /**
   * Added by the user for a patient to identify: it waits for the identification, not for the
   * server.
   */
  val toIdentify: Boolean = false,
  val waitingForUpload: Boolean = false,
)

enum class Membership {
  IN,
  OUT,
  UNKNOWN,
}

data class PatientMembership(
  val list: PatientListEntry,
  val membership: Membership,
  val pending: MemberPending?,
)

data class PatientSummary(
  val id: String,
  val name: String,
  val gender: String?,
  val birthDate: String?,
  val identifier: String?,
)

sealed interface PatientListEdit {
  data class Saved(val id: String) : PatientListEdit

  data object NameMissing : PatientListEdit

  data object NameTaken : PatientListEdit

  data object NotFound : PatientListEdit
}

data class SendResult(
  val sent: Int = 0,
  val refused: Int = 0,
  /**
   * Changes to a patient list that the server no longer has: they cannot be sent and were removed.
   */
  val dropped: Int = 0,
  val unreachable: Boolean = false,
)

enum class RefreshResult {
  DONE,
  UNREACHABLE,
  GONE,
}

/**
 * The patient lists as the screens show them: the rows the server last sent ([PatientListRow]) with
 * the user's unsent changes on top. Nothing is written back to the rows, so a change the server
 * refuses and the user discards simply stops showing.
 */
object PatientListOverlay {
  /**
   * [held] are the patients to identify: an add for one of them waits for the identification, so it
   * is not "not sent" (the server is never asked for it).
   */
  fun items(
    rows: List<PatientListRow>,
    changes: List<PatientListChange>,
    memberChanges: List<PatientListMemberChange>,
    held: Set<String> = emptySet(),
  ): List<PatientListEntry> {
    val changeOf = changes.associateBy { it.listId }
    val memberChangesOf = memberChanges.groupBy { it.listId }
    val shown =
      rows.map { row -> item(row, changeOf[row.id], memberChangesOf[row.id].orEmpty(), held) }
    val known = rows.mapTo(HashSet()) { it.id }
    val created =
      changes
        .filter { it.kind == PatientListChangeKind.CREATE && it.listId !in known }
        .map { change -> newItem(change, memberChangesOf[change.listId].orEmpty(), held) }
    return (shown + created).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
  }

  fun item(
    row: PatientListRow,
    change: PatientListChange?,
    memberChanges: List<PatientListMemberChange>,
    held: Set<String> = emptySet(),
  ): PatientListEntry {
    val edit = change?.takeIf { it.kind == PatientListChangeKind.UPDATE }
    return PatientListEntry(
      id = row.id,
      name = edit?.name ?: row.name,
      description = if (edit != null) edit.description else row.description,
      typeName = row.typeName,
      locationId = row.locationId,
      locationName = row.locationName,
      creatorName = row.creatorName,
      dateCreated = row.dateCreated,
      changedByName = row.changedByName,
      dateChanged = row.dateChanged,
      quantity = quantity(row.quantity, memberChanges),
      attributes = attributes(row.attributesJson),
      state =
        when (change?.kind) {
          PatientListChangeKind.DELETE -> PatientListState.DELETING
          PatientListChangeKind.UPDATE -> PatientListState.EDITED
          else -> PatientListState.SYNCED
        },
      pendingChanges = (if (change != null) 1 else 0) + memberChanges.count { !it.isHeld(held) },
      error = change?.error ?: memberChanges.firstRefusal(),
      membersLoaded = row.membersLoaded,
    )
  }

  private fun newItem(
    change: PatientListChange,
    memberChanges: List<PatientListMemberChange>,
    held: Set<String>,
  ) =
    PatientListEntry(
      id = change.listId,
      name = change.name.orEmpty(),
      description = change.description,
      typeName = null,
      locationId = change.locationId,
      locationName = change.locationName,
      creatorName = null,
      dateCreated = change.createdAt,
      changedByName = null,
      dateChanged = null,
      quantity = memberChanges.count { it.action == PatientListMemberAction.ADD },
      attributes = emptyList(),
      state = PatientListState.NEW,
      pendingChanges = 1 + memberChanges.count { !it.isHeld(held) },
      error = change.error ?: memberChanges.firstRefusal(),
      membersLoaded = true,
    )

  /**
   * The server's reason for the first add or remove it refused (not an add that could not be
   * placed).
   */
  private fun List<PatientListMemberChange>.firstRefusal(): String? = firstNotNullOfOrNull {
    it.error?.takeUnless { e -> e == PatientListsRepository.AMBIGUOUS_UPLOAD }
  }

  /**
   * The server's count with the unsent adds and removes: those not yet sent are assumed to count.
   */
  private fun quantity(server: Int, memberChanges: List<PatientListMemberChange>): Int =
    (server + memberChanges.count { it.action == PatientListMemberAction.ADD } -
        memberChanges.count { it.action == PatientListMemberAction.REMOVE })
      .coerceAtLeast(0)

  fun members(
    rows: List<PatientListMemberRow>,
    memberChanges: List<PatientListMemberChange>,
    held: Set<String> = emptySet(),
  ): List<PatientListMember> {
    val changeOf = memberChanges.associateBy { it.patientId }
    val onServer = rows.mapTo(HashSet()) { it.patientId }
    val shown =
      rows.map { row ->
        val change = changeOf[row.patientId]
        PatientListMember(
          patientId = row.patientId,
          name = row.name,
          gender = row.gender,
          birthDate = row.birthDate,
          identifier = row.identifier,
          pending =
            if (change?.action == PatientListMemberAction.REMOVE) MemberPending.REMOVE else null,
          error = change?.error,
        )
      }
    val added =
      memberChanges
        .filter { it.action == PatientListMemberAction.ADD && it.patientId !in onServer }
        .map { change ->
          PatientListMember(
            patientId = change.patientId,
            name = change.name,
            gender = change.gender,
            birthDate = change.birthDate,
            identifier = change.identifier,
            pending = MemberPending.ADD,
            error = change.error,
            toIdentify = change.patientId in held,
            waitingForUpload = change.heldForUpload,
          )
        }
    return (shown + added).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
  }

  /**
   * An add that is not sent yet: for a patient to identify (until they are identified) or for a
   * patient registered here (until the upload gives them their id).
   */
  fun PatientListMemberChange.isHeld(held: Set<String>) =
    action == PatientListMemberAction.ADD && (patientId in held || heldForUpload)

  fun attributes(json: String?): List<Pair<String, String>> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
      val array = org.json.JSONArray(json)
      (0 until array.length()).mapNotNull { index ->
        val pair = array.optJSONArray(index) ?: return@mapNotNull null
        val type = pair.optString(0)
        val value = pair.optString(1)
        if (type.isBlank() || value.isBlank()) null else type to value
      }
    } catch (e: org.json.JSONException) {
      emptyList()
    }
  }
}
