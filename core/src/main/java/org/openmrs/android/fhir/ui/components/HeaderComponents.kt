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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.remote.ServerConnectivityState
import org.openmrs.android.fhir.extensions.isAppInDarkTheme
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

/**
 * A slim strip under the status bar that says whether the server can be reached: a colored dot and
 * the status on a soft tint of the same color (green connected, amber server unreachable, red
 * offline).
 *
 * While a sync runs ([syncText] set) it turns into the sync's progress: a spinner, "Syncing" with
 * the percentage, and a thin progress bar along its bottom edge ([syncProgress] null while the
 * amount of work is not known yet). Otherwise [liveText] says what live mode is doing and
 * [nextSyncText] when the automatic sync is due.
 */
@Composable
fun NetworkStatusBanner(
  text: String,
  modifier: Modifier = Modifier,
  state: ServerConnectivityState = ServerConnectivityState.ServerConnected,
  syncText: String? = null,
  syncProgress: Float? = null,
  nextSyncText: String? = null,
  onDelayClick: (() -> Unit)? = null,
  liveText: String? = null,
) {
  val isSyncing = syncText != null
  val accent =
    when {
      isSyncing -> MaterialTheme.colorScheme.primary
      state == ServerConnectivityState.ServerConnected -> OpenMrsTheme.colors.success
      state == ServerConnectivityState.InternetOnly -> OpenMrsTheme.colors.warning
      else -> MaterialTheme.colorScheme.error
    }
  Column(
    modifier =
      modifier.fillMaxWidth().background(accent.copy(alpha = 0.14f)).testTag("NetworkStatusBanner"),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(horizontal = 16.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (isSyncing) {
        CircularProgressIndicator(
          modifier = Modifier.size(12.dp),
          strokeWidth = 2.dp,
          color = accent,
        )
      } else {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
      }
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        modifier = Modifier.testTag("NetworkStatusText"),
        text = syncText ?: text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
      if (!isSyncing && liveText != null) {
        Text(
          modifier = Modifier.padding(start = 6.dp).testTag("LiveStatusText"),
          text = "· $liveText",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
        )
      }
      if (!isSyncing && nextSyncText != null) {
        Text(
          modifier = Modifier.padding(start = 6.dp).testTag("NextSyncText"),
          text = "· $nextSyncText",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
        if (onDelayClick != null) {
          Text(
            modifier =
              Modifier.padding(start = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onDelayClick)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("DelaySync"),
            text = stringResource(R.string.auto_sync_delay_link),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
          )
        }
      }
    }
    if (isSyncing) {
      val barModifier = Modifier.fillMaxWidth().height(3.dp).testTag("NetworkSyncProgress")
      if (syncProgress != null) {
        val animated by
          animateFloatAsState(targetValue = syncProgress, label = "banner-sync-progress")
        LinearProgressIndicator(
          progress = { animated },
          modifier = barModifier,
          color = accent,
          trackColor = accent.copy(alpha = 0.18f),
          gapSize = 0.dp,
          drawStopIndicator = {},
        )
      } else {
        LinearProgressIndicator(
          modifier = barModifier,
          color = accent,
          trackColor = accent.copy(alpha = 0.18f),
        )
      }
    } else {
      // Same height as with the progress bar, so the content below does not jump when a sync
      // starts.
      Spacer(modifier = Modifier.height(3.dp))
    }
  }
}

/**
 * Top of the side menu: the same color as the app bar behind the status bar (brand teal, a dark
 * surface in the night theme), the logo and when the data was last synchronized.
 */
@Composable
fun DrawerHeader(
  label: String,
  lastSyncValue: String,
  modifier: Modifier = Modifier,
) {
  val dark = isAppInDarkTheme()
  val barColor =
    if (dark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primary
  val barContent =
    if (dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(barColor)
        .statusBarsPadding()
        .padding(horizontal = 20.dp, vertical = 20.dp)
        .testTag("DrawerHeader"),
    horizontalAlignment = Alignment.Start,
  ) {
    Image(
      // The wordmark in white: it sits straight on the bar, without a plate behind it.
      painter = painterResource(id = R.drawable.openmrs_logo_light),
      contentDescription = null,
      modifier = Modifier.height(40.dp),
    )
    Text(
      modifier = Modifier.padding(top = 20.dp).testTag("DrawerHeaderLabel"),
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = barContent.copy(alpha = 0.8f),
    )
    Text(
      modifier = Modifier.padding(top = 2.dp).testTag("DrawerHeaderValue"),
      text = lastSyncValue,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = barContent,
    )
  }
}
