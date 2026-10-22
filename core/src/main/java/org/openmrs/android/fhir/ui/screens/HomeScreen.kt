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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget

object HomeTestTags {
  const val Screen = "HomeScreen"
  const val NewPatientCard = "HomeNewPatientCard"
  const val PatientListCard = "HomePatientListCard"
  const val CustomPatientListCard = "HomeCustomPatientListCard"
  const val GroupEncounterCard = "HomeGroupEncounterCard"
  const val SyncInfoCard = "HomeSyncInfoCard"
  const val ImportExportCard = "HomeImportExportCard"
}

private enum class HomeCardType {
  NewPatient,
  PatientList,
  CustomPatientList,
  GroupEncounter,
  SyncInfo,
  ImportExport,
}

private fun HomeCardType.tutorialKey(): String =
  when (this) {
    HomeCardType.NewPatient -> TutorialTargets.HOME_REGISTER
    HomeCardType.PatientList -> TutorialTargets.HOME_PATIENTS
    HomeCardType.CustomPatientList -> TutorialTargets.HOME_LISTS
    HomeCardType.GroupEncounter -> TutorialTargets.HOME_GROUP
    HomeCardType.SyncInfo -> TutorialTargets.HOME_SYNC
    HomeCardType.ImportExport -> TutorialTargets.HOME_IMPORT_EXPORT
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
    homeCard(
      HomeCardType.ImportExport,
      R.string.title_import_export,
      R.string.subtitle_import_export,
      R.drawable.ic_import_export,
      HomeTestTags.ImportExportCard,
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
  onImportExportClicked: () -> Unit = {},
) {
  val contentPadding = rememberActionBarContentPadding(extraTop = 8.dp)
  val rows = HomeRows

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag(HomeTestTags.Screen),
    contentPadding = contentPadding,
  ) {
    // Always the first item, even while there is nothing to say: a header that arrives later (the
    // location and the last sync are read after the screen opens) would otherwise be added above
    // the item the list is keeping in place, and push the list up under the app bar.
    item(key = "status-header") {
      if (locationName.isNotBlank() || lastSyncText.isNotBlank()) {
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
                HomeCardType.ImportExport -> onImportExportClicked
              },
          )
      }
    }
  }
}

/**
 * Where the user is working and when the data was last synced, at a glance: a plain row on the
 * screen's background, without a card around it.
 */
@Composable
private fun HomeStatusHeader(locationName: String, lastSyncText: String) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .padding(vertical = 4.dp)
        .testTag("HomeStatusHeader")
        .tutorialTarget(TutorialTargets.HOME_STATUS),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Default.LocationOn,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(20.dp),
      )
    }
    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
      if (locationName.isNotBlank()) {
        Text(
          text = locationName,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      if (lastSyncText.isNotBlank()) {
        Text(
          text = stringResource(id = R.string.last_sync) + ": " + lastSyncText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 6.dp),
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
        .tutorialTarget(config.type.tutorialKey())
        .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = RoundedCornerShape(20.dp),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          painter = painterResource(id = config.iconRes),
          colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
          contentDescription = stringResource(id = R.string.task_image),
          modifier = Modifier.size(26.dp),
        )
      }
      Column(
        modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
      ) {
        Text(
          text = stringResource(id = config.titleRes),
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          modifier = Modifier.padding(top = 2.dp),
          text = stringResource(id = config.subtitleRes),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}
