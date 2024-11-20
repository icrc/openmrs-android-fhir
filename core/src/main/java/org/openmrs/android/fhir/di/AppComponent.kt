package org.openmrs.android.fhir.di

import android.content.Context
import dagger.BindsInstance
import dagger.Component
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
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AppModule::class,
        AssistedViewModelModule::class,
        ViewModelModule::class,
        ViewModelBuilderModule::class,
    ]
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
    fun inject(activity: LoginActivity)
    fun inject(activity: MainActivity)
}