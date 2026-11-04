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
package org.openmrs.android.fhir.forms.diagnosis

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.views.QuestionnaireViewItem
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemAndroidViewHolderDelegate
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemAndroidViewHolderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

object DiagnosisPickerTestTags {
  const val Field = "DiagnosisPickerField"
  const val Open = "DiagnosisPickerOpen"
  const val Search = "DiagnosisPickerSearch"
  const val Done = "DiagnosisPickerDone"

  fun row(code: String) = "DiagnosisPickerRow_$code"
}

private const val SEARCH_PAUSE_MILLIS = 150L

/**
 * The widget of a [isDiagnosisPicker] question: the chosen diagnoses as chips, and a button that
 * opens a search over the whole list where any number can be ticked (one, when the question does
 * not repeat, as the question for the primary diagnosis). Without a rank fixed by the question, the
 * first diagnosis is the primary one and a hint says so. The answers are the codings of the chosen
 * diagnoses (one answer each), so the form library saves, validates and shows them in the review
 * page like any other multiple-choice question.
 */
internal object DiagnosisPickerFactory :
  QuestionnaireItemAndroidViewHolderFactory(R.layout.questionnaire_diagnosis_picker) {
  override fun getQuestionnaireItemViewHolderDelegate() = DiagnosisPickerDelegate()
}

internal class DiagnosisPickerDelegate : QuestionnaireItemAndroidViewHolderDelegate {
  override lateinit var questionnaireViewItem: QuestionnaireViewItem

  // Every item the form library hands over is shown, even when it looks equal to the one before:
  // the library compares the answers shallowly (and shares the response item), so an update that
  // replaces some diagnoses with others would otherwise not be redrawn.
  private val current = mutableStateOf<QuestionnaireViewItem?>(null, neverEqualPolicy())
  private val readOnly = mutableStateOf(false)

  override fun init(itemView: View) {
    itemView.findViewById<ComposeView>(R.id.diagnosis_picker).setContent {
      OpenMrsTheme { current.value?.let { DiagnosisPickerField(it, readOnly.value) } }
    }
  }

  override fun bind(questionnaireViewItem: QuestionnaireViewItem) {
    this.questionnaireViewItem = questionnaireViewItem
    current.value = questionnaireViewItem
  }

  override fun setReadOnly(isReadOnly: Boolean) {
    readOnly.value = isReadOnly
  }
}

private fun QuestionnaireViewItem.pickedCodings(): List<Coding> =
  answers.mapNotNull { it.value as? Coding }.filter { it.hasCode() }

/** "J20.9 · Acute bronchitis": what a chip and the review of an answer say. */
private fun Diagnosis.label(languageTag: String): String =
  nameIn(languageTag).let { if (icd.isEmpty()) it else "$icdLabel · $it" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiagnosisPickerField(item: QuestionnaireViewItem, readOnly: Boolean) {
  val context = LocalContext.current
  val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
  val scope = rememberCoroutineScope()
  val catalogId = item.questionnaireItem.diagnosisCatalogId()
  var catalog by remember(catalogId) { mutableStateOf(DiagnosisCatalogs.cached(catalogId)) }
  var loaded by remember(catalogId) { mutableStateOf(catalog != null) }
  LaunchedEffect(catalogId) {
    if (catalog == null) catalog = DiagnosisCatalogs.get(context, catalogId)
    loaded = true
  }
  var searching by remember { mutableStateOf(false) }
  val picked = item.pickedCodings()
  val error = (item.validationResult as? Invalid)?.getSingleStringValidationMessage()

  fun write(codings: List<Coding>) {
    scope.launch {
      if (codings.isEmpty()) {
        item.clearAnswer()
      } else {
        item.setAnswer(
          *codings
            .map { QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent().setValue(it) }
            .toTypedArray(),
        )
      }
    }
  }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .testTag(DiagnosisPickerTestTags.Field),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val title = item.questionText?.toString().orEmpty()
    if (title.isNotBlank()) {
      Text(
        text = if (item.questionnaireItem.required) "$title *" else title,
        style = MaterialTheme.typography.titleMedium,
      )
    }
    if (picked.isEmpty()) {
      Text(
        text =
          stringResource(
            if (item.questionnaireItem.repeats) {
              R.string.diagnosis_picker_none
            } else {
              R.string.diagnosis_picker_none_single
            },
          ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        picked.forEach { coding ->
          val label =
            catalog?.find(coding.code)?.label(languageTag) ?: coding.display ?: coding.code
          InputChip(
            selected = true,
            enabled = !readOnly,
            onClick = { write(picked.filter { it.code != coding.code }) },
            label = { Text(text = label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            trailingIcon = {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.diagnosis_picker_remove, label),
              )
            },
          )
        }
      }
      if (item.questionnaireItem.diagnosisRank() == null) {
        Text(
          text = stringResource(R.string.diagnosis_picker_rank_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (!readOnly) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = { searching = true },
          enabled = catalog != null,
          modifier = Modifier.heightIn(min = 48.dp).testTag(DiagnosisPickerTestTags.Open),
        ) {
          Icon(imageVector = Icons.Default.Search, contentDescription = null)
          Text(
            text =
              stringResource(
                if (item.questionnaireItem.repeats) {
                  R.string.diagnosis_picker_add
                } else {
                  R.string.diagnosis_picker_add_single
                },
              ),
            modifier = Modifier.padding(start = 8.dp),
          )
        }
        if (!loaded) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
          Text(
            text = stringResource(R.string.diagnosis_picker_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (loaded && catalog == null) {
        Text(
          text = stringResource(R.string.diagnosis_picker_unavailable),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
    if (error != null) {
      Text(
        text = error,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }

  val list = catalog
  if (searching && list != null) {
    DiagnosisSearchDialog(
      catalog = list,
      initial = picked,
      recent = remember(list) { RecentDiagnoses.get(context).mapNotNull { list.find(it) } },
      multiple = item.questionnaireItem.repeats,
      onConfirm = {
        searching = false
        write(it)
      },
      onDismiss = { searching = false },
    )
  }
}

/**
 * The search over [catalog]: the text narrows the list as it is typed, a tap ticks or unticks a
 * diagnosis, and "Done" gives back everything that is ticked (those from earlier searches too).
 * When not [multiple], ticking a diagnosis unticks the one before. With nothing typed, what is
 * ticked is shown and, under it, the [recent] diagnoses.
 */
@Composable
internal fun DiagnosisSearchDialog(
  catalog: DiagnosisCatalog,
  initial: List<Coding>,
  recent: List<Diagnosis> = emptyList(),
  multiple: Boolean = true,
  onConfirm: (List<Coding>) -> Unit,
  onDismiss: () -> Unit,
) {
  val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
  var query by rememberSaveable { mutableStateOf("") }
  val chosen: SnapshotStateList<Coding> = remember { initial.toMutableStateList() }
  val chosenCodes by remember { derivedStateOf { chosen.mapTo(HashSet()) { it.code } } }
  val recentCodes = remember(recent) { recent.mapTo(HashSet()) { it.code } }
  var result by remember { mutableStateOf(DiagnosisSearchResult(emptyList(), 0)) }
  LaunchedEffect(query) {
    // Nothing typed: nothing to search (what is ticked is shown). A pause between key presses, and
    // the search before is dropped: only the last text is searched.
    if (query.isBlank()) {
      result = DiagnosisSearchResult(emptyList(), 0)
      return@LaunchedEffect
    }
    delay(SEARCH_PAUSE_MILLIS)
    result = withContext(Dispatchers.Default) { catalog.search(query) }
  }

  fun toggle(diagnosis: Diagnosis) {
    val existing = chosen.indexOfFirst { it.code == diagnosis.code }
    if (existing >= 0) {
      chosen.removeAt(existing)
    } else {
      if (!multiple) chosen.clear()
      chosen.add(diagnosis.toAnswerCoding(languageTag))
    }
  }

  // With nothing typed, what is already ticked.
  val shown by remember {
    derivedStateOf {
      if (query.isBlank()) {
        // What is ticked and is not one of the recent ones (those are listed below, ticked or not).
        chosen.mapNotNull { catalog.find(it.code) }.filter { it.code !in recentCodes }
      } else {
        result.items
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    // Edge to edge, so the keyboard pushes the buttons up (imePadding) instead of covering them.
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Surface(
      // The system bars and the keyboard are outside of the dialog (it is edge to edge).
      modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
    ) {
      val divider = MaterialTheme.colorScheme.outlineVariant
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text =
            stringResource(
              if (multiple) {
                R.string.diagnosis_picker_title
              } else {
                R.string.diagnosis_picker_title_single
              },
            ),
          style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          modifier = Modifier.fillMaxWidth().testTag(DiagnosisPickerTestTags.Search),
          singleLine = true,
          placeholder = { Text(stringResource(R.string.diagnosis_picker_search_hint)) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (query.isNotEmpty()) {
              IconButton(onClick = { query = "" }) {
                Icon(
                  Icons.Default.Close,
                  contentDescription = stringResource(R.string.diagnosis_picker_clear),
                )
              }
            }
          },
        )
        Text(
          text =
            when {
              query.isBlank() ->
                stringResource(R.string.diagnosis_picker_type_to_search, catalog.size)
              result.total > result.items.size ->
                stringResource(R.string.diagnosis_picker_more, result.items.size, result.total)
              result.total == 0 -> stringResource(R.string.diagnosis_picker_no_match)
              else -> stringResource(R.string.diagnosis_picker_matches, result.total)
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
          items(shown, key = { it.uuid }, contentType = { "diagnosis" }) { diagnosis ->
            DiagnosisRow(diagnosis, diagnosis.code in chosenCodes, languageTag, divider) {
              toggle(diagnosis)
            }
          }
          if (query.isBlank() && recent.isNotEmpty()) {
            item(key = "recent-header", contentType = "header") {
              Text(
                text = stringResource(R.string.diagnosis_picker_recent),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
              )
            }
            items(recent, key = { "recent-${it.uuid}" }, contentType = { "diagnosis" }) { diagnosis,
              ->
              DiagnosisRow(diagnosis, diagnosis.code in chosenCodes, languageTag, divider) {
                toggle(diagnosis)
              }
            }
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.End,
        ) {
          Text(
            text = stringResource(R.string.diagnosis_picker_selected_count, chosen.size),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
          Button(
            onClick = { onConfirm(chosen.toList()) },
            modifier = Modifier.testTag(DiagnosisPickerTestTags.Done),
          ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
              text = stringResource(R.string.diagnosis_picker_done),
              modifier = Modifier.padding(start = 8.dp),
            )
          }
        }
      }
    }
  }
}

/** A diagnosis of the list: a checkbox, its name in the language of the user and its codes. */
@Composable
private fun DiagnosisRow(
  diagnosis: Diagnosis,
  checked: Boolean,
  languageTag: String,
  divider: Color,
  onToggle: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 56.dp)
        .toggleable(
          value = checked,
          role = Role.Checkbox,
          onValueChange = { onToggle() },
        )
        .drawBehind {
          drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 1f)
        }
        .padding(vertical = 4.dp)
        .testTag(DiagnosisPickerTestTags.row(diagnosis.code)),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Checkbox(checked = checked, onCheckedChange = null)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = diagnosis.nameIn(languageTag),
        style = MaterialTheme.typography.bodyLarge,
      )
      Text(
        text = diagnosis.icd.take(3).joinToString(", ").ifEmpty { "—" },
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
