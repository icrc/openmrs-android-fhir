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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.UnsyncedResource
import org.openmrs.android.fhir.ui.components.UnsyncedEncounterRow
import org.openmrs.android.fhir.ui.components.UnsyncedObservationRow
import org.openmrs.android.fhir.ui.components.UnsyncedPatientRow
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

object UnsyncedResourcesTestTags {
  const val Screen = "UnsyncedResourcesScreen"
  const val List = "UnsyncedResourcesList"
  const val EmptyState = "UnsyncedResourcesEmptyState"
  const val EmptyStateMessage = "UnsyncedResourcesEmptyStateMessage"
  const val DeleteAllButton = "UnsyncedResourcesDeleteAll"
  const val DownloadAllButton = "UnsyncedResourcesDownloadAll"
  const val LoadingIndicator = "UnsyncedResourcesLoading"
}

/**
 * The changes that have not been uploaded yet: patients, with their encounters and observations
 * folded underneath. Each can be downloaded (to send it for support) or deleted, and the two
 * actions at the top apply to everything. The screen around it provides the padding.
 */
@Composable
fun UnsyncedResourcesScreen(
  resources: List<UnsyncedResource>,
  isLoading: Boolean,
  onTogglePatientExpand: (String) -> Unit,
  onToggleEncounterExpand: (String) -> Unit,
  onDeleteResource: (UnsyncedResource) -> Unit,
  onDownloadResource: (UnsyncedResource) -> Unit,
  onDeleteAll: () -> Unit,
  onDownloadAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var pendingDeleteResource by remember { mutableStateOf<UnsyncedResource?>(null) }
  var showDeleteAllDialog by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize().testTag(UnsyncedResourcesTestTags.Screen)) {
    when {
      isLoading && resources.isEmpty() -> Unit
      resources.isEmpty() ->
        Column(
          modifier = Modifier.fillMaxSize().testTag(UnsyncedResourcesTestTags.EmptyState),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            modifier = Modifier.size(72.dp),
            painter = painterResource(id = R.drawable.ic_check_circle),
            contentDescription = stringResource(id = R.string.empty_state_icon),
            tint = OpenMrsTheme.colors.success,
          )
          Text(
            modifier =
              Modifier.padding(top = 8.dp).testTag(UnsyncedResourcesTestTags.EmptyStateMessage),
            text = stringResource(id = R.string.no_unsynced_resources_available),
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )
        }
      else ->
        Column(modifier = Modifier.fillMaxSize()) {
          if (isLoading) {
            LinearProgressIndicator(
              modifier = Modifier.fillMaxWidth(),
              color = OpenMrsTheme.colors.progress,
            )
          }
          LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(UnsyncedResourcesTestTags.List),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
          ) {
            item(key = "bulk-actions", contentType = "actions") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
              ) {
                TextButton(
                  modifier = Modifier.testTag(UnsyncedResourcesTestTags.DownloadAllButton),
                  onClick = onDownloadAll,
                ) {
                  Icon(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(id = R.drawable.ic_download),
                    contentDescription = null,
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(text = stringResource(id = R.string.download_all))
                }
                TextButton(
                  modifier = Modifier.testTag(UnsyncedResourcesTestTags.DeleteAllButton),
                  onClick = { showDeleteAllDialog = true },
                ) {
                  Icon(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null,
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(text = stringResource(id = R.string.delete_all))
                }
              }
            }
            items(resources, key = { it.listKey() }, contentType = { it::class }) { resource ->
              when (resource) {
                is UnsyncedResource.PatientItem ->
                  UnsyncedPatientRow(
                    name = resource.patient.name,
                    onToggleExpand = { onTogglePatientExpand(resource.patient.logicalId) },
                    onDownload = { onDownloadResource(resource) },
                    onDelete = { pendingDeleteResource = resource },
                    showExpand = resource.patient.encounters.isNotEmpty(),
                    isSynced = resource.patient.isSynced,
                    isExpanded = resource.patient.isExpanded,
                  )
                is UnsyncedResource.EncounterItem ->
                  UnsyncedEncounterRow(
                    title = resource.encounter.title,
                    hasObservations = resource.encounter.observations.isNotEmpty(),
                    onToggleExpand = { onToggleEncounterExpand(resource.encounter.logicalId) },
                    onDownload = { onDownloadResource(resource) },
                    onDelete = { pendingDeleteResource = resource },
                    isSynced = resource.encounter.isSynced,
                    isExpanded = resource.encounter.isExpanded,
                  )
                is UnsyncedResource.ObservationItem ->
                  UnsyncedObservationRow(
                    title = resource.observation.title,
                    onDownload = { onDownloadResource(resource) },
                    onDelete = { pendingDeleteResource = resource },
                    isSynced = resource.observation.isSynced,
                  )
              }
            }
          }
        }
    }

    if (isLoading && resources.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
          modifier = Modifier.testTag(UnsyncedResourcesTestTags.LoadingIndicator),
          color = OpenMrsTheme.colors.progress,
        )
      }
    }
  }

  pendingDeleteResource?.let { resource ->
    val resourceLabel =
      when (resource) {
        is UnsyncedResource.PatientItem -> stringResource(id = R.string.resource_patient_label)
        is UnsyncedResource.EncounterItem -> stringResource(id = R.string.resource_encounter_label)
        is UnsyncedResource.ObservationItem ->
          stringResource(id = R.string.resource_observation_label)
      }
    AlertDialog(
      onDismissRequest = { pendingDeleteResource = null },
      title = { Text(text = stringResource(id = R.string.delete_resource_title)) },
      text = { Text(text = stringResource(id = R.string.delete_resource_message, resourceLabel)) },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteResource(resource)
            pendingDeleteResource = null
          },
        ) {
          Text(text = stringResource(id = R.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteResource = null }) {
          Text(text = stringResource(id = R.string.cancel))
        }
      },
    )
  }

  if (showDeleteAllDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteAllDialog = false },
      title = { Text(text = stringResource(id = R.string.delete_all_title)) },
      text = { Text(text = stringResource(id = R.string.delete_all_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteAll()
            showDeleteAllDialog = false
          },
        ) {
          Text(text = stringResource(id = R.string.delete_all))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteAllDialog = false }) {
          Text(text = stringResource(id = R.string.cancel))
        }
      },
    )
  }
}

/** Stable identity of a row, so folding and unfolding keeps the list where it is. */
private fun UnsyncedResource.listKey(): String =
  when (this) {
    is UnsyncedResource.PatientItem -> "patient-${patient.logicalId}"
    is UnsyncedResource.EncounterItem -> "encounter-${encounter.logicalId}"
    is UnsyncedResource.ObservationItem -> "observation-${observation.logicalId}"
  }
