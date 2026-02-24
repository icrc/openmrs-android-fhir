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

import android.util.TypedValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.UnsyncedResource
import org.openmrs.android.fhir.ui.components.UnsyncedEncounterRow
import org.openmrs.android.fhir.ui.components.UnsyncedObservationRow
import org.openmrs.android.fhir.ui.components.UnsyncedPatientRow

object UnsyncedResourcesTestTags {
  const val Screen = "UnsyncedResourcesScreen"
  const val List = "UnsyncedResourcesList"
  const val EmptyState = "UnsyncedResourcesEmptyState"
  const val EmptyStateMessage = "UnsyncedResourcesEmptyStateMessage"
  const val DeleteAllButton = "UnsyncedResourcesDeleteAll"
  const val DownloadAllButton = "UnsyncedResourcesDownloadAll"
  const val LoadingIndicator = "UnsyncedResourcesLoading"
}

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
) {
  var pendingDeleteResource by remember { mutableStateOf<UnsyncedResource?>(null) }
  var showDeleteAllDialog by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val actionBarHeightPx = remember {
    val typedValue = TypedValue()
    if (context.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typedValue, true)) {
      TypedValue.complexToDimensionPixelSize(typedValue.data, context.resources.displayMetrics)
    } else {
      0
    }
  }
  val actionBarHeightDp = with(LocalDensity.current) { actionBarHeightPx.toDp() }
  val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + actionBarHeightDp
  val contentPadding =
    PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 16.dp + topInset)

  Box(
    modifier =
      Modifier.fillMaxSize().padding(contentPadding).testTag(UnsyncedResourcesTestTags.Screen),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      when {
        isLoading && resources.isEmpty() -> Unit
        resources.isEmpty() -> {
          Box(
            modifier =
              Modifier.weight(1f).fillMaxWidth().testTag(UnsyncedResourcesTestTags.EmptyState),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Image(
                modifier = Modifier.size(80.dp),
                painter = painterResource(id = R.drawable.ic_baseline_sync_24),
                colorFilter =
                  ColorFilter.tint(colorResource(id = R.color.dashboard_cardview_textcolor)),
                contentDescription = stringResource(id = R.string.empty_state_icon),
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                modifier = Modifier.testTag(UnsyncedResourcesTestTags.EmptyStateMessage),
                text = stringResource(id = R.string.no_unsynced_resources_available),
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                color = colorResource(id = R.color.black),
              )
            }
          }
        }
        else -> {
          if (isLoading) {
            LinearProgressIndicator(
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
              color = Color(0xFF4285F4),
            )
          }
          LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(UnsyncedResourcesTestTags.List),
            contentPadding = PaddingValues(vertical = 16.dp),
          ) {
            items(resources) { resource ->
              when (resource) {
                is UnsyncedResource.PatientItem ->
                  UnsyncedPatientRow(
                    name = resource.patient.name,
                    onToggleExpand = { onTogglePatientExpand(resource.patient.logicalId) },
                    onDownload = { onDownloadResource(resource) },
                    onDelete = { pendingDeleteResource = resource },
                    showExpand = resource.patient.encounters.isNotEmpty(),
                    isSynced = resource.patient.isSynced,
                  )
                is UnsyncedResource.EncounterItem ->
                  UnsyncedEncounterRow(
                    title = resource.encounter.title,
                    hasObservations = resource.encounter.observations.isNotEmpty(),
                    onToggleExpand = { onToggleEncounterExpand(resource.encounter.logicalId) },
                    onDownload = { onDownloadResource(resource) },
                    onDelete = { pendingDeleteResource = resource },
                    isSynced = resource.encounter.isSynced,
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

      Spacer(modifier = Modifier.height(16.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Button(
          modifier = Modifier.weight(1f).testTag(UnsyncedResourcesTestTags.DeleteAllButton),
          onClick = { showDeleteAllDialog = true },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = colorResource(id = R.color.dashboard_cardview_textcolor),
              contentColor = Color.White,
            ),
        ) {
          Text(text = stringResource(id = R.string.delete_all), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Button(
          modifier = Modifier.weight(1f).testTag(UnsyncedResourcesTestTags.DownloadAllButton),
          onClick = onDownloadAll,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = colorResource(id = R.color.dashboard_cardview_textcolor),
              contentColor = Color.White,
            ),
        ) {
          Text(text = stringResource(id = R.string.download_all), fontSize = 12.sp)
        }
      }
    }

    if (isLoading && resources.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
          modifier = Modifier.testTag(UnsyncedResourcesTestTags.LoadingIndicator),
          color = Color(0xFF4285F4),
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
