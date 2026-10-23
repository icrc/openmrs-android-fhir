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
package org.openmrs.android.fhir.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

object IdentifierTypesTestTags {
  const val Dialog = "IdentifierTypesDialog"
  const val Search = "IdentifierTypesSearch"
  const val Loading = "IdentifierTypesLoading"
  const val Empty = "IdentifierTypesEmpty"
  const val Save = "IdentifierTypesSave"

  fun row(uuid: String) = "IdentifierType_$uuid"
}

/**
 * Pick which identifier types new patients get. The required ones are always on (and cannot be
 * switched off); the choice is applied with **Save**, so a slip of the finger changes nothing.
 * [onSave] gets the chosen ids, the required ones included.
 */
@Composable
fun IdentifierTypesDialog(
  identifierTypes: List<IdentifierType>,
  selectedIds: Set<String>,
  isLoading: Boolean,
  onSave: (Set<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  val required =
    remember(identifierTypes) { identifierTypes.filter { it.required }.map { it.uuid } }
  var selection by remember(selectedIds, required) { mutableStateOf(selectedIds + required) }
  var query by rememberSaveable { mutableStateOf("") }
  val visible =
    remember(identifierTypes, query) {
      if (query.isBlank()) {
        identifierTypes
      } else {
        identifierTypes.filter { it.display?.contains(query, ignoreCase = true) == true }
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag(IdentifierTypesTestTags.Dialog),
    shape = RoundedCornerShape(24.dp),
    title = { Text(text = stringResource(R.string.select_identifier_types)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.identifier_types_hint),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (identifierTypes.size > SEARCH_THRESHOLD) {
          AppSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.search_identifier_types),
            modifier = Modifier.testTag(IdentifierTypesTestTags.Search),
          )
        }
        when {
          identifierTypes.isEmpty() && isLoading ->
            Box(
              modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(
                color = OpenMrsTheme.colors.progress,
                modifier = Modifier.testTag(IdentifierTypesTestTags.Loading),
              )
            }
          identifierTypes.isEmpty() ->
            Text(
              text = stringResource(R.string.identifier_types_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier =
                Modifier.fillMaxWidth()
                  .padding(vertical = 24.dp)
                  .testTag(IdentifierTypesTestTags.Empty),
            )
          else ->
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
              items(visible, key = { it.uuid }) { type ->
                val checked = type.uuid in selection
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .alpha(if (type.required) 0.8f else 1f)
                      .clickable(enabled = !type.required, role = Role.Checkbox) {
                        selection = if (checked) selection - type.uuid else selection + type.uuid
                      }
                      .padding(vertical = 2.dp)
                      .testTag(IdentifierTypesTestTags.row(type.uuid)),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Checkbox(checked = checked, onCheckedChange = null, enabled = !type.required)
                  Text(
                    text = type.display.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp),
                  )
                  if (type.required) {
                    Text(
                      text = stringResource(R.string.identifier_type_required),
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier =
                        Modifier.clip(RoundedCornerShape(50))
                          .background(MaterialTheme.colorScheme.secondaryContainer)
                          .padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                  }
                }
                HorizontalDivider(
                  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
              }
            }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onSave(selection) },
        enabled = identifierTypes.isNotEmpty(),
        modifier = Modifier.testTag(IdentifierTypesTestTags.Save),
      ) {
        Text(text = stringResource(R.string.save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
    },
  )
}

private const val SEARCH_THRESHOLD = 8
