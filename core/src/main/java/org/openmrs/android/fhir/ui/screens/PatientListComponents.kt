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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

@Composable
internal fun PatientListBanner(
  text: String,
  modifier: Modifier = Modifier,
  isError: Boolean = false,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  secondLabel: String? = null,
  onSecond: (() -> Unit)? = null,
) {
  val tint = if (isError) MaterialTheme.colorScheme.error else OpenMrsTheme.colors.warning
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = tint.copy(alpha = 0.14f),
  ) {
    Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 2.dp)) {
      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (onAction != null || onSecond != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
          if (onAction != null && actionLabel != null) {
            TextButton(onClick = onAction) { Text(text = actionLabel) }
          }
          if (onSecond != null && secondLabel != null) {
            TextButton(onClick = onSecond) { Text(text = secondLabel) }
          }
        }
      }
    }
  }
}

@Composable
internal fun PatientListEditDialog(
  title: String,
  confirmLabel: String,
  initialName: String,
  initialDescription: String,
  nameError: Int?,
  onConfirm: (name: String, description: String) -> Unit,
  onDismiss: () -> Unit,
  locationName: String? = null,
  note: String? = null,
) {
  var name by rememberSaveable { mutableStateOf(initialName) }
  var description by rememberSaveable { mutableStateOf(initialDescription) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          modifier = Modifier.fillMaxWidth().testTag("PatientListNameField"),
          value = name,
          onValueChange = { name = it },
          label = { Text(text = stringResource(R.string.plist_field_name)) },
          singleLine = true,
          isError = nameError != null,
          supportingText = nameError?.let { { Text(text = stringResource(it)) } },
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        OutlinedTextField(
          modifier = Modifier.fillMaxWidth().testTag("PatientListDescriptionField"),
          value = description,
          onValueChange = { description = it },
          label = { Text(text = stringResource(R.string.plist_field_description)) },
          minLines = 2,
          maxLines = 4,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        if (!locationName.isNullOrBlank()) {
          Text(
            text = stringResource(R.string.plist_create_location, locationName),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (note != null) {
          Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("PatientListDialogConfirm"),
        onClick = { onConfirm(name, description) },
      ) {
        Text(text = confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
    },
  )
}

@Composable
internal fun PatientListDeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.plist_delete_title)) },
    text = { Text(text = stringResource(R.string.plist_delete_message, name)) },
    confirmButton = {
      TextButton(modifier = Modifier.testTag("PatientListDeleteConfirm"), onClick = onConfirm) {
        Text(
          text = stringResource(R.string.plist_action_delete),
          color = MaterialTheme.colorScheme.error,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
    },
  )
}

@Composable
internal fun pendingChangesText(count: Int): String =
  pluralStringResource(R.plurals.plists_pending, count, count)

internal fun formatPatientListDate(millis: Long?): String? =
  millis?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
