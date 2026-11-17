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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.patientlists.MemberPending
import org.openmrs.android.fhir.patientlists.Membership
import org.openmrs.android.fhir.patientlists.PatientMembership
import org.openmrs.android.fhir.ui.components.PatientListTag
import org.openmrs.android.fhir.viewmodel.PatientMembershipUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatientListMembershipCard(
  state: PatientMembershipUiState,
  onManage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (state.lists.isEmpty()) return
  Surface(
    modifier = modifier.fillMaxWidth().testTag("PatientListMembershipCard"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          modifier = Modifier.weight(1f),
          text = stringResource(R.string.plist_card_title).uppercase(),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        if (state.canChange) {
          TextButton(
            modifier = Modifier.testTag("PatientListMembershipManage"),
            onClick = onManage,
          ) {
            Text(text = stringResource(R.string.plist_card_manage))
          }
        }
      }
      val inLists = state.inLists
      if (inLists.isEmpty()) {
        Text(
          modifier = Modifier.padding(bottom = 4.dp),
          text = stringResource(R.string.plist_card_none),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        FlowRow(
          modifier = Modifier.padding(end = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          inLists.forEach { membership ->
            Surface(
              shape = RoundedCornerShape(50),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                text = membership.list.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun PatientListMembershipDialog(
  state: PatientMembershipUiState,
  onToggle: (listId: String, member: Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.plist_membership_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text =
            stringResource(
              if (state.unsent) {
                R.string.plist_membership_unsent
              } else {
                R.string.plist_membership_hint
              },
            ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.reading) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
              modifier = Modifier.padding(end = 8.dp),
              strokeWidth = 2.dp,
            )
            Text(
              text = stringResource(R.string.plist_members_loading),
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
          items(state.lists, key = { it.list.id }) { membership ->
            MembershipRow(
              membership = membership,
              enabled = membership.membership != Membership.UNKNOWN,
              toIdentify = state.toIdentify,
              unsent = state.unsent,
              onToggle = { onToggle(membership.list.id, it) },
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(modifier = Modifier.testTag("PatientListMembershipDone"), onClick = onDismiss) {
        Text(text = stringResource(R.string.close))
      }
    },
  )
}

@Composable
private fun MembershipRow(
  membership: PatientMembership,
  enabled: Boolean,
  toIdentify: Boolean,
  unsent: Boolean,
  onToggle: (Boolean) -> Unit,
) {
  val member = membership.membership == Membership.IN
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(enabled = enabled, role = Role.Checkbox) { onToggle(!member) }
        .padding(vertical = 4.dp)
        .testTag("MembershipRow_${membership.list.id}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = member, onCheckedChange = null, enabled = enabled)
    Column(modifier = Modifier.padding(start = 8.dp)) {
      Text(text = membership.list.name, style = MaterialTheme.typography.bodyLarge)
      when {
        membership.pending == MemberPending.ADD && toIdentify ->
          PatientListTag(text = stringResource(R.string.plist_to_identify_tag), isError = false)
        membership.pending == MemberPending.ADD && unsent ->
          PatientListTag(text = stringResource(R.string.plist_waits_upload_tag), isError = false)
        membership.pending != null ->
          PatientListTag(text = stringResource(R.string.plist_state_edited), isError = false)
      }
    }
  }
}
