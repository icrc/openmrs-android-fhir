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

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.VisitEditState
import org.openmrs.android.fhir.data.VisitPeriodError
import org.openmrs.android.fhir.data.VisitPeriodRules

/**
 * Changes when a visit started and when it ended. The visit has to contain its encounters, so the
 * span they take is shown and a start after the first one, or an end before the last one, is
 * refused with the reason. [onSave] returns what stopped it (`null`: saved, the dialog closes).
 */
@Composable
fun EditVisitDialog(
  state: VisitEditState,
  onSave: suspend (start: Date, end: Date?) -> VisitPeriodError?,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var start by rememberSaveable { mutableLongStateOf(state.start.time) }
  var open by rememberSaveable { mutableStateOf(state.end == null) }
  // A visit that is still open and gets an end starts from now.
  var end by rememberSaveable { mutableLongStateOf((state.end ?: Date()).time) }
  var serverError by rememberSaveable { mutableStateOf<String?>(null) }
  var saving by remember { mutableStateOf(false) }

  val dateFormat = remember { DateFormat.getMediumDateFormat(context) }
  val timeFormat = remember { DateFormat.getTimeFormat(context) }
  fun Date.asText() = dateFormat.format(this) + " " + timeFormat.format(this)

  val problem =
    VisitPeriodRules.check(
      start = Date(start),
      end = if (open) null else Date(end),
      earliest = state.earliest,
      latest = state.latest,
    )

  AlertDialog(
    onDismissRequest = { if (!saving) onDismiss() },
    modifier = Modifier.testTag("EditVisitDialog"),
    title = { Text(text = stringResource(R.string.visit_edit_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.earliest != null && state.latest != null) {
          Text(
            text =
              stringResource(
                R.string.visit_edit_encounters_range,
                state.earliest.asText(),
                state.latest.asText(),
              ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        DateTimeRow(
          label = stringResource(R.string.visit_edit_start),
          millis = start,
          onChange = {
            start = it
            serverError = null
          },
          tag = "VisitEditStart",
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = stringResource(R.string.visit_edit_still_open),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
          )
          Switch(
            checked = open,
            onCheckedChange = {
              open = it
              serverError = null
            },
            modifier = Modifier.testTag("VisitEditOpen"),
          )
        }
        if (!open) {
          DateTimeRow(
            label = stringResource(R.string.visit_edit_end),
            millis = end,
            onChange = {
              end = it
              serverError = null
            },
            tag = "VisitEditEnd",
          )
        }
        val message = serverError ?: problem?.let { visitErrorText(it, state) }
        if (message != null) {
          Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("VisitEditError"),
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = problem == null && !saving,
        modifier = Modifier.testTag("VisitEditSave"),
        onClick = {
          saving = true
          scope.launch {
            val refused = onSave(Date(start), if (open) null else Date(end))
            saving = false
            if (refused == null) {
              onDismiss()
            } else {
              serverError = visitErrorText(refused, state, context)
            }
          }
        },
      ) {
        Text(text = stringResource(R.string.save))
      }
    },
    dismissButton = {
      TextButton(enabled = !saving, onClick = onDismiss) {
        Text(text = stringResource(R.string.cancel))
      }
    },
  )
}

/** A date and a time to change, each opening its picker; nothing in the future can be picked. */
@Composable
private fun DateTimeRow(
  label: String,
  millis: Long,
  onChange: (Long) -> Unit,
  tag: String,
) {
  val context = LocalContext.current
  val dateFormat = remember { DateFormat.getMediumDateFormat(context) }
  val timeFormat = remember { DateFormat.getTimeFormat(context) }
  val date = Date(millis)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      OutlinedButton(
        modifier = Modifier.weight(1f).testTag(tag + "Date"),
        onClick = {
          val calendar = Calendar.getInstance().apply { time = date }
          DatePickerDialog(
              context,
              { _, year, month, day ->
                val changed =
                  Calendar.getInstance().apply {
                    time = date
                    set(year, month, day)
                  }
                onChange(changed.timeInMillis)
              },
              calendar[Calendar.YEAR],
              calendar[Calendar.MONTH],
              calendar[Calendar.DAY_OF_MONTH],
            )
            .apply { datePicker.maxDate = System.currentTimeMillis() }
            .show()
        },
      ) {
        Text(text = dateFormat.format(date))
      }
      OutlinedButton(
        modifier = Modifier.weight(1f).padding(0.dp).testTag(tag + "Time"),
        onClick = {
          val calendar = Calendar.getInstance().apply { time = date }
          TimePickerDialog(
              context,
              { _, hour, minute ->
                val changed =
                  Calendar.getInstance().apply {
                    time = date
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                  }
                onChange(changed.timeInMillis)
              },
              calendar[Calendar.HOUR_OF_DAY],
              calendar[Calendar.MINUTE],
              DateFormat.is24HourFormat(context),
            )
            .show()
        },
      ) {
        Text(text = timeFormat.format(date))
      }
    }
  }
}

@Composable
private fun visitErrorText(error: VisitPeriodError, state: VisitEditState): String =
  visitErrorText(error, state, LocalContext.current)

private fun visitErrorText(
  error: VisitPeriodError,
  state: VisitEditState,
  context: android.content.Context,
): String {
  val format = DateFormat.getMediumDateFormat(context)
  val time = DateFormat.getTimeFormat(context)
  fun Date.asText() = format.format(this) + " " + time.format(this)
  return when (error) {
    VisitPeriodError.START_IN_FUTURE -> context.getString(R.string.visit_edit_error_start_future)
    VisitPeriodError.END_IN_FUTURE -> context.getString(R.string.visit_edit_error_end_future)
    VisitPeriodError.END_BEFORE_START ->
      context.getString(R.string.visit_edit_error_end_before_start)
    VisitPeriodError.ENCOUNTER_BEFORE_START ->
      context.getString(
        R.string.visit_edit_error_encounter_before,
        state.earliest?.asText().orEmpty(),
      )
    VisitPeriodError.ENCOUNTER_AFTER_END ->
      context.getString(R.string.visit_edit_error_encounter_after, state.latest?.asText().orEmpty())
    VisitPeriodError.OTHER_VISIT_OPEN -> context.getString(R.string.reopen_visit_blocked)
  }
}
