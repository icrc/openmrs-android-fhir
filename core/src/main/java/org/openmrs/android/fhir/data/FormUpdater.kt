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

import android.content.Context
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.sync.AcceptRemoteConflictResolver
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.forms.AcceptedFormsStore
import org.openmrs.android.fhir.forms.FormState
import org.openmrs.android.fhir.forms.FormStatus
import org.openmrs.android.fhir.forms.FormVersions
import org.openmrs.android.fhir.forms.bundledFormSummaries
import org.openmrs.android.fhir.forms.bundledQuestionnaireIds
import org.openmrs.android.fhir.forms.fingerprint
import org.openmrs.android.fhir.forms.formsInUse
import org.openmrs.android.fhir.forms.hasCompactAliases
import org.openmrs.android.fhir.forms.readBundledQuestionnaire
import org.openmrs.android.fhir.forms.toFormSummary
import timber.log.Timber

/**
 * Compares the forms of the device with what the server publishes, and takes the ones the user
 * chooses. Asking the server is the user's act (nothing is checked or downloaded by itself), and it
 * is read only: what is taken is stored like a download, so nothing is queued for upload.
 *
 * The form in use is the copy that ships with the app, unless the user took the server's: then it
 * is the server's copy until they go back. With the setting that uses the server's forms every form
 * the engine holds is in use.
 */
@Singleton
class FormUpdater
@Inject
constructor(
  context: Context,
  private val fhirEngine: FhirEngine,
  private val apiManager: ApiManager,
) {
  private val appContext = context.applicationContext
  private val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
  private val lock = Mutex()

  /**
   * @property checkedAt when the server last answered, 0 when it never did since the app started
   * @property working the forms being stored or put back now
   * @property error why the last check failed, if it did
   */
  data class State(
    val forms: List<FormStatus> = emptyList(),
    val checking: Boolean = false,
    val working: Set<String> = emptySet(),
    val checkedAt: Long = 0L,
    val error: String? = null,
  )

  sealed interface Outcome {
    data class Checked(val updates: Int, val new: Int) : Outcome

    data class Taken(val count: Int) : Outcome

    data class BackToApp(val title: String) : Outcome

    data class Failed(val reason: String?) : Outcome
  }

  private class ServerForm(
    val questionnaire: Questionnaire,
    val fingerprint: String,
    val title: String,
    val version: String?,
    val usable: Boolean,
  )

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  private var server: Map<String, ServerForm> = emptyMap()
  private var held: Map<String, String> = emptyMap()
  private var heldTitles: Map<String, String> = emptyMap()
  private var checked = false

  /** The forms of the device and where each one comes from, without asking the server. */
  suspend fun load() {
    lock.withLock {
      if (checked) return
      refreshHeld(withFingerprints = false)
      rebuild()
    }
  }

  /** Asks the server for its forms and compares each with the one in use. */
  suspend fun check(): Outcome =
    lock.withLock {
      _state.update { it.copy(checking = true, error = null) }
      try {
        val remote = apiManager.fetchQuestionnaires()
        withContext(Dispatchers.Default) {
          server =
            remote
              .filter { it.hasIdElement() }
              .associate { questionnaire ->
                val usable = !questionnaire.hasCompactAliases()
                questionnaire.idElement.idPart to
                  ServerForm(
                    questionnaire = questionnaire,
                    fingerprint = questionnaire.fingerprint(parser),
                    title = titleOf(questionnaire),
                    version = questionnaire.version?.takeIf { it.isNotBlank() },
                    usable = usable,
                  )
              }
          appContext.bundledQuestionnaireIds().forEach { appFingerprint(it) }
          refreshHeld(withFingerprints = true)
        }
        checked = true
        _state.update { it.copy(checkedAt = System.currentTimeMillis()) }
        rebuild()
        val forms = _state.value.forms
        Outcome.Checked(
          updates = forms.count { it.state == FormState.UPDATE_AVAILABLE },
          new = forms.count { it.state == FormState.NEW_ON_SERVER },
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Checking the forms of the server failed")
        AuditLog.failure(AuditAction.FORMS_FETCHED, "error" to e.javaClass.simpleName)
        val reason = e.localizedMessage ?: e.javaClass.simpleName
        _state.update { it.copy(error = reason) }
        Outcome.Failed(reason)
      } finally {
        _state.update { it.copy(checking = false) }
      }
    }

  /** Takes the server's copy of these forms: from now on they are the ones in use. */
  suspend fun take(ids: Collection<String>): Outcome =
    lock.withLock {
      val wanted = ids.filter { id -> server[id]?.usable == true }
      if (wanted.isEmpty()) return@withLock Outcome.Taken(0)
      _state.update { it.copy(working = it.working + wanted) }
      try {
        val before = _state.value.forms.associateBy { it.id }
        val copies = wanted.mapNotNull { server[it]?.questionnaire }
        // Stored like a download, so no local change is queued for upload: the server owns them.
        withContext(Dispatchers.IO) {
          fhirEngine.syncDownload(AcceptRemoteConflictResolver) { flowOf(copies) }
        }
        AcceptedFormsStore.add(appContext, wanted)
        held = held + wanted.associateWith { server.getValue(it).fingerprint }
        heldTitles = heldTitles + wanted.associateWith { server.getValue(it).title }
        AuditLog.record(
          AuditAction.FORMS_FETCHED,
          AuditOutcome.SUCCESS,
          "added" to wanted.count { before[it]?.state == FormState.NEW_ON_SERVER },
          "updated" to wanted.count { before[it]?.state != FormState.NEW_ON_SERVER },
          "available" to server.size,
        )
        rebuild()
        Outcome.Taken(wanted.size)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Storing the forms of the server failed")
        AuditLog.failure(AuditAction.FORMS_FETCHED, "error" to e.javaClass.simpleName)
        Outcome.Failed(e.localizedMessage ?: e.javaClass.simpleName)
      } finally {
        _state.update { it.copy(working = it.working - wanted.toSet()) }
      }
    }

  /** Every form the server has that is new or different. */
  suspend fun takeAll(): Outcome =
    take(
      _state.value.forms
        .filter { it.state == FormState.UPDATE_AVAILABLE || it.state == FormState.NEW_ON_SERVER }
        .map { it.id },
    )

  /** Goes back to the copy that ships with the app. */
  suspend fun useAppVersion(id: String): Outcome =
    lock.withLock {
      val title = _state.value.forms.firstOrNull { it.id == id }?.title ?: id
      _state.update { it.copy(working = it.working + id) }
      try {
        AcceptedFormsStore.remove(appContext, id)
        withContext(Dispatchers.IO) {
          fhirEngine.purge(ResourceType.Questionnaire, setOf(id), true)
        }
        held = held - id
        heldTitles = heldTitles - id
        AuditLog.record(
          AuditAction.SETTINGS_CHANGED,
          AuditOutcome.SUCCESS,
          "changed" to listOf("form_app_version=$id"),
        )
        rebuild()
        Outcome.BackToApp(title)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Going back to the app's form failed")
        Outcome.Failed(e.localizedMessage ?: e.javaClass.simpleName)
      } finally {
        _state.update { it.copy(working = it.working - id) }
      }
    }

  /** What the engine holds that is in use; with [withFingerprints] what each copy says as well. */
  private suspend fun refreshHeld(withFingerprints: Boolean) {
    val copies = fhirEngine.formsInUse(appContext).filterNot { it.hasCompactAliases() }
    held =
      copies.associate {
        it.idElement.idPart to if (withFingerprints) it.fingerprint(parser) else ""
      }
    heldTitles = copies.associate { it.idElement.idPart to titleOf(it) }
  }

  private fun appFingerprint(id: String): String? =
    appFingerprints[id]
      ?: appContext
        .readBundledQuestionnaire(id)
        ?.takeIf { it.isNotEmpty() }
        ?.let { parser.parseResource(Questionnaire::class.java, it) as Questionnaire }
        ?.fingerprint(parser)
        ?.also { appFingerprints[id] = it }

  private fun titleOf(questionnaire: Questionnaire): String =
    questionnaire.toFormSummary().titleIn(Locale.getDefault().language)
      ?: questionnaire.idElement.idPart

  private fun rebuild() {
    val bundled = appContext.bundledFormSummaries().associateBy { it.id }
    val language = Locale.getDefault().language
    val ids = appContext.bundledQuestionnaireIds() + held.keys + server.keys
    val forms =
      ids
        .map { id ->
          FormVersions(
              id = id,
              title = heldTitles[id] ?: bundled[id]?.titleIn(language) ?: server[id]?.title ?: id,
              // Before the server was asked what a copy says does not matter: only that there is
              // one.
              app = if (id in bundled) appFingerprints[id] ?: "" else null,
              held = held[id],
              server = server[id]?.fingerprint,
              serverVersion = server[id]?.version,
              serverUsable = server[id]?.usable ?: true,
              checked = checked,
            )
            .status()
        }
        .sortedWith(compareBy({ order(it.state) }, { it.title.lowercase(Locale.ROOT) }))
    _state.update { it.copy(forms = forms) }
  }

  private fun order(state: FormState): Int =
    when (state) {
      FormState.UPDATE_AVAILABLE -> 0
      FormState.NEW_ON_SERVER -> 1
      FormState.NOT_USABLE -> 2
      FormState.NOT_CHECKED,
      FormState.UP_TO_DATE, -> 3
      FormState.NOT_ON_SERVER -> 4
    }

  private companion object {
    /** The copies that ship with the app do not change while it runs. */
    val appFingerprints = ConcurrentHashMap<String, String>()
  }
}
