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

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.remote.CheckState
import org.openmrs.android.fhir.data.remote.Checks
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget
import org.openmrs.android.fhir.viewmodel.CompatibilityRow
import org.openmrs.android.fhir.viewmodel.ServerCompatibilityUiState

object ServerCompatibilityTestTags {
  const val Screen = "ServerCompatibilityScreen"
  const val CheckAgain = "CompatibilityCheckAgain"
  const val WriteTest = "CompatibilityWriteTest"
  const val WriteTestConfirm = "CompatibilityWriteTestConfirm"
  const val Copy = "CompatibilityCopy"

  fun row(key: String) = "CompatibilityRow_$key"
}

@Composable
fun ServerCompatibilityScreen(
  state: ServerCompatibilityUiState,
  onCheckAgain: () -> Unit,
  onWriteTest: () -> Unit,
  onCopy: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var confirmWrite by rememberSaveable { mutableStateOf(false) }
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = rememberAppBarHeight() + 12.dp, bottom = 24.dp)
        .testTag(ServerCompatibilityTestTags.Screen),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = stringResource(R.string.compat_intro),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text =
        stringResource(R.string.compat_server_line, state.serverUrl) +
          "\n" +
          if (state.checkedAt > 0) {
            stringResource(
              R.string.compat_checked_at,
              DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(state.checkedAt)),
            )
          } else {
            stringResource(R.string.compat_never)
          },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!state.reachable) {
      Text(
        text = stringResource(R.string.compat_unreachable, state.unreachableDetail.orEmpty()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }

    Column(
      modifier = Modifier.tutorialTarget(TutorialTargets.COMPAT_ACTIONS),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = onCheckAgain,
          enabled = !state.running,
          modifier = Modifier.weight(1f).testTag(ServerCompatibilityTestTags.CheckAgain),
        ) {
          if (state.running) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.compat_checking))
          } else {
            Text(text = stringResource(R.string.compat_check_again))
          }
        }
        OutlinedButton(
          onClick = { confirmWrite = true },
          enabled = !state.running,
          modifier = Modifier.weight(1f).testTag(ServerCompatibilityTestTags.WriteTest),
        ) {
          Text(text = stringResource(R.string.compat_test_write))
        }
      }
      TextButton(onClick = onCopy, modifier = Modifier.testTag(ServerCompatibilityTestTags.Copy)) {
        Text(text = stringResource(R.string.compat_copy))
      }
    }

    Column(
      modifier = Modifier.tutorialTarget(TutorialTargets.COMPAT_LIST),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      state.rows.forEach { row -> CheckRow(row) }
    }
  }

  if (confirmWrite) {
    AlertDialog(
      onDismissRequest = { confirmWrite = false },
      title = { Text(text = stringResource(R.string.compat_test_write_title)) },
      text = { Text(text = stringResource(R.string.compat_test_write_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmWrite = false
            onWriteTest()
          },
          modifier = Modifier.testTag(ServerCompatibilityTestTags.WriteTestConfirm),
        ) {
          Text(text = stringResource(R.string.compat_test_write_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmWrite = false }) {
          Text(text = stringResource(R.string.cancel))
        }
      },
    )
  }
}

@Composable
private fun CheckRow(row: CompatibilityRow) {
  val result = row.result
  val state = result?.state
  val colors = MaterialTheme.colorScheme
  val (icon, tint) =
    when (state) {
      CheckState.OK -> Icons.Default.CheckCircle to OpenMrsTheme.colors.success
      CheckState.UNSUPPORTED -> Icons.Default.Cancel to colors.error
      CheckState.INFO -> Icons.Default.Info to colors.primary
      CheckState.NOT_USED -> Icons.Default.RemoveCircleOutline to colors.onSurfaceVariant
      CheckState.UNKNOWN,
      null, -> Icons.AutoMirrored.Filled.HelpOutline to colors.onSurfaceVariant
    }
  Surface(
    modifier = Modifier.fillMaxWidth().testTag(ServerCompatibilityTestTags.row(row.key)),
    shape = RoundedCornerShape(16.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.outlineVariant),
  ) {
    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(24.dp),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(titleOf(row.key)),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = stringResource(stateLabel(row.key, result?.detail, state)),
          style = MaterialTheme.typography.labelLarge,
          color = if (state == CheckState.UNSUPPORTED) colors.error else tint,
        )
        Text(
          text = stringResource(whatOf(row.key)),
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
        result?.subject?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = colors.onSurfaceVariant,
          )
        }
        val shown = result?.detail?.takeIf { showsDetail(row.key, it) }
        if (shown != null) {
          SelectionContainer {
            Text(
              text = shown,
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = colors.onSurfaceVariant,
              modifier = Modifier.padding(top = 4.dp),
            )
          }
        }
      }
    }
  }
}

private fun showsDetail(key: String, detail: String): Boolean =
  !(key == Checks.COHORT_MODULE && (detail == "installed" || detail == "not installed"))

@StringRes
private fun stateLabel(key: String, detail: String?, state: CheckState?): Int =
  when (state) {
    CheckState.OK -> R.string.compat_state_ok
    CheckState.UNSUPPORTED -> R.string.compat_state_unsupported
    CheckState.UNKNOWN -> R.string.compat_state_unknown
    CheckState.NOT_USED -> R.string.compat_state_not_used
    CheckState.INFO ->
      if (key == Checks.COHORT_MODULE && detail == "not installed") {
        R.string.compat_state_not_installed
      } else if (key == Checks.COHORT_MODULE) {
        R.string.compat_state_installed
      } else {
        R.string.compat_state_info
      }
    null -> R.string.compat_state_pending
  }

@StringRes
private fun titleOf(key: String): Int =
  when (key) {
    Checks.SERVER -> R.string.compat_server_title
    Checks.FHIR -> R.string.compat_fhir_title
    Checks.GROUPS_READ -> R.string.compat_groups_read_title
    Checks.GROUPS_WRITE -> R.string.compat_groups_write_title
    Checks.GROUP_MEMBERS -> R.string.compat_group_members_title
    Checks.COHORT_MODULE -> R.string.compat_cohort_module_title
    Checks.CONCEPT_COHORT_ID -> R.string.compat_concept_id_title
    else -> R.string.compat_concept_name_title
  }

@StringRes
private fun whatOf(key: String): Int =
  when (key) {
    Checks.SERVER -> R.string.compat_server_what
    Checks.FHIR -> R.string.compat_fhir_what
    Checks.GROUPS_READ -> R.string.compat_groups_read_what
    Checks.GROUPS_WRITE -> R.string.compat_groups_write_what
    Checks.GROUP_MEMBERS -> R.string.compat_group_members_what
    Checks.COHORT_MODULE -> R.string.compat_cohort_module_what
    Checks.CONCEPT_COHORT_ID -> R.string.compat_concept_id_what
    else -> R.string.compat_concept_name_what
  }
