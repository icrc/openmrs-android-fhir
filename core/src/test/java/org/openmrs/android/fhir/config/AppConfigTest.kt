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

package org.openmrs.android.fhir.config

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser

class AppConfigTest {

  private fun parse(extra: String): AppConfig {
    val xml =
      """<app-config>
           <server url="http://host/openmrs" />
           <diagnostics password="x" />
           $extra
         </app-config>"""
    val parser = KXmlParser()
    parser.setInput(StringReader(xml))
    return AppConfig.parse(parser)
  }

  @Test
  fun `the cohort concepts come from the configuration`() {
    val config =
      parse(
        """<group-sessions cohort-identifier-concept=" a-uuid "
             cohort-name-concept="b-uuid" />""",
      )
    assertEquals("a-uuid", config.groupSessions.cohortIdentifierConcept)
    assertEquals("b-uuid", config.groupSessions.cohortNameConcept)
  }

  @Test
  fun `without the element or an attribute the observation is not recorded`() {
    assertEquals(GroupSessionConfig(), parse("").groupSessions)
    val one = parse("""<group-sessions cohort-name-concept="b-uuid" />""").groupSessions
    assertEquals("", one.cohortIdentifierConcept)
    assertEquals("b-uuid", one.cohortNameConcept)
  }

  @Test
  fun `the server checks are on unless the configuration turns them off`() {
    assertEquals(true, parse("").serverCompatibility.enabled)
    assertEquals(true, parse("""<server-compatibility />""").serverCompatibility.enabled)
    assertEquals(
      false,
      parse("""<server-compatibility enabled="false" />""").serverCompatibility.enabled,
    )
  }
}
