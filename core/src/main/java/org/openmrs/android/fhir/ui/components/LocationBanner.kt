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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

/** What the banner on top of the patient form says about the location the work is done at. */
sealed interface LocationBannerState {
  /** The saved location has not been read yet: the banner keeps its place without a flash. */
  data object Loading : LocationBannerState

  data class Selected(val name: String) : LocationBannerState

  /** No location chosen; a guest picks it after signing in, anyone else from the Home menu. */
  data class None(val guest: Boolean) : LocationBannerState
}

/**
 * Hosts [LocationBanner] under the app bar in a layout that is still XML: the view pads itself by
 * the height of the bar, so the form below it starts where the banner ends.
 */
fun ComposeView.setLocationBanner(state: State<LocationBannerState>, showHint: Boolean) {
  setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  setContent {
    OpenMrsTheme {
      LocationBanner(
        state = state.value,
        showHint = showHint,
        modifier = Modifier.padding(top = rememberAppBarHeight()),
      )
    }
  }
}

@Composable
fun LocationBanner(
  state: LocationBannerState,
  modifier: Modifier = Modifier,
  showHint: Boolean = true,
) {
  val none = state as? LocationBannerState.None
  // A missing location is a warning, except for a guest, who has none to choose yet.
  val warning = none != null && !none.guest
  // Plain on the screen's background; only a missing location gets a tinted band.
  val container =
    if (warning) OpenMrsTheme.colors.warning.copy(alpha = 0.18f) else Color.Transparent
  val content = MaterialTheme.colorScheme.onSurface
  val badge = if (warning) OpenMrsTheme.colors.warning else MaterialTheme.colorScheme.primary
  val onBadge = if (warning) Color(0xFF161616) else MaterialTheme.colorScheme.onPrimary

  Surface(
    modifier = modifier.fillMaxWidth().testTag("LocationBanner"),
    color = container,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(badge),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (warning) Icons.Default.Warning else Icons.Default.LocationOn,
          contentDescription = null,
          tint = onBadge,
          modifier = Modifier.size(20.dp),
        )
      }
      Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
        Text(
          text = stringResource(R.string.location_banner_label),
          style = MaterialTheme.typography.labelSmall,
          color = content.copy(alpha = 0.75f),
        )
        when (state) {
          is LocationBannerState.Selected ->
            Text(
              text = state.name,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = content,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("LocationBannerName"),
            )
          is LocationBannerState.None -> {
            Text(
              text = stringResource(R.string.location_banner_none),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = content,
              modifier = Modifier.testTag("LocationBannerNone"),
            )
            if (showHint) {
              Text(
                text =
                  stringResource(
                    if (state.guest) {
                      R.string.location_banner_guest_hint
                    } else {
                      R.string.location_banner_none_hint
                    },
                  ),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.85f),
              )
            }
          }
          // Same height as a one-line name, so the form does not jump when it is read.
          LocationBannerState.Loading ->
            Text(text = " ", style = MaterialTheme.typography.titleSmall)
        }
      }
    }
  }
}
