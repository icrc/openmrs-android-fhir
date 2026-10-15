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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.fhir.datacapture.QuestionnaireFragment
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.util.answerSignature

/**
 * Asks for confirmation before a questionnaire screen is left with unsaved changes.
 *
 * The answers are snapshotted when the questionnaire first resumes; the user is only asked when the
 * current answers differ from that snapshot, so opening a form and backing out never prompts. If no
 * snapshot could be taken the guard stays out of the way.
 */
class UnsavedChangesGuard(
  private val fragment: Fragment,
  private val questionnaireTag: String,
) {
  private var baseline: String? = null

  /** Call from `onViewCreated`. Also intercepts the system back button. */
  fun attach() {
    fragment.childFragmentManager.registerFragmentLifecycleCallbacks(
      object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
          if (f is QuestionnaireFragment && f.tag == questionnaireTag && baseline == null) {
            fragment.viewLifecycleOwner.lifecycleScope.launch {
              baseline = f.getQuestionnaireResponse().answerSignature()
            }
          }
        }
      },
      false,
    )
    val dispatcher = fragment.requireActivity().onBackPressedDispatcher
    dispatcher.addCallback(fragment.viewLifecycleOwner) {
      confirmLeave {
        isEnabled = false
        dispatcher.onBackPressed()
      }
    }
  }

  /** Runs [onLeave] immediately when nothing changed, otherwise after the user confirms. */
  fun confirmLeave(onLeave: () -> Unit) {
    fragment.viewLifecycleOwner.lifecycleScope.launch {
      val questionnaire =
        fragment.childFragmentManager.findFragmentByTag(questionnaireTag) as? QuestionnaireFragment
      val snapshot = baseline
      val changed =
        questionnaire != null &&
          snapshot != null &&
          questionnaire.getQuestionnaireResponse().answerSignature() != snapshot
      if (!changed) {
        onLeave()
        return@launch
      }
      AlertDialog.Builder(fragment.requireContext())
        .setTitle(R.string.discard_changes_title)
        .setMessage(R.string.discard_changes_message)
        .setPositiveButton(R.string.discard_changes_confirm) { _, _ -> onLeave() }
        .setNegativeButton(R.string.discard_changes_keep_editing, null)
        .show()
    }
  }
}
