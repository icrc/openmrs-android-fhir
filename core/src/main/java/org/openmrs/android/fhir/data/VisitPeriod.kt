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

/** Why a visit's start and end cannot be saved. */
enum class VisitPeriodError {
  START_IN_FUTURE,
  END_IN_FUTURE,
  END_BEFORE_START,

  /** An encounter of the visit starts before the visit does. */
  ENCOUNTER_BEFORE_START,

  /** An encounter of the visit ends after the visit does. */
  ENCOUNTER_AFTER_END,

  /** The visit would be open while another one is. */
  OTHER_VISIT_OPEN,
}

/**
 * The start and end of a visit as they can be changed: the visit's own limits, and the span its
 * encounters take ([earliest] to [latest]), which the visit has to contain.
 */
data class VisitEditState(
  val start: Date,
  val end: Date?,
  val earliest: Date?,
  val latest: Date?,
)

/** What a visit's start and end have to satisfy, the same for the dialog and for the save. */
object VisitPeriodRules {
  /** @return what is wrong with the visit running from [start] to [end], or `null` when nothing. */
  fun check(
    start: Date,
    end: Date?,
    earliest: Date?,
    latest: Date?,
    now: Date = Date(),
  ): VisitPeriodError? =
    when {
      start.after(now) -> VisitPeriodError.START_IN_FUTURE
      end != null && end.after(now) -> VisitPeriodError.END_IN_FUTURE
      end != null && end.before(start) -> VisitPeriodError.END_BEFORE_START
      earliest != null && earliest.before(start) -> VisitPeriodError.ENCOUNTER_BEFORE_START
      latest != null && end != null && latest.after(end) -> VisitPeriodError.ENCOUNTER_AFTER_END
      else -> null
    }
}
