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

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.data.database.model.FormSectionItem
import org.openmrs.android.fhir.data.database.model.GroupSessionDraftSummary
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.ui.components.AppSearchField
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

/**
 * The forms an encounter can be created from, grouped in the sections the app is configured with.
 * Sections can be collapsed and the forms searched. [sections] is null while loading.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreateEncounterScreen(
  sections: List<FormSectionItem>?,
  isLoading: Boolean,
  onFormClick: (FormItem) -> Unit,
  modifier: Modifier = Modifier,
  pendingSessions: Map<String, GroupSessionDraftSummary> = emptyMap(),
) {
  val topInset = rememberAppBarHeight()
  var query by remember { mutableStateOf("") }
  var collapsed by remember { mutableStateOf(emptySet<String>()) }
  val isSearching = query.isNotBlank()

  val visibleSections =
    remember(sections, query) {
      sections.orEmpty().mapNotNull { section ->
        val forms =
          if (query.isBlank()) {
            section.forms
          } else {
            section.forms.filter { it.name.contains(query, true) }
          }
        if (forms.isEmpty()) null else section.copy(forms = forms)
      }
    }
  val totalForms = remember(sections) { sections.orEmpty().sumOf { it.forms.size } }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("CreateEncounterScreen"),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(top = topInset + 8.dp)) {
      Text(
        modifier = Modifier.padding(horizontal = 20.dp).testTag("CreateEncounterHeadline"),
        text = stringResource(id = R.string.create_encounter_fragment_headline),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )

      // A search box only helps once there are enough forms to scroll through.
      if (totalForms > SEARCH_THRESHOLD) {
        AppSearchField(
          value = query,
          onValueChange = { query = it },
          placeholder = stringResource(id = R.string.search_forms),
          modifier = Modifier.testTag("FormSearchField"),
        )
      } else {
        Spacer(modifier = Modifier.size(8.dp))
      }

      when {
        sections == null && isLoading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
              color = OpenMrsTheme.colors.progress,
              modifier = Modifier.testTag("CreateEncounterLoading"),
            )
          }
        sections != null && totalForms == 0 ->
          MessageText(R.string.create_encounter_no_forms, Modifier.testTag("CreateEncounterEmpty"))
        isSearching && visibleSections.isEmpty() ->
          Text(
            text = stringResource(id = R.string.create_encounter_no_match, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("CreateEncounterNoMatch"),
          )
        else ->
          LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("FormSectionList"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
          ) {
            visibleSections.forEachIndexed { sectionIndex, section ->
              // While searching every match is shown, whatever the collapsed state.
              val isCollapsed = !isSearching && section.name in collapsed
              stickyHeader(key = "header-$sectionIndex", contentType = "header") {
                FormSectionHeader(
                  title = section.name,
                  count = section.forms.size,
                  isFavorites = section.isFavorites,
                  isCollapsed = isCollapsed,
                  onClick =
                    if (isSearching) {
                      null
                    } else {
                      {
                        collapsed =
                          if (section.name in collapsed) {
                            collapsed - section.name
                          } else {
                            collapsed + section.name
                          }
                      }
                    },
                )
              }
              if (!isCollapsed) {
                items(
                  section.forms,
                  key = { "$sectionIndex/${it.questionnaireId}" },
                  contentType = { "form" },
                ) { form ->
                  FormCard(
                    name = form.name,
                    pending = pendingSessions[form.questionnaireId],
                    onClick = { onFormClick(form) },
                    modifier = Modifier.animateItem(),
                  )
                }
              }
            }
          }
      }
    }
  }
}

@Composable
private fun MessageText(messageResId: Int, modifier: Modifier = Modifier) {
  Text(
    text = stringResource(id = messageResId),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = modifier.fillMaxWidth().padding(24.dp),
  )
}

/** Pinned section title with the number of forms; tapping it collapses or expands the section. */
@Composable
private fun FormSectionHeader(
  title: String,
  count: Int,
  isCollapsed: Boolean,
  onClick: (() -> Unit)?,
  isFavorites: Boolean = false,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)
        .let { base -> onClick?.let { base.clickable(onClick = it) } ?: base }
        .padding(horizontal = 4.dp, vertical = 10.dp)
        .testTag("FormSectionHeader"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      if (isFavorites) {
        Icon(
          imageVector = Icons.Filled.Star,
          contentDescription = null,
          tint = OpenMrsTheme.colors.warning,
          modifier = Modifier.padding(end = 6.dp).size(18.dp),
        )
      }
      Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(1f, fill = false),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (onClick != null) {
      Icon(
        imageVector = if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun FormCard(
  name: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  pending: GroupSessionDraftSummary? = null,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().testTag("FormItem"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border =
      if (pending == null) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
      } else {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
      },
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(40.dp).clip(CircleShape).background(OpenMrsTheme.colors.highlightContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Description,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      }
      Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(
          modifier = Modifier.testTag("FormName"),
          text = name,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        if (pending != null) PendingSessionLine(pending)
      }
      Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** "Pending session · 3 patients · 2 hours ago", in the accent color, under the form's name. */
@Composable
private fun PendingSessionLine(pending: GroupSessionDraftSummary) {
  val context = LocalContext.current
  val patients =
    pluralStringResource(
      R.plurals.pending_session_patients,
      pending.patientCount,
      pending.patientCount,
    )
  val ago =
    DateUtils.getRelativeTimeSpanString(
        pending.lastUpdated,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
      )
      .toString()
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(top = 4.dp).testTag("PendingSession"),
  ) {
    Icon(
      imageVector = Icons.Filled.History,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(16.dp),
    )
    Text(
      text = context.getString(R.string.pending_session_line, patients, ago),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(start = 6.dp),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private const val SEARCH_THRESHOLD = 8
