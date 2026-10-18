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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

/** What is known of a patient's most recent encounter, for the list row. */
sealed interface LastEncounterLabel {
  /** Not looked up yet. */
  data object Loading : LastEncounterLabel

  data object None : LastEncounterLabel

  data class Value(val name: String?, val date: LocalDate?) : LastEncounterLabel
}

/**
 * One patient of the list, as a card: an initials avatar, the name, the identifier with age and
 * gender, the patient's last encounter (what and when) and, for patients that have not been
 * uploaded yet, a sync marker.
 */
@Composable
fun PatientListItemRow(
  name: String,
  ageGenderLabel: String,
  isSynced: Boolean?,
  modifier: Modifier = Modifier,
  identifier: String? = null,
  lastEncounter: LastEncounterLabel = LastEncounterLabel.Loading,
  onClick: (() -> Unit)? = null,
  /** A patient found on the server, not on this device yet: no last encounter, a cloud icon. */
  onServer: Boolean = false,
  /** The patient is being brought from the server. */
  isOpening: Boolean = false,
) {
  Surface(
    modifier =
      modifier.fillMaxWidth().testTag("PatientListItemRow").let { base ->
        onClick?.let { base.clickable(onClick = it) } ?: base
      },
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shadowElevation = 1.dp,
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth().heightIn(min = if (onServer) 84.dp else 104.dp).padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PatientAvatar(name = name)
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            modifier = Modifier.weight(1f, fill = false).testTag("PatientName"),
            text = name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (isSynced != true) {
            Icon(
              modifier = Modifier.padding(start = 8.dp).size(20.dp).testTag("PatientSyncIcon"),
              painter = painterResource(id = R.drawable.ic_baseline_sync_24),
              contentDescription = stringResource(id = R.string.description_status),
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
        Row(
          modifier = Modifier.padding(top = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (!identifier.isNullOrBlank()) {
            Text(
              modifier = Modifier.weight(1f, fill = false).testTag("PatientIdentifier"),
              text = identifier,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(10.dp))
          }
          if (ageGenderLabel.isNotBlank()) {
            Text(
              modifier =
                Modifier.clip(RoundedCornerShape(50))
                  .background(MaterialTheme.colorScheme.secondaryContainer)
                  .padding(horizontal = 10.dp, vertical = 2.dp)
                  .testTag("PatientAgeGender"),
              text = ageGenderLabel,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          } else {
            Box(modifier = Modifier.size(0.dp).testTag("PatientAgeGender"))
          }
        }
        if (!onServer) {
          LastEncounterLine(lastEncounter, modifier = Modifier.padding(top = 8.dp))
        }
      }
      when {
        isOpening ->
          CircularProgressIndicator(
            modifier = Modifier.padding(start = 8.dp).size(24.dp),
            strokeWidth = 2.dp,
          )
        onServer ->
          Icon(
            imageVector = Icons.Outlined.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp).size(26.dp),
          )
        else ->
          Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp).size(26.dp),
          )
      }
    }
  }
}

@Composable
private fun LastEncounterLine(label: LastEncounterLabel, modifier: Modifier = Modifier) {
  val locale = Locale.getDefault()
  val text =
    when (label) {
      LastEncounterLabel.Loading -> "…"
      LastEncounterLabel.None -> stringResource(id = R.string.last_encounter_none)
      is LastEncounterLabel.Value -> {
        val date =
          label.date?.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
          )
        listOfNotNull(
            label.name ?: stringResource(id = R.string.last_encounter_unknown_type),
            date,
          )
          .joinToString(" · ")
      }
    }
  val hasValue = label is LastEncounterLabel.Value
  Row(
    modifier = modifier.testTag("PatientLastEncounter"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Filled.Description,
      contentDescription = stringResource(id = R.string.last_encounter),
      tint =
        if (hasValue) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
      modifier = Modifier.size(18.dp),
    )
    Text(
      modifier = Modifier.padding(start = 8.dp),
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = if (hasValue) FontWeight.Medium else FontWeight.Normal,
      fontStyle = if (hasValue) FontStyle.Normal else FontStyle.Italic,
      color =
        if (hasValue) {
          MaterialTheme.colorScheme.onSurface
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** The patient's initials in a tonal circle. */
@Composable
internal fun PatientAvatar(
  name: String,
  modifier: Modifier = Modifier,
  size: androidx.compose.ui.unit.Dp = 56.dp,
) {
  val initials =
    name
      .split(' ', '-')
      .filter { it.isNotBlank() }
      .take(2)
      .joinToString("") { it.first().uppercase() }
      .ifEmpty { "?" }
  Box(
    modifier =
      modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = initials,
      style =
        if (size < 48.dp) {
          MaterialTheme.typography.titleSmall
        } else {
          MaterialTheme.typography.titleMedium
        },
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
  }
}

/**
 * One location: a tonal pin (a check when it is the current location), the name, and the favorite
 * star. The whole card selects the location; the star only toggles the favorite.
 */
@Composable
fun LocationListItemRow(
  name: String,
  isFavorite: Boolean,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
  onFavoriteClick: () -> Unit,
  onClick: () -> Unit,
) {
  val primary = MaterialTheme.colorScheme.primary
  Surface(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().testTag("LocationListItem"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        width = if (isSelected) 2.dp else 1.dp,
        color = if (isSelected) primary else MaterialTheme.colorScheme.outlineVariant,
      ),
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .background(if (isSelected) primary else OpenMrsTheme.colors.highlightContainer),
        contentAlignment = Alignment.Center,
      ) {
        if (isSelected) {
          Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp),
          )
        } else {
          Icon(
            painter = painterResource(id = R.drawable.ic_location),
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(22.dp),
          )
        }
      }

      Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
          modifier = Modifier.testTag("LocationName"),
          text = name,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (isSelected) {
          Text(
            text = stringResource(id = R.string.location_current_badge),
            style = MaterialTheme.typography.labelSmall,
            color = primary,
          )
        }
      }

      IconButton(modifier = Modifier.testTag("LocationFavorite"), onClick = onFavoriteClick) {
        Icon(
          painter =
            painterResource(
              id = if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outlined,
            ),
          contentDescription = stringResource(id = R.string.description_status),
          tint = if (isFavorite) primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
