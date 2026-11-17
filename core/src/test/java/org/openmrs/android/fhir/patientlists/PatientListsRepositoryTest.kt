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
import org.junit.Before
import org.junit.Test
import org.openmrs.android.fhir.data.database.model.PatientListChangeKind

class PatientListsRepositoryTest {
  private lateinit var dao: FakePatientListDao
  private lateinit var server: FakeGroupServer
  private lateinit var repository: PatientListsRepository
  private var ids = 0
  private var time = 1_000L
  private var toIdentify = emptySet<String>()

  @Before
  fun setUp() {
    dao = FakePatientListDao()
    server = FakeGroupServer()
    ids = 0
    time = 1_000L
    repository =
      PatientListsRepository(
        dao,
        server,
        NoTransaction,
        { time++ },
        { "new-${++ids}" },
        { toIdentify },
      )
  }

  private suspend fun items() = repository.items.first()

  @Test
  fun aNewPatientListShowsAtOnceAsWaitingToBeSent() = runTest {
    val result = repository.create("  Mothers  ", " every Monday ", "loc-1", "North clinic")

    assertThat(result).isEqualTo(PatientListEdit.Saved("new-1"))
    val item = items().single()
    assertThat(item.name).isEqualTo("Mothers")
    assertThat(item.description).isEqualTo("every Monday")
    assertThat(item.state).isEqualTo(PatientListState.NEW)
    assertThat(item.pendingChanges).isEqualTo(1)
    assertThat(server.requests).isEmpty()
  }

  @Test
  fun aNameIsNeededAndMustBeNew() = runTest {
    dao.upsertPatientLists(listOf(row("a", "Mothers")))

    assertThat(repository.create("   ", null, null, null)).isEqualTo(PatientListEdit.NameMissing)
    assertThat(repository.create("mothers", null, null, null)).isEqualTo(PatientListEdit.NameTaken)
    assertThat(repository.create("Fathers", null, null, null))
      .isEqualTo(PatientListEdit.Saved("new-1"))
    assertThat(repository.create("FATHERS", null, null, null)).isEqualTo(PatientListEdit.NameTaken)
  }

  @Test
  fun renamingASyncedPatientListIsOneChangeThatTheLastEditWins() = runTest {
    dao.upsertPatientLists(listOf(row("a", "Mothers", quantity = 3)))

    repository.edit("a", "Mothers and babies", "new")
    repository.edit("a", "Mothers and infants", "newer")

    val item = items().single()
    assertThat(item.name).isEqualTo("Mothers and infants")
    assertThat(item.description).isEqualTo("newer")
    assertThat(item.state).isEqualTo(PatientListState.EDITED)
    assertThat(dao.changes()).hasSize(1)
  }

  @Test
  fun editingBackToWhatTheServerHasLeavesNothingToSend() = runTest {
    dao.upsertPatientLists(listOf(row("a", "Mothers")))
    repository.edit("a", "Other", null)

    repository.edit("a", "Mothers", null)

    assertThat(dao.changes()).isEmpty()
    assertThat(items().single().state).isEqualTo(PatientListState.SYNCED)
  }

  @Test
  fun editingAPatientListNotSentYetChangesTheCreate() = runTest {
    repository.create("Mothers", null, "loc-1", "North")

    repository.edit("new-1", "Mothers 2", "text")

    val change = dao.changes().single()
    assertThat(change.kind).isEqualTo(PatientListChangeKind.CREATE)
    assertThat(change.name).isEqualTo("Mothers 2")
    assertThat(change.locationId).isEqualTo("loc-1")
  }

  @Test
  fun deletingAPatientListNeverSentLeavesNoTrace() = runTest {
    repository.create("Mothers", null, null, null)
    repository.addMembers("new-1", listOf(patient("p1")))

    repository.delete("new-1")

    assertThat(dao.pendingCount()).isEqualTo(0)
    assertThat(items()).isEmpty()
  }

  @Test
  fun deletingASyncedPatientListCanBeUndoneUntilItIsSent() = runTest {
    dao.upsertPatientLists(listOf(row("a", "Mothers")))

    repository.delete("a")
    assertThat(items().single().state).isEqualTo(PatientListState.DELETING)

    repository.undoDelete("a")
    assertThat(items().single().state).isEqualTo(PatientListState.SYNCED)
    assertThat(dao.pendingCount()).isEqualTo(0)
  }

  @Test
  fun addingAPatientShowsInTheListAndTheCount() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 1, membersLoaded = true)))
    dao.upsertMembers(listOf(memberRow("a", "p1")))

    repository.addMembers("a", listOf(patient("p1"), patient("p2", "Zed"), patient("p3", "Amy")))

    val members = repository.members("a").first()
    assertThat(members.map { it.patientId to it.pending })
      .containsExactly("p3" to MemberPending.ADD, "p1" to null, "p2" to MemberPending.ADD)
      .inOrder()
    assertThat(items().single().quantity).isEqualTo(3)
    assertThat(dao.memberChanges()).hasSize(2)
  }

  @Test
  fun addingAndRemovingTheSamePatientBeforeSendingCancelsOut() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 1, membersLoaded = true)))
    dao.upsertMembers(listOf(memberRow("a", "p1")))

    repository.addMembers("a", listOf(patient("p2")))
    repository.removeMember("a", "p2")
    repository.removeMember("a", "p1")
    repository.addMembers("a", listOf(patient("p1")))

    assertThat(dao.memberChanges()).isEmpty()
  }

  @Test
  fun removingAMemberKeepsItInPlaceMarkedUntilItIsSent() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 1, membersLoaded = true)))
    dao.upsertMembers(listOf(memberRow("a", "p1")))

    repository.removeMember("a", "p1")

    val member = repository.members("a").first().single()
    assertThat(member.pending).isEqualTo(MemberPending.REMOVE)
    assertThat(items().single().quantity).isEqualTo(0)

    repository.undoMemberChange("a", "p1")
    assertThat(repository.members("a").first().single().pending).isNull()
  }

  @Test
  fun aCreateIsSentWithItsIdAndThenTheMembersAreAdded() = runTest {
    repository.create("Mothers", "text", "loc-1", "North")
    repository.addMembers("new-1", listOf(patient("p1"), patient("p2")))

    val result = repository.send()

    assertThat(result).isEqualTo(SendResult(sent = 3))
    assertThat(server.requests).containsExactly("create", "add-members:new-1:p1,p2").inOrder()
    assertThat(server.groups["new-1"]?.name).isEqualTo("Mothers")
    assertThat(dao.pendingCount()).isEqualTo(0)
    assertThat(dao.patientList("new-1")?.name).isEqualTo("Mothers")
    assertThat(dao.members("new-1").map { it.patientId }).containsExactly("p1", "p2")
    assertThat(items().single().quantity).isEqualTo(2)
    assertThat(items().single().state).isEqualTo(PatientListState.SYNCED)
  }

  @Test
  fun aServerThatNumbersThePatientListItselfDoesNotLoseTheMembersQueuedForIt() = runTest {
    repository.create("Mothers", null, null, null)
    repository.addMembers("new-1", listOf(patient("p1")))
    server.answer("create", ServerGroupWrite.Done(group("server-7", "Mothers")))

    val result = repository.send()

    assertThat(result).isEqualTo(SendResult(sent = 2))
    assertThat(server.requests).containsExactly("create", "add-members:server-7:p1").inOrder()
    assertThat(dao.patientList("server-7")).isNotNull()
    assertThat(dao.members("server-7").map { it.patientId }).containsExactly("p1")
    assertThat(dao.pendingCount()).isEqualTo(0)
  }

  @Test
  fun aCreateThatTheServerHasAlreadyIsDone() = runTest {
    repository.create("Mothers", null, null, null)
    server.answer("create", ServerGroupWrite.Refused(409, "exists"))

    val result = repository.send()

    assertThat(result).isEqualTo(SendResult(sent = 1))
    assertThat(dao.pendingCount()).isEqualTo(0)
    assertThat(dao.patientList("new-1")).isNotNull()
  }

  @Test
  fun aRefusedCreateKeepsItsReasonHoldsBackItsMembersAndIsNotSentAgainByItself() = runTest {
    repository.create("Mothers", null, null, null)
    repository.addMembers("new-1", listOf(patient("p1")))
    server.answer(
      "create",
      ServerGroupWrite.Refused(422, "A patient list with this name exists already"),
    )

    val first = repository.send()
    val second = repository.send()

    assertThat(first).isEqualTo(SendResult(refused = 1))
    assertThat(second).isEqualTo(SendResult())
    assertThat(server.requests).containsExactly("create")
    assertThat(items().single().error).isEqualTo("A patient list with this name exists already")
    assertThat(dao.pendingCount()).isEqualTo(2)

    server.answers.clear()
    val retry = repository.send(retryRefused = true)
    assertThat(retry).isEqualTo(SendResult(sent = 2))
    assertThat(dao.pendingCount()).isEqualTo(0)
  }

  @Test
  fun anUnreachableServerStopsTheSendAndLosesNothing() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))
    repository.create("Mothers", null, null, null)
    repository.addMembers("a", listOf(patient("p1")))
    server.unreachable = true

    val result = repository.send()

    assertThat(result.unreachable).isTrue()
    assertThat(result.sent).isEqualTo(0)
    assertThat(dao.pendingCount()).isEqualTo(2)
    assertThat(items().first { it.id == "new-1" }.error).isNull()
  }

  @Test
  fun aBusyServerIsNotARefusal() = runTest {
    repository.create("Mothers", null, null, null)
    server.answer("create", ServerGroupWrite.Unreachable("Server answered 503"))

    val result = repository.send()

    assertThat(result.unreachable).isTrue()
    assertThat(dao.change("new-1")?.error).isNull()
  }

  @Test
  fun anEditIsSentAsAnUpdateAndTheRowFollows() = runTest {
    dao.upsertPatientLists(listOf(row("a", "Mothers", quantity = 4, membersLoaded = true)))
    repository.edit("a", "Mothers and babies", "x")

    val result = repository.send()

    assertThat(result).isEqualTo(SendResult(sent = 1))
    assertThat(server.requests).containsExactly("update:a")
    val saved = dao.patientList("a")!!
    assertThat(saved.name).isEqualTo("Mothers and babies")
    assertThat(saved.quantity).isEqualTo(4)
    assertThat(saved.membersLoaded).isTrue()
  }

  @Test
  fun aDeleteIsSentAndThePatientListGoesWithItsMembers() = runTest {
    dao.upsertPatientLists(listOf(row("a")))
    dao.upsertMembers(listOf(memberRow("a", "p1")))
    repository.delete("a")

    val result = repository.send()

    assertThat(result).isEqualTo(SendResult(sent = 1))
    assertThat(dao.patientList("a")).isNull()
    assertThat(dao.members("a")).isEmpty()
  }

  @Test
  fun aDeleteOfAPatientListTheServerLostIsDone() = runTest {
    dao.upsertPatientLists(listOf(row("a")))
    repository.delete("a")
    server.answer("delete:a", ServerGroupWrite.Refused(404, "gone"))

    assertThat(repository.send()).isEqualTo(SendResult(sent = 1))
    assertThat(dao.patientList("a")).isNull()
  }

  @Test
  fun changesToAPatientListTheServerLostAreDroppedAndCounted() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 1, membersLoaded = true)))
    dao.upsertMembers(listOf(memberRow("a", "p1")))
    repository.edit("a", "Renamed", null)
    repository.addMembers("a", listOf(patient("p2"), patient("p3")))
    server.answer("update:a", ServerGroupWrite.Refused(404, "gone"))

    val result = repository.send()

    assertThat(result.dropped).isEqualTo(3)
    assertThat(dao.pendingCount()).isEqualTo(0)
    assertThat(dao.patientList("a")).isNull()
  }

  @Test
  fun onePatientTheServerRefusesDoesNotHoldBackTheOthers() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))
    repository.addMembers("a", listOf(patient("p1"), patient("p2"), patient("p3")))
    server.answer("add-members:a:p1,p2,p3", ServerGroupWrite.Refused(422, "Unknown patient p2"))
    server.answer("add-members:a:p2", ServerGroupWrite.Refused(422, "Unknown patient p2"))

    val result = repository.send()

    assertThat(result).isEqualTo(SendResult(sent = 2, refused = 1))
    assertThat(dao.members("a").map { it.patientId }).containsExactly("p1", "p3")
    val left = dao.memberChanges().single()
    assertThat(left.patientId).isEqualTo("p2")
    assertThat(left.error).isEqualTo("Unknown patient p2")
  }

  @Test
  fun addsAreSentBeforeRemovesAndInBatches() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 1, membersLoaded = true)))
    dao.upsertMembers(listOf(memberRow("a", "old")))
    repository.removeMember("a", "old")
    repository.addMembers("a", (1..105).map { patient("p$it") })

    repository.send()

    assertThat(server.requests.map { it.substringBefore(":a:") })
      .containsExactly("add-members", "add-members", "remove-members")
      .inOrder()
    assertThat(dao.members("a")).hasSize(105)
  }

  @Test
  fun aSyncedPatientListAnswersWithItsNewCount() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 1, membersLoaded = true)))
    repository.addMembers("a", listOf(patient("p2")))
    server.answer(
      "add-members:a",
      ServerGroupWrite.Done(group("a", "Patient list a", listOf("p1", "p2", "p9"))),
    )

    repository.send()

    assertThat(dao.patientList("a")?.quantity).isEqualTo(3)
  }

  @Test
  fun anAddForAPatientToIdentifyWaitsForTheIdentificationAndIsNotSent() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 1, membersLoaded = true)))
    toIdentify = setOf("ph")

    repository.addMembers("a", listOf(patient("ph", "Mother of Ann"), patient("p2")))
    val result = repository.send()

    assertThat(result).isEqualTo(SendResult(sent = 1))
    assertThat(server.requests).containsExactly("add-members:a:p2")
    val members = repository.members("a").first()
    assertThat(members.single { it.patientId == "ph" }.toIdentify).isTrue()
    assertThat(dao.memberChanges().map { it.patientId }).containsExactly("ph")
    assertThat(repository.pendingCount.first()).isEqualTo(0)
    assertThat(items().single().pendingChanges).isEqualTo(0)
  }

  @Test
  fun theAddMovesToTheRealPatientOnceIdentifiedAndIsThenSent() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))
    toIdentify = setOf("ph")
    repository.addMembers("a", listOf(patient("ph", "Mother of Ann")))

    toIdentify = emptySet()
    repository.replacePlaceholder("ph", patient("real", "Beatrice Kamau"))
    val result = repository.send()

    assertThat(result).isEqualTo(SendResult(sent = 1))
    assertThat(server.requests).containsExactly("add-members:a:real")
    assertThat(dao.members("a").single().name).isEqualTo("Beatrice Kamau")
  }

  @Test
  fun aPatientIdentifiedAsSomeoneAlreadyInTheListChangesNothing() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 1, membersLoaded = true)))
    dao.upsertMembers(listOf(memberRow("a", "real")))
    toIdentify = setOf("ph")
    repository.addMembers("a", listOf(patient("ph")))

    repository.replacePlaceholder("ph", patient("real"))

    assertThat(dao.memberChanges()).isEmpty()
  }

  @Test
  fun aPatientToIdentifyLinkedToANewPatientPassesItsAddsOnToThemUntilTheUpload() = runTest {
    dao.upsertPatientLists(listOf(row("a", membersLoaded = true), row("b", membersLoaded = true)))
    toIdentify = setOf("ph")
    repository.addMembers("a", listOf(patient("ph")))
    repository.addMembers("b", listOf(patient("ph"), patient("p2")))

    repository.replacePlaceholder("ph", patient("new", "Nora Walker"), heldForUpload = true)

    assertThat(dao.memberChanges().map { Triple(it.listId, it.patientId, it.heldForUpload) })
      .containsExactly(
        Triple("a", "new", true),
        Triple("b", "new", true),
        Triple("b", "p2", false),
      )
  }

  private class Found(private val answers: Map<String, Uploaded>) : UploadedPatients {
    override suspend fun find(
      change: org.openmrs.android.fhir.data.database.model.PatientListMemberChange,
    ) = answers.getValue(change.patientId)
  }

  @Test
  fun anAddForAPatientNotUploadedYetWaitsAndIsNotSent() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))

    repository.addMembers("a", listOf(patient("local")), heldForUpload = setOf("local"))

    assertThat(repository.pendingCount.first()).isEqualTo(0)
    assertThat(repository.members("a").first().single().waitingForUpload).isTrue()
    assertThat(repository.send()).isEqualTo(SendResult())
    assertThat(server.requests).isEmpty()
  }

  @Test
  fun onceUploadedTheAddFollowsThePatientToTheirNewIdAndIsSent() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))
    repository.addMembers("a", listOf(patient("local", "Ann Doe")), heldForUpload = setOf("local"))

    repository.releaseUploaded(Found(mapOf("local" to Uploaded.Now(patient("server", "Ann Doe")))))

    assertThat(dao.memberChanges().map { it.patientId to it.heldForUpload })
      .containsExactly("server" to false)
    assertThat(repository.send()).isEqualTo(SendResult(sent = 1))
    assertThat(server.requests).containsExactly("add-members:a:server")
  }

  @Test
  fun anAddStaysWhileThePatientIsStillWaitingForTheUpload() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))
    repository.addMembers("a", listOf(patient("local")), heldForUpload = setOf("local"))

    repository.releaseUploaded(Found(mapOf("local" to Uploaded.Waiting)))

    assertThat(dao.memberChanges().map { it.patientId to it.heldForUpload })
      .containsExactly("local" to true)
  }

  @Test
  fun anAddForAPatientThatCannotBeFoundAnyMoreIsDropped() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))
    repository.addMembers("a", listOf(patient("local")), heldForUpload = setOf("local"))

    repository.releaseUploaded(Found(mapOf("local" to Uploaded.Missing)))

    assertThat(dao.memberChanges()).isEmpty()
  }

  @Test
  fun aPatientUploadedAsSomeoneAlreadyInTheListChangesNothing() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 1, membersLoaded = true)))
    dao.upsertMembers(listOf(memberRow("a", "server")))
    repository.addMembers("a", listOf(patient("local")), heldForUpload = setOf("local"))

    repository.releaseUploaded(Found(mapOf("local" to Uploaded.Now(patient("server")))))

    assertThat(dao.memberChanges()).isEmpty()
  }

  @Test
  fun theListsOfAPatientSayInOutOrUnknownAndWhatWaits() = runTest {
    dao.upsertPatientLists(
      listOf(
        row("in", "In", membersLoaded = true),
        row("out", "Out", membersLoaded = true),
        row("unknown", "Unknown", membersLoaded = false),
        row("adding", "Adding", membersLoaded = true),
        row("removing", "Removing", membersLoaded = true),
      ),
    )
    dao.upsertMembers(listOf(memberRow("in", "p1"), memberRow("removing", "p1")))
    repository.addMembers("adding", listOf(patient("p1")))
    repository.removeMember("removing", "p1")

    val lists = repository.membershipsOf("p1").first().associateBy { it.list.id }

    assertThat(lists.getValue("in").membership).isEqualTo(Membership.IN)
    assertThat(lists.getValue("out").membership).isEqualTo(Membership.OUT)
    assertThat(lists.getValue("unknown").membership).isEqualTo(Membership.UNKNOWN)
    assertThat(lists.getValue("adding").pending).isEqualTo(MemberPending.ADD)
    assertThat(lists.getValue("adding").membership).isEqualTo(Membership.IN)
    assertThat(lists.getValue("removing").pending).isEqualTo(MemberPending.REMOVE)
    assertThat(lists.getValue("removing").membership).isEqualTo(Membership.OUT)
  }

  @Test
  fun theMembersOfAListAreTheServersPlusTheAddsMinusTheRemovals() = runTest {
    dao.upsertPatientLists(listOf(row("a", membersLoaded = true)))
    dao.upsertMembers(listOf(memberRow("a", "p1"), memberRow("a", "p2")))
    repository.removeMember("a", "p2")
    repository.addMembers("a", listOf(patient("p3")))

    assertThat(repository.memberIds("a")).containsExactly("p1", "p3")
  }

  @Test
  fun theListIsReadAndWhatWaitsForAGonePatientListIsKept() = runTest {
    dao.upsertPatientLists(listOf(row("a"), row("b"), row("c")))
    repository.edit("c", "Changed", null)
    server.groups["a"] =
      group("a", "A renamed", quantity = 2, location = "North", creator = "Dr Who")
    server.groups["d"] = group("d", "D", quantity = 0)

    assertThat(repository.refreshList()).isEqualTo(RefreshResult.DONE)

    val byId = dao.patientLists().associateBy { it.id }
    assertThat(byId.keys).containsExactly("a", "c", "d")
    assertThat(byId["a"]?.name).isEqualTo("A renamed")
    assertThat(byId["a"]?.locationName).isEqualTo("North")
    assertThat(byId["a"]?.creatorName).isEqualTo("Dr Who")
    assertThat(byId["a"]?.quantity).isEqualTo(2)
  }

  @Test
  fun groupsReadForAnotherPurposeAreKeptWithoutAskingTheServerAgain() = runTest {
    dao.upsertPatientLists(listOf(row("a"), row("b")))

    repository.store(
      listOf(group("a", "A renamed", quantity = 3), group("c", "C", quantity = 0)),
      complete = false,
    )

    assertThat(dao.patientLists().map { it.id }).containsExactly("a", "b", "c")
    assertThat(dao.patientList("a")?.name).isEqualTo("A renamed")
    assertThat(server.requests).isEmpty()

    repository.store(listOf(group("a", "A", quantity = 3)), complete = true)

    assertThat(dao.patientLists().map { it.id }).containsExactly("a")
  }

  @Test
  fun anIncompleteListDeletesNothing() = runTest {
    dao.upsertPatientLists(listOf(row("a"), row("b")))
    server.groups["a"] = group("a", "A")
    server.listComplete = false

    repository.refreshList()

    assertThat(dao.patientLists().map { it.id }).containsExactly("a", "b")
  }

  @Test
  fun anUnreachableServerLeavesTheCacheAsItWas() = runTest {
    dao.upsertPatientLists(listOf(row("a")))
    server.unreachable = true

    assertThat(repository.refreshList()).isEqualTo(RefreshResult.UNREACHABLE)
    assertThat(dao.patientLists()).hasSize(1)
  }

  @Test
  fun theMembersAreReadAgainWhenThePatientListChanged() = runTest {
    dao.upsertPatientLists(listOf(row("a", quantity = 2, membersLoaded = true, dateChanged = 5)))
    dao.upsertPatientLists(listOf(row("b", quantity = 2, membersLoaded = true, dateChanged = 5)))
    server.groups["a"] = group("a", "Patient list a", quantity = 3, changed = 9)
    server.groups["b"] = group("b", "Patient list b", quantity = 2, changed = 5)

    repository.refreshList()

    assertThat(dao.patientList("a")?.membersLoaded).isFalse()
    assertThat(dao.patientList("b")?.membersLoaded).isTrue()
  }

  @Test
  fun openingAPatientListReadsTheMembersAndOnlyAsksForThePatientsItDoesNotHave() = runTest {
    dao.upsertPatientLists(listOf(row("a")))
    dao.upsertMembers(listOf(memberRow("a", "p1", "Known")))
    server.groups["a"] = group("a", "Patient list a", listOf("p1", "p2", "p3"))
    server.patients["p2"] = patient("p2", "Second")

    val result = repository.refreshMembers("a")

    assertThat(result).isEqualTo(RefreshResult.DONE)
    assertThat(server.requests).containsExactly("read:a", "patients:p2,p3").inOrder()
    val members = repository.members("a").first()
    assertThat(members.map { it.name }).containsExactly("", "Known", "Second")
    assertThat(dao.patientList("a")?.membersLoaded).isTrue()
    assertThat(dao.patientList("a")?.quantity).isEqualTo(3)
  }

  @Test
  fun aPatientListTheServerDoesNotHaveIsForgotten() = runTest {
    dao.upsertPatientLists(listOf(row("a")))
    dao.upsertMembers(listOf(memberRow("a", "p1")))
    repository.edit("a", "Renamed", null)

    assertThat(repository.refreshMembers("a")).isEqualTo(RefreshResult.GONE)

    assertThat(dao.patientList("a")).isNull()
    assertThat(dao.members("a")).isEmpty()
    assertThat(dao.pendingCount()).isEqualTo(0)
  }

  @Test
  fun aPatientListNotSentYetIsNotAskedFor() = runTest {
    repository.create("Mothers", null, null, null)

    assertThat(repository.refreshMembers("new-1")).isEqualTo(RefreshResult.DONE)
    assertThat(server.requests).isEmpty()
  }

  @Test
  fun forgettingEverythingEmptiesTheTables() = runTest {
    dao.upsertPatientLists(listOf(row("a")))
    dao.upsertMembers(listOf(memberRow("a", "p1")))
    repository.create("Mothers", null, null, null)
    repository.addMembers("a", listOf(patient("p2")))

    repository.clearAll()

    assertThat(dao.patientLists()).isEmpty()
    assertThat(dao.members("a")).isEmpty()
    assertThat(dao.pendingCount()).isEqualTo(0)
  }

  @Test
  fun discardingRefusedChangesRemovesOnlyThose() = runTest {
    dao.upsertPatientLists(listOf(row("a", membersLoaded = true), row("b", membersLoaded = true)))
    repository.edit("a", "A2", null)
    server.answer("update:a", ServerGroupWrite.Refused(403, "Not allowed"))
    repository.send()
    repository.edit("b", "B2", null)

    repository.discardRefused()

    assertThat(dao.changes().map { it.listId }).containsExactly("b")
  }
}
