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
package org.openmrs.android.fhir.extensions

import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.components.TapObservingConstraintLayout

/**
 * Asks for confirmation before a form screen is left after the user has interacted with it.
 *
 * "Interacted" means a tap inside the screen, so opening a form and backing out never prompts.
 *
 * Deliberately does not read the questionnaire response: asking the form library for its response
 * early changes its internal state (it dropped conditional items and later answers such as gender),
 * so the only safe moment to read it is when the user submits.
 */
class UnsavedChangesGuard(private val fragment: Fragment) {
  private var interacted = false

  /** Call from `onViewCreated`. Also intercepts the system back button. */
  fun attach(container: TapObservingConstraintLayout) {
    container.onTap = { interacted = true }
    val dispatcher = fragment.requireActivity().onBackPressedDispatcher
    dispatcher.addCallback(fragment.viewLifecycleOwner) {
      confirmLeave {
        isEnabled = false
        dispatcher.onBackPressed()
      }
    }
  }

  /** Runs [onLeave] immediately when untouched, otherwise after the user confirms. */
  fun confirmLeave(onLeave: () -> Unit) {
    if (!interacted) {
      onLeave()
      return
    }
    MaterialAlertDialogBuilder(fragment.requireContext())
      .setTitle(R.string.discard_changes_title)
      .setMessage(R.string.discard_changes_message)
      .setPositiveButton(R.string.discard_changes_confirm) { _, _ -> onLeave() }
      .setNegativeButton(R.string.discard_changes_keep_editing, null)
      .show()
  }
}
