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
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.extensions.logicalId
import com.google.android.fhir.search.search
import com.squareup.moshi.Moshi
import java.util.Locale
import javax.inject.Inject
import org.hl7.fhir.r4.model.Questionnaire
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.FormData
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.extensions.readAssetQuestionnaires
import timber.log.Timber

/**
 * The encounter forms available on this device: the questionnaires the server synced plus the ones
 * bundled with the app, each reachable by the encounter type code it declares. Shared by the Create
 * Encounter screen and the screen where the favorites are picked.
 */
class FormCatalog
@Inject
constructor(
  private val applicationContext: Context,
  private val fhirEngine: FhirEngine,
) {
  private val parser = FhirContext.forR4Cached().newJsonParser()

  /**
   * @property formData the parsed `questionnaires` setting, null when none is configured
   * @property translationOverrides the configured name translations for the current language
   * @property formsByCode encounter type code -> the form that declares it (the first one wins)
   */
  class Catalog(
    val formData: FormData?,
    val translationOverrides: Map<String, String>,
    val formsByCode: Map<String, FormItem>,
  ) {
    /** Every form once, alphabetically. */
    val allForms: List<FormItem> by lazy {
      formsByCode.values
        .distinctBy { it.questionnaireId }
        .sortedBy { it.name.lowercase(Locale.ROOT) }
    }
  }

  /**
   * [formDataString] is the `questionnaires` setting (sections of encounter-type codes plus
   * optional translations); a blank or unreadable one means "no sections configured".
   */
  suspend fun load(formDataString: String, encounterTypeSystemUrl: String): Catalog {
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

    fun Questionnaire.toFormItem() =
      FormItem(
        name = getTranslatedTitle(deviceLanguage)
            ?: title?.let { translationOverrides[it] ?: it }
              ?: applicationContext.getString(R.string.no_name_provided),
        questionnaireId = logicalId,
      )

    val formsByCode = linkedMapOf<String, FormItem>()
    questionnaires.forEach { questionnaire ->
      questionnaire.code
        .filter { it.hasSystem() && it.system == encounterTypeSystemUrl && it.hasCode() }
        .forEach { formsByCode.putIfAbsent(it.code, questionnaire.toFormItem()) }
    }
    return Catalog(formData, translationOverrides, formsByCode)
  }

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

  private companion object {
    const val TRANSLATION_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/translation"
    const val LANGUAGE_EXTENSION_URL = "lang"
    const val CONTENT_EXTENSION_URL = "content"
  }
}
