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
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Questionnaire
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.FormData
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.data.database.model.FormSection
import org.openmrs.android.fhir.data.database.model.FormSectionItem
import org.openmrs.android.fhir.extensions.getJsonFileNames
import org.openmrs.android.fhir.extensions.getQuestionnaireOrFromAssets
import org.openmrs.android.fhir.extensions.readFileFromAssets

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

  /*
   * Load encounter form data from the config.
   * Fetch all questionnaire & add to the formItem
   */
  fun loadFormData(formDataString: String, encounterTypeSystemUrl: String) {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(FormData::class.java).lenient()
        val formData = adapter.fromJson(formDataString.trim())
        val translationOverrides = formData?.translationOverrides ?: emptyMap()
        val deviceLanguageOverrides = translationOverrides[Locale.getDefault().language].orEmpty()
        val deviceLanguage = Locale.getDefault().language.lowercase(Locale.ROOT)
        val questionnaires = fhirEngine.search<Questionnaire> {}.map { it.resource }.toMutableList()
        // This code can be refactored once there's possiblity to add assets file to fhirEngine.
        val assetQuestionnaireFileNames = applicationContext.getJsonFileNames()
        assetQuestionnaireFileNames.forEach {
          val questionnaireString = applicationContext.readFileFromAssets(it)
          if (questionnaireString.isNotEmpty()) {
            questionnaires.add(parser.parseResource(Questionnaire::class.java, questionnaireString))
          }
        }

        val encounterTypeCodeToQuestionnaireIdMap = mutableMapOf<String, String>()
        questionnaires.forEach { questionnaire ->
          if (questionnaire.hasCode()) {
            questionnaire.code.forEach {
              if (it.hasSystem() and (it?.system.toString() == encounterTypeSystemUrl)) {
                encounterTypeCodeToQuestionnaireIdMap.putIfAbsent(it.code, questionnaire.logicalId)
              }
            }
          }
        }

        val formSectionItems =
          formData
            ?.formSections
            ?.map {
              it.toFormSectionItem(
                encounterTypeCodeToQuestionnaireIdMap,
                deviceLanguageOverrides,
                deviceLanguage,
              )
            }
            ?.filter { it.forms.isNotEmpty() }
            ?: emptyList()
        _formData.value = formSectionItems
        _isLoading.value = false
      } catch (e: Exception) {
        _error.value = "Failed to load form data: ${e.localizedMessage}"
        _isLoading.value = false
      }
    }
  }

  /*
   * Only add forms to the Form Section whose questionaire
   * are present in local database or in the assets folder.
   */
  internal suspend fun FormSection.toFormSectionItem(
    encounterTypeCodeToQuestionnaireIdMap: Map<String, String>,
    translationOverrides: Map<String, String>,
    deviceLanguage: String,
  ): FormSectionItem {
    val formItems = mutableListOf<FormItem>()
    forms.forEach { encounterTypeCode ->
      val questionnaireId =
        encounterTypeCodeToQuestionnaireIdMap[encounterTypeCode] ?: return@forEach
      val questionnaire: Questionnaire? =
        fhirEngine.getQuestionnaireOrFromAssets(questionnaireId, applicationContext, parser)
      if (questionnaire != null) {
        val localizedQuestionnaireTitle =
          questionnaire.getTranslatedTitle(deviceLanguage)
            ?: questionnaire.title?.let { translationOverrides[it] ?: it }
              ?: applicationContext.getString(R.string.no_name_provided)
        formItems.add(
          FormItem(
            name = localizedQuestionnaireTitle,
            questionnaireId = questionnaireId,
          ),
        )
      }
    }
    val sectionName = translationOverrides[name] ?: name
    return FormSectionItem(
      name = sectionName,
      forms = formItems,
    )
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
