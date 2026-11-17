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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.Period
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.patientlists.MemberPending
import org.openmrs.android.fhir.patientlists.PatientListEdit
import org.openmrs.android.fhir.patientlists.PatientListEntry
import org.openmrs.android.fhir.patientlists.PatientListMember
import org.openmrs.android.fhir.patientlists.PatientListState
import org.openmrs.android.fhir.patientlists.PatientListsRepository
import org.openmrs.android.fhir.ui.components.AppSearchField
import org.openmrs.android.fhir.ui.components.PatientAvatar
import org.openmrs.android.fhir.ui.components.PatientListTag
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget
import org.openmrs.android.fhir.viewmodel.PatientListDetailUiState
import org.openmrs.android.fhir.viewmodel.stateLabel

object PatientListDetailTestTags {
  const val History = "PatientListDetailHistory"
  const val Export = "PatientListDetailExport"
  const val ExportConfirm = "PatientListDetailExportConfirm"
  const val ReadOnlyNotice = "PatientListDetailReadOnlyNotice"
  const val Screen = "PatientListDetailScreen"
  const val AddFab = "PatientListAddPatientsFab"
  const val Edit = "PatientListEditButton"
  const val StartSession = "PatientListStartSessionButton"
  const val Delete = "PatientListDeleteButton"

  fun member(id: String) = "PatientListMember_$id"

  fun removeMember(id: String) = "PatientListRemoveMember_$id"
}

private const val SEARCH_FROM = 8

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PatientListDetailScreen(
  state: PatientListDetailUiState,
  online: Boolean,
  onQueryChange: (String) -> Unit,
  onRefresh: () -> Unit,
  onEdit: (name: String, description: String, onResult: (PatientListEdit) -> Unit) -> Unit,
  onDelete: () -> Unit,
  onUndoDelete: () -> Unit,
  onAddPatients: () -> Unit,
  onStartSession: () -> Unit,
  onDownloadedChange: (Boolean) -> Unit,
  onRemoveMember: (PatientListMember) -> Unit,
  onUndoMember: (PatientListMember) -> Unit,
  onSendNow: () -> Unit,
  onDiscard: () -> Unit,
  onExport: () -> Unit = {},
  onOpenCompatibility: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val patientList = state.patientList
  var showHistory by rememberSaveable { mutableStateOf(false) }
  var confirmExport by rememberSaveable { mutableStateOf(false) }
  var editing by rememberSaveable { mutableStateOf(false) }
  var editError by rememberSaveable { mutableStateOf<Int?>(null) }
  var deleting by rememberSaveable { mutableStateOf(false) }
  val topInset = rememberAppBarHeight()
  val surfaceColor = MaterialTheme.colorScheme.background
  val refreshState =
    rememberPullRefreshState(refreshing = state.isRefreshing, onRefresh = onRefresh)

  Box(modifier = modifier.fillMaxSize().background(surfaceColor)) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = surfaceColor,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      floatingActionButton = {
        if (
          patientList != null &&
            patientList.state != PatientListState.DELETING &&
            state.features.changeMembers
        ) {
          ExtendedFloatingActionButton(
            modifier =
              Modifier.testTag(PatientListDetailTestTags.AddFab)
                .tutorialTarget(TutorialTargets.PLIST_ADD),
            onClick = onAddPatients,
            icon = { Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null) },
            text = { Text(text = stringResource(R.string.plist_add_patients)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          )
        }
      },
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding).pullRefresh(refreshState)) {
        LazyColumn(
          modifier =
            Modifier.fillMaxSize()
              .padding(top = topInset)
              .testTag(PatientListDetailTestTags.Screen),
          contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (patientList != null) {
            item(key = "about", contentType = "about") {
              AboutCard(
                patientList = patientList,
                downloaded = state.downloaded,
                onDownloadedChange = onDownloadedChange,
                onStartSession = onStartSession,
                canEdit = state.features.editLists,
                onEdit = {
                  editError = null
                  editing = true
                },
                onDelete = { deleting = true },
                onUndoDelete = onUndoDelete,
                onHistory = { showHistory = true },
                onExport = { confirmExport = true },
              )
            }
            item(key = "notices", contentType = "notices") {
              Notices(
                patientList,
                state,
                online,
                onSendNow,
                onDiscard,
                onRefresh,
                onOpenCompatibility,
              )
            }
            item(key = "members-header", contentType = "header") {
              MembersHeader(
                total = state.totalMembers,
                modifier = Modifier.tutorialTarget(TutorialTargets.PLIST_MEMBERS),
              )
            }
            if (state.totalMembers >= SEARCH_FROM || state.query.isNotBlank()) {
              item(key = "members-search", contentType = "search") {
                AppSearchField(
                  value = state.query,
                  onValueChange = onQueryChange,
                  placeholder = stringResource(R.string.plist_members_search_hint),
                )
              }
            }
            when {
              state.loadingMembers ->
                item(key = "loading", contentType = "loading") {
                  Row(
                    modifier =
                      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    CircularProgressIndicator(
                      modifier = Modifier.padding(end = 12.dp),
                      strokeWidth = 2.dp,
                    )
                    Text(
                      text = stringResource(R.string.plist_members_loading),
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              state.members.isEmpty() ->
                item(key = "empty", contentType = "empty") {
                  EmptyMembers(searching = state.query.isNotBlank() && state.totalMembers > 0)
                }
              else ->
                items(state.members, key = { it.patientId }, contentType = { "member" }) { member ->
                  MemberRow(
                    member = member,
                    canRemove =
                      patientList.state != PatientListState.DELETING &&
                        state.features.changeMembers,
                    onRemove = { onRemoveMember(member) },
                    onUndo = { onUndoMember(member) },
                  )
                }
            }
          } else if (state.loaded) {
            item(key = "missing") {
              Text(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 48.dp),
                text = stringResource(R.string.plist_gone),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        PullRefreshIndicator(
          refreshing = state.isRefreshing,
          state = refreshState,
          modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset),
        )
      }
    }
  }

  if (editing && patientList != null) {
    PatientListEditDialog(
      title = stringResource(R.string.plist_edit_title),
      confirmLabel = stringResource(R.string.save),
      initialName = patientList.name,
      initialDescription = patientList.description.orEmpty(),
      nameError = editError,
      onConfirm = { name, description ->
        onEdit(name, description) { result ->
          when (result) {
            is PatientListEdit.Saved,
            PatientListEdit.NotFound, -> editing = false
            PatientListEdit.NameMissing -> editError = R.string.plist_name_required
            PatientListEdit.NameTaken -> editError = R.string.plist_name_taken
          }
        }
      },
      onDismiss = { editing = false },
    )
  }
  if (showHistory) {
    PatientListHistoryDialog(events = state.history, onDismiss = { showHistory = false })
  }
  if (confirmExport) {
    AlertDialog(
      onDismissRequest = { confirmExport = false },
      title = { Text(text = stringResource(R.string.plist_export_title)) },
      text = { Text(text = stringResource(R.string.plist_export_message)) },
      confirmButton = {
        TextButton(
          modifier = Modifier.testTag(PatientListDetailTestTags.ExportConfirm),
          onClick = {
            confirmExport = false
            onExport()
          },
        ) {
          Text(text = stringResource(R.string.plist_export_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmExport = false }) {
          Text(text = stringResource(R.string.cancel))
        }
      },
    )
  }
  if (deleting && patientList != null) {
    PatientListDeleteDialog(
      name = patientList.name,
      onConfirm = {
        deleting = false
        onDelete()
      },
      onDismiss = { deleting = false },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutCard(
  patientList: PatientListEntry,
  downloaded: Boolean,
  onDownloadedChange: (Boolean) -> Unit,
  onStartSession: () -> Unit,
  canEdit: Boolean,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onUndoDelete: () -> Unit,
  onHistory: () -> Unit,
  onExport: () -> Unit,
) {
  val label = patientList.stateLabel()
  Surface(
    modifier =
      Modifier.padding(horizontal = 16.dp)
        .fillMaxWidth()
        .tutorialTarget(TutorialTargets.PLIST_ABOUT),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = stringResource(R.string.plist_about).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = patientList.name,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = patientList.description?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.plist_no_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (label != null) {
        PatientListTag(text = stringResource(label), isError = patientList.error != null)
      }
      val created =
        listOfNotNull(patientList.creatorName, formatPatientListDate(patientList.dateCreated))
          .takeIf { it.isNotEmpty() }
          ?.let { stringsOf(it) }
      val changed =
        listOfNotNull(patientList.changedByName, formatPatientListDate(patientList.dateChanged))
          .takeIf { it.isNotEmpty() }
          ?.let { stringsOf(it) }
      if (
        patientList.state != PatientListState.NEW && patientList.state != PatientListState.DELETING
      ) {
        DownloadSwitch(
          downloaded = downloaded,
          onChange = onDownloadedChange,
          modifier = Modifier.tutorialTarget(TutorialTargets.PLIST_DOWNLOAD),
        )
      }
      Detail(R.string.plist_label_type, patientList.typeName)
      Detail(R.string.plist_label_location, patientList.locationName)
      Detail(R.string.plist_label_created, created)
      Detail(R.string.plist_label_changed, changed)
      if (patientList.attributes.isNotEmpty()) {
        Text(
          modifier = Modifier.padding(top = 4.dp),
          text = stringResource(R.string.plist_label_attributes).uppercase(),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        patientList.attributes.forEach { (type, value) -> Detail(type, value) }
      }
      if (patientList.state == PatientListState.DELETING) {
        TextButton(onClick = onUndoDelete) {
          Text(text = stringResource(R.string.plist_action_undo))
        }
      } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          // A session is held with the patients of the device: only a downloaded list can have one.
          if (downloaded) {
            Button(
              modifier =
                Modifier.testTag(PatientListDetailTestTags.StartSession)
                  .tutorialTarget(TutorialTargets.PLIST_SESSION),
              onClick = onStartSession,
            ) {
              Text(text = stringResource(R.string.plist_start_session))
            }
          }
          if (canEdit) {
            OutlinedButton(
              modifier = Modifier.testTag(PatientListDetailTestTags.Edit),
              onClick = onEdit,
            ) {
              Text(text = stringResource(R.string.plist_action_edit))
            }
          }
          OutlinedButton(
            modifier =
              Modifier.testTag(PatientListDetailTestTags.History)
                .tutorialTarget(TutorialTargets.PLIST_HISTORY),
            onClick = onHistory,
          ) {
            Text(text = stringResource(R.string.plist_action_history))
          }
          OutlinedButton(
            modifier =
              Modifier.testTag(PatientListDetailTestTags.Export)
                .tutorialTarget(TutorialTargets.PLIST_EXPORT),
            onClick = onExport,
          ) {
            Text(text = stringResource(R.string.plist_action_export))
          }
          if (canEdit) {
            TextButton(
              modifier = Modifier.testTag(PatientListDetailTestTags.Delete),
              onClick = onDelete,
            ) {
              Text(
                text = stringResource(R.string.plist_action_delete),
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DownloadSwitch(
  downloaded: Boolean,
  onChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().testTag("PatientListDownloadSwitch"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(
        text = stringResource(R.string.plist_download),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = stringResource(R.string.plist_download_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = downloaded, onCheckedChange = onChange)
  }
}

@Composable
private fun stringsOf(parts: List<String>): String =
  if (parts.size == 2) stringResource(R.string.plist_by_on, parts[0], parts[1]) else parts.first()

@Composable
private fun Detail(@androidx.annotation.StringRes label: Int, value: String?) {
  if (value.isNullOrBlank()) return
  Detail(stringResource(label), value)
}

@Composable
private fun Detail(label: String, value: String) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = value, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun Notices(
  patientList: PatientListEntry,
  state: PatientListDetailUiState,
  online: Boolean,
  onSendNow: () -> Unit,
  onDiscard: () -> Unit,
  onRefresh: () -> Unit,
  onOpenCompatibility: () -> Unit,
) {
  Column(
    modifier = Modifier.padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (!state.features.editLists || !state.features.changeMembers) {
      PatientListBanner(
        modifier = Modifier.testTag(PatientListDetailTestTags.ReadOnlyNotice),
        text =
          stringResource(
            if (!state.features.editLists) {
              R.string.plist_readonly_banner
            } else {
              R.string.plist_readonly_members_banner
            },
          ),
        actionLabel = stringResource(R.string.plist_readonly_open),
        onAction = onOpenCompatibility,
      )
    }
    if (state.refreshFailed) {
      PatientListBanner(
        text = stringResource(R.string.plist_members_offline),
        actionLabel = stringResource(R.string.plists_try_again),
        onAction = onRefresh,
      )
    }
    if (patientList.pendingChanges > 0) {
      val failed = patientList.error != null
      PatientListBanner(
        modifier = Modifier.testTag("PatientListDetailPendingNotice"),
        text =
          if (failed) {
            stringResource(R.string.plist_refused_reason, patientList.error.orEmpty())
          } else {
            pendingChangesText(patientList.pendingChanges)
          },
        isError = failed,
        actionLabel =
          stringResource(
            if (state.sending) R.string.plists_sending else R.string.plists_send_now,
          ),
        onAction = if (online && !state.sending) onSendNow else null,
        secondLabel = stringResource(R.string.plist_action_discard),
        onSecond = onDiscard,
      )
    }
  }
}

@Composable
private fun MembersHeader(total: Int, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(R.string.plist_members_header).uppercase(),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      modifier = Modifier.padding(start = 8.dp),
      text = total.toString(),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun EmptyMembers(searching: Boolean) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text =
        stringResource(
          if (searching) R.string.plist_members_no_match else R.string.plist_members_empty_title,
        ),
      style = MaterialTheme.typography.titleSmall,
    )
    if (!searching) {
      Text(
        text = stringResource(R.string.plist_members_empty_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun MemberRow(
  member: PatientListMember,
  canRemove: Boolean,
  onRemove: () -> Unit,
  onUndo: () -> Unit,
) {
  val removing = member.pending == MemberPending.REMOVE
  val adding = member.pending == MemberPending.ADD
  val name = member.name.ifBlank { stringResource(R.string.plist_member_unknown) }
  val details =
    listOfNotNull(
        memberAgeGender(member).ifBlank { null },
        member.identifier?.takeIf { it.isNotBlank() },
      )
      .joinToString(" · ")
  Surface(
    modifier =
      Modifier.padding(horizontal = 16.dp)
        .fillMaxWidth()
        .testTag(PatientListDetailTestTags.member(member.patientId)),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        1.dp,
        if (member.error != null) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.outlineVariant
        },
      ),
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PatientAvatar(name = name, size = 40.dp)
      Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(
          text = name,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          textDecoration = if (removing) TextDecoration.LineThrough else null,
          color =
            if (removing) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.onSurface
            },
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (details.isNotEmpty()) {
          Text(
            text = details,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (removing || adding || member.error != null) {
          PatientListTag(
            modifier = Modifier.padding(top = 4.dp),
            text =
              if (member.error == PatientListsRepository.AMBIGUOUS_UPLOAD) {
                stringResource(R.string.plist_ambiguous_upload_tag)
              } else if (member.error != null) {
                stringResource(R.string.plist_refused_reason, member.error)
              } else {
                stringResource(
                  when {
                    removing -> R.string.plist_member_will_remove
                    member.toIdentify -> R.string.plist_to_identify_tag
                    member.waitingForUpload -> R.string.plist_waits_upload_tag
                    else -> R.string.plist_member_will_add
                  },
                )
              },
            isError = member.error != null,
          )
        }
      }
      when {
        member.pending != null ->
          TextButton(onClick = onUndo) { Text(text = stringResource(R.string.plist_action_undo)) }
        canRemove ->
          IconButton(
            modifier = Modifier.testTag(PatientListDetailTestTags.removeMember(member.patientId)),
            onClick = onRemove,
          ) {
            Icon(
              imageVector = Icons.Default.PersonRemove,
              contentDescription = stringResource(R.string.plist_member_remove),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
      }
    }
  }
}

@Composable
internal fun memberAgeGender(member: PatientListMember): String =
  ageGenderLabel(member.birthDate, member.gender)

@Composable
internal fun ageGenderLabel(birthDate: String?, gender: String?): String {
  // The date is read first: a composable cannot be called inside a try.
  val born = birthDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
  val period = born?.let { Period.between(it, LocalDate.now()) }
  val age =
    when {
      period == null -> null
      period.years > 0 -> stringResource(R.string.age_years_short, period.years)
      period.months > 0 -> stringResource(R.string.age_months_short, period.months)
      else -> stringResource(R.string.age_days_short, period.days.coerceAtLeast(0))
    }
  val sex = gender?.firstOrNull()?.uppercase()
  return listOfNotNull(age, sex).joinToString(", ")
}
