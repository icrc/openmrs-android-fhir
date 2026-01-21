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

import android.util.TypedValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmrs.android.fhir.R

object HomeTestTags {
  const val Screen = "HomeScreen"
  const val NewPatientCard = "HomeNewPatientCard"
  const val PatientListCard = "HomePatientListCard"
  const val CustomPatientListCard = "HomeCustomPatientListCard"
  const val GroupEncounterCard = "HomeGroupEncounterCard"
  const val SyncInfoCard = "HomeSyncInfoCard"
  const val UnsyncedResourcesCard = "HomeUnsyncedResourcesCard"
}

private enum class HomeCardType {
  NewPatient,
  PatientList,
  CustomPatientList,
  GroupEncounter,
  SyncInfo,
  UnsyncedResources,
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

@Composable
fun HomeScreen(
  onNewPatientClicked: () -> Unit,
  onPatientListClicked: () -> Unit,
  onCustomPatientListClicked: () -> Unit,
  onGroupEncounterClicked: () -> Unit,
  onSyncInfoClicked: () -> Unit,
  onUnsyncedResourcesClicked: () -> Unit,
) {
  val context = LocalContext.current
  val actionBarHeightPx = remember {
    val typedValue = TypedValue()
    if (context.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typedValue, true)) {
      TypedValue.complexToDimensionPixelSize(typedValue.data, context.resources.displayMetrics)
    } else {
      0
    }
  }
  val actionBarHeightDp = with(LocalDensity.current) { actionBarHeightPx.toDp() }
  val topInset =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + actionBarHeightDp + 8.dp
  val contentPadding =
    PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 16.dp + topInset)
  val rows =
    listOf(
      HomeRow.SectionHeader(R.string.section_patient_management),
      HomeRow.Card(
        HomeCardConfig(
          type = HomeCardType.NewPatient,
          titleRes = R.string.title_add_patient,
          subtitleRes = R.string.subtitle_add_patient,
          iconRes = R.drawable.ic_home_new_patient,
          testTag = HomeTestTags.NewPatientCard,
        ),
      ),
      HomeRow.Card(
        HomeCardConfig(
          type = HomeCardType.PatientList,
          titleRes = R.string.title_search_patient,
          subtitleRes = R.string.subtitle_search_patient,
          iconRes = R.drawable.ic_home_search,
          testTag = HomeTestTags.PatientListCard,
        ),
      ),
      HomeRow.Card(
        HomeCardConfig(
          type = HomeCardType.CustomPatientList,
          titleRes = R.string.title_custom_patient_list,
          subtitleRes = R.string.subtitle_custom_patient_list,
          iconRes = R.drawable.cloud_sync_24px,
          testTag = HomeTestTags.CustomPatientListCard,
        ),
      ),
      HomeRow.SectionHeader(R.string.section_encounters),
      HomeRow.Card(
        HomeCardConfig(
          type = HomeCardType.GroupEncounter,
          titleRes = R.string.title_group_encounter,
          subtitleRes = R.string.subtitle_group_encounter,
          iconRes = R.drawable.baseline_group_24,
          testTag = HomeTestTags.GroupEncounterCard,
        ),
      ),
      HomeRow.SectionHeader(R.string.section_system),
      HomeRow.Card(
        HomeCardConfig(
          type = HomeCardType.SyncInfo,
          titleRes = R.string.title_sync_info,
          subtitleRes = R.string.subtitle_sync_info,
          iconRes = R.drawable.ic_baseline_sync_24,
          testTag = HomeTestTags.SyncInfoCard,
        ),
      ),
      HomeRow.Card(
        HomeCardConfig(
          type = HomeCardType.UnsyncedResources,
          titleRes = R.string.title_unsynced_resources,
          subtitleRes = R.string.subtitle_unsynced_resources,
          iconRes = R.drawable.ic_offline_resource,
          testTag = HomeTestTags.UnsyncedResourcesCard,
        ),
      ),
    )

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag(HomeTestTags.Screen),
    contentPadding = contentPadding,
  ) {
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
                HomeCardType.UnsyncedResources -> onUnsyncedResourcesClicked
              },
          )
      }
    }
  }
}

@Composable
private fun HomeSectionHeader(titleRes: Int) {
  Text(
    text = stringResource(id = titleRes),
    color = colorResource(id = R.color.black),
    fontSize = 14.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
  )
}

@Composable
private fun HomeDashboardCard(
  config: HomeCardConfig,
  onClick: () -> Unit,
) {
  val verticalSpacing = dimensionResource(id = R.dimen.cardView_margin_vertical)
  val horizontalSpacing = dimensionResource(id = R.dimen.new_patient_horizontal_margin)
  val padding = dimensionResource(id = R.dimen.new_patient_padding)
  val titleColor = colorResource(id = R.color.dashboard_cardview_textcolor)
  val titleStyle = MaterialTheme.typography.titleLarge
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .padding(vertical = verticalSpacing)
        .testTag(config.testTag)
        .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.white)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    shape = RoundedCornerShape(dimensionResource(id = R.dimen.cardView_radius_corner)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(padding),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Image(
        painter = painterResource(id = config.iconRes),
        colorFilter = ColorFilter.tint(titleColor),
        contentDescription = stringResource(id = R.string.task_image),
      )
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalSpacing),
      ) {
        Text(
          text = stringResource(id = config.titleRes),
          color = titleColor,
          style = titleStyle,
          maxLines = 1,
        )
        Text(
          text = stringResource(id = config.subtitleRes),
          color = titleColor,
          fontSize = 12.sp,
          maxLines = 1,
        )
      }
    }
  }
}
