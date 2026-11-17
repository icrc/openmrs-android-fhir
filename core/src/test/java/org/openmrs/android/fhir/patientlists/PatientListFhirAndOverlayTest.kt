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
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.StringType
import org.junit.Test
import org.openmrs.android.fhir.data.database.model.PatientListChange
import org.openmrs.android.fhir.data.database.model.PatientListChangeKind
import org.openmrs.android.fhir.data.database.model.PatientListMemberAction
import org.openmrs.android.fhir.data.database.model.PatientListMemberChange

class PatientListFhirAndOverlayTest {

  @Test
  fun aGroupBecomesARowWithEverythingTheServerAdds() {
    val group =
      group("g1", "Mothers", members = listOf("p1", "p2"), location = "North", creator = "Dr Who")
    group.addExtension(Extension(GroupMapper.DESCRIPTION, StringType("every Monday")))
    group.addExtension(
      Extension(GroupMapper.LIST_TYPE, Reference("PatientListType/t1").setDisplay("Clinic list")),
    )
    group.addExtension(Extension(GroupMapper.DATE_CREATED, DateTimeType("2026-01-02T03:04:05Z")))
    group.addExtension(
      Extension(GroupMapper.CHANGED_BY, Reference().setDisplay("Nurse Joy")),
    )
    group.addExtension(
      Extension(GroupMapper.ATTRIBUTE).apply {
        addExtension(Extension("type", StringType("Program")))
        addExtension(Extension("value", StringType("Primary care")))
      },
    )

    val parsed = GroupMapper.parse(group, now = 99)

    assertThat(parsed.memberIds).containsExactly("p1", "p2").inOrder()
    val row = parsed.row
    assertThat(row.id).isEqualTo("g1")
    assertThat(row.name).isEqualTo("Mothers")
    assertThat(row.description).isEqualTo("every Monday")
    assertThat(row.typeName).isEqualTo("Clinic list")
    assertThat(row.locationId).isEqualTo("loc-North")
    assertThat(row.locationName).isEqualTo("North")
    assertThat(row.creatorName).isEqualTo("Dr Who")
    assertThat(row.changedByName).isEqualTo("Nurse Joy")
    assertThat(row.dateCreated).isEqualTo(DateTimeType("2026-01-02T03:04:05Z").value.time)
    assertThat(row.quantity).isEqualTo(2)
    assertThat(row.refreshedAt).isEqualTo(99)
    assertThat(PatientListOverlay.attributes(row.attributesJson))
      .containsExactly("Program" to "Primary care")
  }

  @Test
  fun aLightGroupHasNoMembersButHasItsQuantity() {
    val parsed = GroupMapper.parse(group("g1", "Mothers", members = null, quantity = 7), 0)

    assertThat(parsed.memberIds).isNull()
    assertThat(parsed.row.quantity).isEqualTo(7)
  }

  @Test
  fun aLocationGivenAsPlainTextStillGivesItsId() {
    val group = group("g1", "Mothers")
    group.addExtension(Extension(GroupMapper.LOCATION, StringType("loc-7")))

    assertThat(GroupMapper.parse(group, 0).row.locationId).isEqualTo("loc-7")
  }

  @Test
  fun aCreateCarriesTheIdOfTheDeviceAndNoMembers() {
    val change =
      PatientListChange(
        "c1",
        PatientListChangeKind.CREATE,
        "Mothers",
        "text",
        "loc-1",
        "North",
        1,
        null,
      )

    val group = GroupMapper.toCreate(change)

    assertThat(group.idElement.idPart).isEqualTo("c1")
    assertThat(group.name).isEqualTo("Mothers")
    assertThat(group.hasMember()).isFalse()
    assertThat(group.getExtensionByUrl(GroupMapper.DESCRIPTION).value.primitiveValue())
      .isEqualTo("text")
    assertThat((group.getExtensionByUrl(GroupMapper.LOCATION).value as Reference).reference)
      .isEqualTo("Location/loc-1")
  }

  @Test
  fun anUpdateHasNoMembersAndClearsAnEmptiedDescription() {
    val change =
      PatientListChange("c1", PatientListChangeKind.UPDATE, "Mothers", null, null, null, 1, null)

    val group = GroupMapper.toUpdate(change)

    assertThat(group.hasMember()).isFalse()
    assertThat(group.getExtensionByUrl(GroupMapper.DESCRIPTION).value.primitiveValue())
      .isEqualTo("")
    assertThat(group.hasExtension(GroupMapper.LOCATION)).isFalse()
  }

  @Test
  fun theParametersHaveOnePatientReferenceEach() {
    val parameters = GroupMapper.parameters(listOf("p1", "p2"))

    assertThat(parameters.parameter.map { it.name }).containsExactly("patient", "patient")
    assertThat(parameters.parameter.map { (it.value as Reference).reference })
      .containsExactly("Patient/p1", "Patient/p2")
      .inOrder()
  }

  @Test
  fun aPatientIsSummarisedWithItsFirstRealIdentifier() {
    val patient =
      Patient().apply {
        id = "p1"
        addName().addGiven("Ann").addGiven("Marie").family = "Smith"
        gender = org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE
        birthDateElement = org.hl7.fhir.r4.model.DateType("1990-05-06")
        addIdentifier().value = "unsynced"
        addIdentifier().value = "100ABC"
      }

    val summary = GroupMapper.summary(patient)

    assertThat(summary)
      .isEqualTo(PatientSummary("p1", "Ann Marie Smith", "female", "1990-05-06", "100ABC"))
  }

  private fun memberChange(
    listId: String,
    patientId: String,
    action: String,
    error: String? = null,
  ) =
    PatientListMemberChange(
      listId,
      patientId,
      action,
      "Name $patientId",
      null,
      null,
      null,
      1,
      error,
    )

  @Test
  fun theCountFollowsWhatWaits() {
    val items =
      PatientListOverlay.items(
        rows = listOf(row("a", quantity = 5, membersLoaded = true)),
        changes = emptyList(),
        memberChanges =
          listOf(
            memberChange("a", "p1", PatientListMemberAction.ADD),
            memberChange("a", "p2", PatientListMemberAction.ADD),
            memberChange("a", "p3", PatientListMemberAction.REMOVE),
          ),
      )

    assertThat(items.single().quantity).isEqualTo(6)
    assertThat(items.single().pendingChanges).isEqualTo(3)
  }

  @Test
  fun theCountNeverGoesBelowZero() {
    val items =
      PatientListOverlay.items(
        rows = listOf(row("a", quantity = 0)),
        changes = emptyList(),
        memberChanges = listOf(memberChange("a", "p3", PatientListMemberAction.REMOVE)),
      )

    assertThat(items.single().quantity).isEqualTo(0)
  }

  @Test
  fun anErrorOfAMemberChangeShowsOnThePatientList() {
    val items =
      PatientListOverlay.items(
        rows = listOf(row("a")),
        changes = emptyList(),
        memberChanges =
          listOf(memberChange("a", "p3", PatientListMemberAction.ADD, "Unknown patient")),
      )

    assertThat(items.single().error).isEqualTo("Unknown patient")
  }

  @Test
  fun thePatientListsAreInAlphabeticalOrderWhateverTheirState() {
    val items =
      PatientListOverlay.items(
        rows = listOf(row("a", "zebra"), row("b", "Apple")),
        changes =
          listOf(
            PatientListChange(
              "c",
              PatientListChangeKind.CREATE,
              "mango",
              null,
              null,
              null,
              1,
              null,
            ),
          ),
        memberChanges = emptyList(),
      )

    assertThat(items.map { it.name }).containsExactly("Apple", "mango", "zebra").inOrder()
    assertThat(items.map { it.state })
      .containsExactly(PatientListState.SYNCED, PatientListState.NEW, PatientListState.SYNCED)
      .inOrder()
  }

  @Test
  fun aDeletedPatientListStaysInTheListMarked() {
    val items =
      PatientListOverlay.items(
        rows = listOf(row("a", "Mothers")),
        changes =
          listOf(
            PatientListChange(
              "a",
              PatientListChangeKind.DELETE,
              "Mothers",
              null,
              null,
              null,
              1,
              null,
            ),
          ),
        memberChanges = emptyList(),
      )

    assertThat(items.single().state).isEqualTo(PatientListState.DELETING)
  }

  @Test
  fun aMemberAlreadyOnTheServerIsNotListedTwiceWhenItIsAlsoQueuedForAdd() {
    val members =
      PatientListOverlay.members(
        rows = listOf(memberRow("a", "p1")),
        memberChanges = listOf(memberChange("a", "p1", PatientListMemberAction.ADD)),
      )

    assertThat(members).hasSize(1)
    assertThat(members.single().pending).isNull()
  }

  @Test
  fun aDamagedAttributesTextGivesNoAttributes() {
    assertThat(PatientListOverlay.attributes("not json")).isEmpty()
    assertThat(PatientListOverlay.attributes(null)).isEmpty()
    assertThat(PatientListOverlay.attributes("""[["a","b"],["","x"],["c",""]]"""))
      .containsExactly("a" to "b")
  }
}
