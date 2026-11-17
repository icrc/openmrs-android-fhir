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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.data.database.model.UnsyncedResource
import org.openmrs.android.fhir.data.sync.SyncMode
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.ui.components.SyncHistoryList
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget

object SyncScreenTestTags {
  const val Screen = "SyncScreen"
  const val StatusCard = "SyncStatusCard"
  const val ToIdentifyCount = "SyncToIdentifyCount"
  const val CancelSyncButton = "SyncCancelButton"
  const val SyncNowButton = "SyncNowButton"
  const val PendingTab = "SyncPendingTab"
  const val HistoryTab = "SyncHistoryTab"
  const val ImportExportButton = "SyncImportExportButton"
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
  onSyncNow: (SyncMode) -> Unit,
  onTogglePatientExpand: (String) -> Unit,
  onToggleEncounterExpand: (String) -> Unit,
  onDeleteResource: (UnsyncedResource) -> Unit,
  onDownloadResource: (UnsyncedResource) -> Unit,
  onDeleteAllPending: () -> Unit,
  onDownloadAllPending: () -> Unit,
  onDeleteSession: (SyncSession) -> Unit,
  onClearHistory: () -> Unit,
  modifier: Modifier = Modifier,
  onImportExport: () -> Unit = {},
  /** What "Sync now" does until the user picks another (the automatic sync's setting). */
  defaultSyncMode: SyncMode = SyncMode.BOTH,
  /** The server cannot be reached: nothing can be synced now. */
  syncAvailable: Boolean = true,
  onIdentify: (patientId: String) -> Unit = {},
  /** Stops the sync that is running. */
  onCancelSync: () -> Unit = {},
  /** Identifying needs a signed-in user with a connection to the server. */
  identifyAvailable: Boolean = true,
  toIdentifyResources: List<UnsyncedResource> = emptyList(),
) {
  val topInset = rememberAppBarHeight()
  val pendingCount = pendingResources.count { it is UnsyncedResource.PatientItem }
  val toIdentifyCount = toIdentifyResources.count { it is UnsyncedResource.PatientItem }

  // Until the user picks a tab: pending changes first, the history when there is nothing to upload.
  var chosenTab by rememberSaveable { mutableStateOf<Int?>(null) }
  val selectedTab =
    chosenTab
      ?: if (pendingCount > 0 || isLoadingPending || toIdentifyResources.isNotEmpty()) 0 else 1

  Column(
    modifier =
      modifier.fillMaxSize().padding(top = topInset + 12.dp).testTag(SyncScreenTestTags.Screen),
  ) {
    // With a big font or a small screen the card is taller than what it may take: it scrolls in
    // its own space (instead of squeezing its last button) and the tabs below keep the rest.
    val cardMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    Column(
      modifier = Modifier.heightIn(max = cardMaxHeight).verticalScroll(rememberScrollState()),
    ) {
      SyncStatusCard(
        isSyncing = isSyncing,
        progress = syncProgress,
        pendingCount = pendingCount,
        toIdentifyCount = toIdentifyCount,
        onReviewToIdentify = { chosenTab = 0 },
        lastSyncText = lastSyncText,
        onSyncNow = onSyncNow,
        onCancelSync = onCancelSync,
        onImportExport = onImportExport,
        defaultMode = defaultSyncMode,
        syncAvailable = syncAvailable,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
    }
    Spacer(modifier = Modifier.height(8.dp))
    TabRow(
      modifier = Modifier.tutorialTarget(TutorialTargets.SYNC_TABS),
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
          toIdentify = toIdentifyResources,
          onIdentify = onIdentify,
          identifyAvailable = identifyAvailable,
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
  toIdentifyCount: Int,
  onReviewToIdentify: () -> Unit,
  lastSyncText: String,
  onSyncNow: (SyncMode) -> Unit,
  onCancelSync: () -> Unit,
  onImportExport: () -> Unit,
  defaultMode: SyncMode,
  syncAvailable: Boolean,
  modifier: Modifier = Modifier,
) {
  // This run only: the choice is not kept, and the setting is not touched.
  var chosen by rememberSaveable(defaultMode.value) { mutableStateOf(defaultMode.value) }
  val mode = SyncMode.fromValue(chosen)
  // What waits for the user is not "uploaded": the uploads are done, but something is left to do.
  val needsAction = !isSyncing && pendingCount == 0 && toIdentifyCount > 0
  val (accent, title) =
    when {
      isSyncing -> OpenMrsTheme.colors.warning to stringResource(R.string.sync_in_progress)
      pendingCount > 0 ->
        OpenMrsTheme.colors.warning to stringResource(R.string.sync_status_pending, pendingCount)
      needsAction ->
        MaterialTheme.colorScheme.primary to stringResource(R.string.sync_status_action_needed)
      else -> OpenMrsTheme.colors.success to stringResource(R.string.sync_status_up_to_date)
    }
  val fraction = progress.fraction

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .testTag(SyncScreenTestTags.StatusCard)
        .tutorialTarget(TutorialTargets.SYNC_STATUS),
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
          } else if (needsAction) {
            Icon(
              imageVector = Icons.Filled.PersonSearch,
              contentDescription = null,
              tint = accent,
              modifier = Modifier.size(24.dp),
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
      if (toIdentifyCount > 0) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(top = 12.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.secondaryContainer)
              .clickable(role = Role.Button, onClick = onReviewToIdentify)
              .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp)
              .testTag(SyncScreenTestTags.ToIdentifyCount),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text =
                pluralStringResource(
                  R.plurals.home_summary_to_identify,
                  toIdentifyCount,
                  toIdentifyCount,
                ),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
              text = stringResource(R.string.sync_identify_hint),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
          Text(
            text = stringResource(R.string.sync_identify_review),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp),
          )
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
      // Three chips side by side while each has room for its word; one under the other when the
      // card is too narrow (a small screen, a long translation), instead of breaking the words.
      BoxWithConstraints(
        modifier =
          Modifier.fillMaxWidth().padding(top = 14.dp).tutorialTarget(TutorialTargets.SYNC_MODE),
      ) {
        val sideBySide = maxWidth >= 300.dp
        val options =
          listOf(
            SyncMode.DOWNLOAD_ONLY to R.string.sync_page_mode_download,
            SyncMode.UPLOAD_ONLY to R.string.sync_page_mode_upload,
            SyncMode.BOTH to R.string.sync_page_mode_both,
          )

        @Composable
        fun RowScope.chip(option: SyncMode, label: Int, wide: Boolean) {
          FilterChip(
            selected = mode == option,
            onClick = { chosen = option.value },
            enabled = !isSyncing && syncAvailable,
            label = {
              Text(
                text = stringResource(label),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
              )
            },
            modifier =
              (if (wide) Modifier.weight(1f) else Modifier.fillMaxWidth()).testTag(
                "SyncMode_${option.value}",
              ),
          )
        }
        if (sideBySide) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (option, label) -> chip(option, label, wide = true) }
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (option, label) -> Row { chip(option, label, wide = false) } }
          }
        }
      }
      if (!syncAvailable) {
        Text(
          text = stringResource(R.string.sync_unavailable_offline),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(top = 8.dp).testTag("SyncOfflineNote"),
        )
      }
      if (mode == SyncMode.DOWNLOAD_ONLY && pendingCount > 0) {
        Text(
          text = stringResource(R.string.sync_download_only_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
      Button(
        onClick = { onSyncNow(mode) },
        enabled = !isSyncing && syncAvailable,
        shape = RoundedCornerShape(14.dp),
        modifier =
          Modifier.fillMaxWidth()
            .padding(top = 14.dp)
            .heightIn(min = 48.dp)
            .testTag(SyncScreenTestTags.SyncNowButton)
            .tutorialTarget(TutorialTargets.SYNC_NOW),
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
      if (isSyncing) {
        OutlinedButton(
          onClick = onCancelSync,
          shape = RoundedCornerShape(14.dp),
          colors =
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
          modifier =
            Modifier.fillMaxWidth()
              .padding(top = 8.dp)
              .heightIn(min = 48.dp)
              .testTag(SyncScreenTestTags.CancelSyncButton),
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(id = R.string.sync_cancel),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
      TextButton(
        onClick = onImportExport,
        modifier = Modifier.fillMaxWidth().testTag(SyncScreenTestTags.ImportExportButton),
      ) {
        Text(
          text = stringResource(id = R.string.sync_cannot_sync_link),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}
