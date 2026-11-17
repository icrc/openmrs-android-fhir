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
package org.openmrs.android.fhir.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.data.FormUpdater
import org.openmrs.android.fhir.forms.EmbeddedFormsStore
import org.openmrs.android.fhir.forms.FormState
import org.openmrs.android.fhir.forms.FormStatus

/**
 * @property embeddedOnly the app uses its own forms: the ones taken from the server replace them
 *   one by one; otherwise the app uses the forms of the server
 */
data class FormUpdatesUiState(
  val forms: List<FormStatus> = emptyList(),
  val checking: Boolean = false,
  val working: Set<String> = emptySet(),
  val checkedAt: Long = 0L,
  val error: String? = null,
  val embeddedOnly: Boolean = true,
) {
  val updates: Int
    get() = forms.count { it.state == FormState.UPDATE_AVAILABLE }

  val new: Int
    get() = forms.count { it.state == FormState.NEW_ON_SERVER }

  val busy: Boolean
    get() = checking || working.isNotEmpty()
}

/** Drives the screen that compares the forms of the device with the server's. */
class FormUpdatesViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val updater: FormUpdater,
) : ViewModel() {
  val uiState: StateFlow<FormUpdatesUiState> =
    updater.state
      .map { state ->
        FormUpdatesUiState(
          forms = state.forms,
          checking = state.checking,
          working = state.working,
          checkedAt = state.checkedAt,
          error = state.error,
          embeddedOnly = EmbeddedFormsStore.get(applicationContext),
        )
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT),
        FormUpdatesUiState(embeddedOnly = EmbeddedFormsStore.get(applicationContext)),
      )

  private val _outcomes = MutableSharedFlow<FormUpdater.Outcome>(extraBufferCapacity = 1)
  val outcomes: SharedFlow<FormUpdater.Outcome> = _outcomes

  init {
    viewModelScope.launch { updater.load() }
  }

  fun check() {
    if (uiState.value.busy) return
    viewModelScope.launch { _outcomes.emit(updater.check()) }
  }

  fun take(id: String) {
    if (uiState.value.busy) return
    viewModelScope.launch { _outcomes.emit(updater.take(listOf(id))) }
  }

  fun takeAll() {
    if (uiState.value.busy) return
    viewModelScope.launch { _outcomes.emit(updater.takeAll()) }
  }

  fun useAppVersion(id: String) {
    if (uiState.value.busy) return
    viewModelScope.launch { _outcomes.emit(updater.useAppVersion(id)) }
  }

  private companion object {
    const val STOP_TIMEOUT = 5_000L
  }
}
