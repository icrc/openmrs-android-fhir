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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.FormItem

object FavoriteFormsTestTags {
  const val Dialog = "FavoriteFormsDialog"
  const val Search = "FavoriteFormsSearch"
  const val Save = "FavoriteFormsSave"
  const val Count = "FavoriteFormsCount"

  fun row(questionnaireId: String) = "FavoriteForm_$questionnaireId"
}

/**
 * Pick the favorite forms, at most [maxFavorites]: every available form with a checkbox. Once the
 * limit is reached the unchecked ones are dimmed and cannot be picked until another is unchecked.
 * [onSave] gets the ids in the order they were picked.
 */
@Composable
fun FavoriteFormsDialog(
  forms: List<FormItem>,
  selectedIds: List<String>,
  maxFavorites: Int,
  onSave: (List<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  var selection by remember { mutableStateOf(selectedIds) }
  var query by rememberSaveable { mutableStateOf("") }
  val visible =
    remember(forms, query) {
      if (query.isBlank()) forms else forms.filter { it.name.contains(query, ignoreCase = true) }
    }
  val limitReached = selection.size >= maxFavorites

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag(FavoriteFormsTestTags.Dialog),
    shape = RoundedCornerShape(24.dp),
    title = { Text(text = stringResource(R.string.favorite_forms)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.favorite_forms_count, selection.size, maxFavorites),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color =
            if (limitReached) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
          modifier = Modifier.testTag(FavoriteFormsTestTags.Count),
        )
        if (forms.size > SEARCH_THRESHOLD) {
          AppSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.search_forms),
            modifier = Modifier.testTag(FavoriteFormsTestTags.Search),
          )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
          items(visible, key = { it.questionnaireId }) { form ->
            val checked = form.questionnaireId in selection
            val enabled = checked || !limitReached
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .alpha(if (enabled) 1f else 0.45f)
                  .clickable(enabled = enabled) {
                    selection =
                      if (checked) {
                        selection - form.questionnaireId
                      } else {
                        selection + form.questionnaireId
                      }
                  }
                  .padding(vertical = 2.dp)
                  .testTag(FavoriteFormsTestTags.row(form.questionnaireId)),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
              Text(
                text = form.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(start = 12.dp, end = 4.dp),
              )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onSave(selection) },
        modifier = Modifier.testTag(FavoriteFormsTestTags.Save),
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
