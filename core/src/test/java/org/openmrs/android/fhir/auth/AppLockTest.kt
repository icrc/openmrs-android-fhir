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
package org.openmrs.android.fhir.auth

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AppLockTest {
  @Test
  fun aShortAbsenceDoesNotLock() {
    assertFalse(AppLock.isLockDue(awayMillis = 10_000, graceSeconds = 30, screenWentOff = false))
  }

  @Test
  fun anAbsenceOfTheChosenDelayLocks() {
    assertTrue(AppLock.isLockDue(awayMillis = 30_000, graceSeconds = 30, screenWentOff = false))
    assertTrue(AppLock.isLockDue(awayMillis = 61_000, graceSeconds = 60, screenWentOff = false))
  }

  @Test
  fun aLongerDelayHoldsLonger() {
    assertFalse(AppLock.isLockDue(awayMillis = 120_000, graceSeconds = 300, screenWentOff = false))
  }

  @Test
  fun everyTimeLocksAfterARealAbsence() {
    assertTrue(AppLock.isLockDue(awayMillis = 2_000, graceSeconds = 0, screenWentOff = false))
  }

  @Test
  fun oneScreenHandingOverToAnotherDoesNotLockEvenOnEveryTime() {
    assertFalse(AppLock.isLockDue(awayMillis = 40, graceSeconds = 0, screenWentOff = false))
  }

  @Test
  fun theScreenGoingOffLocksWhateverTheDelay() {
    assertTrue(AppLock.isLockDue(awayMillis = 500, graceSeconds = 300, screenWentOff = true))
  }
}
