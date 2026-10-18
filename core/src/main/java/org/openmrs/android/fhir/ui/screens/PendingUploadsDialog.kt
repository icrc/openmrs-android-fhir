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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.PendingUser
import org.openmrs.android.fhir.extensions.isAppInDarkTheme

/**
 * The small sync icon of the login page. A cloud with a tick when nothing is waiting to upload; an
 * amber cloud with an arrow, and the number of users, when some user still has data on the device
 * that has not reached the server. A tap opens [PendingUploadsDialog]. [pendingUsers] is `null`
 * until it is known, which looks like "nothing waiting" (the icon never blocks the page).
 */
@Composable
fun PendingUploadsButton(
  pendingUsers: List<PendingUser>?,
  onUseUser: ((String) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  var showDialog by remember { mutableStateOf(false) }
  val waiting = !pendingUsers.isNullOrEmpty()
  val description =
    if (waiting) {
      stringResource(R.string.login_pending_icon_waiting, pendingUsers.orEmpty().size)
    } else {
      stringResource(R.string.login_pending_icon_none)
    }
  IconButton(
    onClick = { showDialog = true },
    modifier = modifier.size(40.dp).testTag("PendingUploadsButton"),
  ) {
    if (waiting) {
      BadgedBox(
        badge = {
          Badge(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ) {
            Text(text = pendingUsers.orEmpty().size.toString())
          }
        },
        modifier = Modifier.semantics { contentDescription = description },
      ) {
        Icon(
          imageVector = Icons.Filled.CloudUpload,
          contentDescription = null,
          tint = pendingColor(),
        )
      }
    } else {
      Icon(
        imageVector = Icons.Filled.CloudDone,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
      )
    }
  }
  if (showDialog) {
    PendingUploadsDialog(
      pendingUsers = pendingUsers.orEmpty(),
      onUseUser = onUseUser,
      onDismiss = { showDialog = false },
    )
  }
}

/** Amber on both themes: a warning that is not an error. */
@Composable
private fun pendingColor(): Color = if (isAppInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFD97706)

/**
 * The users who still have data on this device waiting to upload, each with a button that takes
 * their name to the login page. Users with nothing waiting are not listed.
 */
@Composable
fun PendingUploadsDialog(
  pendingUsers: List<PendingUser>,
  onUseUser: ((String) -> Unit)?,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("PendingUploadsDialog"),
    title = { Text(text = stringResource(R.string.login_pending_title)) },
    text = {
      if (pendingUsers.isEmpty()) {
        Text(
          text = stringResource(R.string.login_pending_none),
          style = MaterialTheme.typography.bodyMedium,
        )
      } else {
        Column(
          modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = stringResource(R.string.login_pending_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          pendingUsers.forEach { pending ->
            PendingUserRow(
              pending = pending,
              onUse =
                onUseUser?.let { use ->
                  {
                    use(pending.user)
                    onDismiss()
                  }
                },
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag("PendingUploadsClose")) {
        Text(text = stringResource(R.string.close))
      }
    },
  )
}

@Composable
private fun PendingUserRow(pending: PendingUser, onUse: (() -> Unit)?) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    modifier = Modifier.fillMaxWidth().testTag("PendingUser_${pending.user}"),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.size(36.dp).clip(CircleShape).background(pendingColor().copy(alpha = 0.2f)),
        ) {
          Text(
            text = pending.user.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = pendingColor(),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = pending.user,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              if (pending.patients > 0) {
                stringResource(R.string.login_pending_counts, pending.patients, pending.records)
              } else {
                stringResource(R.string.login_pending_records_only, pending.records)
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (onUse != null) {
        Button(
          onClick = onUse,
          modifier =
            Modifier.fillMaxWidth()
              .padding(top = 10.dp)
              .testTag("PendingUserLogin_${pending.user}"),
        ) {
          Text(text = stringResource(R.string.login_pending_use_user))
        }
      }
    }
  }
}
