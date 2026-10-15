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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.fhir.datacapture.R as SdcR
import java.util.Collections
import java.util.WeakHashMap
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
): QuestionnaireFragment {
  explainBlockedNavigationIn(this)
  return QuestionnaireFragment.builder()
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
}

/** Hosts whose child fragment manager already carries the callback below. */
private val hostsWithNavigationHint: MutableSet<Fragment> = Collections.newSetFromMap(WeakHashMap())

/**
 * The form library refuses "Next" / "Review" while a required question on the page is unanswered
 * and only marks that question, which is often below the fold, so the button looks dead. This tells
 * the user why, without reading the response (which would disturb the form state).
 */
private fun explainBlockedNavigationIn(host: Fragment) {
  if (!hostsWithNavigationHint.add(host)) return
  host.childFragmentManager.registerFragmentLifecycleCallbacks(
    object : FragmentManager.FragmentLifecycleCallbacks() {
      override fun onFragmentViewCreated(
        fm: FragmentManager,
        f: Fragment,
        v: View,
        savedInstanceState: Bundle?,
      ) {
        if (f is QuestionnaireFragment) v.watchNavigationButtons()
      }
    },
    false,
  )
}

private fun View.watchNavigationButtons() {
  val root = this
  viewTreeObserver.addOnGlobalLayoutListener {
    root.findViewById<View>(SdcR.id.pagination_next_button)?.let { button ->
      if (button.getTag(SdcR.id.pagination_next_button) == null) {
        button.setTag(SdcR.id.pagination_next_button, true)
        button.onPressed { root.hintIfPageDidNotChange(button) }
      }
    }
    root.findViewById<View>(SdcR.id.review_mode_button)?.let { button ->
      if (button.getTag(SdcR.id.review_mode_button) == null) {
        button.setTag(SdcR.id.review_mode_button, true)
        button.onPressed { root.hintIfReviewDidNotOpen() }
      }
    }
  }
}

/** Runs [action] when the button is released, leaving the library's own click handling alone. */
@SuppressLint("ClickableViewAccessibility")
private fun View.onPressed(action: () -> Unit) {
  setOnTouchListener { _, event ->
    if (event.actionMasked == MotionEvent.ACTION_UP) action()
    false
  }
}

private fun View.hintIfPageDidNotChange(nextButton: View) {
  val progress = findViewById<ProgressBar>(SdcR.id.questionnaire_progress_indicator)
  val before = progress?.progress
  postDelayed(
    {
      val stayedOnPage = progress == null || progress.progress == before
      if (isAttachedToWindow && nextButton.isShown && stayedOnPage) showRequiredHint()
    },
    BLOCKED_CHECK_DELAY_MS,
  )
}

private fun View.hintIfReviewDidNotOpen() {
  postDelayed(
    {
      val editButton = findViewById<View>(SdcR.id.review_mode_edit_button)
      if (isAttachedToWindow && editButton?.isShown != true) showRequiredHint()
    },
    BLOCKED_CHECK_DELAY_MS,
  )
}

private fun View.showRequiredHint() {
  Toast.makeText(context, R.string.questionnaire_required_answers_missing, Toast.LENGTH_LONG).show()
}

private const val BLOCKED_CHECK_DELAY_MS = 700L

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
