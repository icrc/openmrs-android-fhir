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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.extensions.AppLanguage
import org.openmrs.android.fhir.extensions.ColorVisionMode
import org.openmrs.android.fhir.extensions.ThemeMode
import org.openmrs.android.fhir.settingsshare.SettingChange
import org.openmrs.android.fhir.settingsshare.SettingsScanActivity
import org.openmrs.android.fhir.settingsshare.SharedSetting
import timber.log.Timber

private enum class ShareStep {
  Choice,
  Export,
  Import,
  Review,
}

/**
 * Passes the app's settings from one device to another: **Export** shows a QR code, **Import**
 * scans one (or takes the code pasted) and lists what would change. Nothing is applied until the
 * user approves, and each difference can be left out (the server, which signs the user out, starts
 * left out).
 *
 * @param onApplyServer called with the new server when the user approved it.
 * @param onApplied called after the approved settings were applied (screens are recreated to show
 *   them).
 */
@Composable
fun ShareSettingsDialog(
  onDismiss: () -> Unit,
  onApplyServer: ((String) -> Unit)? = null,
  onApplied: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  // By default (the login page) the server is saved and the app restarts on the login page; the
  // screens are recreated to show the rest.
  val applyServer: (String) -> Unit = onApplyServer ?: { url -> saveServerAndRestart(context, url) }
  val applied: () -> Unit = onApplied ?: { context.findActivity()?.recreate() }
  val share = remember { FhirApplication.appComponent(context).settingsShare() }
  val scope = rememberCoroutineScope()
  var step by remember { mutableStateOf(ShareStep.Choice) }
  var code by remember { mutableStateOf<String?>(null) }
  var qr by remember { mutableStateOf<Bitmap?>(null) }
  var pasted by remember { mutableStateOf("") }
  var invalid by remember { mutableStateOf(false) }
  var changes by remember { mutableStateOf<List<SettingChange>>(emptyList()) }
  val taken = remember { mutableStateMapOf<SharedSetting, Boolean>() }

  fun review(text: String) {
    scope.launch {
      val parsed = share.parse(text)
      if (parsed == null) {
        invalid = true
        return@launch
      }
      invalid = false
      changes = share.changesFrom(parsed)
      taken.clear()
      // The server signs the user out and can point the app anywhere: never on by itself.
      changes.forEach { taken[it.setting] = it.setting != SharedSetting.SERVER }
      step = ShareStep.Review
    }
  }

  val scanner =
    rememberLauncherForActivityResult(ScanContract()) { result ->
      result.contents?.let { review(it) }
    }

  LaunchedEffect(step) {
    if (step == ShareStep.Export && qr == null) {
      try {
        val text = share.export()
        code = text
        qr =
          withContext(Dispatchers.Default) {
            BarcodeEncoder().encodeBitmap(text, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE)
          }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Could not draw the settings code")
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("ShareSettingsDialog"),
    shape = RoundedCornerShape(24.dp),
    title = {
      Text(
        text =
          stringResource(
            when (step) {
              ShareStep.Export -> R.string.share_export_title
              ShareStep.Review -> R.string.share_review_title
              else -> R.string.share_title
            },
          ),
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when (step) {
          ShareStep.Choice -> {
            Text(
              text = stringResource(R.string.share_choose),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChoiceButton(
              icon = Icons.Filled.QrCode2,
              title = stringResource(R.string.share_export),
              description = stringResource(R.string.share_export_desc),
              tag = "ShareExport",
              onClick = { step = ShareStep.Export },
            )
            ChoiceButton(
              icon = Icons.Filled.QrCodeScanner,
              title = stringResource(R.string.share_import),
              description = stringResource(R.string.share_import_desc),
              tag = "ShareImport",
              onClick = { step = ShareStep.Import },
            )
          }
          ShareStep.Export -> {
            Text(
              text = stringResource(R.string.share_export_hint),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
              val bitmap = qr
              if (bitmap == null) {
                CircularProgressIndicator(modifier = Modifier.padding(48.dp))
              } else {
                // Black on white whatever the theme: that is what scanners read.
                Image(
                  bitmap = bitmap.asImageBitmap(),
                  contentDescription = stringResource(R.string.share_export_title),
                  modifier =
                    Modifier.size(280.dp)
                      .background(Color.White, RoundedCornerShape(12.dp))
                      .padding(8.dp)
                      .testTag("ShareQrCode"),
                )
              }
            }
            OutlinedButton(
              onClick = { code?.let { copy(context, it) } },
              enabled = code != null,
              modifier = Modifier.fillMaxWidth().testTag("ShareCopyCode"),
            ) {
              Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = stringResource(R.string.share_copy_code))
            }
          }
          ShareStep.Import -> {
            Button(
              onClick = {
                scanner.launch(
                  ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(context.getString(R.string.share_scan_prompt))
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                    .setCaptureActivity(SettingsScanActivity::class.java),
                )
              },
              modifier = Modifier.fillMaxWidth().testTag("ShareScan"),
            ) {
              Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = stringResource(R.string.share_scan))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            OutlinedTextField(
              value = pasted,
              onValueChange = {
                pasted = it
                invalid = false
              },
              label = { Text(text = stringResource(R.string.share_paste_label)) },
              isError = invalid,
              supportingText =
                if (invalid) {
                  { Text(text = stringResource(R.string.share_invalid)) }
                } else {
                  null
                },
              minLines = 2,
              maxLines = 4,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("SharePasteField"),
            )
            OutlinedButton(
              onClick = { review(pasted) },
              enabled = pasted.isNotBlank(),
              modifier = Modifier.fillMaxWidth().testTag("ShareReview"),
            ) {
              Text(text = stringResource(R.string.share_review))
            }
          }
          ShareStep.Review -> {
            if (changes.isEmpty()) {
              Text(
                text = stringResource(R.string.share_no_changes),
                style = MaterialTheme.typography.bodyLarge,
              )
            } else {
              Text(
                text = stringResource(R.string.share_review_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              changes.forEach { change ->
                ChangeRow(
                  context = context,
                  change = change,
                  taken = taken[change.setting] == true,
                  onTakenChange = { taken[change.setting] = it },
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      if (step == ShareStep.Review && changes.isNotEmpty()) {
        val chosen = changes.filter { taken[it.setting] == true }
        TextButton(
          enabled = chosen.isNotEmpty(),
          modifier = Modifier.testTag("ShareApply"),
          onClick = {
            scope.launch {
              try {
                share.apply(chosen)
              } catch (e: CancellationException) {
                throw e
              } catch (e: Exception) {
                Timber.e(e, "Could not apply the imported settings")
              }
              val server = chosen.firstOrNull { it.setting == SharedSetting.SERVER }
              onDismiss()
              // The server restarts the app, which shows everything else as well.
              if (server != null) applyServer(server.proposed) else applied()
            }
          },
        ) {
          Text(text = stringResource(R.string.share_apply, chosen.size))
        }
      } else {
        TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.close)) }
      }
    },
    dismissButton = {
      if (step != ShareStep.Choice) {
        TextButton(
          onClick = { step = if (step == ShareStep.Review) ShareStep.Import else ShareStep.Choice },
        ) {
          Text(text = stringResource(R.string.settings_back))
        }
      } else {
        TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
      }
    },
  )
}

@Composable
private fun ChoiceButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  description: String,
  tag: String,
  onClick: () -> Unit,
) {
  OutlinedButton(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth().testTag(tag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
      Column(modifier = Modifier.padding(start = 14.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * One difference: what it is, this device's value and the one in the code, and a switch to take it.
 */
@Composable
private fun ChangeRow(
  context: Context,
  change: SettingChange,
  taken: Boolean,
  onTakenChange: (Boolean) -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
        .padding(12.dp)
        .testTag("ShareChange_${change.setting.key}"),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(change.setting.label),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = valueText(context, change.setting, change.current),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "→ " + valueText(context, change.setting, change.proposed),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color =
            if (taken) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Switch(
          checked = taken,
          onCheckedChange = onTakenChange,
          modifier = Modifier.testTag("ShareTake_${change.setting.key}"),
        )
        Text(
          text = stringResource(if (taken) R.string.share_take else R.string.share_keep),
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }
    if (change.setting == SharedSetting.SERVER) {
      Text(
        text = stringResource(R.string.share_server_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 6.dp).heightIn(min = 0.dp),
      )
    }
  }
}

/** A value as the user reads it. */
private fun valueText(context: Context, setting: SharedSetting, value: String): String =
  when {
    setting.isBoolean ->
      context.getString(if (value == "true") R.string.share_value_on else R.string.share_value_off)
    setting == SharedSetting.TOKEN_DELAY || setting == SharedSetting.PERIODIC_DELAY ->
      context.getString(R.string.share_minutes, value.toIntOrNull() ?: 0)
    setting == SharedSetting.AUTO_SYNC_MODE ->
      context.getString(
        if (value == "upload") R.string.sync_mode_upload_only else R.string.sync_mode_both,
      )
    setting == SharedSetting.THEME ->
      context.getString(
        when (ThemeMode.entries.firstOrNull { it.value == value }) {
          ThemeMode.LIGHT -> R.string.theme_light
          ThemeMode.DARK -> R.string.theme_dark
          else -> R.string.theme_system
        },
      )
    setting == SharedSetting.COLOR_VISION ->
      context.getString(
        if (
          ColorVisionMode.entries.firstOrNull { it.value == value } == ColorVisionMode.COLOR_BLIND
        ) {
          R.string.color_vision_color_blind
        } else {
          R.string.color_vision_standard
        },
      )
    setting == SharedSetting.LANGUAGE ->
      AppLanguage.entries.firstOrNull { it.tag == value }?.nativeName ?: value
    else -> value
  }

private fun copy(context: Context, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("OpenMRS settings", text))
  org.openmrs.android.fhir.ui.components.AppMessage.show(
    context,
    R.string.share_code_copied,
    org.openmrs.android.fhir.ui.components.MessageKind.Success,
  )
}

private const val QR_SIZE = 720
