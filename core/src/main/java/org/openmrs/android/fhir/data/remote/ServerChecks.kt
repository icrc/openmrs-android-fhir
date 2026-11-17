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

import ca.uhn.fhir.context.FhirContext
import java.util.UUID
import org.hl7.fhir.r4.model.CapabilityStatement
import org.hl7.fhir.r4.model.Group
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.Resource
import org.json.JSONArray
import org.json.JSONObject
import org.openmrs.android.fhir.patientlists.GroupMapper

data class ProbeAnswer(val code: Int, val text: String?) {
  val answered: Boolean
    get() = code > 0
}

interface ServerProbe {
  suspend fun send(method: String, url: String, body: Resource? = null): ProbeAnswer
}

data class ServerEndpoints(val fhir: String, val rest: String)

enum class CheckState {
  OK,

  /** The server does not (the app works without the feature that needs it). */
  UNSUPPORTED,

  /** The check could not tell (no answer, no permission, an answer that means nothing). */
  UNKNOWN,

  /** Not a requirement: something worth knowing when something does not work. */
  INFO,

  /** Nothing to check: the setting that needs it is not used. */
  NOT_USED,
}

object Checks {
  const val SERVER = "server"
  const val FHIR = "fhir"
  const val GROUPS_READ = "groups_read"
  const val GROUP_MEMBERS = "group_members"
  const val GROUPS_WRITE = "groups_write"
  const val COHORT_MODULE = "cohort_module"
  const val CONCEPT_COHORT_ID = "concept_cohort_id"
  const val CONCEPT_COHORT_NAME = "concept_cohort_name"

  val ORDER =
    listOf(
      SERVER,
      FHIR,
      GROUPS_READ,
      GROUPS_WRITE,
      GROUP_MEMBERS,
      COHORT_MODULE,
      CONCEPT_COHORT_ID,
      CONCEPT_COHORT_NAME,
    )
}

/**
 * What one check found. [detail] is what the server said or what was tried, for whoever debugs;
 * [subject] is what the check was about when that is not obvious (the concept's id).
 */
data class CheckResult(
  val key: String,
  val state: CheckState,
  val detail: String? = null,
  val subject: String? = null,
  val at: Long = 0L,
)

data class ServerReport(
  val serverUrl: String,
  val results: List<CheckResult> = emptyList(),
  val checkedAt: Long = 0L,
) {
  fun result(key: String): CheckResult? = results.firstOrNull { it.key == key }

  fun withResults(changed: List<CheckResult>, at: Long): ServerReport {
    val keys = changed.map { it.key }.toSet()
    val merged = results.filter { it.key !in keys } + changed
    return copy(
      results = merged.sortedBy { Checks.ORDER.indexOf(it.key).takeIf { i -> i >= 0 } ?: 99 },
      checkedAt = at,
    )
  }

  fun toJson(): String =
    JSONObject()
      .put("server", serverUrl)
      .put("checkedAt", checkedAt)
      .put(
        "results",
        JSONArray().also { array ->
          results.forEach {
            array.put(
              JSONObject()
                .put("key", it.key)
                .put("state", it.state.name)
                .put("detail", it.detail ?: JSONObject.NULL)
                .put("subject", it.subject ?: JSONObject.NULL)
                .put("at", it.at),
            )
          }
        },
      )
      .toString()

  companion object {
    fun fromJson(text: String?): ServerReport? =
      try {
        val json = JSONObject(text ?: return null)
        val array = json.getJSONArray("results")
        ServerReport(
          serverUrl = json.getString("server"),
          checkedAt = json.optLong("checkedAt"),
          results =
            (0 until array.length()).map { i ->
              val item = array.getJSONObject(i)
              CheckResult(
                key = item.getString("key"),
                state = CheckState.valueOf(item.getString("state")),
                detail = item.optString("detail").takeIf { it.isNotEmpty() && it != "null" },
                subject = item.optString("subject").takeIf { it.isNotEmpty() && it != "null" },
                at = item.optLong("at"),
              )
            },
        )
      } catch (e: Exception) {
        null
      }
  }
}

/**
 * What the app needs from a server beyond standard OpenMRS, read from a report. Anything the checks
 * have not found out is allowed: the app is offline first, so it cannot wait for a check; what the
 * server then refuses is learned (see [ServerChecker.refusalMeansUnsupported]).
 */
data class ServerFeatures(
  val editLists: Boolean = true,
  val changeMembers: Boolean = true,
  val missingConcepts: Set<String> = emptySet(),
) {
  fun hasConcept(id: String) = id.isNotBlank() && id !in missingConcepts

  companion object {
    fun of(report: ServerReport?): ServerFeatures {
      if (report == null) return ServerFeatures()
      fun unsupported(key: String) = report.result(key)?.state == CheckState.UNSUPPORTED
      return ServerFeatures(
        editLists = !unsupported(Checks.GROUPS_WRITE),
        changeMembers = !unsupported(Checks.GROUP_MEMBERS) && !unsupported(Checks.GROUPS_WRITE),
        missingConcepts =
          report.results
            .filter { it.key.startsWith("concept_") && it.state == CheckState.UNSUPPORTED }
            .mapNotNull { it.subject }
            .toSet(),
      )
    }
  }
}

/**
 * Finds out what a server supports. The checks that are run on their own only read (and send an
 * operation to a patient list that cannot exist, which a server that has it answers "not found"
 * to); the one that writes, [writeTest], creates a temporary patient list and deletes it again, and
 * is only run when the user asks.
 */
class ServerChecker(
  private val probe: ServerProbe,
  private val endpoints: ServerEndpoints,
  private val cohortIdentifierConcept: String,
  private val cohortNameConcept: String,
  private val clock: () -> Long = System::currentTimeMillis,
) {

  suspend fun readOnly(): List<CheckResult> {
    val server = server()
    if (server.state != CheckState.OK) return listOf(server)
    return listOf(
      server,
      fhir(),
      groupsRead(),
      groupMembers(),
      cohortModule(),
      concept(Checks.CONCEPT_COHORT_ID, cohortIdentifierConcept),
      concept(Checks.CONCEPT_COHORT_NAME, cohortNameConcept),
    )
  }

  private fun result(
    key: String,
    state: CheckState,
    detail: String? = null,
    subject: String? = null,
  ) = CheckResult(key, state, detail, subject, clock())

  private suspend fun server(): CheckResult {
    val answer = probe.send("GET", endpoints.rest + "session")
    return when {
      !answer.answered -> result(Checks.SERVER, CheckState.UNKNOWN, answer.text ?: "No answer")
      answer.code == 401 || answer.code == 403 ->
        result(Checks.SERVER, CheckState.UNKNOWN, "Not signed in (${answer.code})")
      answer.code == 200 -> result(Checks.SERVER, CheckState.OK, "200")
      else -> result(Checks.SERVER, CheckState.UNKNOWN, "Answered ${answer.code}")
    }
  }

  private suspend fun fhir(): CheckResult {
    val answer = probe.send("GET", endpoints.fhir + "metadata")
    if (answer.code != 200) {
      return result(Checks.FHIR, CheckState.UNSUPPORTED, "Answered ${answer.code}")
    }
    val statement = parse(answer.text) as? CapabilityStatement
    val version = statement?.fhirVersion?.toCode()
    val software =
      statement?.software?.let { "${it.name.orEmpty()} ${it.version.orEmpty()}".trim() }
    return when {
      version == null -> result(Checks.FHIR, CheckState.UNSUPPORTED, "Not a capability statement")
      !version.startsWith("4.") ->
        result(Checks.FHIR, CheckState.UNSUPPORTED, "FHIR $version (R4 is needed)")
      else ->
        result(
          Checks.FHIR,
          CheckState.OK,
          listOfNotNull("FHIR $version", software?.takeIf { it.isNotEmpty() }).joinToString(", "),
        )
    }
  }

  private suspend fun groupsRead(): CheckResult {
    val answer = probe.send("GET", endpoints.fhir + "Group?_count=1&_elements=id")
    return when {
      answer.code == 200 -> result(Checks.GROUPS_READ, CheckState.OK, "200")
      answer.code == 401 || answer.code == 403 ->
        result(Checks.GROUPS_READ, CheckState.UNKNOWN, "No permission (${answer.code})")
      answer.answered ->
        result(Checks.GROUPS_READ, CheckState.UNSUPPORTED, "Answered ${answer.code}${said(answer)}")
      else -> result(Checks.GROUPS_READ, CheckState.UNKNOWN, answer.text ?: "No answer")
    }
  }

  /**
   * `$add-members` of a patient on a patient list that does not exist: a server that has the
   * operation answers "not found", one that does not know it answers that it does not know how to
   * handle it. Nothing is changed either way.
   */
  private suspend fun groupMembers(): CheckResult {
    val absent = UUID.randomUUID().toString()
    val answer =
      probe.send(
        "POST",
        endpoints.fhir + "Group/$absent/\$add-members",
        GroupMapper.parameters(listOf(UUID.randomUUID().toString())),
      )
    return when {
      answer.code == 404 || answer.code == 410 ->
        result(Checks.GROUP_MEMBERS, CheckState.OK, "${answer.code}${said(answer)}")
      !answer.answered ->
        result(Checks.GROUP_MEMBERS, CheckState.UNKNOWN, answer.text ?: "No answer")
      answer.code == 401 || answer.code == 403 ->
        result(Checks.GROUP_MEMBERS, CheckState.UNKNOWN, "No permission (${answer.code})")
      answer.code in setOf(400, 405, 501) && doesNotKnowOperation(answer.text) ->
        result(Checks.GROUP_MEMBERS, CheckState.UNSUPPORTED, "${answer.code}${said(answer)}")
      else ->
        result(Checks.GROUP_MEMBERS, CheckState.UNKNOWN, "Answered ${answer.code}${said(answer)}")
    }
  }

  private suspend fun cohortModule(): CheckResult {
    val answer = probe.send("GET", endpoints.rest + "cohortm/cohort?limit=1")
    return when (answer.code) {
      200 -> result(Checks.COHORT_MODULE, CheckState.INFO, "installed")
      404 -> result(Checks.COHORT_MODULE, CheckState.INFO, "not installed")
      else -> result(Checks.COHORT_MODULE, CheckState.UNKNOWN, "Answered ${answer.code}")
    }
  }

  private suspend fun concept(key: String, id: String): CheckResult {
    if (id.isBlank()) return result(key, CheckState.NOT_USED)
    val answer = probe.send("GET", endpoints.rest + "concept/$id?v=custom:(uuid,display)")
    return when {
      answer.code == 200 ->
        result(
          key,
          CheckState.OK,
          runCatching { JSONObject(answer.text.orEmpty()).optString("display") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() },
          id,
        )
      answer.code == 404 -> result(key, CheckState.UNSUPPORTED, "No such concept", id)
      answer.code == 401 || answer.code == 403 ->
        result(key, CheckState.UNKNOWN, "No permission to look (${answer.code})", id)
      else -> result(key, CheckState.UNKNOWN, answer.text ?: "Answered ${answer.code}", id)
    }
  }

  /**
   * Creates a patient list with an id of its own, then deletes it: the only way to know that the
   * server accepts what the app sends. A server that has the Cohort Module and not the patient list
   * module refuses it (422).
   */
  suspend fun writeTest(): CheckResult {
    val id = UUID.randomUUID().toString()
    val group =
      Group().apply {
        setId(id)
        type = Group.GroupType.PERSON
        actual = true
        name = "Compatibility test " + id.take(8)
      }
    val created = probe.send("PUT", endpoints.fhir + "Group/$id", group)
    if (created.code in 200..299) {
      val removed = probe.send("DELETE", endpoints.fhir + "Group/$id")
      val cleaned = removed.code in 200..299 || removed.code == 404 || removed.code == 410
      return result(
        Checks.GROUPS_WRITE,
        CheckState.OK,
        if (cleaned) {
          "Created and deleted a temporary patient list"
        } else {
          "Created a temporary patient list ($id); deleting it answered ${removed.code}"
        },
      )
    }
    val unsupported = refusalMeansUnsupported(created.code, created.text)
    return result(
      Checks.GROUPS_WRITE,
      if (unsupported) CheckState.UNSUPPORTED else CheckState.UNKNOWN,
      "Answered ${created.code}${said(created)}",
    )
  }

  private fun said(answer: ProbeAnswer): String {
    val outcome = parse(answer.text) as? OperationOutcome
    val text =
      outcome?.issueFirstRep?.let { it.diagnostics ?: it.details?.text }
        ?: answer.text?.takeIf { outcome == null && !it.trimStart().startsWith("<") }
    return text?.take(DETAIL_LENGTH)?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
  }

  private fun parse(text: String?): Resource? =
    if (text.isNullOrBlank()) {
      null
    } else {
      try {
        FhirContext.forR4Cached().newJsonParser().parseResource(text) as? Resource
      } catch (e: Exception) {
        null
      }
    }

  companion object {
    private const val DETAIL_LENGTH = 300

    private fun doesNotKnowOperation(text: String?): Boolean =
      text.orEmpty().contains("know how to handle", ignoreCase = true) ||
        text.orEmpty().contains("unknown operation", ignoreCase = true) ||
        text.orEmpty().contains("not supported", ignoreCase = true)

    /**
     * Whether a refusal of a change to a patient list says the server cannot do this at all (and so
     * will refuse it again) rather than that this change was wrong (a name taken, no permission): a
     * method not allowed or implemented, or a 400 or 422 that names the Cohort Module or a missing
     * feature.
     */
    fun refusalMeansUnsupported(code: Int, text: String?): Boolean =
      when (code) {
        405,
        501, -> true
        400,
        422, ->
          text.orEmpty().let {
            it.contains("cohort", ignoreCase = true) ||
              it.contains("not supported", ignoreCase = true) ||
              it.contains("unsupported", ignoreCase = true) ||
              doesNotKnowOperation(it)
          }
        else -> false
      }
  }
}
