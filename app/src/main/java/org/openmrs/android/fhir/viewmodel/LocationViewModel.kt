package org.openmrs.android.fhir.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.rest.gclient.StringClientParam
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.search
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Location
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys

class LocationViewModel(application: Application, private val fhirEngine: FhirEngine) : AndroidViewModel(application) {
    private var masterLocationsList: MutableList<LocationItem> = mutableListOf()
    var favoriteLocationSet: MutableSet<String>? = null
    init {
        getLocations()
    }

    val locations = MutableLiveData<List<LocationItem>>()

    private fun getLocations() {
        viewModelScope.launch {
            val locationsList: MutableList<LocationItem> = mutableListOf()
            fhirEngine.search<Location> {
                sort(
                    StringClientParam(Location.SP_NAME),
                    Order.ASCENDING
                )
            }
                .mapIndexed { index, fhirLocation -> fhirLocation.resource.toLocationItem(index + 1) }
                .let { locationsList.addAll(it) }
            masterLocationsList = locationsList
            locations.value = locationsList
        }

    }

    fun setFavoriteLocations(context: Context){
        viewModelScope.launch {
            favoriteLocationSet = context.dataStore.data.first()[PreferenceKeys.FAVORITE_LOCATIONS]?.toMutableSet()
            if (favoriteLocationSet == null)
                favoriteLocationSet = mutableSetOf()
        }
    }

    fun getFavoriteLocationsList(query: String = ""): List<LocationItem> {
        return masterLocationsList.filter {
            (favoriteLocationSet?.contains(it.resourceId) ?: false) and it.name.contains(query, true)
        }
    }

    fun getLocationsListFiltered(query: String = ""): List<LocationItem> {
        return if (favoriteLocationSet == null){
            masterLocationsList.filter {
                it.name.contains(query, true)
            }
        } else {
            masterLocationsList.filter {
                (!favoriteLocationSet?.contains(it.resourceId)!!) and it.name.contains(query, true)
            }
        }

    }

    private fun Location.toLocationItem(position: Int): LocationItem {
        return LocationItem(
            id = position.toString(),
            resourceId = idElement.idPart,
            status = status.name,
            name = name,
            description ?: ""
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