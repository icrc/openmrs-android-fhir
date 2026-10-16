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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.data.FormCatalog
import org.openmrs.android.fhir.data.database.model.FormItem
import timber.log.Timber

/** The forms the user can pick favorites from (Settings), and which ones are picked. */
class FavoriteFormsViewModel
@Inject
constructor(
  applicationContext: Context,
  private val formCatalog: FormCatalog,
) : ViewModel() {
  private val demoDataStore = DemoDataStore(applicationContext)

  private val _forms = MutableStateFlow<List<FormItem>?>(null)

  /** Every form that can be a favorite; null while loading. */
  val forms: StateFlow<List<FormItem>?> = _forms

  /** The favorite forms' ids, in the order they were picked. */
  val favoriteIds: StateFlow<List<String>> =
    demoDataStore
      .getFavoriteFormIdsFlow()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun load() {
    viewModelScope.launch {
      try {
        _forms.value = withContext(Dispatchers.Default) { formCatalog.load().allForms }
      } catch (e: Exception) {
        Timber.e(e, "Could not load the forms")
        _forms.value = emptyList()
      }
    }
  }

  fun setFavorites(ids: List<String>) {
    viewModelScope.launch {
      demoDataStore.setFavoriteFormIds(ids.distinct().take(DemoDataStore.MAX_FAVORITE_FORMS))
    }
  }
}
