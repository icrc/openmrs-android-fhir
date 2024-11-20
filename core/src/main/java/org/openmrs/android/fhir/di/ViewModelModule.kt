package org.openmrs.android.fhir.di;

import androidx.lifecycle.ViewModel;

import org.openmrs.android.fhir.viewmodel.AddPatientViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;
import org.openmrs.android.fhir.viewmodel.LocationViewModel
import org.openmrs.android.fhir.viewmodel.LoginActivityViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

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
    @ViewModelKey(LoginActivityViewModel::class)
    abstract fun bindLoginActivityViewModel(viewmodel: LoginActivityViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(MainActivityViewModel::class)
    abstract fun bindMainActivityViewModel(viewmodel: MainActivityViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(PatientListViewModel::class)
    abstract fun bindPatientListViewModel(viewmodel: PatientListViewModel): ViewModel


}
