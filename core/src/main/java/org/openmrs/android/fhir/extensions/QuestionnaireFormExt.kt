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

import androidx.fragment.app.Fragment
import com.google.android.fhir.datacapture.QuestionnaireFragment
import org.openmrs.android.fhir.R

/**
 * Creates the data-capture form with the display options every screen shares (review page, optional
 * markers). Screens only state what differs: [responseJson] to pre-fill, [showCancelButton] and the
 * submit button ([showSubmitButton] / [submitButtonText]); options left `null` keep the library
 * default.
 */
fun Fragment.newQuestionnaireFragment(
  questionnaireJson: String,
  responseJson: String? = null,
  showCancelButton: Boolean? = null,
  showSubmitButton: Boolean? = null,
  submitButtonText: String? = null,
): QuestionnaireFragment =
  QuestionnaireFragment.builder()
    .apply {
      setQuestionnaire(questionnaireJson)
      responseJson?.let { setQuestionnaireResponse(it) }
      showReviewPageBeforeSubmit(
        requireContext().resources.getBoolean(R.bool.show_review_page_before_submit),
      )
      showOptionalText(true)
      showRequiredText(false)
      showCancelButton?.let { setShowCancelButton(it) }
      showSubmitButton?.let { setShowSubmitButton(it) }
      submitButtonText?.let { setSubmitButtonText(it) }
    }
    .build()

/** Calls [onSubmit] / [onCancel] when the form's own submit / cancel button is pressed. */
fun Fragment.onQuestionnaireResult(onSubmit: () -> Unit, onCancel: (() -> Unit)? = null) {
  childFragmentManager.setFragmentResultListener(
    QuestionnaireFragment.SUBMIT_REQUEST_KEY,
    viewLifecycleOwner,
  ) { _, _ ->
    onSubmit()
  }
  if (onCancel != null) {
    childFragmentManager.setFragmentResultListener(
      QuestionnaireFragment.CANCEL_REQUEST_KEY,
      viewLifecycleOwner,
    ) { _, _ ->
      onCancel()
    }
  }
}
