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
package org.openmrs.android.fhir.util

import kotlin.test.assertEquals
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.IntegerType
import org.hl7.fhir.r4.model.Observation
import org.junit.Test

/** Which parent an observation goes to when the same question sits under several parents. */
class ObservationChildMatchingTest {
  private val count = "count-concept"

  // A question answered with `value` (null: not answered), under the given parent.
  private fun info(link: String, parent: String, value: Int?, concept: String = count) =
    ObservationChildInfo(
      childLinkId = link,
      childCodingKeys = setOf(CodingKey(null, concept)),
      parentCoding = Coding().setCode(parent),
      parentCodingKey = CodingKey(null, parent),
      expectedValueTokens = if (value == null) emptySet() else setOf(value.toString()),
    )

  private fun lookup(vararg infos: ObservationChildInfo) =
    ObservationGroupLookup(
      childInfos = infos.toList(),
      childInfosByCodingKey =
        infos
          .toList()
          .flatMap { i -> i.childCodingKeys.map { it to i } }
          .groupBy({ it.first }, { it.second }),
      parentCodingKeys = infos.map { it.parentCodingKey }.toSet(),
    )

  private fun observation(value: Int, concept: String = count) =
    Observation().apply {
      code = CodeableConcept().addCoding(Coding().setCode(concept))
      this.value = IntegerType(value)
    }

  private fun parentsOf(
    lookup: ObservationGroupLookup,
    vararg observations: Observation,
  ): List<String?> {
    val assignment = lookup.assignChildInfos(observations.toList())
    return observations.map { assignment.infoOf(it)?.parentCoding?.code }
  }

  @Test
  fun theSameValueUnderEveryParentGoesToEachParentInTheOrderOfTheForm() {
    val lookup = lookup(info("a", "new", 0), info("b", "closed", 0), info("c", "individual", 0))

    assertEquals(
      listOf("new", "closed", "individual"),
      parentsOf(lookup, observation(0), observation(0), observation(0)),
    )
  }

  @Test
  fun valuesThatDifferStillGoToTheParentWhoseQuestionHasThatValue() {
    val lookup = lookup(info("a", "new", 3), info("b", "closed", 7), info("c", "individual", 3))

    assertEquals(
      listOf("new", "closed", "individual"),
      parentsOf(lookup, observation(3), observation(7), observation(3)),
    )
  }

  @Test
  fun aQuestionThatWasNotAnsweredDoesNotTakeThePlaceOfAnAnsweredOne() {
    val lookup = lookup(info("a", "new", 5), info("b", "closed", null), info("c", "individual", 5))

    assertEquals(listOf("new", "individual"), parentsOf(lookup, observation(5), observation(5)))
  }

  @Test
  fun aQuestionUnderOneParentOnlyIsNotAffected() {
    val lookup = lookup(info("a", "new", 0), info("only", "referred", 0, concept = "other"))

    assertEquals(
      listOf("referred", "new"),
      parentsOf(lookup, observation(0, concept = "other"), observation(0)),
    )
  }

  @Test
  fun anObservationOutsideTheAssignmentIsMatchedByItself() {
    val lookup = lookup(info("a", "new", 0), info("b", "closed", 0))
    val assignment = lookup.assignChildInfos(listOf(observation(0)))

    assertEquals("new", assignment.infoOf(observation(0))?.parentCoding?.code)
  }
}
