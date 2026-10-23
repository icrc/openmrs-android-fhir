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
package org.openmrs.android.fhir.data

import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** What a visit's start and end have to satisfy, in the order the checks are made. */
class VisitPeriodRulesTest {
  private val now = Date(1_000_000_000_000L)

  private fun at(minutesFromNow: Int) = Date(now.time + minutesFromNow * 60_000L)

  @Test
  fun aVisitThatContainsItsEncountersIsValid() {
    assertNull(
      VisitPeriodRules.check(at(-120), at(-30), earliest = at(-90), latest = at(-45), now = now),
    )
  }

  @Test
  fun anOpenVisitIsValid() {
    assertNull(
      VisitPeriodRules.check(at(-120), null, earliest = at(-90), latest = at(-45), now = now),
    )
  }

  @Test
  fun aVisitCannotStartInTheFuture() {
    assertEquals(
      VisitPeriodError.START_IN_FUTURE,
      VisitPeriodRules.check(at(5), null, earliest = null, latest = null, now = now),
    )
  }

  @Test
  fun aVisitCannotEndInTheFuture() {
    assertEquals(
      VisitPeriodError.END_IN_FUTURE,
      VisitPeriodRules.check(at(-60), at(5), earliest = null, latest = null, now = now),
    )
  }

  @Test
  fun aVisitCannotEndBeforeItStarts() {
    assertEquals(
      VisitPeriodError.END_BEFORE_START,
      VisitPeriodRules.check(at(-30), at(-60), earliest = null, latest = null, now = now),
    )
  }

  @Test
  fun anEncounterBeforeTheStartIsRejected() {
    assertEquals(
      VisitPeriodError.ENCOUNTER_BEFORE_START,
      VisitPeriodRules.check(at(-60), at(-10), earliest = at(-90), latest = at(-30), now = now),
    )
  }

  @Test
  fun anEncounterAfterTheEndIsRejected() {
    assertEquals(
      VisitPeriodError.ENCOUNTER_AFTER_END,
      VisitPeriodRules.check(at(-120), at(-60), earliest = at(-90), latest = at(-30), now = now),
    )
  }

  @Test
  fun anEncounterAfterTheEndOfAnOpenVisitIsNotAProblem() {
    assertNull(
      VisitPeriodRules.check(at(-120), null, earliest = at(-90), latest = at(-30), now = now),
    )
  }

  @Test
  fun anEncounterExactlyAtTheLimitsIsInside() {
    assertNull(
      VisitPeriodRules.check(at(-90), at(-30), earliest = at(-90), latest = at(-30), now = now),
    )
  }

  @Test
  fun theFutureStartIsReportedBeforeAnythingElse() {
    assertEquals(
      VisitPeriodError.START_IN_FUTURE,
      VisitPeriodRules.check(at(10), at(-10), earliest = at(-90), latest = at(-30), now = now),
    )
  }
}
