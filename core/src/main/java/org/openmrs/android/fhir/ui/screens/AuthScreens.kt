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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
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
import org.openmrs.android.fhir.data.database.model.PendingUser
import org.openmrs.android.fhir.extensions.isAppInDarkTheme
import org.openmrs.android.fhir.ui.tutorial.TutorialAction
import org.openmrs.android.fhir.ui.tutorial.TutorialController
import org.openmrs.android.fhir.ui.tutorial.TutorialOverlay
import org.openmrs.android.fhir.ui.tutorial.TutorialStep
import org.openmrs.android.fhir.ui.tutorial.TutorialTargets
import org.openmrs.android.fhir.ui.tutorial.tutorialTarget
import org.openmrs.android.fhir.viewmodel.LoginUiState

/**
 * The frame of the login page: a brand header with the logo, a card for the form, and a footer with
 * the app version and (when [showServerSettings]) the server settings gear.
 */
@Composable
private fun LoginFrame(
  logoTag: String,
  screenTag: String,
  modifier: Modifier = Modifier,
  showServerSettings: Boolean = false,
  footerActions: @Composable () -> Unit = {},
  /** Starts the tour of the page; no button when `null`. */
  onShowTour: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val context = LocalContext.current
  val version = remember {
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
      .getOrNull()
      .orEmpty()
  }
  // Kept across the recreation a theme change causes, so the dialog stays open.
  var showServerDialog by rememberSaveable { mutableStateOf(false) }
  var showShareDialog by rememberSaveable { mutableStateOf(false) }

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
      footerActions()
      if (onShowTour != null) {
        IconButton(
          onClick = onShowTour,
          modifier = Modifier.size(40.dp).testTag("TutorialButton"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = stringResource(R.string.tutorial_show),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      IconButton(
        onClick = { showShareDialog = true },
        modifier =
          Modifier.size(40.dp)
            .testTag("ShareSettingsButton")
            .tutorialTarget(TutorialTargets.LOGIN_SHARE),
      ) {
        Icon(
          imageVector = Icons.Filled.QrCode2,
          contentDescription = stringResource(R.string.share_title),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(
        onClick = { showServerDialog = true },
        modifier =
          Modifier.size(40.dp)
            .testTag("ServerSettingsButton")
            .tutorialTarget(TutorialTargets.LOGIN_SERVER),
      ) {
        Icon(
          imageVector = Icons.Filled.Settings,
          contentDescription = stringResource(R.string.settings),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  if (showShareDialog) {
    ShareSettingsDialog(onDismiss = { showShareDialog = false })
  }
  if (showServerDialog) {
    ServerSettingsDialog(
      onDismiss = { showServerDialog = false },
      showServer = showServerSettings,
    )
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
      // The lettering is white on the dark teal of the light theme, and dark on the bright teal of
      // the dark theme: the logo sits straight on the header, without a plate.
      Image(
        painter =
          painterResource(
            id = if (isAppInDarkTheme()) R.drawable.openmrs_logo else R.drawable.openmrs_logo_light,
          ),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.width(240.dp).height(70.dp).testTag(logoTag),
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

/**
 * The one login page. Depending on the `auth_method` build setting it shows the username and
 * password form, the single sign-on button, or both (separated by "or"); the "Continue without
 * signing in" action is added when [onContinueWithoutLogin] is set.
 */
@Composable
fun LoginScreen(
  showBasic: Boolean,
  showSso: Boolean,
  uiState: LoginUiState,
  isSsoEnabled: Boolean,
  /** The server answers: without it there is no signing in, only the guest way. */
  serverReachable: Boolean = true,
  onBasicLogin: (String, String) -> Unit,
  onSsoLogin: () -> Unit,
  modifier: Modifier = Modifier,
  ssoProviderName: String? = null,
  initialUsername: String = "",
  onContinueWithoutLogin: (() -> Unit)? = null,
  /** The users with data on the device waiting to upload; `null` while not known. */
  pendingUsers: List<PendingUser>? = null,
) {
  var username by rememberSaveable { mutableStateOf(initialUsername) }
  // Counts the "log in as" choices, so the password field takes the focus after each one.
  var usernameChosen by remember { mutableIntStateOf(0) }
  // The tour of this page, for what the page shows.
  val tour =
    remember(showBasic, showSso, onContinueWithoutLogin != null) {
      listOfNotNull(
        if (showBasic) {
          TutorialStep(
            TutorialTargets.LOGIN_USERNAME,
            R.string.tutorial_login_credentials_title,
            R.string.tutorial_login_credentials_body,
            TutorialAction.Acknowledge,
          )
        } else {
          null
        },
        if (showSso) {
          TutorialStep(
            TutorialTargets.LOGIN_SSO,
            R.string.tutorial_login_sso_title,
            R.string.tutorial_login_sso_body,
            TutorialAction.Acknowledge,
          )
        } else {
          null
        },
        if (onContinueWithoutLogin != null) {
          TutorialStep(
            TutorialTargets.LOGIN_GUEST,
            R.string.tutorial_login_guest_title,
            R.string.tutorial_login_guest_body,
            TutorialAction.Acknowledge,
          )
        } else {
          null
        },
        TutorialStep(
          TutorialTargets.LOGIN_SERVER,
          R.string.tutorial_login_server_title,
          R.string.tutorial_login_server_body,
          TutorialAction.Acknowledge,
        ),
        TutorialStep(
          TutorialTargets.LOGIN_SHARE,
          R.string.tutorial_login_share_title,
          R.string.tutorial_login_share_body,
          TutorialAction.Acknowledge,
        ),
      )
    }
  Box(modifier = modifier) {
    LoginFrame(
      logoTag = "LoginLogo",
      screenTag = "LoginScreen",
      showServerSettings = showBasic,
      onShowTour = { TutorialController.start(tour) },
      footerActions = {
        PendingUploadsButton(
          pendingUsers = pendingUsers,
          onUseUser =
            if (showBasic) {
              { chosen ->
                username = chosen
                usernameChosen++
              }
            } else {
              null
            },
        )
      },
    ) {
      if (showBasic) {
        BasicLoginForm(
          uiState = uiState,
          onLoginClick = onBasicLogin,
          signInAvailable = serverReachable,
          username = username,
          onUsernameChange = { username = it },
          initialUsername = initialUsername,
          usernameChosen = usernameChosen,
        )
      }
      if (showBasic && showSso) {
        OrDivider()
      }
      if (showSso) {
        SsoLoginButton(
          providerName = ssoProviderName,
          isPrimary = !showBasic,
          enabled = isSsoEnabled && serverReachable,
          onClick = onSsoLogin,
        )
      }
      if (!serverReachable) {
        Text(
          text =
            stringResource(
              if (onContinueWithoutLogin != null) {
                R.string.login_offline_note_guest
              } else {
                R.string.login_offline_note
              },
            ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("LoginOfflineNote"),
        )
      }
      if (onContinueWithoutLogin != null) {
        ContinueWithoutLogin(onClick = onContinueWithoutLogin)
      }
    }
    // Above the whole page; draws nothing while no tour runs.
    TutorialOverlay()
  }
}

@Composable
private fun ColumnScope.BasicLoginForm(
  uiState: LoginUiState,
  onLoginClick: (String, String) -> Unit,
  signInAvailable: Boolean,
  username: String,
  onUsernameChange: (String) -> Unit,
  initialUsername: String = "",
  usernameChosen: Int = 0,
) {
  var password by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }
  val passwordFocus = remember { FocusRequester() }
  val isLoading = uiState is LoginUiState.Loading

  // A remembered username leaves only the password to type.
  LaunchedEffect(Unit) { if (initialUsername.isNotEmpty()) passwordFocus.requestFocus() }
  // A username picked from the list of users with data waiting: only the password is left.
  LaunchedEffect(usernameChosen) {
    if (usernameChosen > 0) {
      password = ""
      passwordFocus.requestFocus()
    }
  }

  if (isLoading) {
    LinearProgressIndicator(
      modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("BasicLoginProgress"),
    )
  }
  OutlinedTextField(
    value = username,
    onValueChange = onUsernameChange,
    modifier =
      Modifier.fillMaxWidth()
        .testTag("BasicLoginUsername")
        .tutorialTarget(TutorialTargets.LOGIN_USERNAME),
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
    modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).testTag("BasicLoginPassword"),
    label = { Text(text = stringResource(id = R.string.password)) },
    leadingIcon = { Icon(imageVector = Icons.Filled.Lock, contentDescription = null) },
    singleLine = true,
    shape = RoundedCornerShape(16.dp),
    visualTransformation =
      if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
    keyboardOptions =
      KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
    keyboardActions =
      KeyboardActions(onDone = { if (signInAvailable) onLoginClick(username, password) }),
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
    enabled = !isLoading && signInAvailable,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("BasicLoginButton"),
  ) {
    Text(text = stringResource(id = R.string.button_login))
  }
}

/** Single sign-on; the main action when it is the only method, otherwise the alternative. */
@Composable
private fun SsoLoginButton(
  providerName: String?,
  isPrimary: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val label =
    when {
      !providerName.isNullOrBlank() -> stringResource(R.string.sign_in_with_provider, providerName)
      isPrimary -> stringResource(R.string.button_login)
      else -> stringResource(R.string.sign_in_with_sso)
    }
  val modifier =
    Modifier.fillMaxWidth()
      .height(52.dp)
      .testTag("LoginButton")
      .tutorialTarget(TutorialTargets.LOGIN_SSO)
  if (isPrimary) {
    Button(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(16.dp),
      modifier = modifier,
    ) {
      Text(text = label)
    }
  } else {
    OutlinedButton(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(16.dp),
      modifier = modifier,
    ) {
      Text(text = label)
    }
  }
}

@Composable
private fun OrDivider() {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    HorizontalDivider(
      modifier = Modifier.weight(1f),
      color = MaterialTheme.colorScheme.outlineVariant,
    )
    Text(
      text = stringResource(R.string.login_or),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 12.dp),
    )
    HorizontalDivider(
      modifier = Modifier.weight(1f),
      color = MaterialTheme.colorScheme.outlineVariant,
    )
  }
}

/** Secondary action under the sign-in options; only shown when the `skip_login` setting is on. */
@Composable
private fun ColumnScope.ContinueWithoutLogin(onClick: () -> Unit) {
  HorizontalDivider(
    modifier = Modifier.padding(vertical = 16.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
  OutlinedButton(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    modifier =
      Modifier.fillMaxWidth()
        .height(48.dp)
        .testTag("SkipLoginButton")
        .tutorialTarget(TutorialTargets.LOGIN_GUEST),
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
