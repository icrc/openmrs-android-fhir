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
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.FormCatalog
import org.openmrs.android.fhir.data.GroupSessionDraftRepository
import org.openmrs.android.fhir.data.database.model.CustomFormSection
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.data.database.model.FormSectionItem
import org.openmrs.android.fhir.data.database.model.GroupSessionDraftSummary
import timber.log.Timber

class CreateEncounterViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val formCatalog: FormCatalog,
  private val groupSessionDraftRepository: GroupSessionDraftRepository,
) : ViewModel() {

  private val demoDataStore = DemoDataStore(applicationContext)

  private val _formData = MutableLiveData<List<FormSectionItem>?>()
  val formData: LiveData<List<FormSectionItem>?> = _formData

  private val _pendingSessions = MutableLiveData<Map<String, GroupSessionDraftSummary>>(emptyMap())

  /** The group sessions that can be resumed, by form id. */
  val pendingSessions: LiveData<Map<String, GroupSessionDraftSummary>> = _pendingSessions

  private val _isLoading = MutableLiveData<Boolean>()
  val isLoading: LiveData<Boolean> = _isLoading

  private val _error = MutableLiveData<String>()
  val error: LiveData<String> = _error

  private var sections: List<FormSectionItem>? = null
  private var formsById: Map<String, FormItem> = emptyMap()
  private var favoriteIds: List<String> = emptyList()
  private var customSections: List<CustomFormSection>? = null

  init {
    // Favorites and the user's own sections follow the settings: a change shows up without
    // reloading the forms.
    viewModelScope.launch {
      combine(demoDataStore.getFavoriteFormIdsFlow(), demoDataStore.getCustomFormSectionsFlow()) {
          favorites,
          custom,
          ->
          favorites to custom
        }
        .collect { (favorites, custom) ->
          favoriteIds = favorites
          customSections = custom
          publish()
        }
    }
  }

  /**
   * Builds the sections of the **Create Encounter** screen.
   *
   * The sections are the ones declared in the app configuration. Each code is resolved to the
   * questionnaire (synced or bundled in the app) that declares it, and codes without a
   * questionnaire are skipped. When no sections are configured, every available encounter form is
   * offered in one list, so the setting can be left out entirely. The favorite forms (picked in
   * Settings) come first, in a section of their own.
   */
  fun loadFormData() {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        val catalog = withContext(Dispatchers.Default) { formCatalog.load() }
        formsById = catalog.allForms.associateBy { it.questionnaireId }
        sections = buildSections(catalog)
        publish()
      } catch (e: Exception) {
        Timber.e(e, "Could not load the encounter forms")
        _error.value = "Failed to load form data: ${e.localizedMessage}"
      } finally {
        _isLoading.value = false
      }
    }
  }

  /** Looks again at the drafts; called whenever the screen comes back in front. */
  fun loadPendingSessions() {
    viewModelScope.launch {
      try {
        _pendingSessions.value =
          withContext(Dispatchers.IO) { groupSessionDraftRepository.pendingSessions() }
      } catch (e: Exception) {
        Timber.e(e, "Could not read the pending group sessions")
      }
    }
  }

  private fun publish() {
    val own =
      customSections
        ?.map { FormSectionItem(it.name, it.formIds.mapNotNull { id -> formsById[id] }) }
        ?.filter { it.forms.isNotEmpty() }
        ?.takeIf { it.isNotEmpty() }
    val base = own ?: sections ?: return
    val favorites = favoriteIds.mapNotNull { formsById[it] }
    _formData.value =
      if (favorites.isEmpty()) {
        base
      } else {
        listOf(
          FormSectionItem(
            name = applicationContext.getString(R.string.favorite_forms),
            forms = favorites,
            isFavorites = true,
          ),
        ) + base
      }
  }

  private fun buildSections(catalog: FormCatalog.Catalog): List<FormSectionItem> {
    val configured =
      catalog.sections
        .map { section ->
          FormSectionItem(
            name = section.nameFor(catalog.language),
            forms = section.forms.mapNotNull { code -> catalog.formsByCode[code] },
          )
        }
        .filter { it.forms.isNotEmpty() }
    if (configured.isNotEmpty()) return configured

    // Nothing configured, or none of the configured codes has a questionnaire: offer every form.
    if (catalog.sections.isNotEmpty()) {
      Timber.w("No questionnaire matches the configured encounter types; listing every form")
    }
    val all = catalog.allForms
    return if (all.isEmpty()) {
      emptyList()
    } else {
      listOf(FormSectionItem(applicationContext.getString(R.string.all_forms), all))
    }
  }
}
