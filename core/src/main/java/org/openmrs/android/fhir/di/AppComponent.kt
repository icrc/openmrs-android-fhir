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

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton
import org.openmrs.android.fhir.BasicLoginActivity
import org.openmrs.android.fhir.LoginActivity
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.fragments.AddPatientFragment
import org.openmrs.android.fhir.fragments.EditEncounterFragment
import org.openmrs.android.fhir.fragments.EditPatientFragment
import org.openmrs.android.fhir.fragments.GenericFormEntryFragment
import org.openmrs.android.fhir.fragments.IdentifierFragment
import org.openmrs.android.fhir.fragments.LocationFragment
import org.openmrs.android.fhir.fragments.PatientDetailsFragment
import org.openmrs.android.fhir.fragments.PatientListFragment
import org.openmrs.android.fhir.fragments.SettingsFragment
import org.openmrs.android.fhir.fragments.SyncInfoFragment

@Singleton
@Component(
  modules =
    [
      AppModule::class,
      AssistedViewModelModule::class,
      ViewModelModule::class,
      ViewModelBuilderModule::class,
    ],
)
interface AppComponent {

  @Component.Factory
  interface Factory {
    fun create(@BindsInstance applicationContext: Context): AppComponent
  }

  /*
   * Fragments & Activities
   */
  fun inject(fragment: AddPatientFragment)

  fun inject(fragment: LocationFragment)

  fun inject(fragment: EditEncounterFragment)

  fun inject(fragment: EditPatientFragment)

  fun inject(fragment: GenericFormEntryFragment)

  fun inject(fragment: IdentifierFragment)

  fun inject(fragment: PatientListFragment)

  fun inject(fragment: PatientDetailsFragment)

  fun inject(fragment: SyncInfoFragment)

  fun inject(activity: LoginActivity)

  fun inject(activity: MainActivity)

  fun inject(fragment: SettingsFragment)

  fun inject(activity: BasicLoginActivity)
}
