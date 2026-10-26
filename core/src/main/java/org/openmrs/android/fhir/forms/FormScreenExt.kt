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
package org.openmrs.android.fhir.forms

import android.content.Context
import android.view.View
import kotlinx.coroutines.flow.first
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.ui.components.LocationBannerState

/** Shows or hides the progress bar and the layer that blocks touches while a form is saved. */
fun setFormBusy(progressBar: View, touchBlocker: View, busy: Boolean) {
  val visibility = if (busy) View.VISIBLE else View.GONE
  progressBar.visibility = visibility
  touchBlocker.visibility = visibility
}

/** The location chosen on this device, as saved with the preferences. */
data class SavedLocation(val id: String?, val name: String?) {
  /** What the banner above a form says about it. */
  fun bannerState(context: Context): LocationBannerState =
    if (name.isNullOrBlank()) {
      LocationBannerState.None(guest = GuestSession.isActive(context))
    } else {
      LocationBannerState.Selected(name)
    }
}

/** One read of the saved preferences for the location's id and name. */
suspend fun Context.savedLocation(): SavedLocation {
  val saved = applicationContext.dataStore.data.first()
  return SavedLocation(saved[PreferenceKeys.LOCATION_ID], saved[PreferenceKeys.LOCATION_NAME])
}
