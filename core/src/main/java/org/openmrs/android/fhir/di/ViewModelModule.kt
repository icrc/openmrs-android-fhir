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
package org.openmrs.android.fhir.di

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import org.openmrs.android.fhir.viewmodel.AddPatientViewModel
import org.openmrs.android.fhir.viewmodel.BasicLoginActivityViewModel
import org.openmrs.android.fhir.viewmodel.CreateEncounterViewModel
import org.openmrs.android.fhir.viewmodel.IdentifierSelectionViewModel
import org.openmrs.android.fhir.viewmodel.LocationViewModel
import org.openmrs.android.fhir.viewmodel.LoginActivityViewModel
import org.openmrs.android.fhir.viewmodel.PatientListViewModel
import org.openmrs.android.fhir.viewmodel.SelectPatientListViewModel
import org.openmrs.android.fhir.viewmodel.SettingsViewModel
import org.openmrs.android.fhir.viewmodel.SyncInfoViewModel
import org.openmrs.android.fhir.viewmodel.UnsyncedResourcesViewModel

@Module
abstract class ViewModelModule {

  /*
   * Viewmodels
   */

  @Binds
  @IntoMap
  @ViewModelKey(AddPatientViewModel::class)
  abstract fun bindAddPatientViewModel(viewmodel: AddPatientViewModel): ViewModel

  @Binds
  @IntoMap
  @ViewModelKey(LocationViewModel::class)
  abstract fun bindLocationViewModel(viewmodel: LocationViewModel): ViewModel

  @Binds
  @IntoMap
  @ViewModelKey(IdentifierSelectionViewModel::class)
  abstract fun bindIdentifierSelectionViewModel(viewmodel: IdentifierSelectionViewModel): ViewModel

  @Binds
  @IntoMap
  @ViewModelKey(SelectPatientListViewModel::class)
  abstract fun bindSelectPatientListViewModel(viewmodel: SelectPatientListViewModel): ViewModel

  @Binds
  @IntoMap
  @ViewModelKey(LoginActivityViewModel::class)
  abstract fun bindLoginActivityViewModel(viewmodel: LoginActivityViewModel): ViewModel

  @Binds
  @IntoMap
  @ViewModelKey(PatientListViewModel::class)
  abstract fun bindPatientListViewModel(viewmodel: PatientListViewModel): ViewModel

  @Binds
  @IntoMap
  @ViewModelKey(SyncInfoViewModel::class)
  abstract fun bindSyncInfoViewModel(viewmodel: SyncInfoViewModel): ViewModel

  @Binds
  @IntoMap
  @ViewModelKey(UnsyncedResourcesViewModel::class)
  abstract fun bindUnsyncedResourcesViewModel(viewmodel: UnsyncedResourcesViewModel): ViewModel

  @Binds
  @IntoMap
  @ViewModelKey(CreateEncounterViewModel::class)
  abstract fun bindCreateEncounterViewModel(viewmodel: CreateEncounterViewModel): ViewModel

  @Binds
  @IntoMap
  @ViewModelKey(BasicLoginActivityViewModel::class)
  abstract fun bindBasicLoginActivityViewModel(viewmodel: BasicLoginActivityViewModel): ViewModel

  @Binds
  @IntoMap
  @ViewModelKey(SettingsViewModel::class)
  abstract fun bindSettingsViewModel(viewmodel: SettingsViewModel): ViewModel
}
