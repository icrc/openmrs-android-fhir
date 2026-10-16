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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.remote.ServerSettings
import org.openmrs.android.fhir.extensions.AppLanguage
import org.openmrs.android.fhir.ui.components.LanguagePicker

private sealed interface TestState {
  data object Idle : TestState

  data object Running : TestState

  data class Done(val result: ServerSettings.TestResult) : TestState
}

/**
 * Lets the user point the app at another OpenMRS server by IP address or domain, and check that it
 * answers before saving. Saving restarts the app so every component uses the new server.
 */
@Composable
fun ServerSettingsDialog(onDismiss: () -> Unit, showServer: Boolean = true) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val defaultUrl = remember { ServerSettings.defaultUrl(context) }
  val currentOverride = remember { ServerSettings.override(context) }
  var input by remember { mutableStateOf(currentOverride ?: defaultUrl) }
  var testState by remember { mutableStateOf<TestState>(TestState.Idle) }
  val normalized = ServerSettings.normalize(input)

  fun save(url: String?) {
    ServerSettings.setOverride(context, url)
    ServerSettings.restartApp(context)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(24.dp),
    title = { Text(text = stringResource(R.string.settings)) },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
      ) {
        Text(
          text = stringResource(R.string.settings_language),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
        LanguagePicker(
          selected = AppLanguage.current(context),
          onSelected = { AppLanguage.apply(context, it) },
        )
        if (!showServer) return@Column
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
          text = stringResource(R.string.server_settings_title),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = stringResource(R.string.server_settings_description),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = input,
          onValueChange = {
            input = it
            testState = TestState.Idle
          },
          label = { Text(text = stringResource(R.string.server_address)) },
          placeholder = { Text(text = "192.168.1.10:8080") },
          singleLine = true,
          isError = input.isNotBlank() && normalized == null,
          supportingText = {
            val shown = normalized
            Text(
              text =
                if (shown != null) {
                  stringResource(R.string.server_will_use, shown)
                } else {
                  stringResource(R.string.server_address_invalid)
                },
            )
          },
          keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("ServerUrlField"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedButton(
            onClick = {
              val target = normalized ?: return@OutlinedButton
              testState = TestState.Running
              scope.launch { testState = TestState.Done(ServerSettings.testConnection(target)) }
            },
            enabled = normalized != null && testState != TestState.Running,
            modifier = Modifier.testTag("TestConnectionButton"),
          ) {
            Text(text = stringResource(R.string.test_connection))
          }
          Spacer(modifier = Modifier.width(12.dp))
          TestStatus(testState)
        }
      }
    },
    confirmButton = {
      if (showServer) {
        TextButton(
          onClick = {
            val target = normalized ?: return@TextButton
            if (target == defaultUrl) save(null) else save(target)
          },
          enabled = normalized != null,
          modifier = Modifier.testTag("ServerSaveButton"),
        ) {
          Text(text = stringResource(R.string.save))
        }
      } else {
        TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.close)) }
      }
    },
    dismissButton = {
      if (showServer) {
        Row {
          if (currentOverride != null) {
            TextButton(onClick = { save(null) }) {
              Text(text = stringResource(R.string.use_default_server))
            }
          }
          TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        }
      }
    },
  )
}

@Composable
private fun TestStatus(state: TestState) {
  when (state) {
    TestState.Idle -> Unit
    TestState.Running ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.testing_connection),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    is TestState.Done -> {
      val result = state.result
      val ok = result is ServerSettings.TestResult.Success
      val message =
        when (result) {
          is ServerSettings.TestResult.Success ->
            stringResource(R.string.connection_success, result.millis)
          is ServerSettings.TestResult.Failure -> stringResource(failureMessage(result))
        }
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color =
          if (ok) {
            org.openmrs.android.fhir.ui.theme.OpenMrsTheme.colors.success
          } else {
            MaterialTheme.colorScheme.error
          },
        modifier = Modifier.testTag("ConnectionResult"),
      )
    }
  }
}

private fun failureMessage(failure: ServerSettings.TestResult.Failure): Int =
  when (failure.reason) {
    ServerSettings.Reason.InvalidAddress -> R.string.server_address_invalid
    ServerSettings.Reason.ServerNotFound -> R.string.connection_error_not_found
    ServerSettings.Reason.TimedOut -> R.string.connection_error_timeout
    ServerSettings.Reason.CertificateProblem -> R.string.connection_error_certificate
    ServerSettings.Reason.CleartextNotAllowed -> R.string.connection_error_cleartext
    ServerSettings.Reason.UnexpectedAnswer -> R.string.connection_error_unexpected
    ServerSettings.Reason.Unreachable -> R.string.connection_error_unreachable
  }
