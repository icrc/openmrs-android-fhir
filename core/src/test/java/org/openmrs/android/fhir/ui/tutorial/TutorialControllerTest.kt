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

import androidx.compose.ui.geometry.Rect
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class TutorialControllerTest {
  private fun step(
    key: String,
    action: TutorialAction = TutorialAction.Acknowledge,
    optional: Boolean = false,
  ) = TutorialStep(key, 0, 0, action, optional)

  @Before fun setUp() = TutorialController.end()

  @After
  fun tearDown() {
    TutorialController.end()
    listOf("a", "b", "c", "d").forEach(TutorialController::unregister)
  }

  @Test
  fun backGoesToTheStepBefore() {
    TutorialController.start(listOf(step("a"), step("b"), step("c")))
    TutorialController.acknowledge()
    TutorialController.acknowledge()

    TutorialController.previous()

    assertEquals(1, TutorialController.active?.index)
  }

  @Test
  fun thereIsNoBackOnTheFirstStep() {
    TutorialController.start(listOf(step("a"), step("b")))

    assertNull(TutorialController.previousIndex())
  }

  @Test
  fun backSkipsAnOptionalStepWhoseElementIsNotThere() {
    TutorialController.register("a", Rect.Zero)
    TutorialController.start(listOf(step("a"), step("b", optional = true), step("c")))
    TutorialController.acknowledge()
    TutorialController.skipMissing()

    assertEquals(0, TutorialController.previousIndex())
  }

  @Test
  fun backStopsBeforeAStepThatAskedForATap() {
    TutorialController.start(listOf(step("a", TutorialAction.Tap), step("b")))
    TutorialController.skipMissing()

    assertNull(TutorialController.previousIndex())
  }

  @Test
  fun anAbsentOptionalStepIsNotCounted() {
    TutorialController.register("a", Rect.Zero)
    TutorialController.register("c", Rect.Zero)
    TutorialController.start(listOf(step("a"), step("b", optional = true), step("c")))

    assertEquals(1 to 2, TutorialController.progress())
    TutorialController.acknowledge()
    TutorialController.skipMissing()
    assertEquals(2 to 2, TutorialController.progress())
  }

  @Test
  fun aPresentOptionalStepIsCounted() {
    listOf("a", "b", "c").forEach { TutorialController.register(it, Rect.Zero) }
    TutorialController.start(listOf(step("a"), step("b", optional = true), step("c")))

    assertEquals(1 to 3, TutorialController.progress())
  }

  @Test
  fun aTourEndsAfterItsLastStep() {
    TutorialController.start(listOf(step("a")))
    TutorialController.acknowledge()

    assertFalse(TutorialController.isActive)
  }

  @Test
  fun aRunningTourIsNotRestarted() {
    TutorialController.start(listOf(step("a"), step("b")))
    TutorialController.acknowledge()

    TutorialController.start(listOf(step("c")))

    assertTrue(TutorialController.isActive)
    assertEquals(1, TutorialController.active?.index)
  }
}
