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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * The state of the tour on screen. One tour at a time, held by the process (it survives a rotation;
 * if the process dies the tour is simply over). Nothing here touches the network or the disk.
 *
 * Two halves talk through it: the elements of the screens announce where they are
 * (`Modifier.tutorialTarget`), and one overlay ([TutorialOverlay]) draws the tour above the whole
 * window and tells it what the user did.
 */
object TutorialController {
  /** The step on show: [index] in [steps]. */
  data class Active(val steps: List<TutorialStep>, val index: Int) {
    val step: TutorialStep
      get() = steps[index]
  }

  var active by mutableStateOf<Active?>(null)
    private set

  /** Where each announced element is, in window coordinates. */
  val targets = mutableStateMapOf<String, Rect>()

  val isActive: Boolean
    get() = active != null

  fun register(key: String, bounds: Rect) {
    if (targets[key] != bounds) targets[key] = bounds
  }

  fun unregister(key: String) {
    targets.remove(key)
  }

  fun start(steps: List<TutorialStep>) {
    if (steps.isEmpty() || isActive) return
    active = Active(steps, 0)
  }

  /** The user tapped the element with [key]: it counts only for the step that asks for that. */
  fun onTargetTapped(key: String) {
    val current = active?.step ?: return
    if (current.action == TutorialAction.Tap && current.targetKey == key) next()
  }

  /** The button of an [TutorialAction.Acknowledge] step. */
  fun acknowledge() {
    if (active?.step?.action == TutorialAction.Acknowledge) next()
  }

  /** The element of an optional step is not there: on to the next step. */
  fun skipMissing() = next()

  /** Ends the whole tour, whichever step it is at ("Skip tour", the close button, Back). */
  fun end() {
    active = null
  }

  private fun next() {
    val current = active ?: return
    active =
      if (current.index + 1 < current.steps.size) current.copy(index = current.index + 1) else null
  }
}
