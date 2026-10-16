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
import android.view.ViewTreeObserver
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
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
      showReviewPageBeforeSubmit(true)
      showOptionalText(true)
      showRequiredText(false)
      showCancelButton?.let { setShowCancelButton(it) }
      showSubmitButton?.let { setShowSubmitButton(it) }
      submitButtonText?.let { setSubmitButtonText(it) }
    }
    .build()
}

/**
 * Adds the form to [containerId] once the screen has drawn a first frame. Building a data-capture
 * form is heavy; doing it before the first frame makes the transition stutter and shows nothing.
 * The progress indicator of the screen (if it has one) is shown until the form is in place.
 */
fun Fragment.addQuestionnaireWhenDrawn(
  containerId: Int,
  tag: String,
  create: () -> QuestionnaireFragment,
  onAdded: () -> Unit = {},
) {
  val root = view ?: return
  setFormLoading(true)
  // One frame to show the indicator, then build the form right after it has been drawn.
  root.postOnAnimation {
    root.post {
      if (!isAdded || view == null || childFragmentManager.isStateSaved) return@post
      if (childFragmentManager.findFragmentByTag(tag) == null) {
        childFragmentManager.commit { add(containerId, create(), tag) }
      }
      // The form lays itself out on the next frames; keep the indicator until then.
      root.postDelayed({ if (isAdded) setFormLoading(false) }, FORM_LOADING_MIN_MS)
      onAdded()
    }
  }
}

/** Shows or hides the screen's progress indicator without blocking touches. */
fun Fragment.setFormLoading(loading: Boolean) {
  view?.findViewById<View>(R.id.progressBar)?.visibility = if (loading) View.VISIBLE else View.GONE
}

private const val FORM_LOADING_MIN_MS = 250L

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
  // The buttons are part of the form's fixed layout: find them once and stop listening, instead of
  // searching the hierarchy after every layout pass while the user scrolls or types.
  var nextHooked = false
  var reviewHooked = false
  val listener =
    object : ViewTreeObserver.OnGlobalLayoutListener {
      override fun onGlobalLayout() {
        if (!nextHooked) {
          root.findViewById<View>(SdcR.id.pagination_next_button)?.let { button ->
            nextHooked = true
            button.onPressed { root.hintIfPageDidNotChange(button) }
          }
        }
        if (!reviewHooked) {
          root.findViewById<View>(SdcR.id.review_mode_button)?.let { button ->
            reviewHooked = true
            button.onPressed { root.hintIfReviewDidNotOpen() }
          }
        }
        if (nextHooked && reviewHooked && root.viewTreeObserver.isAlive) {
          root.viewTreeObserver.removeOnGlobalLayoutListener(this)
        }
      }
    }
  viewTreeObserver.addOnGlobalLayoutListener(listener)
  // A form without one of the buttons never finishes; stop after the first few frames anyway.
  postDelayed(
    {
      if (root.viewTreeObserver.isAlive) {
        root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
      }
    },
    HOOK_WINDOW_MS,
  )
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
private const val HOOK_WINDOW_MS = 5_000L

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
