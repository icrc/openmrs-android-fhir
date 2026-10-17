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
import java.util.Locale
import javax.inject.Inject
import org.hl7.fhir.r4.model.Questionnaire
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.config.FormSectionConfig
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.extensions.AppLanguage
import org.openmrs.android.fhir.extensions.allQuestionnaires
import org.openmrs.android.fhir.extensions.translatedTitle

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
   * @property sections the sections declared in the app configuration
   * @property language the language code names are shown in
   * @property formsByCode encounter type code -> the form that declares it (the first one wins)
   */
  class Catalog(
    val sections: List<FormSectionConfig>,
    val language: String,
    val formsByCode: Map<String, FormItem>,
  ) {
    /** Every form once, alphabetically. */
    val allForms: List<FormItem> by lazy {
      formsByCode.values
        .distinctBy { it.questionnaireId }
        .sortedBy { it.name.lowercase(Locale.ROOT) }
    }
  }

  suspend fun load(): Catalog {
    val language = AppLanguage.currentTag(applicationContext).lowercase(Locale.ROOT)

    // The engine's forms, and the ones shipped with the app that the engine does not have.
    val questionnaires = fhirEngine.allQuestionnaires(applicationContext, parser)

    fun Questionnaire.toFormItem() =
      FormItem(
        name = translatedTitle(language)
            ?: title ?: applicationContext.getString(R.string.no_name_provided),
        questionnaireId = logicalId,
      )

    val formsByCode = linkedMapOf<String, FormItem>()
    questionnaires.forEach { questionnaire ->
      questionnaire.code
        .filter { it.hasSystem() && it.system == ENCOUNTER_TYPE_SYSTEM_URL && it.hasCode() }
        .forEach { formsByCode.putIfAbsent(it.code, questionnaire.toFormItem()) }
    }
    return Catalog(AppConfig.get(applicationContext).formSections, language, formsByCode)
  }

  companion object {
    /** The code system OpenMRS uses for encounter types; forms are matched by their code. */
    const val ENCOUNTER_TYPE_SYSTEM_URL = "http://fhir.openmrs.org/code-system/encounter-type"
  }
}
