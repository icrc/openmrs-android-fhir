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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.components.PatientListItemRow
import org.openmrs.android.fhir.ui.patient.displayIdentifier
import org.openmrs.android.fhir.ui.patient.formatAgeGender
import org.openmrs.android.fhir.unidentified.UnidentifiedPatients
import org.openmrs.android.fhir.viewmodel.PatientListViewModel
import org.openmrs.android.fhir.viewmodel.toPatientItem
import timber.log.Timber

/**
 * Finds, on the server, the patient that records entered "to identify after" are about.
 *
 * With [entryId] it is about that one patient. Without it the entries are listed first (the
 * patients waiting to be identified, each with its note), then the search. [onIdentified] is called
 * with the real patient's id and the number of records moved.
 */
@Composable
fun IdentifyPatientDialog(
  entryId: String?,
  onDismiss: () -> Unit,
  onIdentified: (patientId: String, moved: Int) -> Unit = { _, _ -> },
  /**
   * The patient cannot be found on the server: register a new one for what was recorded (the id of
   * the entry). `null`: not offered.
   */
  onRegisterNew: ((entryId: String) -> Unit)? = null,
) {
  val context = LocalContext.current
  val unidentified = remember { FhirApplication.appComponent(context).unidentifiedPatients() }
  var selected by rememberSaveable { mutableStateOf(entryId) }
  var entries by remember { mutableStateOf<List<UnidentifiedPatients.Entry>?>(null) }
  var reload by remember { mutableIntStateOf(0) }
  LaunchedEffect(reload) { entries = unidentified.entries() }
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize().testTag("IdentifyPatientDialog"),
      color = MaterialTheme.colorScheme.background,
    ) {
      Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        val list = entries
        val entry = list?.firstOrNull { it.patientId == selected }
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = { if (selected != null && entryId == null) selected = null else onDismiss() },
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.close),
            )
          }
          Text(
            text =
              stringResource(
                if (entry != null) R.string.identify_title else R.string.identify_list_title,
              ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
          )
        }
        when {
          list == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          entry != null ->
            SearchStep(
              entry = entry,
              unidentified = unidentified,
              onIdentified = { patientId, moved ->
                selected = null
                reload++
                onIdentified(patientId, moved)
              },
              onRegisterNew = onRegisterNew,
            )
          list.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                text = stringResource(R.string.identify_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          else -> EntryList(list) { selected = it.patientId }
        }
      }
    }
  }
}

@Composable
private fun EntryList(
  entries: List<UnidentifiedPatients.Entry>,
  onClick: (UnidentifiedPatients.Entry) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    items(entries, key = { it.patientId }) { entry ->
      Surface(
        modifier =
          Modifier.fillMaxWidth().testTag("IdentifyEntry").clickable(role = Role.Button) {
            onClick(entry)
          },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border =
          androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.PersonSearch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
          )
          Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
              text = entry.note.ifBlank { stringResource(R.string.identify_later_no_note) },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text =
                stringResource(R.string.identify_records_summary, entry.visits, entry.encounters),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text =
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                  .format(Date(entry.createdAt)),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

private sealed interface SearchState {
  data object Idle : SearchState

  data object Loading : SearchState

  data object Failed : SearchState

  data class Found(val patients: List<PatientListViewModel.PatientItem>) : SearchState
}

@Composable
private fun SearchStep(
  entry: UnidentifiedPatients.Entry,
  unidentified: UnidentifiedPatients,
  onIdentified: (patientId: String, moved: Int) -> Unit,
  onRegisterNew: ((entryId: String) -> Unit)?,
) {
  val scope = rememberCoroutineScope()
  var warnRegister by rememberSaveable { mutableStateOf(false) }
  var query by rememberSaveable(entry.patientId) { mutableStateOf(entry.note) }
  var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
  var confirming by remember { mutableStateOf<PatientListViewModel.PatientItem?>(null) }
  var working by remember { mutableStateOf(false) }
  var failed by remember { mutableStateOf(false) }

  // The server is asked once the typing pauses.
  LaunchedEffect(query) {
    val text = query.trim()
    if (text.length < 2) {
      state = SearchState.Idle
      return@LaunchedEffect
    }
    state = SearchState.Loading
    delay(500)
    state =
      try {
        val found = withContext(Dispatchers.IO) { unidentified.searchServer(text) }
        SearchState.Found(found.mapIndexed { index, patient -> patient.toPatientItem(index + 1) })
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.w(e, "The server search failed")
        SearchState.Failed
      }
  }

  Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.secondaryContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = entry.note.ifBlank { stringResource(R.string.identify_later_no_note) },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
          text = stringResource(R.string.identify_records_summary, entry.visits, entry.encounters),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
    }
    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("IdentifySearchField"),
      label = { Text(text = stringResource(R.string.identify_search_label)) },
      placeholder = { Text(text = stringResource(R.string.identify_search_hint)) },
      leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
    )
    if (onRegisterNew != null) {
      TextButton(
        onClick = { warnRegister = true },
        modifier = Modifier.padding(top = 4.dp).testTag("IdentifyRegisterNew"),
      ) {
        Text(text = stringResource(R.string.identify_register_action))
      }
    }
    Spacer(modifier = Modifier.height(8.dp))
    when (val current = state) {
      SearchState.Idle ->
        Text(
          text = stringResource(R.string.identify_search_idle),
          style = MaterialTheme.typography.bodyMedium,
          fontStyle = FontStyle.Italic,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 8.dp),
        )
      SearchState.Loading ->
        Box(
          modifier = Modifier.fillMaxWidth().padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      SearchState.Failed ->
        Text(
          text = stringResource(R.string.live_server_search_failed),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(vertical = 8.dp),
        )
      is SearchState.Found ->
        if (current.patients.isEmpty()) {
          Text(
            text = stringResource(R.string.identify_no_results),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
          )
        } else {
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
          ) {
            items(current.patients, key = { it.resourceId }) { patient ->
              PatientListItemRow(
                name = patient.name,
                ageGenderLabel = formatAgeGender(patient),
                isSynced = true,
                identifier = patient.displayIdentifier(),
                onServer = true,
                isOpening = working && confirming?.resourceId == patient.resourceId,
                onClick = { if (!working) confirming = patient },
              )
            }
          }
        }
    }
  }

  if (warnRegister && onRegisterNew != null) {
    AlertDialog(
      onDismissRequest = { warnRegister = false },
      modifier = Modifier.testTag("IdentifyRegisterWarning"),
      icon = {
        Icon(
          imageVector = Icons.Filled.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
        )
      },
      title = { Text(text = stringResource(R.string.identify_register_warning_title)) },
      text = { Text(text = stringResource(R.string.identify_register_warning_message)) },
      confirmButton = {
        TextButton(
          modifier = Modifier.testTag("IdentifyRegisterConfirm"),
          onClick = {
            warnRegister = false
            onRegisterNew(entry.patientId)
          },
        ) {
          Text(
            text = stringResource(R.string.identify_register_warning_confirm),
            color = MaterialTheme.colorScheme.error,
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { warnRegister = false }) {
          Text(text = stringResource(R.string.identify_register_warning_cancel))
        }
      },
    )
  }

  confirming?.let { patient ->
    AlertDialog(
      onDismissRequest = { if (!working) confirming = null },
      title = { Text(text = stringResource(R.string.identify_confirm_title)) },
      text = {
        Column {
          Text(
            text =
              stringResource(
                R.string.identify_confirm_message,
                entry.note.ifBlank { stringResource(R.string.identify_later_no_note) },
                patient.name,
              ),
          )
          if (failed) {
            Text(
              text = stringResource(R.string.identify_failed),
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(top = 8.dp),
            )
          }
        }
      },
      confirmButton = {
        TextButton(
          enabled = !working,
          modifier = Modifier.testTag("IdentifyConfirm"),
          onClick = {
            working = true
            failed = false
            scope.launch {
              try {
                val moved = unidentified.identify(entry.patientId, patient.resourceId)
                confirming = null
                onIdentified(patient.resourceId, moved)
              } catch (e: CancellationException) {
                throw e
              } catch (e: Exception) {
                Timber.e(e, "Could not identify the patient")
                failed = true
              } finally {
                working = false
              }
            }
          },
        ) {
          Text(text = stringResource(R.string.identify_confirm_action))
        }
      },
      dismissButton = {
        TextButton(enabled = !working, onClick = { confirming = null }) {
          Text(text = stringResource(R.string.cancel))
        }
      },
    )
  }
}

/**
 * The note that tells the patients to identify apart: asked when one is created, editable after.
 */
@Composable
fun UnidentifiedNoteDialog(
  title: String,
  initial: String,
  confirmLabel: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var note by rememberSaveable { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("UnidentifiedNoteDialog"),
    title = { Text(text = title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.identify_note_hint),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          modifier = Modifier.fillMaxWidth().testTag("UnidentifiedNoteField"),
          label = { Text(text = stringResource(R.string.identify_note_label)) },
          minLines = 2,
          maxLines = 4,
          shape = RoundedCornerShape(12.dp),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(note) },
        modifier = Modifier.testTag("UnidentifiedNoteConfirm"),
      ) {
        Text(text = confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
    },
  )
}
