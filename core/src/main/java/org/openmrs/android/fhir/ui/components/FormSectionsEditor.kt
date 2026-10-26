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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.CustomFormSection
import org.openmrs.android.fhir.data.database.model.FormItem

object FormSectionsEditorTestTags {
  const val Dialog = "FormSectionsEditor"
  const val AddSection = "FormSectionsAddSection"
  const val Save = "FormSectionsSave"
  const val Cancel = "FormSectionsCancel"

  fun name(index: Int) = "FormSectionName_$index"

  fun addForms(index: Int) = "FormSectionAddForms_$index"
}

private data class EditableSection(val id: Int, val name: String, val formIds: List<String>)

/**
 * Full-screen editor for the user's own form sections: name them, put forms in them (a form can be
 * in several), order them. Nothing is kept until **Save**. [initial] is what the editor starts
 * from, the app's sections when the user has none of their own yet.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FormSectionsEditorDialog(
  forms: List<FormItem>,
  initial: List<CustomFormSection>,
  onSave: (List<CustomFormSection>) -> Unit,
  onDismiss: () -> Unit,
) {
  val formsById = remember(forms) { forms.associateBy { it.questionnaireId } }
  val sections = remember {
    mutableStateListOf<EditableSection>().apply {
      addAll(initial.mapIndexed { i, s -> EditableSection(i, s.name, s.formIds) })
    }
  }
  var nextId by remember { mutableIntStateOf(initial.size) }
  var pickerFor by remember { mutableStateOf<Int?>(null) }
  val defaultName = stringResource(R.string.form_section_default_name)

  fun update(id: Int, change: (EditableSection) -> EditableSection) {
    val index = sections.indexOfFirst { it.id == id }
    if (index >= 0) sections[index] = change(sections[index])
  }

  fun move(index: Int, by: Int) {
    val target = index + by
    if (target !in sections.indices) return
    val moved = sections.removeAt(index)
    sections.add(target, moved)
  }

  val canSave = sections.isNotEmpty() && sections.all { it.name.isNotBlank() }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize().testTag(FormSectionsEditorTestTags.Dialog),
      color = MaterialTheme.colorScheme.background,
    ) {
      Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Text(
          text = stringResource(R.string.form_sections_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        )
        Text(
          text = stringResource(R.string.form_sections_editor_hint),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
          itemsIndexed(sections, key = { _, section -> section.id }) { index, section ->
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
              Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  OutlinedTextField(
                    value = section.name,
                    onValueChange = { text -> update(section.id) { it.copy(name = text) } },
                    label = { Text(text = stringResource(R.string.form_section_name)) },
                    singleLine = true,
                    isError = section.name.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag(FormSectionsEditorTestTags.name(index)),
                  )
                  IconButton(onClick = { move(index, -1) }, enabled = index > 0) {
                    Icon(
                      imageVector = Icons.Filled.KeyboardArrowUp,
                      contentDescription = stringResource(R.string.form_section_move_up),
                    )
                  }
                  IconButton(onClick = { move(index, 1) }, enabled = index < sections.lastIndex) {
                    Icon(
                      imageVector = Icons.Filled.KeyboardArrowDown,
                      contentDescription = stringResource(R.string.form_section_move_down),
                    )
                  }
                  IconButton(onClick = { sections.removeAt(index) }) {
                    Icon(
                      imageVector = Icons.Filled.Delete,
                      contentDescription = stringResource(R.string.form_section_remove),
                      tint = MaterialTheme.colorScheme.error,
                    )
                  }
                }
                FlowRow(
                  modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  section.formIds
                    .mapNotNull { formsById[it] }
                    .forEach { form ->
                      InputChip(
                        selected = false,
                        onClick = {
                          update(section.id) {
                            it.copy(formIds = it.formIds - form.questionnaireId)
                          }
                        },
                        label = { Text(text = form.name) },
                        trailingIcon = {
                          Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                          )
                        },
                      )
                    }
                }
                TextButton(
                  onClick = { pickerFor = section.id },
                  modifier = Modifier.testTag(FormSectionsEditorTestTags.addForms(index)),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Text(
                    text = stringResource(R.string.form_section_add_forms),
                    modifier = Modifier.padding(start = 6.dp),
                  )
                }
              }
            }
          }
          item(key = "add-section") {
            OutlinedButton(
              onClick = {
                sections.add(
                  EditableSection(nextId, "$defaultName ${sections.size + 1}", emptyList()),
                )
                nextId++
              },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag(FormSectionsEditorTestTags.AddSection),
            ) {
              Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.form_section_add),
                modifier = Modifier.padding(start = 8.dp),
              )
            }
          }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).testTag(FormSectionsEditorTestTags.Cancel),
          ) {
            Text(text = stringResource(R.string.cancel))
          }
          androidx.compose.material3.Button(
            onClick = {
              onSave(
                sections
                  .map { CustomFormSection(it.name.trim(), it.formIds) }
                  .filter { it.formIds.any { id -> id in formsById } },
              )
            },
            enabled = canSave,
            modifier = Modifier.weight(1f).testTag(FormSectionsEditorTestTags.Save),
          ) {
            Text(text = stringResource(R.string.save))
          }
        }
      }
    }
  }

  pickerFor?.let { id ->
    sections
      .firstOrNull { it.id == id }
      ?.let { section ->
        FavoriteFormsDialog(
          title = section.name.ifBlank { defaultName },
          forms = forms,
          selectedIds = section.formIds,
          maxFavorites = null,
          onSave = { ids ->
            pickerFor = null
            update(id) { it.copy(formIds = ids) }
          },
          onDismiss = { pickerFor = null },
        )
      }
  }
}
