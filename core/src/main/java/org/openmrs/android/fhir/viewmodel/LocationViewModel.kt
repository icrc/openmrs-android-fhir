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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.rest.gclient.StringClientParam
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.search
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Location
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys

class LocationViewModel @Inject constructor(private val fhirEngine: FhirEngine) : ViewModel() {
  private var masterLocationsList: MutableList<LocationItem> = mutableListOf()
  var favoriteLocationSet: MutableSet<String>? = null

  val locations = MutableLiveData<List<LocationItem>>()

  fun getLocations() {
    viewModelScope.launch {
      val locationsList: MutableList<LocationItem> = mutableListOf()
      fhirEngine
        .search<Location> {
          sort(
            StringClientParam(Location.SP_NAME),
            Order.ASCENDING,
          )
        }
        .mapIndexed { index, fhirLocation -> fhirLocation.resource.toLocationItem(index + 1) }
        .let { locationsList.addAll(it) }
      masterLocationsList = locationsList
      locations.value = locationsList
    }
  }

  fun setFavoriteLocations(context: Context) {
    viewModelScope.launch {
      favoriteLocationSet =
        context.dataStore.data.first()[PreferenceKeys.FAVORITE_LOCATIONS]?.toMutableSet()
      if (favoriteLocationSet == null) {
        favoriteLocationSet = mutableSetOf()
      }
    }
  }

  fun getFavoriteLocationsList(query: String = ""): List<LocationItem> {
    return masterLocationsList.filter {
      (favoriteLocationSet?.contains(it.resourceId) ?: false) and it.name.contains(query, true)
    }
  }

  fun getLocationsListFiltered(query: String = ""): List<LocationItem> {
    return if (favoriteLocationSet == null) {
      masterLocationsList.filter { it.name.contains(query, true) }
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
      description ?: "",
    )
  }

  data class LocationItem(
    val id: String,
    val resourceId: String,
    val status: String,
    val name: String,
    val description: String,
  )
}
