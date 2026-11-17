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
package org.openmrs.android.fhir.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.ui.components.PatientAvatar
import org.openmrs.android.fhir.ui.components.PatientListTag
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget
import org.openmrs.android.fhir.viewmodel.GroupSessionUiState
import org.openmrs.android.fhir.viewmodel.SessionPatient

object GroupSessionPatientsTestTags {
  const val Screen = "GroupSessionPatientsScreen"
  const val Start = "GroupSessionStart"
  const val AddPatients = "GroupSessionAddPatients"
  const val IdentifyLater = "GroupSessionIdentifyLater"
  const val SelectAll = "GroupSessionSelectAll"

  fun row(id: String) = "GroupSessionRow_$id"

  fun remove(id: String) = "GroupSessionRemove_$id"
}

@Composable
fun GroupSessionPatientsScreen(
  state: GroupSessionUiState,
  onTick: (patientId: String, ticked: Boolean) -> Unit,
  onTickAll: (Boolean) -> Unit,
  onRemove: (String) -> Unit,
  onAddPatients: () -> Unit,
  onIdentifyLater: (note: String) -> Unit,
  onStart: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var askNote by rememberSaveable { mutableStateOf(false) }
  val topInset = rememberAppBarHeight()
  val surfaceColor = MaterialTheme.colorScheme.background
  val participants = state.participantIds.size
  Box(modifier = modifier.fillMaxSize().background(surfaceColor)) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = surfaceColor,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      bottomBar = {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 8.dp,
        ) {
          Button(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag(GroupSessionPatientsTestTags.Start),
            enabled = participants > 0 && !state.starting,
            onClick = onStart,
          ) {
            Text(text = stringResource(R.string.start_with_count, participants))
          }
        }
      },
    ) { padding ->
      LazyColumn(
        modifier =
          Modifier.fillMaxSize()
            .padding(padding)
            .padding(top = topInset)
            .testTag(GroupSessionPatientsTestTags.Screen),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        item(key = "title") {
          Text(
            text = state.listName.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
          )
        }
        if (!state.joinsList) {
          item(key = "no-join") {
            PatientListBanner(text = stringResource(R.string.plist_session_no_join_note))
          }
        }
        item(key = "actions") {
          Row(
            modifier = Modifier.fillMaxWidth().tutorialTarget(TutorialTargets.PSESSION_ADD),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              modifier = Modifier.weight(1f).testTag(GroupSessionPatientsTestTags.AddPatients),
              onClick = onAddPatients,
            ) {
              Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
              Text(
                modifier = Modifier.padding(start = 6.dp),
                text = stringResource(R.string.plist_add_patients),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            OutlinedButton(
              modifier = Modifier.weight(1f).testTag(GroupSessionPatientsTestTags.IdentifyLater),
              onClick = { askNote = true },
            ) {
              Icon(imageVector = Icons.Default.PersonSearch, contentDescription = null)
              Text(
                modifier = Modifier.padding(start = 6.dp),
                text = stringResource(R.string.identify_later_action),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        if (state.loading) {
          item(key = "loading") {
            Box(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          }
        }
        if (state.members.isNotEmpty() || state.added.isNotEmpty()) {
          item(key = "select-all") {
            SelectAllRow(
              ticked = state.allTicked,
              count = participants,
              total = state.members.count { it.onDevice } + state.added.size,
              onChange = onTickAll,
            )
          }
        }
        if (!state.loading && state.members.isEmpty()) {
          item(key = "members-empty") {
            Text(
              modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
              text = stringResource(R.string.plist_session_list_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (state.members.isNotEmpty()) {
          item(key = "members-header") {
            SectionHeader(
              text = stringResource(R.string.plist_session_in_list),
              modifier = Modifier.tutorialTarget(TutorialTargets.PSESSION_PATIENTS),
            )
          }
          items(state.members, key = { "m-" + it.patient.id }) { row ->
            SessionRow(row = row, onTick = onTick, onRemove = null)
          }
        }
        if (state.added.isNotEmpty()) {
          item(key = "added-header") {
            SectionHeader(text = stringResource(R.string.plist_session_added))
          }
          item(key = "added-note") {
            Text(
              text = stringResource(R.string.plist_session_added_note),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          items(state.added, key = { "a-" + it.patient.id }) { row ->
            SessionRow(row = row, onTick = onTick, onRemove = onRemove)
          }
        }
      }
    }
  }
  if (askNote) {
    UnidentifiedNoteDialog(
      title = stringResource(R.string.identify_note_title),
      initial = "",
      confirmLabel = stringResource(R.string.identify_note_create),
      onConfirm = {
        askNote = false
        onIdentifyLater(it)
      },
      onDismiss = { askNote = false },
    )
  }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
  Text(
    modifier = modifier.padding(top = 8.dp),
    text = text.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.primary,
  )
}

@Composable
private fun SelectAllRow(ticked: Boolean, count: Int, total: Int, onChange: (Boolean) -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(role = Role.Checkbox) { onChange(!ticked) }
        .padding(vertical = 4.dp)
        .testTag(GroupSessionPatientsTestTags.SelectAll),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = ticked, onCheckedChange = null)
    Text(
      modifier = Modifier.weight(1f).padding(start = 8.dp),
      text = stringResource(R.string.select_all_patients),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
    )
    Text(
      text = stringResource(R.string.patients_selected_of, count, total),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SessionRow(
  row: SessionPatient,
  onTick: (String, Boolean) -> Unit,
  onRemove: ((String) -> Unit)?,
) {
  val patient = row.patient
  val details =
    listOfNotNull(
        ageGenderLabel(patient.birthDate, patient.gender).ifBlank { null },
        patient.identifier?.takeIf { it.isNotBlank() },
      )
      .joinToString(" · ")
  val enabled = row.onDevice
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(
          if (row.ticked && enabled) {
            OpenMrsTheme.colors.highlightContainer
          } else {
            MaterialTheme.colorScheme.surface
          },
        )
        .clickable(enabled = enabled, role = Role.Checkbox) { onTick(patient.id, !row.ticked) }
        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
        .testTag(GroupSessionPatientsTestTags.row(patient.id)),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    PatientAvatar(name = patient.name, size = 40.dp)
    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
      Text(
        text = patient.name.ifBlank { stringResource(R.string.plist_member_unknown) },
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color =
          if (enabled) {
            MaterialTheme.colorScheme.onSurface
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (details.isNotEmpty()) {
        Text(
          text = details,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      val note =
        when {
          !row.onDevice -> R.string.plist_session_not_here
          row.toIdentify -> R.string.plist_to_identify_tag
          row.notSentYet -> R.string.plist_picker_not_sent
          else -> null
        }
      if (note != null) {
        PatientListTag(
          modifier = Modifier.padding(top = 4.dp),
          text = stringResource(note),
          isError = false,
        )
      }
    }
    if (onRemove != null) {
      IconButton(
        modifier = Modifier.testTag(GroupSessionPatientsTestTags.remove(patient.id)),
        onClick = { onRemove(patient.id) },
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = stringResource(R.string.plist_session_remove),
        )
      }
    }
    Checkbox(checked = row.ticked && enabled, onCheckedChange = null, enabled = enabled)
  }
}
