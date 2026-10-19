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
package org.openmrs.android.fhir.ui.tutorial

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import org.openmrs.android.fhir.R

/**
 * The tour of each screen, started by the help button of the app bar. A tour only explains: every
 * step is an [TutorialAction.Acknowledge] one, so nothing on the screen is used (or navigated away
 * from) while it runs.
 */
object ScreenTours {
  /**
   * [viewTargets] maps a target key to the id of an Android view of the screen (a form that is
   * still XML), whose place is read when the tour starts.
   */
  class Tour(val steps: List<TutorialStep>, val viewTargets: Map<String, Int> = emptyMap())

  private fun step(
    key: String,
    @StringRes title: Int,
    @StringRes body: Int,
    optional: Boolean = false,
  ) = TutorialStep(key, title, body, TutorialAction.Acknowledge, optional)

  private val home =
    Tour(
      listOf(
        step(
          TutorialTargets.HOME_STATUS,
          R.string.help_home_status_title,
          R.string.help_home_status_body,
          optional = true,
        ),
        step(
          TutorialTargets.HOME_REGISTER,
          R.string.help_home_register_title,
          R.string.help_home_register_body,
        ),
        step(
          TutorialTargets.HOME_PATIENTS,
          R.string.help_home_patients_title,
          R.string.help_home_patients_body,
        ),
        step(
          TutorialTargets.HOME_LISTS,
          R.string.help_home_lists_title,
          R.string.help_home_lists_body,
        ),
        step(
          TutorialTargets.HOME_GROUP,
          R.string.help_home_group_title,
          R.string.help_home_group_body,
        ),
        step(
          TutorialTargets.HOME_SYNC,
          R.string.help_home_sync_title,
          R.string.help_home_sync_body,
        ),
        step(
          TutorialTargets.HOME_IMPORT_EXPORT,
          R.string.help_home_importexport_title,
          R.string.help_home_importexport_body,
        ),
      ),
    )

  private val patients =
    Tour(
      listOf(
        step(
          TutorialTargets.PATIENT_SEARCH,
          R.string.tutorial_patients_search_title,
          R.string.tutorial_patients_search_body,
        ),
        step(
          TutorialTargets.PATIENT_LIST,
          R.string.help_patients_list_title,
          R.string.help_patients_list_body,
        ),
        step(
          TutorialTargets.PATIENT_IDENTIFY_LATER,
          R.string.help_patients_identify_title,
          R.string.help_patients_identify_body,
          optional = true,
        ),
        step(
          TutorialTargets.PATIENT_REGISTER,
          R.string.tutorial_patients_register_title,
          R.string.help_patients_register_body,
        ),
      ),
    )

  private val details =
    Tour(
      listOf(
        step(
          TutorialTargets.DETAILS_OVERVIEW,
          R.string.help_details_overview_title,
          R.string.help_details_overview_body,
          optional = true,
        ),
        step(
          TutorialTargets.DETAILS_ENCOUNTERS,
          R.string.help_details_encounters_title,
          R.string.help_details_encounters_body,
          optional = true,
        ),
        step(
          TutorialTargets.DETAILS_NEW_ENCOUNTER,
          R.string.help_details_new_title,
          R.string.help_details_new_body,
        ),
      ),
    )

  private val sync =
    Tour(
      listOf(
        step(
          TutorialTargets.SYNC_STATUS,
          R.string.help_sync_status_title,
          R.string.help_sync_status_body,
        ),
        step(
          TutorialTargets.SYNC_MODE,
          R.string.help_sync_mode_title,
          R.string.help_sync_mode_body,
        ),
        step(TutorialTargets.SYNC_NOW, R.string.help_sync_now_title, R.string.help_sync_now_body),
        step(
          TutorialTargets.SYNC_TABS,
          R.string.help_sync_tabs_title,
          R.string.help_sync_tabs_body,
        ),
      ),
    )

  private val importExport =
    Tour(
      listOf(
        step(
          TutorialTargets.IE_EXPORT,
          R.string.help_ie_export_title,
          R.string.help_ie_export_body,
        ),
        step(
          TutorialTargets.IE_IMPORT,
          R.string.help_ie_import_title,
          R.string.help_ie_import_body,
        ),
      ),
    )

  private val settings =
    Tour(
      listOf(
        step(
          TutorialTargets.SETTINGS_LANGUAGE,
          R.string.help_settings_language_title,
          R.string.help_settings_language_body,
          optional = true,
        ),
        step(
          TutorialTargets.SETTINGS_APPEARANCE,
          R.string.help_settings_appearance_title,
          R.string.help_settings_appearance_body,
          optional = true,
        ),
        step(
          TutorialTargets.SETTINGS_SYNC,
          R.string.help_settings_sync_title,
          R.string.help_settings_sync_body,
        ),
        step(
          TutorialTargets.SETTINGS_LIVE,
          R.string.help_settings_live_title,
          R.string.help_settings_live_body,
        ),
        step(
          TutorialTargets.SETTINGS_SHARED,
          R.string.help_settings_shared_title,
          R.string.help_settings_shared_body,
          optional = true,
        ),
        step(
          TutorialTargets.SETTINGS_SECURITY,
          R.string.help_settings_security_title,
          R.string.help_settings_security_body,
          optional = true,
        ),
      ),
    )

  private val patientLists =
    Tour(
      listOf(
        step(
          TutorialTargets.LISTS_SEARCH,
          R.string.help_lists_search_title,
          R.string.help_lists_search_body,
          optional = true,
        ),
        step(
          TutorialTargets.LISTS_LIST,
          R.string.help_lists_list_title,
          R.string.help_lists_list_body,
        ),
      ),
    )

  private val location =
    Tour(
      listOf(
        step(
          TutorialTargets.LOCATION_SEARCH,
          R.string.help_location_search_title,
          R.string.help_location_search_body,
          optional = true,
        ),
        step(
          TutorialTargets.LOCATION_LIST,
          R.string.help_location_list_title,
          R.string.help_location_list_body,
        ),
      ),
    )

  private val forms =
    Tour(
      listOf(
        step(
          TutorialTargets.FORMS_SEARCH,
          R.string.help_forms_search_title,
          R.string.help_forms_search_body,
          optional = true,
        ),
        step(
          TutorialTargets.FORMS_LIST,
          R.string.help_forms_list_title,
          R.string.help_forms_list_body,
        ),
      ),
    )

  private val registrationForm =
    Tour(
      listOf(
        step(
          TutorialTargets.FORM_CONTAINER,
          R.string.help_form_registration_title,
          R.string.help_form_registration_body,
        ),
      ),
      mapOf(TutorialTargets.FORM_CONTAINER to R.id.add_patient_container),
    )

  private val encounterForm =
    Tour(
      listOf(
        step(
          TutorialTargets.FORM_CONTAINER,
          R.string.help_form_encounter_title,
          R.string.help_form_encounter_body,
        ),
      ),
      mapOf(TutorialTargets.FORM_CONTAINER to R.id.form_entry_container),
    )

  private val groupForm =
    Tour(
      listOf(
        step(
          TutorialTargets.FORM_TABS,
          R.string.help_group_tabs_title,
          R.string.help_group_tabs_body,
          optional = true,
        ),
        step(
          TutorialTargets.FORM_CONTAINER,
          R.string.help_form_encounter_title,
          R.string.help_form_encounter_body,
        ),
      ),
      mapOf(
        TutorialTargets.FORM_CONTAINER to R.id.form_entry_container,
        TutorialTargets.FORM_TABS to R.id.patientTabLayout,
      ),
    )

  /** The tour of the screen with this navigation destination id; `null` when it has none. */
  fun forDestination(@IdRes destinationId: Int): Tour? =
    when (destinationId) {
      R.id.home_fragment -> home
      R.id.patient_list -> patients
      R.id.patient_details -> details
      R.id.syncInfoFragment -> sync
      R.id.importExportFragment -> importExport
      R.id.settings_fragment -> settings
      R.id.selectPatientListFragment -> patientLists
      R.id.locationFragment -> location
      R.id.createEncounterFragment -> forms
      R.id.addPatientFragment,
      R.id.editPatientFragment, -> registrationForm
      R.id.genericFormEntryFragment,
      R.id.editEncounterFragment, -> encounterForm
      R.id.groupFormEntryFragment -> groupForm
      else -> null
    }

  /** Every view target key of every tour, to forget them when the screen is left. */
  val viewTargetKeys: Set<String> = setOf(TutorialTargets.FORM_CONTAINER, TutorialTargets.FORM_TABS)
}
