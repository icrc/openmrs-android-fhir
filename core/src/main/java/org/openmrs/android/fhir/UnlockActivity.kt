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
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.auth.AppLock
import org.openmrs.android.fhir.extensions.OfflineAuthPrompt
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

/**
 * The lock screen: the app was out of sight for a while and the user chose an offline login, so the
 * fingerprint, the face or the screen lock is asked before anything is shown again. It sits over
 * the screen the user left; unlocking just closes it.
 *
 * With no way to unlock (the biometrics were removed, the cancelled prompt) the user can try again
 * or sign in with the account instead.
 */
class UnlockActivity : AppCompatActivity() {
  private var canAsk by mutableStateOf(true)
  private var asking = false
  private var askedOnVisit = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppLock.markLocking()
    setContent {
      OpenMrsTheme {
        Column(
          modifier =
            Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .verticalScroll(rememberScrollState())
              .padding(32.dp)
              .testTag("UnlockScreen"),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          androidx.compose.foundation.layout.Box(
            modifier =
              Modifier.size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(44.dp),
            )
          }
          Spacer(modifier = Modifier.height(24.dp))
          Text(
            text = stringResource(R.string.unlock_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text =
              stringResource(
                if (canAsk) R.string.unlock_message else R.string.unlock_unavailable,
              ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(32.dp))
          if (canAsk) {
            Button(
              onClick = ::ask,
              modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("UnlockButton"),
            ) {
              Text(text = stringResource(R.string.unlock_button))
            }
          }
          TextButton(
            onClick = ::signInAgain,
            modifier = Modifier.fillMaxWidth().testTag("UnlockSignInAgain"),
          ) {
            Text(text = stringResource(R.string.unlock_sign_in_again), textAlign = TextAlign.Center)
          }
        }
      }
    }
    // Back does not get round the lock: it leaves the app, which stays locked.
    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          moveTaskToBack(true)
        }
      },
    )
  }

  override fun onResume() {
    super.onResume()
    canAsk = OfflineAuthPrompt.isAvailable(this)
    // Asked by itself once per visit: a cancelled prompt brings this screen back to the front, and
    // asking again at once would never let the user reach the buttons.
    if (canAsk && !askedOnVisit) {
      askedOnVisit = true
      ask()
    }
  }

  override fun onStop() {
    super.onStop()
    // Back from another app: the check is asked again.
    if (!asking) askedOnVisit = false
  }

  private fun ask() {
    if (asking) return
    asking = true
    OfflineAuthPrompt.authenticate(
      activity = this,
      title = getString(R.string.unlock_prompt_title),
      subtitle = getString(R.string.unlock_message),
      onSuccess = {
        asking = false
        AuditLog.record(AuditAction.LOGIN, AuditOutcome.SUCCESS, "method" to "unlock")
        AppLock.unlocked()
        finish()
      },
      onFailed = { cancelled ->
        asking = false
        // Cancelled: the button asks again. Impossible (no biometrics or screen lock any more):
        // only the account is left.
        if (!cancelled) canAsk = false
      },
    )
  }

  /** The account instead: the login page, with this task started afresh. */
  private fun signInAgain() {
    AppLock.unlocked()
    startActivity(
      Intent(this, LoginActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
    finish()
  }
}
