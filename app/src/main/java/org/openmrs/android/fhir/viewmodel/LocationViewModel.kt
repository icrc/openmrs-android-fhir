package org.openmrs.android.fhir.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.search
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Location

class LocationViewModel(application: Application, private val fhirEngine: FhirEngine) : AndroidViewModel(application) {

    init {
        getLocations()
    }

    val locations = MutableLiveData<List<LocationItem>>()

    private fun getLocations() {
        viewModelScope.launch {
            val locationsList : MutableList<LocationItem> = mutableListOf()
            fhirEngine.search<Location> {  }
                .mapIndexed { index, fhirLocation -> fhirLocation.resource.toLocationItem(index + 1) }
                .let { locationsList.addAll(it) }
            locations.value = locationsList
        }

    }

    private fun Location.toLocationItem(position: Int): LocationItem {
        return LocationItem(
            id = position.toString(),
            resourceId = idElement.idPart,
            status = status.name,
            name = name,
            description = description
        )
    }

    data class LocationItem(
        val id:String,
        val resourceId: String,
        val status:String,
        val name:String,
        val description:String,
    )

    class LocationViewModelFactory(
        private val application: Application,
        private val fhirEngine: FhirEngine,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LocationViewModel::class.java)) {
                return LocationViewModel(application, fhirEngine) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

}