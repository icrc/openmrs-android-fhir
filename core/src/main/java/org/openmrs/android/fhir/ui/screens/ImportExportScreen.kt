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

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget
import org.openmrs.android.fhir.viewmodel.ImportExportUiState

object ImportExportTestTags {
  const val Screen = "ImportExportScreen"
  const val ExportButton = "ExportButton"
  const val ShareButton = "ExportShareButton"
  const val SaveButton = "ExportSaveButton"
  const val ImportButton = "ImportChooseButton"
  const val UploadButton = "ImportUploadButton"
  const val PinDialog = "PinDialog"
  const val PinField = "PinField"
  const val PinRepeatField = "PinRepeatField"
  const val PinConfirm = "PinConfirm"
}

private const val PIN_LENGTH = 4

/**
 * Move data that cannot be synced to another device: export what waits to be uploaded into a
 * PIN-protected file, or import such a file and upload it.
 */
@Composable
fun ImportExportScreen(
  state: ImportExportUiState,
  onExport: (pin: String) -> Unit,
  onShare: () -> Unit,
  onSave: () -> Unit,
  onChooseFile: () -> Unit,
  onImport: (pin: String) -> Unit,
  onCancelImport: () -> Unit,
  onUpload: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val topInset = rememberAppBarHeight()
  var askExportPin by rememberSaveable { mutableStateOf(false) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = topInset + 12.dp, bottom = 24.dp)
        .testTag(ImportExportTestTags.Screen),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
      text = stringResource(R.string.import_export_intro),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Box(modifier = Modifier.tutorialTarget(TutorialTargets.IE_EXPORT)) {
      ExportCard(
        state = state,
        onExportClick = { askExportPin = true },
        onShare = onShare,
        onSave = onSave,
      )
    }
    Box(modifier = Modifier.tutorialTarget(TutorialTargets.IE_IMPORT)) {
      ImportCard(state = state, onChooseFile = onChooseFile, onUpload = onUpload)
    }
  }

  if (askExportPin) {
    PinDialog(
      title = stringResource(R.string.pin_export_title),
      message = stringResource(R.string.pin_export_message),
      confirmLabel = stringResource(R.string.export_title),
      repeat = true,
      onConfirm = { pin ->
        askExportPin = false
        onExport(pin)
      },
      onDismiss = { askExportPin = false },
    )
  }
  if (state.importFile != null) {
    PinDialog(
      title = stringResource(R.string.pin_import_title),
      message = stringResource(R.string.pin_import_message),
      confirmLabel = stringResource(R.string.import_title),
      repeat = false,
      error = if (state.wrongPin) stringResource(R.string.pin_wrong) else null,
      busy = state.busy,
      onConfirm = onImport,
      onDismiss = onCancelImport,
    )
  }
}

@Composable
private fun SectionCard(
  title: String,
  iconRes: Int,
  busy: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shadowElevation = 1.dp,
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier.size(40.dp)
              .clip(CircleShape)
              .background(OpenMrsTheme.colors.highlightContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      if (busy) {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
          color = OpenMrsTheme.colors.progress,
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
      }
      content()
    }
  }
}

@Composable
private fun ExportCard(
  state: ImportExportUiState,
  onExportClick: () -> Unit,
  onShare: () -> Unit,
  onSave: () -> Unit,
) {
  val pending = state.pending
  SectionCard(
    title = stringResource(R.string.export_title),
    iconRes = R.drawable.ic_transfer_upload,
    busy = state.busy && state.importFile == null,
  ) {
    Text(
      text =
        when {
          pending == null -> ""
          pending == 0 -> stringResource(R.string.export_nothing)
          else -> stringResource(R.string.export_pending, pending)
        },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
      onClick = onExportClick,
      enabled = !state.busy && (pending ?: 0) > 0,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag(ImportExportTestTags.ExportButton),
    ) {
      Text(text = stringResource(R.string.export_button))
    }
    state.exported?.let { exported ->
      val size = Formatter.formatShortFileSize(LocalContext.current, exported.sizeBytes)
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = OpenMrsTheme.colors.highlightContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = stringResource(R.string.export_ready_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              "${exported.file.name}\n" +
                stringResource(R.string.export_summary, exported.items, size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = onShare,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f).testTag(ImportExportTestTags.ShareButton),
            ) {
              Text(text = stringResource(R.string.export_share))
            }
            OutlinedButton(
              onClick = onSave,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f).testTag(ImportExportTestTags.SaveButton),
            ) {
              Text(text = stringResource(R.string.export_save))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ImportCard(state: ImportExportUiState, onChooseFile: () -> Unit, onUpload: () -> Unit) {
  SectionCard(
    title = stringResource(R.string.import_title),
    iconRes = R.drawable.ic_transfer_download,
    busy = state.busy && state.importFile != null,
  ) {
    Text(
      text = stringResource(R.string.import_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
      onClick = onChooseFile,
      enabled = !state.busy,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag(ImportExportTestTags.ImportButton),
    ) {
      Text(text = stringResource(R.string.import_choose))
    }
    state.imported?.let { result ->
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = OpenMrsTheme.colors.highlightContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = stringResource(R.string.import_done_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              stringResource(
                R.string.import_done_summary,
                result.added,
                result.updated,
                result.skipped,
              ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (result.skipped > 0) {
            Text(
              text = stringResource(R.string.import_skipped_hint),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (result.added + result.updated > 0) {
            Button(
              onClick = onUpload,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag(ImportExportTestTags.UploadButton),
            ) {
              Text(text = stringResource(R.string.import_upload_now))
            }
          }
        }
      }
    }
  }
}

/** Asks for the 4-digit PIN; the export asks twice, so a typo does not lock the file for good. */
@Composable
private fun PinDialog(
  title: String,
  message: String,
  confirmLabel: String,
  repeat: Boolean,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
  error: String? = null,
  busy: Boolean = false,
) {
  var pin by rememberSaveable { mutableStateOf("") }
  var again by rememberSaveable { mutableStateOf("") }
  var tried by rememberSaveable { mutableStateOf<String?>(null) }
  val shownError = if (pin == tried) error else null
  val mismatch = repeat && again.length == PIN_LENGTH && again != pin
  val ready = pin.length == PIN_LENGTH && (!repeat || again == pin) && !busy

  AlertDialog(
    modifier = Modifier.testTag(ImportExportTestTags.PinDialog),
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(text = title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
          value = pin,
          onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) pin = it },
          label = { Text(stringResource(R.string.pin_label)) },
          singleLine = true,
          isError = shownError != null,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth().testTag(ImportExportTestTags.PinField),
        )
        if (repeat) {
          OutlinedTextField(
            value = again,
            onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) again = it },
            label = { Text(stringResource(R.string.pin_repeat_label)) },
            singleLine = true,
            isError = mismatch,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().testTag(ImportExportTestTags.PinRepeatField),
          )
        }
        val problem = shownError ?: if (mismatch) stringResource(R.string.pin_mismatch) else null
        if (problem != null) {
          Text(
            text = problem,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        if (busy) {
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = OpenMrsTheme.colors.progress,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          tried = pin
          onConfirm(pin)
        },
        enabled = ready,
        modifier = Modifier.testTag(ImportExportTestTags.PinConfirm),
      ) {
        Text(text = confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !busy) {
        Text(text = stringResource(R.string.cancel))
      }
    },
  )
}
