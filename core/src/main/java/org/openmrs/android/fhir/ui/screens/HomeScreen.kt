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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.rememberActionBarContentPadding

object HomeTestTags {
  const val Screen = "HomeScreen"
  const val NewPatientCard = "HomeNewPatientCard"
  const val PatientListCard = "HomePatientListCard"
  const val CustomPatientListCard = "HomeCustomPatientListCard"
  const val GroupEncounterCard = "HomeGroupEncounterCard"
  const val SyncInfoCard = "HomeSyncInfoCard"
}

private enum class HomeCardType {
  NewPatient,
  PatientList,
  CustomPatientList,
  GroupEncounter,
  SyncInfo,
}

private data class HomeCardConfig(
  val type: HomeCardType,
  val titleRes: Int,
  val subtitleRes: Int,
  val iconRes: Int,
  val testTag: String,
)

private sealed class HomeRow {
  data class SectionHeader(val titleRes: Int) : HomeRow()

  data class Card(val config: HomeCardConfig) : HomeRow()
}

private fun homeCard(
  type: HomeCardType,
  @StringRes titleRes: Int,
  @StringRes subtitleRes: Int,
  @DrawableRes iconRes: Int,
  testTag: String,
) = HomeRow.Card(HomeCardConfig(type, titleRes, subtitleRes, iconRes, testTag))

private val HomeRows: List<HomeRow> =
  listOf(
    HomeRow.SectionHeader(R.string.section_patient_management),
    homeCard(
      HomeCardType.NewPatient,
      R.string.title_add_patient,
      R.string.subtitle_add_patient,
      R.drawable.ic_home_new_patient,
      HomeTestTags.NewPatientCard,
    ),
    homeCard(
      HomeCardType.PatientList,
      R.string.title_search_patient,
      R.string.subtitle_search_patient,
      R.drawable.ic_home_search,
      HomeTestTags.PatientListCard,
    ),
    homeCard(
      HomeCardType.CustomPatientList,
      R.string.title_custom_patient_list,
      R.string.subtitle_custom_patient_list,
      R.drawable.cloud_sync_24px,
      HomeTestTags.CustomPatientListCard,
    ),
    HomeRow.SectionHeader(R.string.section_encounters),
    homeCard(
      HomeCardType.GroupEncounter,
      R.string.title_group_encounter,
      R.string.subtitle_group_encounter,
      R.drawable.baseline_group_24,
      HomeTestTags.GroupEncounterCard,
    ),
    HomeRow.SectionHeader(R.string.section_system),
    homeCard(
      HomeCardType.SyncInfo,
      R.string.nav_sync,
      R.string.subtitle_sync_center,
      R.drawable.ic_baseline_sync_24,
      HomeTestTags.SyncInfoCard,
    ),
  )

@Composable
fun HomeScreen(
  onNewPatientClicked: () -> Unit,
  onPatientListClicked: () -> Unit,
  onCustomPatientListClicked: () -> Unit,
  onGroupEncounterClicked: () -> Unit,
  onSyncInfoClicked: () -> Unit,
  locationName: String = "",
  lastSyncText: String = "",
) {
  val contentPadding = rememberActionBarContentPadding(extraTop = 8.dp)
  val rows = HomeRows

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag(HomeTestTags.Screen),
    contentPadding = contentPadding,
  ) {
    if (locationName.isNotBlank() || lastSyncText.isNotBlank()) {
      item(key = "status-header") {
        HomeStatusHeader(locationName = locationName, lastSyncText = lastSyncText)
      }
    }
    items(rows, key = { row -> row.hashCode() }) { row ->
      when (row) {
        is HomeRow.SectionHeader -> HomeSectionHeader(titleRes = row.titleRes)
        is HomeRow.Card ->
          HomeDashboardCard(
            config = row.config,
            onClick =
              when (row.config.type) {
                HomeCardType.NewPatient -> onNewPatientClicked
                HomeCardType.PatientList -> onPatientListClicked
                HomeCardType.CustomPatientList -> onCustomPatientListClicked
                HomeCardType.GroupEncounter -> onGroupEncounterClicked
                HomeCardType.SyncInfo -> onSyncInfoClicked
              },
          )
      }
    }
  }
}

/** Where the user is working and when the data was last synced, at a glance. */
@Composable
private fun HomeStatusHeader(locationName: String, lastSyncText: String) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("HomeStatusHeader"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    shape = RoundedCornerShape(16.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      if (locationName.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = locationName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }
      if (lastSyncText.isNotBlank()) {
        Text(
          modifier = Modifier.padding(top = 4.dp),
          text = stringResource(id = R.string.last_sync) + ": " + lastSyncText,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
    }
  }
}

@Composable
private fun HomeSectionHeader(titleRes: Int) {
  Text(
    text = stringResource(id = titleRes),
    color = MaterialTheme.colorScheme.primary,
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
  )
}

@Composable
private fun HomeDashboardCard(
  config: HomeCardConfig,
  onClick: () -> Unit,
) {
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .padding(vertical = 6.dp)
        .testTag(config.testTag)
        .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    shape = RoundedCornerShape(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          painter = painterResource(id = config.iconRes),
          colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
          contentDescription = stringResource(id = R.string.task_image),
          modifier = Modifier.size(24.dp),
        )
      }
      Column(
        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
      ) {
        Text(
          text = stringResource(id = config.titleRes),
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 2,
        )
        Text(
          text = stringResource(id = config.subtitleRes),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 2,
        )
      }
      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
