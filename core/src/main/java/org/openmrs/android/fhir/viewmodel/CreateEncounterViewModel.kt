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
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.data.database.model.FormData
import org.openmrs.android.fhir.extensions.readFileFromAssets

class CreateEncounterViewModel
@Inject
constructor(
  private val applicationContext: Context,
) : ViewModel() {

  private val _formData = MutableLiveData<FormData?>()
  val formData: LiveData<FormData?> = _formData

  private val _isLoading = MutableLiveData<Boolean>()
  val isLoading: LiveData<Boolean> = _isLoading

  private val _error = MutableLiveData<String>()
  val error: LiveData<String> = _error

  fun loadFormData() {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        val jsonString = applicationContext.readFileFromAssets("forms_config.json")
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(FormData::class.java)
        val formData = adapter.fromJson(jsonString)
        _formData.value = formData
        _isLoading.value = false
      } catch (e: Exception) {
        _error.value = "Failed to load form data: ${e.localizedMessage}"
        _isLoading.value = false
      }
    }
  }
}
