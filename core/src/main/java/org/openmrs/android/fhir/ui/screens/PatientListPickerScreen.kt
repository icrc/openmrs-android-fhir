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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.patientlists.PatientListEntry
import org.openmrs.android.fhir.ui.components.AppSearchField
import org.openmrs.android.fhir.ui.components.PatientAvatar
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget
import org.openmrs.android.fhir.viewmodel.PatientListPickerUiState
import org.openmrs.android.fhir.viewmodel.PickerCandidate

object PatientListPickerTestTags {
  const val Screen = "PatientListPickerScreen"
  const val Search = "PatientListPickerSearch"
  const val Add = "PatientListPickerAdd"
  const val IdentifyLater = "PatientListPickerIdentifyLater"
  const val CreatePatient = "PatientListPickerCreatePatient"
  const val SelectAll = "PatientListPickerSelectAll"
  const val FromList = "PatientListPickerFromList"

  fun otherList(id: String) = "PatientListPickerOtherList_$id"

  fun row(id: String) = "PatientListPickerRow_$id"
}

@Composable
fun PatientListPickerScreen(
  state: PatientListPickerUiState,
  onQueryChange: (String) -> Unit,
  onToggle: (String) -> Unit,
  onAdd: () -> Unit,
  modifier: Modifier = Modifier,
  onIdentifyLater: ((note: String) -> Unit)? = null,
  onCreatePatient: (() -> Unit)? = null,
  onSelectAll: ((Boolean) -> Unit)? = null,
  onLoadOtherLists: (() -> Unit)? = null,
  onPickList: ((String) -> Unit)? = null,
) {
  var askNote by rememberSaveable { mutableStateOf(false) }
  var pickingList by rememberSaveable { mutableStateOf(false) }
  val topInset = rememberAppBarHeight()
  val surfaceColor = MaterialTheme.colorScheme.background
  val onDevice = state.candidates.filter { it.onDevice }
  val onServer = state.candidates.filterNot { it.onDevice }
  Box(modifier = modifier.fillMaxSize().background(surfaceColor)) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = surfaceColor,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      bottomBar = {
        if (state.selected.isNotEmpty()) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
          ) {
            Button(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 10.dp)
                  .testTag(PatientListPickerTestTags.Add),
              enabled = !state.adding,
              onClick = onAdd,
            ) {
              Text(
                text =
                  pluralStringResource(
                    R.plurals.plist_picker_add,
                    state.selected.size,
                    state.selected.size,
                  ),
              )
            }
          }
        }
      },
    ) { padding ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(padding)
            .padding(top = topInset + 4.dp)
            .testTag(PatientListPickerTestTags.Screen),
      ) {
        AppSearchField(
          value = state.query,
          onValueChange = onQueryChange,
          placeholder = stringResource(R.string.plist_picker_search_hint),
          modifier = Modifier.testTag(PatientListPickerTestTags.Search),
        )
        LazyColumn(
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (onIdentifyLater != null) {
            item(key = "identify-later") {
              OutlinedButton(
                modifier = Modifier.fillMaxWidth().testTag(PatientListPickerTestTags.IdentifyLater),
                onClick = { askNote = true },
              ) {
                Icon(imageVector = Icons.Default.PersonSearch, contentDescription = null)
                Text(
                  modifier = Modifier.padding(start = 8.dp),
                  text = stringResource(R.string.identify_later_action),
                )
              }
            }
          }
          if (onCreatePatient != null) {
            item(key = "create-patient") { CreatePatientRow(onCreatePatient) }
          }
          if (onSelectAll != null || onPickList != null) {
            item(key = "bulk") {
              BulkActions(
                state = state,
                onSelectAll = onSelectAll,
                onPickList =
                  onPickList?.let {
                    {
                      onLoadOtherLists?.invoke()
                      pickingList = true
                    }
                  },
              )
            }
          }
          if (state.query.isBlank()) {
            item(key = "hint") {
              Text(
                text =
                  stringResource(
                    if (state.session) {
                      R.string.plist_picker_session_hint
                    } else {
                      R.string.plist_picker_hint
                    },
                  ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (onDevice.isNotEmpty()) {
            item(key = "device-header") { Header(R.string.plist_picker_on_device, false) }
            items(onDevice, key = { "d-" + it.patient.id }) { candidate ->
              CandidateRow(candidate, candidate.patient.id in state.selected, onToggle)
            }
          }
          if (state.serverSearching) {
            item(key = "server-header") {
              Header(R.string.plist_picker_on_server, state.serverLoading)
            }
            items(onServer, key = { "s-" + it.patient.id }) { candidate ->
              CandidateRow(candidate, candidate.patient.id in state.selected, onToggle)
            }
            if (state.serverFailed) {
              item(key = "server-failed") {
                Text(
                  text = stringResource(R.string.plist_picker_server_failed),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
          if (!state.loading && !state.serverLoading && state.candidates.isEmpty()) {
            item(key = "none") {
              Text(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                text = stringResource(R.string.plist_picker_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
  if (pickingList && onPickList != null) {
    OtherListsDialog(
      lists = state.otherLists,
      onPick = {
        pickingList = false
        onPickList(it)
      },
      onDismiss = { pickingList = false },
    )
  }
  if (askNote && onIdentifyLater != null) {
    UnidentifiedNoteDialog(
      title = stringResource(R.string.identify_note_title),
      initial = state.query,
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
private fun BulkActions(
  state: PatientListPickerUiState,
  onSelectAll: ((Boolean) -> Unit)?,
  onPickList: (() -> Unit)?,
) {
  val choices = state.candidates.count { it.selectable }
  Row(
    modifier = Modifier.fillMaxWidth().tutorialTarget(TutorialTargets.PLIST_BULK),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (onSelectAll != null && choices > 1) {
      OutlinedButton(
        modifier = Modifier.weight(1f).testTag(PatientListPickerTestTags.SelectAll),
        onClick = { onSelectAll(!state.allSelected) },
      ) {
        Text(
          text =
            if (state.allSelected) {
              stringResource(R.string.plist_picker_unselect_all)
            } else {
              stringResource(R.string.plist_picker_select_all, choices)
            },
          textAlign = TextAlign.Center,
        )
      }
    }
    if (onPickList != null) {
      OutlinedButton(
        modifier = Modifier.weight(1f).testTag(PatientListPickerTestTags.FromList),
        enabled = !state.loadingOther && !state.adding,
        onClick = onPickList,
      ) {
        if (state.loadingOther) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
          Text(text = stringResource(R.string.plist_picker_from_list), textAlign = TextAlign.Center)
        }
      }
    }
  }
}

@Composable
private fun OtherListsDialog(
  lists: List<PatientListEntry>,
  onPick: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.plist_picker_from_list_title)) },
    text = {
      if (lists.isEmpty()) {
        Text(text = stringResource(R.string.plist_picker_from_list_none))
      } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
          items(lists, key = { it.id }) { list ->
            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { onPick(list.id) }
                  .padding(vertical = 10.dp)
                  .testTag(PatientListPickerTestTags.otherList(list.id)),
            ) {
              Text(text = list.name, style = MaterialTheme.typography.bodyLarge)
              Text(
                text =
                  pluralStringResource(
                    R.plurals.plist_picker_from_list_members,
                    list.quantity,
                    list.quantity,
                  ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
    },
  )
}

@Composable
private fun CreatePatientRow(onCreate: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      modifier = Modifier.weight(1f),
      text = stringResource(R.string.plist_picker_create_prompt),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(
      modifier = Modifier.testTag(PatientListPickerTestTags.CreatePatient),
      onClick = onCreate,
    ) {
      Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
      Text(
        modifier = Modifier.padding(start = 6.dp),
        text = stringResource(R.string.plist_picker_create_action),
      )
    }
  }
}

@Composable
private fun Header(title: Int, loading: Boolean) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(title).uppercase(),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
    )
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.padding(start = 10.dp).size(14.dp),
        strokeWidth = 2.dp,
      )
    }
  }
}

@Composable
private fun CandidateRow(
  candidate: PickerCandidate,
  selected: Boolean,
  onToggle: (String) -> Unit,
) {
  val patient = candidate.patient
  val details =
    listOfNotNull(
        ageGenderLabel(patient.birthDate, patient.gender).ifBlank { null },
        patient.identifier?.takeIf { it.isNotBlank() },
      )
      .joinToString(" · ")
  val reason =
    when {
      candidate.alreadyMember -> R.string.plist_picker_already
      candidate.notSentYet -> R.string.plist_picker_not_sent
      else -> null
    }
  val enabled = candidate.selectable
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(
          if (selected) {
            OpenMrsTheme.colors.highlightContainer
          } else {
            MaterialTheme.colorScheme.surface
          },
        )
        .clickable(enabled = enabled, role = Role.Checkbox) { onToggle(patient.id) }
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .testTag(PatientListPickerTestTags.row(patient.id)),
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
      if (reason != null) {
        Text(
          text = stringResource(reason),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
  }
}
