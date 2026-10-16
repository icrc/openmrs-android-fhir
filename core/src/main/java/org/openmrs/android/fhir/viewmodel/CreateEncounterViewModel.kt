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
package org.openmrs.android.fhir.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.extensions.logicalId
import com.google.android.fhir.search.search
import com.squareup.moshi.Moshi
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.Questionnaire
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.FormData
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.data.database.model.FormSectionItem
import org.openmrs.android.fhir.extensions.readAssetQuestionnaires
import timber.log.Timber

class CreateEncounterViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
) : ViewModel() {

  private val _formData = MutableLiveData<List<FormSectionItem>?>()
  val formData: LiveData<List<FormSectionItem>?> = _formData

  private val _isLoading = MutableLiveData<Boolean>()
  val isLoading: LiveData<Boolean> = _isLoading

  private val _error = MutableLiveData<String>()
  val error: LiveData<String> = _error

  private val parser = FhirContext.forR4Cached().newJsonParser()

  /**
   * Builds the sections of the **Create Encounter** screen.
   *
   * [formDataString] is the `questionnaires` setting: sections of encounter-type codes plus
   * optional translations. Each code is resolved to the questionnaire (synced or bundled in the
   * app) that declares it, and codes without a questionnaire are skipped. When no sections are
   * configured, every available encounter form is offered in one list, so the setting can be left
   * out entirely.
   */
  fun loadFormData(formDataString: String, encounterTypeSystemUrl: String) {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        val sections =
          withContext(Dispatchers.Default) { buildSections(formDataString, encounterTypeSystemUrl) }
        _formData.value = sections
      } catch (e: Exception) {
        Timber.e(e, "Could not load the encounter forms")
        _error.value = "Failed to load form data: ${e.localizedMessage}"
      } finally {
        _isLoading.value = false
      }
    }
  }

  private suspend fun buildSections(
    formDataString: String,
    encounterTypeSystemUrl: String,
  ): List<FormSectionItem> {
    val formData = parseFormData(formDataString)
    val deviceLanguage = Locale.getDefault().language.lowercase(Locale.ROOT)
    val translationOverrides = formData?.translationOverrides?.get(deviceLanguage).orEmpty()

    val questionnaires = fhirEngine.search<Questionnaire> {}.map { it.resource }.toMutableList()
    // Forms shipped with the app are listed too; the ones the engine already has are skipped.
    questionnaires.addAll(
      applicationContext.readAssetQuestionnaires(
        parser,
        questionnaires.map { it.idElement.idPart }.toSet(),
      ),
    )

    // Encounter type code -> the questionnaire that declares it (the first one wins).
    val questionnaireByCode = linkedMapOf<String, Questionnaire>()
    questionnaires.forEach { questionnaire ->
      questionnaire.code
        .filter { it.hasSystem() && it.system == encounterTypeSystemUrl && it.hasCode() }
        .forEach { questionnaireByCode.putIfAbsent(it.code, questionnaire) }
    }

    fun Questionnaire.toFormItem() =
      FormItem(
        name = getTranslatedTitle(deviceLanguage)
            ?: title?.let { translationOverrides[it] ?: it }
              ?: applicationContext.getString(R.string.no_name_provided),
        questionnaireId = logicalId,
      )

    val configured =
      formData
        ?.formSections
        .orEmpty()
        .map { section ->
          FormSectionItem(
            name = translationOverrides[section.name] ?: section.name,
            forms = section.forms.mapNotNull { code -> questionnaireByCode[code]?.toFormItem() },
          )
        }
        .filter { it.forms.isNotEmpty() }
    if (configured.isNotEmpty()) return configured

    // Nothing configured, or none of the configured codes has a questionnaire: offer every form.
    if (!formData?.formSections.isNullOrEmpty()) {
      Timber.w("No questionnaire matches the configured encounter types; listing every form")
    }
    val all =
      questionnaireByCode.values
        .distinctBy { it.logicalId }
        .map { it.toFormItem() }
        .sortedBy { it.name.lowercase(Locale.ROOT) }
    return if (all.isEmpty()) {
      emptyList()
    } else {
      listOf(FormSectionItem(applicationContext.getString(R.string.all_forms), all))
    }
  }

  /** The `questionnaires` setting; a blank or unreadable value means "no sections configured". */
  private fun parseFormData(formDataString: String): FormData? {
    if (formDataString.isBlank()) return null
    return try {
      Moshi.Builder()
        .build()
        .adapter(FormData::class.java)
        .lenient()
        .fromJson(formDataString.trim())
    } catch (e: Exception) {
      Timber.w(e, "The questionnaires setting is not valid JSON; listing every encounter form")
      null
    }
  }

  private fun Questionnaire.getTranslatedTitle(deviceLanguage: String): String? {
    return this.titleElement
      ?.extension
      ?.asSequence()
      ?.filter { it.url == TRANSLATION_EXTENSION_URL }
      ?.mapNotNull { translationExtension ->
        val lang =
          translationExtension.extension
            .firstOrNull { it.url == LANGUAGE_EXTENSION_URL }
            ?.value
            ?.primitiveValue()
            ?.lowercase(Locale.ROOT)
        val content =
          translationExtension.extension
            .firstOrNull { it.url == CONTENT_EXTENSION_URL }
            ?.value
            ?.primitiveValue()
        if (lang != null && lang == deviceLanguage) content else null
      }
      ?.firstOrNull()
  }

  companion object {
    private const val TRANSLATION_EXTENSION_URL =
      "http://hl7.org/fhir/StructureDefinition/translation"
    private const val LANGUAGE_EXTENSION_URL = "lang"
    private const val CONTENT_EXTENSION_URL = "content"
  }
}
