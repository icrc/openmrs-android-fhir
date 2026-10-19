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
package org.openmrs.android.fhir.ui.tutorial

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Makes this element something a tour can point at: `Modifier.tutorialTarget("home.patients")`.
 *
 * It announces where the element is (so a step can highlight it) and watches, without consuming
 * anything, whether it is tapped, so the element keeps working exactly as before and the tour sees
 * what the user did. Put it before the element's own `clickable`. It costs nothing when no tour
 * runs.
 */
fun Modifier.tutorialTarget(key: String): Modifier = composed {
  DisposableEffect(key) { onDispose { TutorialController.unregister(key) } }
  this.onGloballyPositioned { TutorialController.register(key, it.boundsInWindow()) }
    .pointerInput(key) {
      awaitEachGesture {
        // The Initial pass comes before the element's own handlers and nothing is consumed here.
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
        if (up != null) TutorialController.onTargetTapped(key)
      }
    }
}

/** The elements the first-run tour points at. */
object TutorialTargets {
  const val HOME_PATIENTS = "home.patients"
  const val PATIENT_SEARCH = "patients.search"
  const val PATIENT_REGISTER = "patients.register"
}
