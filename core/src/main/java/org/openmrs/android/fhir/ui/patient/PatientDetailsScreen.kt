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
package org.openmrs.android.fhir.ui.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.composeEmail
import org.openmrs.android.fhir.extensions.dialNumber
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.ui.components.EncounterListItemRow
import org.openmrs.android.fhir.ui.components.PatientDetailLine
import org.openmrs.android.fhir.ui.components.PatientDetailsHeaderRow
import org.openmrs.android.fhir.ui.components.PatientDetailsOverviewHeader
import org.openmrs.android.fhir.ui.components.PatientPropertyRow
import org.openmrs.android.fhir.ui.components.PatientUnsyncedCard
import org.openmrs.android.fhir.ui.components.VisitListItemRow
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget
import org.openmrs.android.fhir.viewmodel.PatientDetailCondition
import org.openmrs.android.fhir.viewmodel.PatientDetailData
import org.openmrs.android.fhir.viewmodel.PatientDetailEncounter
import org.openmrs.android.fhir.viewmodel.PatientDetailHeader
import org.openmrs.android.fhir.viewmodel.PatientDetailObservation
import org.openmrs.android.fhir.viewmodel.PatientDetailOverview
import org.openmrs.android.fhir.viewmodel.PatientDetailProperty
import org.openmrs.android.fhir.viewmodel.PatientDetailUnidentified
import org.openmrs.android.fhir.viewmodel.PatientDetailVisit
import org.openmrs.android.fhir.viewmodel.PatientExtraDetail
import org.openmrs.android.fhir.viewmodel.PatientListViewModel
import org.openmrs.android.fhir.viewmodel.PatientUnsynced

/**
 * The patient file: profile card (with edit), a notice while the patient is not uploaded, then the
 * visits and encounters. Tapping an encounter shows its answers, its pencil edits it, an open visit
 * can be ended, and the button starts a new encounter. [patientData] is null while the data is
 * loading.
 */
@Composable
fun PatientDetailsScreen(
  patientData: List<PatientDetailData>?,
  onEditPatient: () -> Unit,
  onViewEncounter: (encounterId: String, encounterType: String) -> Unit,
  onEditEncounter: (encounterId: String, encounterType: String) -> Unit,
  onEndVisit: (visitId: String) -> Unit,
  onCreateEncounter: () -> Unit,
  modifier: Modifier = Modifier,
  onStartVisit: () -> Unit = {},
  onReopenVisit: (visitId: String) -> Unit = {},
  onIdentifyPatient: () -> Unit = {},
  onEditNote: () -> Unit = {},
) {
  val topInset = rememberAppBarHeight()
  val encounterCount =
    remember(patientData) { patientData.orEmpty().count { it is PatientDetailEncounter } }
  val firstHeader = remember(patientData) { patientData?.firstOrNull { it is PatientDetailHeader } }

  Box(
    modifier = modifier.fillMaxSize().testTag("PatientDetailsScreen"),
  ) {
    if (patientData == null) {
      CircularProgressIndicator(
        modifier = Modifier.align(Alignment.Center).testTag("PatientDetailsLoading"),
      )
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("PatientDetailsList"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding =
          PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 12.dp, bottom = 104.dp),
      ) {
        items(patientData, key = { it.listKey() }, contentType = { it::class }) { item ->
          when (item) {
            is PatientDetailOverview ->
              PatientDetailsOverviewHeader(
                modifier = Modifier.tutorialTarget(TutorialTargets.DETAILS_OVERVIEW),
                name = item.patient.name,
                identifiers = item.patient.identifiers,
                ageGender = formatAgeGender(item.patient),
                details = patientDetailLines(item.patient),
                extraDetails = extraDetailLines(item.extraDetails),
                // A placeholder has no registration to edit.
                onEditClick = if (item.unidentified) null else onEditPatient,
              )
            is PatientDetailUnidentified ->
              UnidentifiedCard(
                note = item.note,
                onIdentify = onIdentifyPatient,
                onEditNote = onEditNote,
              )
            is PatientUnsynced -> PatientUnsyncedCard()
            is PatientDetailHeader ->
              PatientDetailsHeaderRow(
                // Only the first header is the target of the tour.
                modifier =
                  if (item === firstHeader) {
                    Modifier.tutorialTarget(TutorialTargets.DETAILS_ENCOUNTERS)
                  } else {
                    Modifier
                  },
                title = item.header,
                count = item.count ?: encounterCount,
                actionText = if (item.startVisit) stringResource(R.string.start_visit) else null,
                onActionClick = onStartVisit,
              )
            is PatientDetailVisit ->
              VisitListItemRow(
                encounterType = item.visit.code,
                encounterDate =
                  if (item.visit.endDate.isBlank()) {
                    stringResource(R.string.visit_started_at, item.visit.startDate)
                  } else {
                    item.visit.getPeriods()
                  },
                statusText =
                  stringResource(
                    if (item.isActive) {
                      R.string.visit_status_active
                    } else {
                      R.string.visit_status_closed
                    },
                  ),
                isActive = item.isActive,
                showSyncIcon = !item.isSynced,
                // Only an open visit can be ended, and only an ended one reopened.
                onDateClick = if (item.isActive) ({ onEndVisit(item.visit.id) }) else null,
                onReopenClick = if (item.canReopen) ({ onReopenVisit(item.visit.id) }) else null,
              )
            is PatientDetailEncounter ->
              EncounterListItemRow(
                modifier = if (item.indented) Modifier.padding(start = 20.dp) else Modifier,
                encounterType = item.encounter.type,
                encounterDate = item.encounter.dateTime,
                showSyncIcon = item.encounter.isSynced?.not() ?: true,
                onTitleClick = {
                  onViewEncounter(
                    item.encounter.encounterId.orEmpty(),
                    item.encounter.encounterType.orEmpty(),
                  )
                },
                onEditClick = {
                  onEditEncounter(
                    item.encounter.encounterId.orEmpty(),
                    item.encounter.encounterType.orEmpty(),
                  )
                },
              )
            is PatientDetailProperty ->
              PatientPropertyRow(
                header = item.patientProperty.header,
                value = item.patientProperty.value,
                showSyncIcon = !item.patientProperty.isSynced,
              )
            is PatientDetailObservation ->
              PatientPropertyRow(
                header = item.observation.code,
                value = item.observation.value,
                showSyncIcon = false,
              )
            is PatientDetailCondition ->
              PatientPropertyRow(
                header = item.condition.code,
                value = item.condition.value,
                showSyncIcon = false,
              )
            else -> Unit
          }
        }
      }

      ExtendedFloatingActionButton(
        onClick = onCreateEncounter,
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .padding(16.dp)
            .testTag("CreateEncounterButton")
            .tutorialTarget(TutorialTargets.DETAILS_NEW_ENCOUNTER),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
        text = { Text(text = stringResource(id = R.string.create_encounter)) },
      )
    }
  }
}

/**
 * What a patient to identify after is: records entered without registering the patient, kept on the
 * device and not sent until the real patient is found.
 */
@Composable
private fun UnidentifiedCard(note: String, onIdentify: () -> Unit, onEditNote: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth().testTag("UnidentifiedCard"),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Filled.PersonSearch,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
          modifier = Modifier.padding(start = 12.dp),
          text = stringResource(R.string.identify_banner_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
      Text(
        text = note.ifBlank { stringResource(R.string.identify_later_no_note) },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
      Text(
        text = stringResource(R.string.identify_banner_message),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onIdentify, modifier = Modifier.testTag("IdentifyPatientButton")) {
          Text(text = stringResource(R.string.identify_banner_identify))
        }
        TextButton(onClick = onEditNote, modifier = Modifier.testTag("EditNoteButton")) {
          Text(text = stringResource(R.string.identify_banner_edit_note))
        }
      }
    }
  }
}

/** Stable identity of a row so the list keeps its place when data refreshes. */
private fun PatientDetailData.listKey(): Any =
  when (this) {
    is PatientDetailOverview -> "patient"
    is PatientDetailUnidentified -> "unidentified-note"
    is PatientUnsynced -> "unsynced"
    is PatientDetailHeader -> "header-$header"
    is PatientDetailVisit -> "visit-${visit.id}"
    is PatientDetailEncounter -> "encounter-${encounter.encounterId ?: encounter.dateTime}"
    else -> "other-${hashCode()}"
  }

@Composable
private fun patientDetailLines(patient: PatientListViewModel.PatientItem): List<PatientDetailLine> {
  val genderLabel = stringResource(R.string.patient_gender)
  val dobLabel = stringResource(R.string.patient_property_dob)
  val phoneLabel = stringResource(R.string.patient_phone_number)
  val addressLabel = stringResource(R.string.patient_property_address)
  val context = LocalContext.current
  return remember(patient, genderLabel, dobLabel, phoneLabel, addressLabel) {
    buildList {
      patient.gender
        .takeIf { it.isNotBlank() }
        ?.let { add(PatientDetailLine(Icons.Filled.Person, genderLabel, it.titleCase())) }
      patient.dob?.let {
        add(
          PatientDetailLine(
            Icons.Filled.Cake,
            dobLabel,
            it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
          ),
        )
      }
      patient.phone
        .takeIf { it.isNotBlank() }
        ?.let { number ->
          add(
            PatientDetailLine(
              Icons.Filled.Phone,
              phoneLabel,
              number,
              { context.dialNumber(number) },
            ),
          )
        }
      listOf(patient.city, patient.country)
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .takeIf { it.isNotBlank() }
        ?.let { add(PatientDetailLine(Icons.Filled.LocationOn, addressLabel, it)) }
    }
  }
}

/** The rest of the record; numbers call and addresses write when tapped. */
@Composable
private fun extraDetailLines(details: List<PatientExtraDetail>): List<PatientDetailLine> {
  val context = LocalContext.current
  return remember(details) {
    details.map { detail ->
      when (detail.kind) {
        PatientExtraDetail.Kind.PHONE ->
          PatientDetailLine(Icons.Filled.Phone, detail.label, detail.value) {
            context.dialNumber(detail.value)
          }
        PatientExtraDetail.Kind.EMAIL ->
          PatientDetailLine(Icons.Filled.Email, detail.label, detail.value) {
            context.composeEmail(detail.value)
          }
        PatientExtraDetail.Kind.TEXT ->
          PatientDetailLine(Icons.Filled.Info, detail.label, detail.value)
      }
    }
  }
}

private fun String.titleCase(): String =
  lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
