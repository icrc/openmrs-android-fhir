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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R

@Composable
fun PatientListItemRow(
  name: String,
  ageGenderLabel: String,
  isSynced: Boolean?,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  Surface(
    modifier =
      modifier.testTag("PatientListItemRow").let { base ->
        onClick?.let { base.clickable(onClick = it) } ?: base
      },
    color = colorResource(id = R.color.white),
  ) {
    Row(
      modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Start,
    ) {
      if (isSynced != true) {
        Icon(
          modifier = Modifier.size(20.dp).testTag("PatientSyncIcon"),
          painter = painterResource(id = R.drawable.ic_baseline_sync_24),
          contentDescription = stringResource(id = R.string.description_status),
          tint = MaterialTheme.colorScheme.error,
        )
      }

      Spacer(modifier = Modifier.width(12.dp))
      Text(
        modifier = Modifier.weight(1f).testTag("PatientName"),
        text = name,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        modifier = Modifier.testTag("PatientAgeGender").padding(end = 8.dp),
        text = ageGenderLabel,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun IdentifierTypeListItemRow(
  name: String,
  isRequired: Boolean,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val showIcon = isSelected || isRequired
  val background =
    if (isSelected) {
      colorResource(id = R.color.selected_item_background)
    } else {
      colorResource(id = R.color.white)
    }

  Surface(
    modifier = modifier.testTag("IdentifierTypeRow").clip(RoundedCornerShape(8.dp)),
    color = background,
    shadowElevation = if (isSelected) 1.dp else 0.dp,
  ) {
    Row(
      modifier =
        Modifier.padding(horizontal = 12.dp, vertical = 10.dp).let { base ->
          if (onClick != null && !isRequired) base.clickable(onClick = onClick) else base
        },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        modifier = Modifier.weight(1f).testTag("IdentifierTypeName"),
        text = name,
        style = MaterialTheme.typography.bodyLarge,
      )

      val iconTint =
        if (isRequired) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        }
      val iconModifier = Modifier.testTag("IdentifierTypeIcon")

      if (showIcon) {
        Icon(
          modifier = iconModifier.size(20.dp),
          painter =
            painterResource(
              id =
                if (isRequired) {
                  R.drawable.ic_check_decagram_green
                } else {
                  R.drawable.ic_check_decagram
                },
            ),
          contentDescription = stringResource(id = R.string.description_status),
          tint = iconTint,
        )
      } else {
        // Keep a tagged node for testing while preventing the icon from being laid out/visible.
        Box(modifier = iconModifier.size(0.dp).semantics { hideFromAccessibility() })
      }
    }
  }
}

@Composable
fun LocationListItemRow(
  name: String,
  isFavorite: Boolean,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
  onFavoriteClick: () -> Unit,
  onClick: () -> Unit,
) {
  val backgroundColor =
    if (isSelected) {
      colorResource(id = R.color.selected_item_background)
    } else {
      colorResource(id = R.color.white)
    }

  Surface(
    modifier = modifier.clip(RoundedCornerShape(8.dp)).testTag("LocationListItem"),
    color = backgroundColor,
    tonalElevation = if (isSelected) 1.dp else 0.dp,
  ) {
    Row(
      modifier =
        Modifier.padding(horizontal = 12.dp, vertical = 12.dp).clickable(onClick = onClick),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          modifier = Modifier.testTag("LocationName"),
          text = name,
          style = MaterialTheme.typography.bodyLarge,
        )
      }

      IconButton(modifier = Modifier.testTag("LocationFavorite"), onClick = onFavoriteClick) {
        Icon(
          painter =
            painterResource(
              id = if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outlined,
            ),
          contentDescription = stringResource(id = R.string.description_status),
          tint =
            if (isFavorite) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
      }
    }
  }
}
