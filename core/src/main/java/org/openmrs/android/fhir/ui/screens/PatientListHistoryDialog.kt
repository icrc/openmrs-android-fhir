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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.PatientListEvent
import org.openmrs.android.fhir.data.database.model.PatientListEventAction

object PatientListHistoryTestTags {
  const val Dialog = "PatientListHistoryDialog"
}

@Composable
fun PatientListHistoryDialog(events: List<PatientListEvent>, onDismiss: () -> Unit) {
  AlertDialog(
    modifier = Modifier.testTag(PatientListHistoryTestTags.Dialog),
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.plist_history_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (events.isEmpty()) {
          Text(text = stringResource(R.string.plist_history_empty))
        } else {
          LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            items(events, key = { it.id }) { event ->
              Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(text = eventText(event), style = MaterialTheme.typography.bodyMedium)
                Text(
                  text =
                    stringResource(
                      R.string.plist_history_by_on,
                      event.user.ifBlank { stringResource(R.string.plist_history_guest) },
                      DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(event.at)),
                    ),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
          }
          Text(
            text = stringResource(R.string.plist_history_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.close)) }
    },
  )
}

@Composable
private fun eventText(event: PatientListEvent): String =
  when (event.action) {
    PatientListEventAction.LIST_CREATED ->
      stringResource(R.string.plist_history_created, event.detail.orEmpty())
    PatientListEventAction.LIST_RENAMED ->
      stringResource(R.string.plist_history_renamed, event.detail.orEmpty())
    PatientListEventAction.LIST_DELETED -> stringResource(R.string.plist_history_deleted)
    PatientListEventAction.MEMBER_ADDED ->
      stringResource(R.string.plist_history_added, event.patientName.orEmpty())
    PatientListEventAction.MEMBER_REMOVED ->
      stringResource(R.string.plist_history_removed, event.patientName.orEmpty())
    else -> event.action
  }
