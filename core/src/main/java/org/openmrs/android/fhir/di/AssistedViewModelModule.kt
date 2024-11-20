package org.openmrs.android.fhir.di

import androidx.lifecycle.ViewModel
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import org.openmrs.android.fhir.viewmodel.EditEncounterViewModel
import org.openmrs.android.fhir.viewmodel.EditPatientViewModel
import org.openmrs.android.fhir.viewmodel.GenericFormEntryViewModel
import org.openmrs.android.fhir.viewmodel.PatientDetailsViewModel

@Module
class AssistedViewModelModule {

    @Provides
    @IntoMap
    @ViewModelKey(EditEncounterViewModel::class)
    fun editEncounterViewModelAssistedFactory(
        factory: EditEncounterViewModel.Factory
    ): ViewModelAssistedFactory<out ViewModel> = factory

    @Provides
    @IntoMap
    @ViewModelKey(EditPatientViewModel::class)
    fun editPatientViewModelAssistedFactory(
        factory: EditPatientViewModel.Factory
    ): ViewModelAssistedFactory<out ViewModel> = factory

    @Provides
    @IntoMap
    @ViewModelKey(GenericFormEntryViewModel::class)
    fun genericFormEntryViewModelAssistedFactory(
        factory: GenericFormEntryViewModel.Factory
    ): ViewModelAssistedFactory<out ViewModel> = factory


    @Provides
    @IntoMap
    @ViewModelKey(PatientDetailsViewModel::class)
    fun patientDetailsViewModelAssistedFactory(
        factory: PatientDetailsViewModel.Factory
    ): ViewModelAssistedFactory<out ViewModel> = factory
}