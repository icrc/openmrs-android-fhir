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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hl7.fhir.r4.model.Identifier
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

@Composable
fun PatientPropertyRow(
  header: String,
  value: String,
  showSyncIcon: Boolean,
  modifier: Modifier = Modifier,
  onPrimaryClick: (() -> Unit)? = null,
) {
  Surface(color = MaterialTheme.colorScheme.surface) {
    Row(
      modifier =
        modifier
          .fillMaxWidth()
          .padding(
            start = dimensionResource(id = R.dimen.text_margin),
            end = 8.dp,
            top = 8.dp,
            bottom = 8.dp,
          )
          .testTag("PatientPropertyRow"),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (showSyncIcon) {
        Icon(
          modifier = Modifier.size(20.dp).padding(end = 8.dp).testTag("SyncStatusIcon"),
          painter = painterResource(id = R.drawable.ic_baseline_sync_24),
          contentDescription = stringResource(id = R.string.description_status),
          tint = OpenMrsTheme.colors.warning,
        )
      } else {
        Spacer(modifier = Modifier.width(28.dp))
      }

      Column(
        modifier =
          Modifier.weight(1f).let { base ->
            onPrimaryClick?.let { base.clickable(role = Role.Button, onClick = it) } ?: base
          },
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          modifier = Modifier.testTag("PropertyLabel"),
          text = header,
          color = Color.DarkGray,
          fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          modifier = Modifier.testTag("PropertyValue"),
          text = value,
          fontSize = 16.sp,
          fontWeight = FontWeight.Normal,
        )
      }
    }
  }
}

/** Section title with an optional count, e.g. "ENCOUNTERS 4". */
@Composable
fun PatientDetailsHeaderRow(
  title: String,
  modifier: Modifier = Modifier,
  count: Int? = null,
  actionText: String? = null,
  onActionClick: () -> Unit = {},
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp)
        .testTag("PatientDetailsHeader"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
    )
    if (count != null) {
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (actionText != null) {
      Spacer(modifier = Modifier.weight(1f))
      TextButton(
        onClick = onActionClick,
        modifier = Modifier.testTag("PatientDetailsHeaderAction"),
      ) {
        Text(text = actionText)
      }
    }
  }
}

/** One line of the patient's profile: a small icon, a caption and the value. */
data class PatientDetailLine(
  val icon: ImageVector,
  val label: String,
  val value: String,
  /**
   * Tapping the value does this (call the number, write to the address); not tappable if `null`.
   */
  val onClick: (() -> Unit)? = null,
)

/**
 * A line of the profile; a number or an address that can be acted on looks and acts like a link.
 */
@Composable
private fun PatientDetailLineRow(line: PatientDetailLine) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .then(
          if (line.onClick != null) {
            Modifier.clickable(role = Role.Button, onClick = line.onClick)
          } else {
            Modifier
          },
        )
        .testTag("PatientDetailLine"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = line.icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
    Spacer(modifier = Modifier.width(14.dp))
    Column {
      Text(
        text = line.label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = line.value,
        style = MaterialTheme.typography.bodyLarge,
        color =
          if (line.onClick != null) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurface
          },
        textDecoration = if (line.onClick != null) TextDecoration.Underline else null,
      )
    }
  }
}

/**
 * The patient's profile card: initials avatar, name, age and gender, the identifiers as chips and
 * (when given) the contact details and an edit button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatientDetailsOverviewHeader(
  name: String,
  identifiers: List<Identifier>,
  modifier: Modifier = Modifier,
  ageGender: String = "",
  details: List<PatientDetailLine> = emptyList(),
  /** More facts, shown under "Show all details". */
  extraDetails: List<PatientDetailLine> = emptyList(),
  onEditClick: (() -> Unit)? = null,
  /** Something about the patient has not been sent to the server yet. */
  unsynced: Boolean = false,
) {
  var showAll by rememberSaveable { mutableStateOf(false) }
  val visibleIdentifiers =
    identifiers
      .filterNot { identifier ->
        identifier.type?.text?.equals("unsynced", ignoreCase = true) == true
      }
      .mapNotNull { identifier ->
        val typeText = identifier.type?.text.orEmpty()
        val valueText = identifier.value.orEmpty()
        when {
          valueText.isBlank() -> null
          typeText.isNotBlank() -> "$typeText: $valueText"
          else -> valueText
        }
      }
  Surface(
    modifier = modifier.fillMaxWidth().testTag("PatientDetailsOverviewHeader"),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shadowElevation = 1.dp,
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = initialsOf(name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            modifier = Modifier.testTag("PatientDetailsName"),
            text = name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (ageGender.isNotBlank()) {
            Text(
              modifier = Modifier.testTag("PatientDetailsAgeGender"),
              text = ageGender,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (unsynced) {
        PatientUnsyncedCard(modifier = Modifier.padding(top = 16.dp))
      }

      if (visibleIdentifiers.isNotEmpty()) {
        FlowRow(
          modifier = Modifier.padding(top = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          visibleIdentifiers.forEach { label ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = OpenMrsTheme.colors.highlightContainer,
            ) {
              Text(
                modifier =
                  Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("PatientDetailsIdentifier"),
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      if (details.isNotEmpty()) {
        HorizontalDivider(
          modifier = Modifier.padding(vertical = 16.dp),
          color = MaterialTheme.colorScheme.outlineVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          details.forEach { line -> PatientDetailLineRow(line) }
        }
      }

      if (extraDetails.isNotEmpty()) {
        if (showAll) {
          if (details.isEmpty()) {
            HorizontalDivider(
              modifier = Modifier.padding(vertical = 16.dp),
              color = MaterialTheme.colorScheme.outlineVariant,
            )
          } else {
            Spacer(modifier = Modifier.height(12.dp))
          }
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            extraDetails.forEach { line -> PatientDetailLineRow(line) }
          }
        }
        TextButton(
          onClick = { showAll = !showAll },
          modifier = Modifier.padding(top = 8.dp).testTag("PatientShowAllDetails"),
        ) {
          Icon(
            imageVector = if (showAll) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text =
              stringResource(
                if (showAll) R.string.patient_details_hide else R.string.patient_details_show_all,
              ),
          )
        }
      }

      if (onEditClick != null) {
        FilledTonalButton(
          onClick = onEditClick,
          modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("PatientEditButton"),
        ) {
          Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(id = R.string.edit_patient))
        }
      }
    }
  }
}

private fun initialsOf(name: String): String =
  name
    .split(' ', '-')
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifEmpty { "?" }

/**
 * Tells the user the patient has changes that were not uploaded yet. A soft strip for the inside of
 * the patient's card, under the name.
 */
@Composable
fun PatientUnsyncedCard(modifier: Modifier = Modifier) {
  val warning = OpenMrsTheme.colors.warning
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(warning.copy(alpha = 0.14f))
        .padding(horizontal = 12.dp, vertical = 10.dp)
        .testTag("PatientUnsyncedCard"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier.size(32.dp).clip(CircleShape).background(warning),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_baseline_sync_24),
        contentDescription = stringResource(id = R.string.task_image),
        // Dark on the yellow badge, in both themes.
        tint = androidx.compose.ui.graphics.Color(0xFF3A2C00),
        modifier = Modifier.size(18.dp),
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = stringResource(id = R.string.patient_unsynced_info),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
