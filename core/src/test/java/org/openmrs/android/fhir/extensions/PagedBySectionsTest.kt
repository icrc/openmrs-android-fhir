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

import kotlin.test.assertEquals
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType
import org.junit.Test

class PagedBySectionsTest {
  private fun question(id: String) =
    QuestionnaireItemComponent().apply {
      linkId = id
      type = QuestionnaireItemType.STRING
    }

  private fun group(id: String, vararg children: QuestionnaireItemComponent) =
    QuestionnaireItemComponent().apply {
      linkId = id
      type = QuestionnaireItemType.GROUP
      children.forEach { addItem(it) }
    }

  private fun form(vararg items: QuestionnaireItemComponent) =
    Questionnaire().apply { items.forEach { addItem(it) } }

  private fun Questionnaire.pages() =
    item.map { page -> page.linkId to page.item.map { it.linkId } }

  @Test
  fun theQuestionsOfAWrapperGroupStayAfterTheSectionThatPrecedesThem() {
    // The shape of the registration form: the names first, then loose questions, then sections.
    val registration =
      form(
        group(
          "client",
          group("name", question("first"), question("family")),
          question("dobKnown"),
          question("dob"),
          question("gender"),
          group("phone", question("number")),
          group("address", question("city")),
        ),
      )

    registration.pagedBySections()

    assertEquals(
      listOf(
        "name" to listOf("first", "family", "dobKnown", "dob", "gender"),
        "phone" to listOf("number"),
        "address" to listOf("city"),
      ),
      registration.pages(),
    )
  }

  @Test
  fun aQuestionBeforeEverySectionGoesToTheTopOfTheFirstPage() {
    val registration =
      form(
        group(
          "client",
          question("intro"),
          group("name", question("first")),
          group("address", question("city")),
        ),
      )

    registration.pagedBySections()

    assertEquals(
      listOf("name" to listOf("intro", "first"), "address" to listOf("city")),
      registration.pages(),
    )
  }

  @Test
  fun aQuestionBetweenTwoLaterSectionsStaysWithTheOneBeforeIt() {
    val registration =
      form(
        group(
          "client",
          group("name", question("first")),
          group("contact", question("phone")),
          question("email"),
          group("address", question("city")),
        ),
      )

    registration.pagedBySections()

    assertEquals(
      listOf(
        "name" to listOf("first"),
        "contact" to listOf("phone", "email"),
        "address" to listOf("city"),
      ),
      registration.pages(),
    )
  }

  @Test
  fun questionsOutsideAnyGroupGoToTheTopOfTheFirstPage() {
    val registration =
      form(question("identifier"), group("name", question("first")), group("city", question("c")))

    registration.pagedBySections()

    assertEquals(
      listOf("name" to listOf("identifier", "first"), "city" to listOf("c")),
      registration.pages(),
    )
  }
}
