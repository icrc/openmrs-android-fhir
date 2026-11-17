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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.patientlists.PatientListEntry
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget
import org.openmrs.android.fhir.viewmodel.GroupSessionListUiState

object GroupSessionListTestTags {
  const val Screen = "GroupSessionListScreen"
  const val Empty = "GroupSessionListEmpty"

  fun row(id: String) = "GroupSessionListRow_$id"
}

@Composable
fun GroupSessionListScreen(
  state: GroupSessionListUiState,
  onChoose: (PatientListEntry) -> Unit,
  modifier: Modifier = Modifier,
) {
  val topInset = rememberAppBarHeight()
  val surfaceColor = MaterialTheme.colorScheme.background
  Box(
    modifier =
      modifier.fillMaxSize().background(surfaceColor).testTag(GroupSessionListTestTags.Screen),
  ) {
    when {
      state.loading ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      state.lists.isEmpty() ->
        Box(
          modifier =
            Modifier.fillMaxSize()
              .padding(horizontal = 32.dp)
              .testTag(GroupSessionListTestTags.Empty),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.plist_session_none),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      else ->
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(top = topInset),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          item(key = "hint") {
            Text(
              modifier =
                Modifier.padding(bottom = 4.dp).tutorialTarget(TutorialTargets.PSESSION_LIST),
              text = stringResource(R.string.plist_session_choose_hint),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          items(state.lists, key = { it.id }) { list -> ListCard(list, onChoose) }
        }
    }
  }
}

@Composable
private fun ListCard(list: PatientListEntry, onChoose: (PatientListEntry) -> Unit) {
  Surface(
    onClick = { onChoose(list) },
    modifier = Modifier.fillMaxWidth().testTag(GroupSessionListTestTags.row(list.id)),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(OpenMrsTheme.colors.highlightContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Groups,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      }
      Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(
          text = list.name,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
        )
        val subtitle =
          listOfNotNull(
              stringResource(R.string.patient_list_patient_count, list.quantity),
              list.locationName,
            )
            .joinToString(" · ")
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
