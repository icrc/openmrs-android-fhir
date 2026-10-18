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
package org.openmrs.android.fhir

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.auth.AuthMethod
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.auth.OfflineAuthMethod
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.extensions.AuthDialogs
import org.openmrs.android.fhir.extensions.BiometricPromptHelper
import org.openmrs.android.fhir.extensions.BiometricUtils
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.screens.LoginScreen
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.LoginActivityViewModel
import org.openmrs.android.fhir.viewmodel.LoginUiState
import timber.log.Timber

/**
 * The one login page. It offers username and password, single sign-on (OpenID) or both, as set by
 * the `auth_method` build setting, plus "Continue without signing in" when `skip_login` is on.
 */
class LoginActivity : AppCompatActivity() {

  private sealed interface SsoAction {
    data class LaunchIntent(val intent: Intent) : SsoAction

    /** The provider configuration could not be read; tapping the button tries again. */
    data object Retry : SsoAction

    data object Disabled : SsoAction
  }

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<LoginActivityViewModel> { viewModelFactory }

  private lateinit var biometricPrompt: BiometricPrompt
  private lateinit var executor: Executor
  private var offlineAuthMethod: OfflineAuthMethod? = null

  private val showBasic by lazy { AuthMethod.BASIC in viewModel.offeredMethods }
  private val showSso by lazy { AuthMethod.OPENID in viewModel.offeredMethods }

  private var ssoAction: SsoAction by mutableStateOf(SsoAction.Disabled)
  private var loginUiState: LoginUiState by mutableStateOf(LoginUiState.Idle)
  val dialogStateForTest: MutableState<AuthDialogs.DialogConfig?> = mutableStateOf(null)

  private val getContent =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      lifecycleScope.launch {
        if (result.resultCode != RESULT_OK) return@launch
        // The response is handled once, when the page is back in front of the user.
        lifecycle.withResumed {}
        Timber.i("Exchange for token")
        val response = result.data?.let { AuthorizationResponse.fromIntent(it) }
        val ex = AuthorizationException.fromIntent(result.data)
        viewModel.handleLoginResponse(response, ex)
        onSignedIn()
      }
    }

  private val confirmCredLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
      BiometricPromptHelper.handleConfirmCredentialResult(
        activity = this,
        result = res,
        sessionTokenProvider = { viewModel.sessionTokenToEncrypt },
        cipherProvider = { BiometricUtils.getEncryptionCipher(this) },
        onNavigate = { navigateToMain() },
        onSaved = { offlineAuthMethod?.let { BiometricUtils.setOfflineAuthMethod(this, it) } },
      )
    }

  private val signInFromGuest by lazy {
    intent.getBooleanExtra(GuestSession.EXTRA_SIGN_IN_FROM_GUEST, false)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    (application as FhirApplication).appComponent.inject(this)
    setContent {
      val pendingUsers by viewModel.pendingUsers.collectAsState()
      OpenMrsTheme {
        LoginScreen(
          showBasic = showBasic,
          showSso = showSso,
          uiState = loginUiState,
          isSsoEnabled = ssoAction != SsoAction.Disabled,
          initialUsername = viewModel.lastUsername,
          pendingUsers = pendingUsers,
          onBasicLogin = { username, password -> viewModel.login(username, password) },
          onSsoLogin = ::onSsoLoginClick,
          ssoProviderName = AppConfig.get(this@LoginActivity).sso?.providerName.orEmpty(),
          onContinueWithoutLogin =
            if (GuestSession.isAllowed(this@LoginActivity) && !signInFromGuest) {
              ::continueWithoutLogin
            } else {
              null
            },
        )
        LaunchedEffect(loginUiState) {
          when (val state = loginUiState) {
            is LoginUiState.Failure -> showToastMessage(getString(state.resId))
            is LoginUiState.LockedOut -> showToastMessage(getString(R.string.locked_out_message))
            is LoginUiState.Success -> onSignedIn()
            else -> Unit
          }
        }
        val dialogConfig = dialogStateForTest.value
        if (dialogConfig != null) {
          androidx.compose.material3.AlertDialog(
            onDismissRequest = dialogConfig.onDismiss,
            confirmButton = {
              androidx.compose.material3.TextButton(onClick = dialogConfig.onConfirm) {
                androidx.compose.material3.Text(text = dialogConfig.positiveText.toString())
              }
            },
            dismissButton = {
              androidx.compose.material3.TextButton(onClick = dialogConfig.onDismiss) {
                androidx.compose.material3.Text(text = dialogConfig.negativeText.toString())
              }
            },
            title = { androidx.compose.material3.Text(text = dialogConfig.title.toString()) },
            text = { androidx.compose.material3.Text(text = dialogConfig.message.toString()) },
          )
        }
      }
    }

    executor = ContextCompat.getMainExecutor(this)

    biometricPrompt =
      BiometricPromptHelper.createBiometricPrompt(
        activity = this,
        executor = executor,
        sessionTokenProvider = { viewModel.sessionTokenToEncrypt },
        onNavigate = { navigateToMain() },
        onSaved = { offlineAuthMethod?.let { BiometricUtils.setOfflineAuthMethod(this, it) } },
      )

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state -> loginUiState = state }
      }
    }

    if (showSso) {
      lifecycleScope.launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
          // With username and password on the page too, an unreachable provider is only reported
          // when the user taps the single sign-on button.
          prepareSso(reportError = !showBasic)
        }
      }
    }
  }

  /** Reads the provider configuration and prepares the authorization request. */
  private suspend fun prepareSso(reportError: Boolean) {
    val loginIntent = viewModel.createIntent()
    val lastConfigurationError = viewModel.getLastConfigurationError()
    if (lastConfigurationError != null) {
      if (reportError) {
        AppMessage.show(
          this,
          lastConfigurationError.cause?.localizedMessage ?: lastConfigurationError.localizedMessage,
          MessageKind.Error,
          long = true,
        )
      }
      ssoAction = SsoAction.Retry
    } else if (loginIntent != null) {
      ssoAction = SsoAction.LaunchIntent(loginIntent)
    } else {
      ssoAction = SsoAction.Disabled
    }
  }

  private fun onSsoLoginClick() {
    when (val action = ssoAction) {
      is SsoAction.LaunchIntent -> getContent.launch(action.intent)
      SsoAction.Retry -> {
        Timber.i("retrying the provider configuration")
        lifecycleScope.launch { prepareSso(reportError = true) }
      }
      SsoAction.Disabled -> Unit
    }
  }

  /** A session exists (either method); ask about offline login once, then continue. */
  private fun onSignedIn() {
    if (BiometricUtils.hasOfflineLoginDecision(this)) {
      navigateToMain()
    } else {
      AuthDialogs.showOfflineLoginOptIn(
        activity = this,
        onProceedWithBiometric = { promptBiometricEncryption() },
        navigateToMain = { navigateToMain() },
        onDeclined = { BiometricUtils.setOfflineLoginDeclined(this) },
      )
    }
  }

  private fun promptBiometricEncryption() {
    BiometricPromptHelper.promptBiometricEncryption(
      activity = this,
      biometricPrompt = biometricPrompt,
      confirmCredLauncher = confirmCredLauncher,
      navigateToMain = { navigateToMain() },
      showToast = { msg -> AppMessage.show(this, msg, MessageKind.Error, long = true) },
      onMethodSelected = { method -> offlineAuthMethod = method },
    )
  }

  private fun navigateToMain() {
    if (signInFromGuest) {
      // Back to the guest session that asked for the sign-in; its data is kept.
      GuestSession.end(this)
      setResult(RESULT_OK)
      finish()
      return
    }
    startActivity(Intent(this, MainActivity::class.java))
    finish()
  }

  private fun continueWithoutLogin() {
    GuestSession.start(this)
    AuditLog.setUser(null)
    AuditLog.record(AuditAction.GUEST_SESSION_STARTED)
    startActivity(Intent(this, MainActivity::class.java))
    finish()
  }

  private fun showToastMessage(message: String) {
    AppMessage.show(this, message, MessageKind.Error)
  }

  @VisibleForTesting
  fun setLoginUiStateForTest(state: LoginUiState) {
    loginUiState = state
  }

  @VisibleForTesting
  fun showOfflineOptInForTest() {
    AuthDialogs.showOfflineLoginOptIn(
      activity = this,
      onProceedWithBiometric = {},
      navigateToMain = {},
    )
  }
}
