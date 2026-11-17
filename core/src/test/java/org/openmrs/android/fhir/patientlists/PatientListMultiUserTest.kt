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

import com.google.android.fhir.LocalChange
import com.google.android.fhir.LocalChangeToken
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Patient
import org.junit.Before
import org.junit.Test
import org.openmrs.android.fhir.data.database.model.PatientListEventAction

class PatientListMultiUserTest {
  private lateinit var raw: FakePatientListDao
  private lateinit var server: FakeGroupServer
  private var current = "alice"
  private var time = 1_000L

  private fun repositoryFor(name: String): PatientListsRepository {
    current = name
    return PatientListsRepository(
      UserScopedPatientLists(raw, { current }),
      server,
      NoTransaction,
      { time++ },
      { "new-$time" },
      { emptySet() },
      { current },
    )
  }

  @Before
  fun setUp() {
    raw = FakePatientListDao()
    server = FakeGroupServer()
    time = 1_000L
    current = "alice"
  }

  @Test
  fun aChangeIsSeenAndSentOnlyBySomeoneWhoMadeIt() = runTest {
    raw.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))
    val alice = repositoryFor("alice")
    alice.addMembers("a", listOf(patient("p1")))

    val bob = repositoryFor("bob")

    assertThat(bob.pendingNow()).isEqualTo(0)
    assertThat(bob.members("a").first()).isEmpty()
    assertThat(bob.send()).isEqualTo(SendResult())
    assertThat(server.requests).isEmpty()

    repositoryFor("alice")
    assertThat(alice.pendingNow()).isEqualTo(1)
    assertThat(alice.send()).isEqualTo(SendResult(sent = 1))
    assertThat(server.requests).containsExactly("add-members:a:p1")
  }

  @Test
  fun theSamePatientCanWaitInTwoUsersChangesWithoutOneReplacingTheOther() = runTest {
    raw.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))
    val alice = repositoryFor("alice")
    alice.addMembers("a", listOf(patient("p1")))
    val bob = repositoryFor("bob")
    bob.addMembers("a", listOf(patient("p1")))

    assertThat(raw.allMemberChanges().map { it.owner }).containsExactly("alice", "bob")

    bob.undoMemberChange("a", "p1")

    assertThat(raw.allMemberChanges().map { it.owner }).containsExactly("alice")
  }

  @Test
  fun aNewPatientListOfOneUserIsNotInTheListsOfAnother() = runTest {
    repositoryFor("alice").create("Mothers", null, null, null)

    val bob = repositoryFor("bob")

    assertThat(bob.items.first()).isEmpty()
    assertThat(bob.create("Mothers", null, null, null))
      .isInstanceOf(PatientListEdit.Saved::class.java)
  }

  @Test
  fun aPatientUploadedWhileAnotherUserIsSignedInStillJoinsTheListOfTheirOwner() = runTest {
    raw.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))
    val alice = repositoryFor("alice")
    alice.addMembers("a", listOf(patient("local")), heldForUpload = setOf("local"))

    val bob = repositoryFor("bob")
    bob.releaseUploaded { Uploaded.Now(patient("server")) }

    assertThat(raw.allMemberChanges().map { Triple(it.patientId, it.owner, it.heldForUpload) })
      .containsExactly(Triple("server", "alice", false))
    assertThat(bob.send()).isEqualTo(SendResult())
    assertThat(server.requests).isEmpty()
  }

  @Test
  fun whatTheUploadNotedIsUsedToFollowThePatient() = runTest {
    raw.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))
    val repository = repositoryFor("alice")
    repository.addMembers("a", listOf(patient("local")), heldForUpload = setOf("local"))
    repository.addMembers("a", listOf(patient("other")))

    repository.noteUploaded(listOf("local" to "server", "other" to "elsewhere"))

    assertThat(raw.uploadedId("local")?.serverId).isEqualTo("server")
    assertThat(raw.uploadedId("other")).isNull()
  }

  @Test
  fun severalPatientsThatFitAreNeverGuessedAndTheAddWaits() = runTest {
    raw.upsertPatientLists(listOf(row("a", quantity = 0, membersLoaded = true)))
    val repository = repositoryFor("alice")
    repository.addMembers("a", listOf(patient("local")), heldForUpload = setOf("local"))

    repository.releaseUploaded { Uploaded.Ambiguous }

    val change = raw.allMemberChanges().single()
    assertThat(change.patientId).isEqualTo("local")
    assertThat(change.heldForUpload).isTrue()
    assertThat(change.error).isEqualTo(PatientListsRepository.AMBIGUOUS_UPLOAD)
    assertThat(repository.send()).isEqualTo(SendResult())
    assertThat(server.requests).isEmpty()
  }

  @Test
  fun theChoiceAmongPatientsThatFitIsNotMadeWhenThereAreSeveral() {
    val one = Patient().apply { id = "p1" }
    val two = Patient().apply { id = "p2" }

    assertThat(UploadedPatientFinder.choose(emptyList())).isEqualTo(Uploaded.Missing)
    assertThat(UploadedPatientFinder.choose(listOf(one))).isInstanceOf(Uploaded.Now::class.java)
    assertThat(UploadedPatientFinder.choose(listOf(one, two))).isEqualTo(Uploaded.Ambiguous)
  }

  @Test
  fun theHistoryNamesWhoDidWhat() = runTest {
    raw.upsertPatientLists(listOf(row("a", quantity = 1, membersLoaded = true)))
    raw.upsertMembers(listOf(memberRow("a", "p9")))
    val alice = repositoryFor("alice")
    alice.addMembers("a", listOf(patient("p1", "Ann")))
    val bob = repositoryFor("bob")
    bob.removeMember("a", "p9")

    val history = bob.history("a").first()

    assertThat(history.map { Triple(it.action, it.user, it.patientName) })
      .containsExactly(
        Triple(PatientListEventAction.MEMBER_REMOVED, "bob", "Patient p9"),
        Triple(PatientListEventAction.MEMBER_ADDED, "alice", "Ann"),
      )
      .inOrder()
  }

  @Test
  fun theIdTheServerGaveAPatientIsReadFromTheAnswerOfTheUpload() {
    val created =
      LocalChange(
        resourceType = "Patient",
        resourceId = "local-id",
        timestamp = Instant.now(),
        type = LocalChange.Type.INSERT,
        payload = "{}",
        token = LocalChangeToken(listOf(1L)),
      )
    val answer = Patient().apply { id = "Patient/server-id/_history/1" }
    val inBundle =
      Bundle.BundleEntryResponseComponent().apply {
        location = "http://server/Patient/server-id/_history/1"
      }

    assertThat(UploadedIds.pairs(listOf(created), answer))
      .containsExactly("local-id" to "server-id")
    assertThat(UploadedIds.pairs(listOf(created), inBundle))
      .containsExactly("local-id" to "server-id")
    assertThat(UploadedIds.pairs(listOf(created.copy(type = LocalChange.Type.UPDATE)), answer))
      .isEmpty()
    assertThat(UploadedIds.pairs(listOf(created.copy(resourceType = "Encounter")), answer))
      .isEmpty()
  }
}
