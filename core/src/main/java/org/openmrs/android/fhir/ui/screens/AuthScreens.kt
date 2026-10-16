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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.viewmodel.LoginUiState

/**
 * The frame shared by both login screens: a brand header with the logo, a card for the form, and a
 * footer with the app version and (when [showServerSettings]) the server settings gear.
 */
@Composable
private fun LoginFrame(
  logoTag: String,
  screenTag: String,
  modifier: Modifier = Modifier,
  showServerSettings: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  val context = LocalContext.current
  val version = remember {
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
      .getOrNull()
      .orEmpty()
  }
  var showServerDialog by remember { mutableStateOf(false) }

  Box(
    modifier =
      modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(screenTag),
  ) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
      Box(modifier = Modifier.fillMaxWidth()) {
        LoginHeader(logoTag = logoTag)
        Card(
          modifier = Modifier.fillMaxWidth().padding(top = 250.dp, start = 20.dp, end = 20.dp),
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(24.dp), content = content)
        }
      }
      // Room for the footer below the card.
      Spacer(modifier = Modifier.height(88.dp))
    }

    Row(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.app_version_footer, version),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f).testTag("LoginVersion"),
      )
      if (showServerSettings) {
        IconButton(
          onClick = { showServerDialog = true },
          modifier = Modifier.size(40.dp).testTag("ServerSettingsButton"),
        ) {
          Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.server_settings_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  if (showServerDialog) {
    ServerSettingsDialog(onDismiss = { showServerDialog = false })
  }
}

@Composable
private fun LoginHeader(logoTag: String) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .height(290.dp)
        .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
        .background(
          Brush.verticalGradient(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
          ),
        ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // The artwork has dark lettering, so it always sits on a white plate.
      Image(
        painter = painterResource(id = R.drawable.openmrs_logo),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier =
          Modifier.width(260.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(logoTag),
      )
      Text(
        text = stringResource(R.string.login_welcome),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.padding(top = 20.dp),
      )
      Text(
        text = stringResource(R.string.login_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
        modifier = Modifier.padding(top = 4.dp),
      )
    }
  }
}

@Composable
fun LoginScreen(
  isButtonEnabled: Boolean,
  onLoginClick: () -> Unit,
  modifier: Modifier = Modifier,
  onContinueWithoutLogin: (() -> Unit)? = null,
) {
  LoginFrame(logoTag = "LoginLogo", screenTag = "LoginScreen", modifier = modifier) {
    Button(
      onClick = onLoginClick,
      enabled = isButtonEnabled,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("LoginButton"),
    ) {
      Text(text = stringResource(id = R.string.button_login))
    }
    if (onContinueWithoutLogin != null) {
      ContinueWithoutLogin(onClick = onContinueWithoutLogin)
    }
  }
}

/** Secondary action under the login button; only shown when the `skip_login` setting is on. */
@Composable
private fun ColumnScope.ContinueWithoutLogin(onClick: () -> Unit) {
  HorizontalDivider(
    modifier = Modifier.padding(vertical = 16.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
  OutlinedButton(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("SkipLoginButton"),
  ) {
    Text(text = stringResource(id = R.string.continue_without_login))
  }
  Text(
    text = stringResource(id = R.string.continue_without_login_hint),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
  )
}

@Composable
fun BasicLoginScreen(
  uiState: LoginUiState,
  onLoginClick: (String, String) -> Unit,
  modifier: Modifier = Modifier,
  onContinueWithoutLogin: (() -> Unit)? = null,
) {
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }
  val passwordFocus = remember { FocusRequester() }

  LoginFrame(
    logoTag = "BasicLoginLogo",
    screenTag = "BasicLoginScreen",
    modifier = modifier,
    showServerSettings = true,
  ) {
    if (uiState is LoginUiState.Loading) {
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("BasicLoginProgress"),
      )
    }
    OutlinedTextField(
      value = username,
      onValueChange = { username = it },
      modifier = Modifier.fillMaxWidth().testTag("BasicLoginUsername"),
      label = { Text(text = stringResource(id = R.string.username)) },
      leadingIcon = { Icon(imageVector = Icons.Filled.Person, contentDescription = null) },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
      keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
      value = password,
      onValueChange = { password = it },
      modifier =
        Modifier.fillMaxWidth().focusRequester(passwordFocus).testTag("BasicLoginPassword"),
      label = { Text(text = stringResource(id = R.string.password)) },
      leadingIcon = { Icon(imageVector = Icons.Filled.Lock, contentDescription = null) },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
      visualTransformation =
        if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
      keyboardOptions =
        KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
      keyboardActions = KeyboardActions(onDone = { onLoginClick(username, password) }),
      trailingIcon = {
        val icon = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
        val contentDescription =
          if (isPasswordVisible) {
            stringResource(id = R.string.hide_password)
          } else {
            stringResource(id = R.string.show_password)
          }
        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
          Icon(imageVector = icon, contentDescription = contentDescription)
        }
      },
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(
      onClick = { onLoginClick(username, password) },
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("BasicLoginButton"),
    ) {
      Text(text = stringResource(id = R.string.button_login))
    }
    if (onContinueWithoutLogin != null) {
      ContinueWithoutLogin(onClick = onContinueWithoutLogin)
    }
  }
}

@Composable
fun SplashScreen(
  isProgressVisible: Boolean,
  statusText: String?,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize().padding(32.dp).testTag("SplashScreen")) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      if (isProgressVisible) {
        androidx.compose.material3.CircularProgressIndicator(
          modifier = Modifier.testTag("SplashProgress"),
        )
      }
      if (!statusText.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = statusText,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.testTag("SplashStatusText"),
        )
      }
    }
  }
}
