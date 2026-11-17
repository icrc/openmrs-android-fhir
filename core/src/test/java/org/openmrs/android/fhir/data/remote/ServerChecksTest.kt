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

package org.openmrs.android.fhir.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.hl7.fhir.r4.model.Resource
import org.junit.Test

class ServerChecksTest {

  private class FakeServer(val answers: MutableMap<String, ProbeAnswer> = mutableMapOf()) :
    ServerProbe {
    val requests = mutableListOf<String>()

    override suspend fun send(method: String, url: String, body: Resource?): ProbeAnswer {
      requests += "$method $url"
      val key =
        answers.keys.firstOrNull { k ->
          k.startsWith("$method ") && url.contains(k.substring(method.length + 1))
        }
      return key?.let(answers::get) ?: ProbeAnswer(0, "No answer")
    }
  }

  private val endpoints = ServerEndpoints("http://s/fhir/", "http://s/rest/")
  private val capability =
    """{"resourceType":"CapabilityStatement","status":"active","date":"2026-01-01",
       "kind":"instance","fhirVersion":"4.0.1","format":["json"],
       "software":{"name":"OpenMRS","version":"2.7"}}"""

  private fun checker(server: FakeServer, id: String = "", name: String = "") =
    ServerChecker(server, endpoints, id, name) { 5L }

  private fun healthy() =
    FakeServer(
      mutableMapOf(
        "GET rest/session" to ProbeAnswer(200, "{}"),
        "GET fhir/metadata" to ProbeAnswer(200, capability),
        "GET fhir/Group" to ProbeAnswer(200, "{}"),
        "POST fhir/Group/" to ProbeAnswer(404, ""),
        "GET rest/cohortm/cohort" to ProbeAnswer(200, "{}"),
      ),
    )

  private fun List<CheckResult>.state(key: String) = first { it.key == key }.state

  @Test
  fun aServerThatDoesWhatTheAppNeedsIsSupportedAndNothingWasWritten() = runTest {
    val server = healthy()

    val results = checker(server).readOnly()

    assertThat(results.state(Checks.SERVER)).isEqualTo(CheckState.OK)
    assertThat(results.state(Checks.FHIR)).isEqualTo(CheckState.OK)
    assertThat(results.state(Checks.GROUPS_READ)).isEqualTo(CheckState.OK)
    assertThat(results.state(Checks.GROUP_MEMBERS)).isEqualTo(CheckState.OK)
    assertThat(results.state(Checks.COHORT_MODULE)).isEqualTo(CheckState.INFO)
    assertThat(results.state(Checks.CONCEPT_COHORT_ID)).isEqualTo(CheckState.NOT_USED)
    assertThat(server.requests.none { it.startsWith("PUT") || it.startsWith("DELETE") }).isTrue()
  }

  @Test
  fun aServerThatDoesNotAnswerChecksNothingElse() = runTest {
    val results = checker(FakeServer()).readOnly()

    assertThat(results.map { it.key }).containsExactly(Checks.SERVER)
    assertThat(results.single().state).isEqualTo(CheckState.UNKNOWN)
  }

  @Test
  fun anOperationTheServerDoesNotKnowIsNotSupported() = runTest {
    val server = healthy()
    server.answers["POST fhir/Group/"] =
      ProbeAnswer(
        400,
        """{"resourceType":"OperationOutcome","issue":[{"severity":"error","code":"processing",
           "diagnostics":"The FHIR endpoint on this server does not know how to handle POST operation"}]}""",
      )

    val results = checker(server).readOnly()

    assertThat(results.state(Checks.GROUP_MEMBERS)).isEqualTo(CheckState.UNSUPPORTED)
  }

  @Test
  fun aConceptTheServerLacksIsNotSupportedAndNamedInTheFeatures() = runTest {
    val server = healthy()
    server.answers["GET rest/concept/known"] = ProbeAnswer(200, """{"display":"Cohort"}""")
    server.answers["GET rest/concept/missing"] = ProbeAnswer(404, "")

    val results = checker(server, id = "known", name = "missing").readOnly()

    assertThat(results.state(Checks.CONCEPT_COHORT_ID)).isEqualTo(CheckState.OK)
    assertThat(results.state(Checks.CONCEPT_COHORT_NAME)).isEqualTo(CheckState.UNSUPPORTED)
    val features = ServerFeatures.of(ServerReport("s").withResults(results, 1L))
    assertThat(features.hasConcept("known")).isTrue()
    assertThat(features.hasConcept("missing")).isFalse()
    assertThat(features.hasConcept("")).isFalse()
  }

  @Test
  fun aConceptTheCheckCouldNotLookUpIsStillAllowed() = runTest {
    val server = healthy()
    server.answers["GET rest/concept/hidden"] = ProbeAnswer(403, "")

    val results = checker(server, id = "hidden").readOnly()

    assertThat(results.state(Checks.CONCEPT_COHORT_ID)).isEqualTo(CheckState.UNKNOWN)
    assertThat(ServerFeatures.of(ServerReport("s").withResults(results, 1L)).hasConcept("hidden"))
      .isTrue()
  }

  @Test
  fun theWriteTestCreatesThenDeletesAndAServerThatRefusesIsNotSupported() = runTest {
    val accepting = healthy()
    accepting.answers["PUT fhir/Group/"] = ProbeAnswer(201, "{}")
    accepting.answers["DELETE fhir/Group/"] = ProbeAnswer(200, "")
    val ok = checker(accepting).writeTest()
    assertThat(ok.state).isEqualTo(CheckState.OK)
    assertThat(accepting.requests.map { it.substringBefore(' ') }).containsExactly("PUT", "DELETE")

    val refusing = healthy()
    refusing.answers["PUT fhir/Group/"] =
      ProbeAnswer(
        422,
        """{"resourceType":"OperationOutcome","issue":[{"severity":"error","code":"processing",
           "diagnostics":"A standard cohort should not be created while the cohort module is active"}]}""",
      )
    val refused = checker(refusing).writeTest()
    assertThat(refused.state).isEqualTo(CheckState.UNSUPPORTED)
    assertThat(refused.detail).contains("cohort module is active")
    assertThat(refusing.requests.none { it.startsWith("DELETE") }).isTrue()
  }

  @Test
  fun aRefusalForAnotherReasonIsNotTakenForALackOfSupport() {
    assertThat(ServerChecker.refusalMeansUnsupported(422, "The name is already used")).isFalse()
    assertThat(ServerChecker.refusalMeansUnsupported(403, "No permission")).isFalse()
    assertThat(ServerChecker.refusalMeansUnsupported(422, "the Cohort Module is active")).isTrue()
    assertThat(ServerChecker.refusalMeansUnsupported(405, null)).isTrue()
    assertThat(ServerChecker.refusalMeansUnsupported(501, "")).isTrue()
  }

  @Test
  fun theFeaturesFollowTheReport() {
    val report =
      ServerReport("s")
        .withResults(
          listOf(CheckResult(Checks.GROUPS_WRITE, CheckState.UNSUPPORTED, "422")),
          1L,
        )

    val features = ServerFeatures.of(report)

    assertThat(features.editLists).isFalse()
    assertThat(features.changeMembers).isFalse()
    assertThat(ServerFeatures.of(null)).isEqualTo(ServerFeatures())
    assertThat(ServerFeatures.of(ServerReport("s")).editLists).isTrue()
  }

  @Test
  fun aReportSurvivesBeingSavedAndRead() {
    val report =
      ServerReport("http://s")
        .withResults(
          listOf(
            CheckResult(Checks.FHIR, CheckState.OK, "FHIR 4.0.1", at = 3L),
            CheckResult(
              Checks.CONCEPT_COHORT_ID,
              CheckState.UNSUPPORTED,
              "No such concept",
              "abc",
              4L,
            ),
          ),
          9L,
        )

    val read = ServerReport.fromJson(report.toJson())

    assertThat(read).isEqualTo(report)
    assertThat(ServerReport.fromJson("not json")).isNull()
    assertThat(ServerReport.fromJson(null)).isNull()
  }
}
