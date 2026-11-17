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

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.forms.FormSource
import org.openmrs.android.fhir.forms.FormState
import org.openmrs.android.fhir.forms.FormStatus
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget
import org.openmrs.android.fhir.viewmodel.FormUpdatesUiState

object FormUpdatesTestTags {
  const val Screen = "FormUpdatesScreen"
  const val Check = "FormUpdatesCheck"
  const val TakeAll = "FormUpdatesTakeAll"

  fun row(id: String) = "FormUpdatesRow_$id"

  fun action(id: String) = "FormUpdatesAction_$id"
}

/**
 * The forms of the device next to the server's: where each one comes from, whether the server has a
 * different one, and the buttons that take it. Nothing is asked of the server until the user asks.
 *
 * @property canCheck the user can ask the server (signed in)
 * @property serverReachable the server answered the last time the app looked
 */
@Composable
fun FormUpdatesScreen(
  state: FormUpdatesUiState,
  canCheck: Boolean,
  serverReachable: Boolean,
  onCheck: () -> Unit,
  onTakeAll: () -> Unit,
  onTake: (String) -> Unit,
  onUseApp: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag(FormUpdatesTestTags.Screen),
    contentPadding =
      androidx.compose.foundation.layout.PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = rememberAppBarHeight() + 12.dp,
        bottom = 24.dp,
      ),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item(key = "summary") { Summary(state, canCheck, serverReachable, onCheck, onTakeAll) }
    itemsIndexed(state.forms, key = { _, form -> form.id }) { index, form ->
      FormRow(
        modifier =
          if (index == 0) Modifier.tutorialTarget(TutorialTargets.FORMUPDATES_LIST) else Modifier,
        form = form,
        working = form.id in state.working,
        enabled = !state.busy,
        onTake = { onTake(form.id) },
        onUseApp = { onUseApp(form.id) },
      )
    }
  }
}

@Composable
private fun Summary(
  state: FormUpdatesUiState,
  canCheck: Boolean,
  serverReachable: Boolean,
  onCheck: () -> Unit,
  onTakeAll: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = Modifier.tutorialTarget(TutorialTargets.FORMUPDATES_ACTIONS).padding(bottom = 4.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text =
        stringResource(
          if (state.embeddedOnly) {
            R.string.forms_updates_intro_app
          } else {
            R.string.forms_updates_intro_server
          },
        ),
      style = MaterialTheme.typography.bodyMedium,
      color = colors.onSurfaceVariant,
    )
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = if (state.updates + state.new > 0) colors.secondaryContainer else colors.surface,
      border = BorderStroke(1.dp, colors.outlineVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val checkedLine =
          when {
            state.checking -> stringResource(R.string.forms_updates_checking)
            state.checkedAt > 0 ->
              stringResource(
                R.string.forms_updates_checked,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                  .format(Date(state.checkedAt)),
              )
            else -> stringResource(R.string.forms_updates_never)
          }
        Text(
          text = checkedLine,
          style = MaterialTheme.typography.labelLarge,
          color = colors.onSurfaceVariant,
        )
        if (state.checkedAt > 0 && !state.checking) {
          Text(
            text =
              if (state.updates + state.new == 0) {
                stringResource(R.string.forms_updates_all_current)
              } else {
                listOfNotNull(
                    state.updates
                      .takeIf { it > 0 }
                      ?.let { pluralStringResource(R.plurals.forms_updates_count, it, it) },
                    state.new
                      .takeIf { it > 0 }
                      ?.let { pluralStringResource(R.plurals.forms_new_count, it, it) },
                  )
                  .joinToString(" · ")
              },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color =
              if (state.updates + state.new == 0) colors.onSurface else colors.onSecondaryContainer,
          )
        }
        state.error?.let {
          Text(
            text = stringResource(R.string.forms_updates_error, it),
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
          )
        }
        if (!canCheck) {
          Text(
            text = stringResource(R.string.forms_updates_sign_in),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
          )
        } else if (!serverReachable) {
          Text(
            text = stringResource(R.string.forms_updates_unreachable),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
          )
        }
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(top = 4.dp),
        ) {
          Button(
            onClick = onCheck,
            enabled = canCheck && serverReachable && !state.busy,
            modifier = Modifier.weight(1f).testTag(FormUpdatesTestTags.Check),
          ) {
            if (state.checking) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.onPrimary,
              )
              Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
              text =
                stringResource(
                  if (state.checking) {
                    R.string.forms_updates_checking_short
                  } else {
                    R.string.forms_updates_check
                  },
                ),
            )
          }
          if (state.updates + state.new > 0) {
            FilledTonalButton(
              onClick = onTakeAll,
              enabled = !state.busy,
              modifier = Modifier.weight(1f).testTag(FormUpdatesTestTags.TakeAll),
            ) {
              Text(
                text = stringResource(R.string.forms_updates_take_all, state.updates + state.new),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FormRow(
  modifier: Modifier,
  form: FormStatus,
  working: Boolean,
  enabled: Boolean,
  onTake: () -> Unit,
  onUseApp: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val (icon, tint) = look(form.state)
  Surface(
    modifier = modifier.fillMaxWidth().testTag(FormUpdatesTestTags.row(form.id)),
    shape = RoundedCornerShape(16.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.outlineVariant),
  ) {
    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(24.dp),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = form.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = stringResource(label(form.state)),
          style = MaterialTheme.typography.labelLarge,
          color = tint,
        )
        Text(
          text =
            listOfNotNull(
                stringResource(
                  when (form.inUse) {
                    FormSource.APP -> R.string.forms_using_app
                    FormSource.SERVER -> R.string.forms_using_server
                    FormSource.NONE -> R.string.forms_using_none
                  },
                ),
                form.serverVersion?.let { stringResource(R.string.forms_server_version, it) },
              )
              .joinToString(" · "),
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
        Text(
          text = form.id,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = colors.onSurfaceVariant,
        )
        if (form.inUse == FormSource.SERVER) {
          TextButton(
            onClick = onUseApp,
            enabled = enabled,
            modifier = Modifier.testTag(FormUpdatesTestTags.action(form.id) + "_app"),
          ) {
            Text(text = stringResource(R.string.forms_action_use_app))
          }
        }
      }
      if (working) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
      } else if (
        form.state == FormState.UPDATE_AVAILABLE || form.state == FormState.NEW_ON_SERVER
      ) {
        FilledTonalButton(
          onClick = onTake,
          enabled = enabled,
          modifier = Modifier.testTag(FormUpdatesTestTags.action(form.id)),
        ) {
          Text(
            text =
              stringResource(
                if (form.state == FormState.NEW_ON_SERVER) {
                  R.string.forms_action_add
                } else {
                  R.string.forms_action_update
                },
              ),
          )
        }
      }
    }
  }
}

@Composable
private fun look(state: FormState): Pair<ImageVector, Color> {
  val colors = MaterialTheme.colorScheme
  return when (state) {
    FormState.UP_TO_DATE -> Icons.Default.CheckCircle to OpenMrsTheme.colors.success
    FormState.UPDATE_AVAILABLE -> Icons.Default.CloudDownload to colors.primary
    FormState.NEW_ON_SERVER -> Icons.Default.AddCircle to colors.primary
    FormState.NOT_ON_SERVER -> Icons.Default.RemoveCircleOutline to colors.onSurfaceVariant
    FormState.NOT_USABLE -> Icons.Default.Warning to colors.error
    FormState.NOT_CHECKED -> Icons.AutoMirrored.Filled.HelpOutline to colors.onSurfaceVariant
  }
}

@StringRes
private fun label(state: FormState): Int =
  when (state) {
    FormState.NOT_CHECKED -> R.string.forms_state_not_checked
    FormState.UP_TO_DATE -> R.string.forms_state_up_to_date
    FormState.UPDATE_AVAILABLE -> R.string.forms_state_update
    FormState.NEW_ON_SERVER -> R.string.forms_state_new
    FormState.NOT_ON_SERVER -> R.string.forms_state_not_on_server
    FormState.NOT_USABLE -> R.string.forms_state_not_usable
  }
