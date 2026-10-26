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

import androidx.annotation.StringRes

/** What the user has to do on the highlighted element to go on to the next step. */
sealed interface TutorialAction {
  /**
   * Tap the element. Everything outside it is blocked, so this is the only way on. The tap also
   * does what it always does (open the screen, the form...), so the tour can go on in the next
   * screen.
   */
  data object Tap : TutorialAction

  /**
   * Read the explanation and press the button of the callout. The element is shown but not
   * touchable: nothing on the screen can be used until the step is acknowledged.
   */
  data object Acknowledge : TutorialAction
}

/**
 * One step of a tour: [targetKey] names the element to highlight (see `Modifier.tutorialTarget`),
 * which may be in a screen that is not open yet: the tour waits for it to appear.
 */
data class TutorialStep(
  val targetKey: String,
  @StringRes val title: Int,
  @StringRes val body: Int,
  val action: TutorialAction = TutorialAction.Tap,
  /**
   * The element may not be on the screen (a list that is empty, a button only some users have): the
   * step is then skipped instead of ending the tour.
   */
  val optional: Boolean = false,
)
