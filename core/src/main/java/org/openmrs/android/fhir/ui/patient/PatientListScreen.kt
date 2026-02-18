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
package org.openmrs.android.fhir.ui.patient

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.Period
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.components.PatientListContainerScreen
import org.openmrs.android.fhir.ui.components.PatientListItemRow
import org.openmrs.android.fhir.viewmodel.PatientListUiState
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

object PatientListScreenTestTags {
  const val Screen = "PatientListScreen"
  const val SearchField = "PatientListSearchField"
  const val EmptyState = "PatientListEmptyState"
  const val EmptyStateMessage = "PatientListEmptyStateMessage"
  const val Fab = "PatientListFab"
}

@Composable
fun PatientListScreen(
  uiState: PatientListUiState,
  onQueryChange: (String) -> Unit,
  onRefresh: () -> Unit,
  onPatientClick: (PatientListViewModel.PatientItem) -> Unit,
  onFabClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val contentPadding = rememberActionBarContentPadding()
  val surfaceColor = colorResource(id = R.color.white)
  Box(modifier = modifier.fillMaxSize().background(surfaceColor)) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = surfaceColor,
      floatingActionButton = {
        ExtendedFloatingActionButton(
          modifier = Modifier.testTag(PatientListScreenTestTags.Fab),
          onClick = onFabClick,
          icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
          text = { Text(text = stringResource(id = R.string.register_patient)) },
          containerColor = colorResource(id = R.color.surfaceVariant_neutral_variant_30),
          contentColor = surfaceColor,
        )
      },
    ) { paddingValues ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .background(surfaceColor)
            .padding(paddingValues)
            .padding(contentPadding)
            .testTag(PatientListScreenTestTags.Screen),
      ) {
        PatientSearchField(
          query = uiState.query,
          onQueryChange = onQueryChange,
        )
        PatientListContainerScreen(
          patients = uiState.patients,
          isLoading = uiState.isLoading,
          isRefreshing = uiState.isRefreshing,
          onRefresh = onRefresh,
          modifier = Modifier.weight(1f).background(surfaceColor),
          emptyContent = { PatientListEmptyState() },
        ) { patient ->
          PatientListItemRow(
            name = patient.name,
            ageGenderLabel = "${getFormattedAge(patient)},${patient.gender[0].uppercase()}",
            isSynced = patient.isSynced,
            onClick = { onPatientClick(patient) },
          )
        }
      }
    }
  }
}

@Composable
private fun PatientSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val outlineColor = colorResource(id = R.color.outline_neutral_variant_60)
  val surfaceColor = colorResource(id = R.color.white)
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 12.dp)
        .testTag(PatientListScreenTestTags.SearchField),
    label = { Text(text = stringResource(id = R.string.query_hint_patient_search)) },
    trailingIcon = {
      Icon(
        painter = painterResource(id = R.drawable.ic_home_search),
        contentDescription = null,
        tint = outlineColor,
      )
    },
    singleLine = true,
    shape = RoundedCornerShape(4.dp),
    colors =
      OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = outlineColor,
        focusedBorderColor = outlineColor,
        unfocusedLabelColor = outlineColor,
        focusedLabelColor = outlineColor,
        focusedContainerColor = surfaceColor,
        unfocusedContainerColor = surfaceColor,
      ),
  )
}

@Composable
private fun PatientListEmptyState(
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize().testTag(PatientListScreenTestTags.EmptyState),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Spacer(modifier = Modifier.height(40.dp))
      Image(
        modifier = Modifier.size(80.dp),
        painter = painterResource(id = R.drawable.ic_home_new_patient),
        contentDescription = null,
        colorFilter = ColorFilter.tint(colorResource(id = R.color.dashboard_cardview_textcolor)),
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        modifier = Modifier.testTag(PatientListScreenTestTags.EmptyStateMessage),
        text =
          stringResource(
            id = R.string.no_patients_available_register_a_new_one_using_the_button_below,
          ),
        color = colorResource(id = R.color.black),
        fontSize = 16.sp,
        fontStyle = FontStyle.Italic,
      )
    }
  }
}

private fun getFormattedAge(
  patientItem: PatientListViewModel.PatientItem,
): String {
  if (patientItem.dob == null) return ""
  return Period.between(patientItem.dob, LocalDate.now()).let {
    when {
      it.years > 0 -> it.years.toString()
      it.months > 0 -> it.months.toString() + " months"
      else -> it.days.toString() + " months"
    }
  }
}

@Composable
private fun rememberActionBarContentPadding(): PaddingValues {
  val context = LocalContext.current
  val actionBarHeightPx = remember {
    val typedValue = android.util.TypedValue()
    if (context.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typedValue, true)) {
      android.util.TypedValue.complexToDimensionPixelSize(
        typedValue.data,
        context.resources.displayMetrics,
      )
    } else {
      0
    }
  }
  val actionBarHeightDp = with(LocalDensity.current) { actionBarHeightPx.toDp() }
  val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + actionBarHeightDp
  return PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 16.dp + topInset)
}
