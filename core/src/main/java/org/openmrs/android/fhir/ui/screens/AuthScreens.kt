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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.viewmodel.LoginUiState

@Composable
fun LoginScreen(
  isButtonEnabled: Boolean,
  onLoginClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize().testTag("LoginScreen")) {
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Image(
        painter = painterResource(id = R.drawable.openmrs_logo),
        contentDescription = null,
        modifier = Modifier.width(300.dp).height(140.dp).testTag("LoginLogo"),
      )
      Spacer(modifier = Modifier.height(100.dp))
      Button(
        onClick = onLoginClick,
        enabled = isButtonEnabled,
        modifier = Modifier.width(200.dp).testTag("LoginButton"),
      ) {
        Text(text = stringResource(id = R.string.button_login))
      }
    }
  }
}

@Composable
fun BasicLoginScreen(
  uiState: LoginUiState,
  onLoginClick: (String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }
  Column(
    modifier = modifier.fillMaxSize().padding(20.dp).testTag("BasicLoginScreen"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (uiState is LoginUiState.Loading) {
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().testTag("BasicLoginProgress"),
      )
    } else {
      Spacer(modifier = Modifier.height(4.dp))
    }
    Spacer(modifier = Modifier.height(200.dp))
    Image(
      painter = painterResource(id = R.drawable.openmrs_logo),
      contentDescription = null,
      modifier = Modifier.fillMaxWidth().testTag("BasicLoginLogo"),
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
      value = username,
      onValueChange = { username = it },
      modifier = Modifier.fillMaxWidth().testTag("BasicLoginUsername"),
      label = { Text(text = stringResource(id = R.string.username)) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
      value = password,
      onValueChange = { password = it },
      modifier = Modifier.fillMaxWidth().testTag("BasicLoginPassword"),
      label = { Text(text = stringResource(id = R.string.password)) },
      singleLine = true,
      visualTransformation =
        if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
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
    Spacer(modifier = Modifier.height(16.dp))
    Button(
      onClick = { onLoginClick(username, password) },
      modifier = Modifier.fillMaxWidth().testTag("BasicLoginButton"),
    ) {
      Text(text = stringResource(id = R.string.button_login))
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
          color = colorResource(id = R.color.black),
          modifier = Modifier.testTag("SplashStatusText"),
        )
      }
    }
  }
}
