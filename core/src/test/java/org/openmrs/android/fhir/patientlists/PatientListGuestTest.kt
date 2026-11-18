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

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.hl7.fhir.r4.model.Reference
import org.junit.Before
import org.junit.Test
import org.openmrs.android.fhir.data.database.model.PatientListChange
import org.openmrs.android.fhir.data.database.model.PatientListChangeKind

/** A patient list made without a session is sent, complete, once someone has signed in. */
class PatientListGuestTest {
  private lateinit var raw: FakePatientListDao
  private lateinit var server: FakeGroupServer
  private var current = ""
  private var signedIn = false
  private var time = 1_000L

  private fun repository(listType: String? = null) =
    PatientListsRepository(
      UserScopedPatientLists(raw) { current },
      server,
      NoTransaction,
      { time++ },
      { "new-$time" },
      { emptySet() },
      user = { current },
      signedIn = { signedIn },
      listType = { listType },
    )

  @Before
  fun setUp() {
    raw = FakePatientListDao()
    server = FakeGroupServer()
    current = ""
    signedIn = false
    time = 1_000L
  }

  @Test
  fun aGuestCreatesAndManagesAListAndNothingIsAskedOfTheServer() = runTest {
    val guest = repository()

    val saved = guest.create("Mothers", "every Monday", null, null) as PatientListEdit.Saved
    guest.addMembers(saved.id, listOf(patient("p1")))

    val shown = guest.items.first().single()
    assertThat(shown.name).isEqualTo("Mothers")
    assertThat(guest.members(saved.id).first().map { it.patientId }).containsExactly("p1")
    assertThat(guest.send()).isEqualTo(SendResult())
    assertThat(guest.refreshList()).isEqualTo(RefreshResult.DONE)
    assertThat(guest.refreshMembers(saved.id)).isEqualTo(RefreshResult.DONE)
    assertThat(server.requests).isEmpty()
  }

  @Test
  fun whatAGuestDidBecomesTheChangesOfTheUserWhoSignsIn() = runTest {
    raw.upsertPatientLists(listOf(row("existing", quantity = 0, membersLoaded = true)))
    val guest = repository()
    val saved = guest.create("Mothers", "every Monday", null, null) as PatientListEdit.Saved
    guest.addMembers(saved.id, listOf(patient("p1")))
    guest.addMembers("existing", listOf(patient("p2")))

    current = "alice"
    signedIn = true
    val alice = repository()
    assertThat(alice.pendingNow()).isEqualTo(0)
    alice.adoptGuestChanges()

    assertThat(alice.pendingNow()).isEqualTo(3)
    assertThat(raw.allChanges().map { it.owner }.distinct()).containsExactly("alice")
    assertThat(raw.allMemberChanges().map { it.owner }.distinct()).containsExactly("alice")
    assertThat(alice.send()).isEqualTo(SendResult(sent = 3))
    assertThat(server.requests)
      .containsExactly(
        "create",
        "read:${saved.id}",
        "add-members:${saved.id}:p1",
        "add-members:existing:p2",
      )
  }

  @Test
  fun aChangeOfTheUserIsNotReplacedByTheGuestsForTheSamePatient() = runTest {
    raw.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))
    repository().addMembers("a", listOf(patient("p1", "Guest entry")))
    current = "alice"
    signedIn = true
    val alice = repository()
    alice.addMembers("a", listOf(patient("p1", "Own entry")))

    alice.adoptGuestChanges()

    assertThat(raw.allMemberChanges().map { it.name }).containsExactly("Own entry")
  }

  @Test
  fun aListMadeBeforeALocationWasChosenIsSentWithIt() = runTest {
    val guest = repository(listType = "type-uuid")
    val saved = guest.create("Mothers", "every Monday", null, null) as PatientListEdit.Saved
    guest.create("Elsewhere", null, "kept-location", "Kept") as PatientListEdit.Saved
    current = "alice"
    signedIn = true
    val alice = repository(listType = "type-uuid")
    alice.adoptGuestChanges()

    alice.completeLocation("north", "North clinic")
    alice.send()

    val sent = server.groups.getValue(saved.id)
    assertThat(sent.name).isEqualTo("Mothers")
    assertThat(GroupMapper.parse(sent, 0L).row.description).isEqualTo("every Monday")
    assertThat(GroupMapper.parse(sent, 0L).row.locationId).isEqualTo("north")
    assertThat(raw.allChanges()).isEmpty()
    val kept = server.groups.values.single { it.name == "Elsewhere" }
    assertThat(GroupMapper.parse(kept, 0L).row.locationId).isEqualTo("kept-location")
  }

  private fun strictRepository() =
    PatientListsRepository(
      UserScopedPatientLists(raw) { current },
      server,
      NoTransaction,
      { time++ },
      { "new-$time" },
      { emptySet() },
      user = { current },
      signedIn = { signedIn },
      savedLocation = { location },
      needsLocation = true,
    )

  private var location: Pair<String, String?>? = null

  @Test
  fun aListWithoutALocationWaitsForOneAndTakesItWhenThereIsOne() = runTest {
    current = "alice"
    signedIn = true
    val repository = strictRepository()
    val saved = repository.create("Mothers", null, null, null) as PatientListEdit.Saved
    repository.addMembers(saved.id, listOf(patient("p1")))

    assertThat(repository.send()).isEqualTo(SendResult())
    assertThat(server.requests).isEmpty()
    assertThat(raw.allChanges().single().locationId).isNull()

    location = "north" to "North clinic"
    assertThat(repository.send().sent).isEqualTo(2)

    assertThat(GroupMapper.parse(server.groups.getValue(saved.id), 0L).row.locationId)
      .isEqualTo("north")
    assertThat(server.requests)
      .containsExactly("create", "read:${saved.id}", "add-members:${saved.id}:p1")
  }

  @Test
  fun aListCreatedWithALocationKeepsItEvenWhenAnotherIsChosenLater() = runTest {
    current = "alice"
    signedIn = true
    location = "north" to "North clinic"
    val repository = strictRepository()
    val saved = repository.create("Mothers", null, "north", "North clinic") as PatientListEdit.Saved

    location = "south" to "South clinic"
    repository.send()

    assertThat(GroupMapper.parse(server.groups.getValue(saved.id), 0L).row.locationId)
      .isEqualTo("north")
  }

  @Test
  fun aListIsCreatedWithEveryFieldTheAppKnows() {
    val change =
      PatientListChange(
        listId = "id-1",
        kind = PatientListChangeKind.CREATE,
        name = "Mothers",
        description = "every Monday",
        locationId = "north",
        locationName = "North clinic",
        createdAt = 1L,
        error = null,
      )

    val group = GroupMapper.toCreate(change, "type-uuid")

    assertThat(group.idElement.idPart).isEqualTo("id-1")
    assertThat(group.name).isEqualTo("Mothers")
    assertThat(group.active).isTrue()
    assertThat(group.actual).isTrue()
    assertThat(group.type.toCode()).isEqualTo("person")
    val parsed = GroupMapper.parse(group, 0L).row
    assertThat(parsed.description).isEqualTo("every Monday")
    assertThat(parsed.locationId).isEqualTo("north")
    assertThat(
        (group.getExtensionByUrl(GroupMapper.LIST_TYPE).value as Reference).reference,
      )
      .isEqualTo("CohortType/type-uuid")
    assertThat(GroupMapper.toCreate(change).getExtensionByUrl(GroupMapper.LIST_TYPE)).isNull()
  }
}
