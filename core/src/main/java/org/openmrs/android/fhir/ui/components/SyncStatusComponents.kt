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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.data.database.model.SyncStatus

@Immutable
sealed class SyncStatusUiState {
  data class Syncing(val completed: Int, val total: Int) : SyncStatusUiState()

  data class Success(val message: String) : SyncStatusUiState()

  data class Error(val message: String) : SyncStatusUiState()

  data object Idle : SyncStatusUiState()
}

@Composable
fun SyncStatusLayout(
  state: SyncStatusUiState,
  modifier: Modifier = Modifier,
) {
  if (state is SyncStatusUiState.Idle) return

  val (headerColor, iconColor, iconRes, label, progress, percentLabel, progressColor) =
    when (state) {
      is SyncStatusUiState.Syncing -> {
        val progress = state.completed.coerceAtLeast(0)
        val total = state.total.takeIf { it > 0 } ?: 100
        val fraction = (progress.toFloat() / total).coerceIn(0f, 1f)
        val percent = (fraction * 100).toInt()
        Quintuple(
          colorResource(id = R.color.syncing_background),
          colorResource(id = R.color.dashboard_cardview_textcolor),
          R.drawable.ic_baseline_sync_24,
          stringResource(id = R.string.syncing),
          fraction,
          "$percent%",
          MaterialTheme.colorScheme.primary,
        )
      }
      is SyncStatusUiState.Success ->
        Quintuple(
          colorResource(id = R.color.tertiary_green_80),
          colorResource(id = R.color.white),
          R.drawable.ic_check_circle,
          state.message,
          1f,
          stringResource(id = R.string._100),
          colorResource(id = R.color.tertiary_green_80),
        )
      is SyncStatusUiState.Error ->
        Quintuple(
          colorResource(id = R.color.error_red_40),
          colorResource(id = R.color.white),
          R.drawable.ic_error,
          state.message,
          1f,
          stringResource(id = R.string._0),
          colorResource(id = R.color.error_red_40),
        )
      SyncStatusUiState.Idle -> throw IllegalStateException("Idle state should return early")
    }

  Card(
    modifier =
      modifier.fillMaxWidth().padding(vertical = dimensionResource(id = R.dimen.text_margin)),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation =
      CardDefaults.cardElevation(defaultElevation = dimensionResource(id = R.dimen.status_margin)),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().background(color = headerColor).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Icon(
          modifier = Modifier.size(24.dp),
          painter = painterResource(id = iconRes),
          contentDescription = label,
          tint = iconColor,
        )
        Text(
          modifier = Modifier.padding(start = 5.dp),
          text = label,
          style = MaterialTheme.typography.bodyLarge,
          color = iconColor,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
        text = percentLabel,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Start,
        style = MaterialTheme.typography.bodyMedium,
      )
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp),
        progress = { progress },
        color = progressColor,
      )
    }
  }
}

@Composable
fun SyncSessionCard(
  session: SyncSession,
  onDeleteSession: (SyncSession) -> Unit,
  modifier: Modifier = Modifier,
) {
  val statusColor =
    when (session.status) {
      SyncStatus.ONGOING -> R.color.orange
      SyncStatus.COMPLETED -> R.color.tertiary_green_80
      SyncStatus.COMPLETED_WITH_ERRORS -> R.color.error_red_40
    }

  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(
          horizontal = dimensionResource(id = R.dimen.cardView_margin_horizontal) / 2,
          vertical = 4.dp,
        ),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation =
      CardDefaults.cardElevation(defaultElevation = dimensionResource(id = R.dimen.status_margin)),
    shape = RoundedCornerShape(dimensionResource(id = R.dimen.cardView_radius_corner)),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .height(IntrinsicSize.Min)
          .padding(end = 12.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Box(
        modifier = Modifier.width(8.dp).fillMaxHeight().background(colorResource(id = statusColor)),
      )

      Column(
        modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp),
      ) {
        Text(
          text = stringResource(id = R.string.start_time_with_value, session.startTime),
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = colorResource(id = R.color.dashboard_cardview_textcolor),
        )
        Text(
          modifier = Modifier.padding(top = 8.dp),
          text =
            stringResource(
              id = R.string.sync_session_progress,
              session.downloadedPatients,
              session.totalPatientsToDownload,
              session.uploadedPatients,
              session.totalPatientsToUpload,
            ),
          fontSize = 14.sp,
          color = colorResource(id = R.color.dashboard_cardview_textcolor),
        )
        Text(
          modifier = Modifier.padding(top = 8.dp),
          text =
            session.completionTime?.let {
              stringResource(id = R.string.completed_at_with_value, it)
            }
              ?: stringResource(id = R.string.in_progress),
          fontSize = 14.sp,
          color = colorResource(id = R.color.dashboard_cardview_textcolor),
        )

        if (session.status == SyncStatus.COMPLETED_WITH_ERRORS && session.errors.isNotEmpty()) {
          Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(
              text = stringResource(id = R.string.errors_with_count, session.errors.size),
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = colorResource(id = R.color.dashboard_cardview_textcolor),
            )
            Text(
              modifier = Modifier.padding(top = 8.dp),
              text = session.errors.joinToString(separator = "\n") { "- $it" },
              fontSize = 12.sp,
              color = colorResource(id = R.color.dashboard_cardview_textcolor),
            )
          }
        }
      }

      if (session.status != SyncStatus.ONGOING) {
        IconButton(
          modifier =
            Modifier.size(40.dp)
              .background(colorResource(id = R.color.unknown_risk_background), shape = CircleShape),
          onClick = { onDeleteSession(session) },
          colors =
            IconButtonDefaults.iconButtonColors(contentColor = colorResource(id = R.color.white)),
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_delete),
            contentDescription = stringResource(id = R.string.delete),
            tint = Color.White,
          )
        }
      }
    }
  }
}

@Composable
fun SyncInfoContent(
  syncSessions: List<SyncSession>,
  onDeleteSession: (SyncSession) -> Unit,
  onClearAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize()) {
    if (syncSessions.isEmpty()) {
      Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            modifier = Modifier.size(80.dp),
            painter = painterResource(id = R.drawable.ic_baseline_sync_24),
            contentDescription = stringResource(id = R.string.no_sync_info_available),
            tint = colorResource(id = R.color.dashboard_cardview_textcolor),
          )
          Text(
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(id = R.string.no_sync_info_available),
            fontSize = 16.sp,
            fontStyle = FontStyle.Italic,
            color = colorResource(id = R.color.black),
            textAlign = TextAlign.Center,
          )
        }
      }
    } else {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        syncSessions.forEach { session ->
          SyncSessionCard(
            session = session,
            onDeleteSession = onDeleteSession,
          )
        }
      }
    }

    Button(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
      onClick = onClearAll,
      enabled = syncSessions.isNotEmpty(),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = colorResource(id = R.color.dashboard_cardview_textcolor),
          contentColor = colorResource(id = R.color.white),
        ),
      shape = RoundedCornerShape(4.dp),
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_delete),
        contentDescription = stringResource(id = R.string.delete),
        tint = Color.White,
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = stringResource(id = R.string.clear_sync_data), fontSize = 12.sp)
    }
  }
}

private data class Quintuple(
  val headerColor: Color,
  val iconColor: Color,
  @DrawableRes val iconRes: Int,
  val label: String,
  val progress: Float,
  val percentLabel: String,
  val progressColor: Color,
)
