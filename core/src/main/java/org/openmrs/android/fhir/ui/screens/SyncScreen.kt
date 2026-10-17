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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.data.database.model.UnsyncedResource
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.ui.components.SyncHistoryList
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

object SyncScreenTestTags {
  const val Screen = "SyncScreen"
  const val StatusCard = "SyncStatusCard"
  const val SyncNowButton = "SyncNowButton"
  const val PendingTab = "SyncPendingTab"
  const val HistoryTab = "SyncHistoryTab"
}

/**
 * The one place for synchronization: a status card with the **Sync now** button, then two tabs, the
 * changes still waiting to be uploaded and the history of past syncs.
 */
@Composable
fun SyncScreen(
  syncSessions: List<SyncSession>,
  pendingResources: List<UnsyncedResource>,
  isLoadingPending: Boolean,
  isSyncing: Boolean,
  syncProgress: SyncProgressState,
  lastSyncText: String,
  onSyncNow: () -> Unit,
  onTogglePatientExpand: (String) -> Unit,
  onToggleEncounterExpand: (String) -> Unit,
  onDeleteResource: (UnsyncedResource) -> Unit,
  onDownloadResource: (UnsyncedResource) -> Unit,
  onDeleteAllPending: () -> Unit,
  onDownloadAllPending: () -> Unit,
  onDeleteSession: (SyncSession) -> Unit,
  onClearHistory: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val topInset = rememberAppBarHeight()
  val pendingCount = pendingResources.count { it is UnsyncedResource.PatientItem }

  // Until the user picks a tab: pending changes first, the history when there is nothing to upload.
  var chosenTab by rememberSaveable { mutableStateOf<Int?>(null) }
  val selectedTab = chosenTab ?: if (pendingCount > 0 || isLoadingPending) 0 else 1

  Column(
    modifier =
      modifier.fillMaxSize().padding(top = topInset + 12.dp).testTag(SyncScreenTestTags.Screen),
  ) {
    SyncStatusCard(
      isSyncing = isSyncing,
      progress = syncProgress,
      pendingCount = pendingCount,
      lastSyncText = lastSyncText,
      onSyncNow = onSyncNow,
      modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(modifier = Modifier.height(8.dp))
    TabRow(
      selectedTabIndex = selectedTab,
      containerColor = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.primary,
    ) {
      Tab(
        modifier = Modifier.testTag(SyncScreenTestTags.PendingTab),
        selected = selectedTab == 0,
        onClick = { chosenTab = 0 },
        text = {
          Text(
            text = "${stringResource(id = R.string.sync_tab_pending)} · $pendingCount",
            fontWeight = FontWeight.SemiBold,
          )
        },
      )
      Tab(
        modifier = Modifier.testTag(SyncScreenTestTags.HistoryTab),
        selected = selectedTab == 1,
        onClick = { chosenTab = 1 },
        text = {
          Text(
            text = "${stringResource(id = R.string.sync_tab_history)} · ${syncSessions.size}",
            fontWeight = FontWeight.SemiBold,
          )
        },
      )
    }
    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
      if (selectedTab == 0) {
        UnsyncedResourcesScreen(
          resources = pendingResources,
          isLoading = isLoadingPending,
          onTogglePatientExpand = onTogglePatientExpand,
          onToggleEncounterExpand = onToggleEncounterExpand,
          onDeleteResource = onDeleteResource,
          onDownloadResource = onDownloadResource,
          onDeleteAll = onDeleteAllPending,
          onDownloadAll = onDownloadAllPending,
        )
      } else {
        SyncHistoryList(
          syncSessions = syncSessions,
          onDeleteSession = onDeleteSession,
          onClearAll = onClearHistory,
        )
      }
    }
  }
}

/**
 * What state the data is in and what to do about it: syncing (with progress), changes waiting to be
 * uploaded, or everything up to date, plus when the last sync was and the button to start one.
 */
@Composable
private fun SyncStatusCard(
  isSyncing: Boolean,
  progress: SyncProgressState,
  pendingCount: Int,
  lastSyncText: String,
  onSyncNow: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (accent, title) =
    when {
      isSyncing -> OpenMrsTheme.colors.warning to stringResource(R.string.sync_in_progress)
      pendingCount > 0 ->
        OpenMrsTheme.colors.warning to stringResource(R.string.sync_status_pending, pendingCount)
      else -> OpenMrsTheme.colors.success to stringResource(R.string.sync_status_up_to_date)
    }
  val fraction = progress.fraction

  Surface(
    modifier = modifier.fillMaxWidth().testTag(SyncScreenTestTags.StatusCard),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shadowElevation = 1.dp,
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
          contentAlignment = Alignment.Center,
        ) {
          if (isSyncing) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              strokeWidth = 3.dp,
              color = accent,
            )
          } else {
            Icon(
              painter =
                painterResource(
                  id =
                    if (pendingCount > 0) {
                      R.drawable.ic_baseline_sync_24
                    } else {
                      R.drawable.ic_check_circle
                    },
                ),
              contentDescription = null,
              tint = accent,
              modifier = Modifier.size(24.dp),
            )
          }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (lastSyncText.isNotBlank()) {
            Text(
              text = stringResource(R.string.sync_last_value, lastSyncText),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      if (isSyncing) {
        if (fraction != null) {
          LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp).clip(CircleShape),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
          )
        } else {
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp).clip(CircleShape),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
          )
          if (progress.current > 0) {
            Text(
              text = stringResource(R.string.sync_items_so_far, progress.current),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 6.dp),
            )
          }
        }
      }
      Button(
        onClick = onSyncNow,
        enabled = !isSyncing,
        shape = RoundedCornerShape(14.dp),
        modifier =
          Modifier.fillMaxWidth()
            .padding(top = 14.dp)
            .height(48.dp)
            .testTag(SyncScreenTestTags.SyncNowButton),
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_baseline_sync_24),
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(id = R.string.sync_now),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}
